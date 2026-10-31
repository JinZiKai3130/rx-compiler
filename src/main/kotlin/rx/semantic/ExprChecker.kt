package rx.semantic

import rx.ast.*

/**
 * 阶段 8（二）：表达式检查。
 *
 * 核心是 [check]：`check(expr, expected) -> Type`，算完写回 [ExprNode.type]。
 * 批次 A 已实现：字面量、路径、块、if / while / loop / break / continue / return；
 * 其余节点（运算符、调用、字段、下标、强转、数组、结构体字面量）暂时返回
 * [UnknownType]，由批次 B–F 逐个替换。
 *
 * 块也是表达式（[BlockExprNode]），但块的作用域与语句归 [BodyChecker] 管，
 * 这里只回调 `body.checkBlock`，两边互相调用所以持有它的引用。
 */
class ExprChecker(
    private val ctx: SemanticContext,
    private val body: BodyChecker,
) {

    /** 表达式入口：算出类型并写回 [ExprNode.type]。 */
    fun check(expr: ExprNode, expected: Type? = null): Type {
        val type = when (expr) {
            is IntLitNode -> intLiteralType(expr, expected)
            is BoolLitNode -> BoolType
            is UnitLitNode -> UnitType

            is PathExprNode -> checkPathExpr(expr)
            is BlockExprNode -> body.checkBlock(expr, expected)

            is IfExprNode -> checkIf(expr, expected)
            is WhileExprNode -> checkWhile(expr, expected)
            is LoopExprNode -> checkLoop(expr, expected)
            is BreakExprNode -> checkBreak(expr)
            is ContinueExprNode -> checkContinue(expr)
            is ReturnExprNode -> checkReturn(expr)

            is AssignExprNode -> {
                checkAssign(expr)
                UnitType                       // 赋值表达式的值恒为 ()
            }

            is CallExprNode -> checkCallExpr(expr)
            is StructLitNode -> checkStructLitExpr(expr)

            is UnaryExprNode -> checkUnary(expr)
            is ReferenceExprNode -> checkReference(expr)
            is BinaryExprNode -> checkBinary(expr, expected)
            is FieldExprNode -> placeOfField(expr).type
            is IndexExprNode -> placeOfIndex(expr).type
            is MethodCallExprNode -> checkMethodCall(expr)
            is CastExprNode -> checkCast(expr)
            is ArrayLitNode -> checkArrayLit(expr, expected)

            // 不可达：ExprNode 不是 sealed，编译器要求一个 else
            else -> UnknownType
        }

        expr.type = type
        return type
    }

    private fun intLiteralType(node: IntLitNode, expected: Type?): Type = when (node.suffix) {
        "i32" -> I32Type
        "u32" -> U32Type
        "isize" -> IsizeType
        "usize" -> UsizeType
        null -> expected as? IntegerType ?: I32Type   // 期望类型 → 默认 i32
        else -> throw SemanticError(node.span, "invalid integer suffix '${node.suffix}'")
    }

    private fun checkIf(node: IfExprNode, expected: Type?): Type {
        val condType = check(node.condition, BoolType)
        requireAssignable(condType, BoolType, node.condition.span, "if condition")

        val thenType = body.checkBlock(node.thenblock, expected)
        val elseBranch = node.elseBranch
        if (elseBranch == null) { // 没有else分支，直接走
            requireAssignable(thenType, UnitType, node.thenblock.span, "if without else")
            return UnitType
        }

        val elseType = check(elseBranch, expected)
        return join(thenType, elseType, node.span, "if branches")
    }

    private fun checkWhile(node: WhileExprNode, expected: Type?): Type {
        // while 的条件在任何循环之外求值：条件里的 break/continue 不能指向任何循环
        val savedLoops = ctx.loopStack.toMutableList()
        ctx.loopStack.clear()
        val condType = try {
            check(node.condition, BoolType)
        } finally {
            ctx.loopStack.addAll(savedLoops)
        }
        requireAssignable(condType, BoolType, node.condition.span, "while condition")

        // 这里才进入循环体
        ctx.loopStack.addLast(
            LoopContext(acceptsValue = false, expected = expected, span = node.span)
        )
        try {
            val bodyType = body.checkBlock(node.block, UnitType)
            requireAssignable(bodyType, UnitType, node.block.span, "while body")
        } finally {
            ctx.loopStack.removeLast()
        }
        return UnitType                       // while 恒 ()
    }

    private fun checkLoop(node: LoopExprNode, expected: Type?): Type {
        val loop = LoopContext(acceptsValue = true, expected = expected, span = node.span)
        ctx.loopStack.addLast(loop)
        try {
            val bodyType = body.checkBlock(node.block, UnitType)
            requireAssignable(bodyType, UnitType, node.block.span, "loop body")
        } finally {
            ctx.loopStack.removeLast()
        }

        var result: Type = NeverType
        for (breakType in loop.breakTypes) {
            result = join(result, breakType, node.span, "break values")
        }
        return result
    }

    private fun checkBreak(node: BreakExprNode): Type {
        val loop = ctx.loopStack.lastOrNull()
            ?: throw SemanticError(node.span, "`break` outside a loop")

        val value = node.value
        if (value != null) {
            if (!loop.acceptsValue) {
                throw SemanticError(node.span, "`while` does not accept a break value")
            }
            val valueType = check(value, loop.expected)
            loop.expected?.let {
                requireAssignable(valueType, it, value.span, "break value")
            }
            loop.breakTypes.add(valueType)
        }
        return NeverType
    }

    private fun checkContinue(node: ContinueExprNode): Type {
        if (ctx.loopStack.isEmpty()) {
            throw SemanticError(node.span, "`continue` outside a loop")
        }
        return NeverType
    }

    private fun checkReturn(node: ReturnExprNode): Type {
        val valueType = node.value?.let { check(it, ctx.currentReturnType) } ?: UnitType
        requireAssignable(valueType, ctx.currentReturnType, node.span, "return value")
        return NeverType
    }
    
    /** `&expr` / `&mut expr`：先只给出类型；place 与可变性检查在批次 C。 */
    private fun checkReference(node: ReferenceExprNode): Type {
        val place = checkPlace(node.operand)          // 永远先检查/定型（共用 & 和 &mut）
        val inner = node.operand.type!!

        // 仅仅是&
        if (!node.mutable) return RefType(false, inner)   // &e：只要表达式合法即可

        // &mut
        if (place != null && !place.mutable) {
            throw SemanticError(node.operand.span, "cannot mutably borrow an immutable place")
        }
        return RefType(true, inner)
    }

    private fun checkUnary(node: UnaryExprNode): Type = when (node.op) {
        UnOp.NEG -> { // 负号
            val operand = stripSharedRef(
                check(node.operand),
                node.operand.span,
                "unary '-' operand",
            )
            if (operand != UnknownType) {
                val integer = operand as? IntegerType
                    ?: throw SemanticError(
                        node.operand.span,
                        "unary '-' requires an integer, found $operand",
                    )
                if (!integer.signed) {
                    throw SemanticError(
                        node.operand.span,
                        "unary '-' requires a signed integer, found $operand",
                    )
                }
            }
            operand
        }

        UnOp.NOT -> { // !的处理
            val operand = stripSharedRef(
                check(node.operand),
                node.operand.span,
                "unary '!' operand",
            )
            if (operand != UnknownType && operand !is IntegerType && operand != BoolType) {
                throw SemanticError(
                    node.operand.span,
                    "unary '!' requires an integer or bool, found $operand",
                )
            }
            operand
        }

        UnOp.DEREF -> placeOfDeref(node).type
    }

    private fun checkBinary(node: BinaryExprNode, expected: Type?): Type = when (node.op) {
        BinOp.ADD, BinOp.SUB, BinOp.MUL, BinOp.DIV, BinOp.MOD -> checkArithmetic(node, expected)
        BinOp.BIT_AND, BinOp.BIT_OR, BinOp.BIT_XOR -> checkBitwise(node, expected)
        BinOp.SHL, BinOp.SHR -> checkShift(node, expected)
        BinOp.AND, BinOp.OR -> checkLogical(node)
        BinOp.EQ, BinOp.NE -> checkEquality(node, expected)
        BinOp.LT, BinOp.LE, BinOp.GT, BinOp.GE -> checkOrdering(node, expected)
    }

    private fun checkArithmetic(node: BinaryExprNode, expected: Type?): Type {
        val left = stripSharedRef(check(node.left, expected), node.left.span, "left operand")
        val right = stripSharedRef(
            check(node.right, left.takeIf { it is IntegerType }),   // 用剥过引用的左类型当期望
            node.right.span,
            "right operand",
        )

        val type = mergeOperandTypes(left, right, node.span)
        if (type != UnknownType && type !is IntegerType) {
            throw SemanticError(node.span, "arithmetic requires integers, found $type")
        }
        return type
    }

    private fun checkBitwise(node: BinaryExprNode, expected: Type?): Type {
        val left = stripSharedRef(check(node.left, expected), node.left.span, "left operand")
        val right = stripSharedRef(
            check(node.right, left.takeIf { it is IntegerType || it == BoolType }),
            node.right.span,
            "right operand",
        )

        val type = mergeOperandTypes(left, right, node.span)
        if (type != UnknownType && type !is IntegerType && type != BoolType) {
            throw SemanticError(node.span, "bitwise operators require integers or bool, found $type")
        }
        return type
    }

    private fun checkShift(node: BinaryExprNode, expected: Type?): Type {
        val left = stripSharedRef(check(node.left, expected), node.left.span, "shift left operand")
        val count = stripSharedRef(check(node.right), node.right.span, "shift count")

        if (left != UnknownType && left !is IntegerType) {
            throw SemanticError(node.left.span, "shift left operand must be an integer, found $left")
        }
        // 计数只需是整数，**不要求**与左操作数同型（3i32 << 33u32 是合法用例）
        if (count != UnknownType && count !is IntegerType) {
            throw SemanticError(node.right.span, "shift count must be an integer, found $count")
        }
        return left
    }

    private fun checkLogical(node: BinaryExprNode): Type {
        // && / || 没有引用变体：这里**不**剥引用
        val left = check(node.left, BoolType)
        val right = check(node.right, BoolType)
        val op = if (node.op == BinOp.AND) "&&" else "||"

        requireAssignable(left, BoolType, node.left.span, "left operand of '$op'")
        requireAssignable(right, BoolType, node.right.span, "right operand of '$op'")
        return BoolType
    }

    private fun checkEquality(node: BinaryExprNode, expected: Type?): Type {
        val left = check(node.left, expected)
        val right = check(node.right, left)

        if (!assignable(right, left) && !assignable(left, right)) {
            throw SemanticError(
                node.span,
                "equality operands must have the same type: $left vs $right",
            )
        }
        requireEquatable(left, node.span)
        return BoolType
    }

    /**
     * `==` / `!=` 的能力检查：引用比地址，不需要被指类型实现 PartialEq；
     * 其余类型按 [supportsPartialEq] 递归（容器看元素，结构体看 derive）。
     */
    private fun requireEquatable(type: Type, span: Span) {
        if (!supportsPartialEq(type)) {
            throw SemanticError(
                span,
                "equality requires PartialEq, but '$type' does not implement it",
            )
        }
    }

    private fun checkOrdering(node: BinaryExprNode, expected: Type?): Type {
        // 比较是**不对称**的：左边先定型，右边向左边调整
        val left = check(node.left, expected)
        val right = check(node.right, left)

        requireAssignable(right, left, node.right.span, "right operand")
        requireComparable(left, node.left.span, "left operand")
        return BoolType
    }

    /** 去掉全部引用层后必须是整数或布尔。 */
    private fun requireComparable(type: Type, span: Span, what: String) {
        if (type == UnknownType) return
        var base: Type = type
        while (base is RefType) base = base.inner
        if (base !is IntegerType && base != BoolType) {
            throw SemanticError(span, "$what cannot be ordered: $type")
        }
    }

    /** 两侧必须同型；任一侧未知时整表达式未知（避免误伤未实现节点的类型）。 */
    private fun mergeOperandTypes(left: Type, right: Type, span: Span): Type = when {
        left == UnknownType || right == UnknownType -> UnknownType
        left == right -> left
        else -> throw SemanticError(span, "operands must have the same type: $left vs $right")
    }

    private fun checkCast(node: CastExprNode): Type {
        val target = ctx.resolveType(node.targetType)
        if (target !is IntegerType) {
            throw SemanticError(
                node.targetType.span,
                "casts must target an integer type, found $target",
            )
        }

        val source = check(node.expr)
        if (source != UnknownType && source !is IntegerType && source != BoolType) {
            throw SemanticError(
                node.expr.span,
                "only integers and bool can be cast, found $source",
            )
        }
        return target
    }

    private fun checkAssign(node: AssignExprNode): Type {
        val place = checkPlace(node.target)
            ?: throw SemanticError(node.target.span, "assignment target is not a place")
        if (!place.mutable) {
            throw SemanticError(node.target.span, "cannot assign to an immutable place")
        }

        if (node.op == AssignOp.ASSIGN) {
            val valueType = check(node.value, place.type)        // 让无后缀字面量按目标定型
            requireAssignable(valueType, place.type, node.value.span, "assignment")
            return UnitType
        }
        // 复合赋值 ↓
        checkCompoundAssignment(node, place.type)
        return UnitType
    }

    private fun checkCompoundAssignment(node: AssignExprNode, targetType: Type) {
        if (targetType == UnknownType) {
            check(node.value)
            return
        }

        val rhs = stripSharedRef(
            check(node.value, targetType.takeIf { it is IntegerType || it == BoolType }),
            node.value.span,
            "right operand of compound assignment",
        )

        when (node.op) {
            AssignOp.ADD_ASSIGN, AssignOp.SUB_ASSIGN, AssignOp.MUL_ASSIGN,
            AssignOp.DIV_ASSIGN, AssignOp.MOD_ASSIGN -> {
                if (targetType !is IntegerType) {
                    throw SemanticError(
                        node.target.span,
                        "arithmetic compound assignment requires an integer destination, found $targetType",
                    )
                }
                requireSameOperandType(rhs, targetType, node)

            }

            AssignOp.BIT_AND_ASSIGN, AssignOp.BIT_OR_ASSIGN, AssignOp.BIT_XOR_ASSIGN -> {
                if (targetType !is IntegerType && targetType != BoolType) {
                    throw SemanticError(
                        node.target.span,
                        "bitwise compound assignment requires an integer or bool destination, found $targetType",
                    )
                }
                requireSameOperandType(rhs, targetType, node)
            }

            AssignOp.SHL_ASSIGN, AssignOp.SHR_ASSIGN -> { // 左移 右移
                if (targetType !is IntegerType) {
                    throw SemanticError(
                        node.target.span,
                        "shift compound assignment requires an integer destination, found $targetType",
                    )
                }
                // 计数只要是整数即可，不要求同型
                if (rhs != UnknownType && rhs !is IntegerType) {
                    throw SemanticError(
                        node.value.span,
                        "shift count must be an integer, found $rhs",
                    )
                }
            }

            AssignOp.ASSIGN -> Unit
        }
    }

    /** RHS 与 LHS 必须同型；RHS 是脚手架 `?` 时放行。 */
    private fun requireSameOperandType(rhs: Type, lhs: Type, node: AssignExprNode) {
        if (rhs != UnknownType && rhs != lhs) {
            throw SemanticError(
                node.value.span,
                "compound assignment operands must have the same type: $lhs vs $rhs",
            )
        }
    }

    private fun checkPlace(expr: ExprNode): Place? = when (expr) {
        is PathExprNode   -> placeOfPath(expr)
        is UnaryExprNode  -> if (expr.op == UnOp.DEREF) placeOfDeref(expr)
                            else { check(expr); null }
        is FieldExprNode  -> placeOfField(expr)
        is IndexExprNode  -> placeOfIndex(expr)
        else              -> { check(expr); null }   // 字面量 / 二元 / 调用 / 块 / 强转…
    }

    private fun placeOfPath(node: PathExprNode): Place? {
        val type = check(node)
        val segment = node.segments.singleOrNull() ?: return null
        val symbol = ctx.currentScope.lookupValue(segment.name) as? LocalSymbol ?: return null
        return Place(type, symbol.mutable, underShared = false)
    }

    private fun placeOfDeref(node: UnaryExprNode): Place {
        val base = checkPlace(node.operand)          // 保证 operand.type 已写回
        val operandType = node.operand.type!!        // 契约：checkPlace 后非 null

        if (operandType == UnknownType) {            // 脚手架通配，见下
            node.type = UnknownType                  // 契约：checkPlace 返回后 type 必须非 null
            return Place(UnknownType, mutable = true, underShared = false)
        }

        val place = when (operandType) {
            is RefType -> {
                val inherited = base?.underShared ?: false
                Place(
                    type = operandType.inner,
                    mutable = operandType.mutable && !inherited,
                    underShared = inherited || !operandType.mutable,
                )
            }
            // Box<T> 的解引用是"投影"：可写性跟盒子本身走；
            // 盒子里装的是 &mut T 时，写权限来自那层 &mut（下一层 * 再算）
            is BoxType -> Place(
                type = operandType.element,
                mutable = base?.mutable ?: true,
                underShared = base?.underShared ?: false,
            )
            else -> throw SemanticError(node.span, "cannot dereference a value of type $operandType")
        }
        node.type = place.type        // ← 契约：checkPlace 返回后 type 一定非 null
        return place
    }

    /** 脚手架：接收者类型未知时，整条投影链保持"未知但可写"，避免误报。 */
    private fun unknownPlace(): Place =
        Place(UnknownType, mutable = true, underShared = false)

    /**
     * 投影的共同第一步：剥掉接收者类型外层的引用，得到"容器本身"与
     * "到达容器时手里的权限"。引用层会**覆盖**继承来的权限：
     *
     *   `&T`     → 只读，并且毒化后续（里面的 `&mut` 是假的）
     *   `&mut T` → 可写（绑定是不是 `mut` 无关：`let r: &mut T` 也能写）
     *
     * 返回 (容器类型, 容器位置的 Place)；容器类型是 `?` 时第二个元素无意义。
     */
    private fun containerPlace(base: Place?, baseType: Type): Pair<Type, Place?> {
        var type = baseType
        var place = base
        while (true) {
            when (type) {
                is RefType -> {
                    val inherited = place?.underShared ?: false
                    place = Place(
                        type = type.inner,
                        mutable = type.mutable && !inherited,
                        underShared = inherited || !type.mutable,
                    )
                    type = type.inner
                }

                is BoxType -> {
                    // `*Box` 也是拥有权下移：`values[0]` 里的 `values: Box<Vec<T>>`
                    // 等价于对盒子内容取下标，权限照抄盒子本身，不毒化。
                    place = Place(
                        type = type.element,
                        mutable = place?.mutable ?: true,
                        underShared = place?.underShared ?: false,
                    )
                    type = type.element
                }

                else -> return type to place
            }
        }
    }

    /**
     * 拥有权直接下移的投影（struct 字段 / 数组元素 / `*Box`）：
     * 里面的东西本来就是你的，所以权限原样继承，**不**毒化后续。
     *
     * 这就是 `fn f(values: [&mut i32; 1]) { *values[0] = 2; }` 合法、
     * 而 `fn f(values: Vec<&mut i32>) { *values[0] = 2; }` 非法的差别来源。
     */
    private fun ownedProjection(type: Type, container: Place?): Place = Place(
        type = type,
        // base == null：接收者是临时值（`make().field`），归我们所有 → 可写
        mutable = container?.mutable ?: true,
        underShared = container?.underShared ?: false,
    )

    /**
     * 借用型投影（Vec 元素）：元素是"借"出来的，要碰它就得先能可变地借到
     * 容器本身。容器只读时，元素位置只读，且其中的 `&mut` 一并失效。
     */
    private fun borrowingProjection(type: Type, container: Place?): Place {
        val writable = container?.mutable ?: true
        return Place(
            type = type,
            mutable = writable,
            underShared = (container?.underShared ?: false) || !writable,
        )
    }

    private fun placeOfField(node: FieldExprNode): Place {
        val base = checkPlace(node.receiver)          // ← 权限在这一行接到链上
        val (containerType, container) = containerPlace(base, node.receiver.type!!)

        val fieldType = when (containerType) {
            UnknownType -> UnknownType
            is StructType -> {
                val owner = ctx.globals.lookupType(containerType.name) as? StructSymbol
                    ?: throw SemanticError(
                        node.span,
                        "unresolved struct type '${containerType.name}'",
                    )
                owner.fields[node.name]
                    ?: throw SemanticError(
                        node.span,
                        "struct '${containerType.name}' has no field '${node.name}'",
                    )
            }

            else -> throw SemanticError(node.span, "type '$containerType' has no fields")
        }

        node.type = fieldType
        if (containerType == UnknownType) return unknownPlace()

        // 字段是拥有权下移的投影：可写性照抄容器，毒化状态也不变
        return ownedProjection(fieldType, container)
    }

    private fun placeOfIndex(node: IndexExprNode): Place {
        val base = checkPlace(node.receiver)
        val (containerType, container) = containerPlace(base, node.receiver.type!!)

        // 下标恒为 usize；传 expected，让无后缀字面量（a[0]）直接定成 usize
        // 下标类型检查
        val indexType = check(node.index, UsizeType)
        if (indexType != UnknownType && indexType != UsizeType) {
            throw SemanticError(node.index.span, "index must be usize, found $indexType")
        }

        // 数组中元素类型
        val elementType = when (containerType) {
            UnknownType -> UnknownType
            is ArrayType -> containerType.element
            is VecType -> containerType.element
            else -> throw SemanticError(node.span, "type '$containerType' is not indexable")
        }
        node.type = elementType

        // 返回container的类型，vec和[]不同
        return when (containerType) {
            UnknownType -> unknownPlace()
            is ArrayType -> ownedProjection(elementType, container)     // 数组：直接投影
            else -> borrowingProjection(elementType, container)         // Vec：借用投影
        }
    }

    private fun checkPathExpr(node: PathExprNode): Type {
        val first = node.segments.firstOrNull()
            ?: throw SemanticError(node.span, "empty path")

        return if (node.segments.size == 1) {
            checkSingleSegmentValuePath(node, first)
        } else {
            checkMultiSegmentPath(node)
        }
    }

    private fun checkSingleSegmentValuePath(
        node: PathExprNode,
        segment: PathSegment,
    ): Type {
        val name = segment.name

        if (name == "self") {
            if (!ctx.currentHasSelfParam) {
                throw SemanticError(node.span, "`self` is only allowed inside a method")
            }
            if (segment.genericArgs.isNotEmpty()) {
                throw SemanticError(node.span, "`self` takes no type arguments")
            }
            // 不再特殊返回：self 已由 BodyChecker 声明为 LocalSymbol，
            // 类型（S / &S / &mut S）跟着签名走，交给下面的普通查找。
        }

        val symbol = ctx.currentScope.lookupValue(name)
            ?: throw SemanticError(node.span, "unresolved value '$name'")

        if (segment.genericArgs.isNotEmpty()) {
            throw SemanticError(
                node.span,
                "functions and values have no type parameters",
            )
        }

        return when (symbol) {
            is LocalSymbol -> symbol.type
            is ConstantSymbol -> ctx.resolveType(symbol.node.type)
            else -> UnknownType      // 函数 / 内建函数作值：批次 D 才有意义
        }
    }

    /**
     * 解析多段路径的第一段并完成整条路径的静态校验：
     * - `Self` → 当前 impl 的 struct；
     * - 结构体名 → 该 struct；
     * - `Box` / `Vec` 等内建类型 → null（没有成员表，调用解析单独处理）。
     *
     * 校验内容：第一段泛型实参的合法性、后续段不得带泛型实参、
     * 泛型实参里的类型名递归检查。取值（[checkMultiSegmentPath]）与
     * 调用解析共用这一份逻辑。
     */
    private fun resolvePathOwner(node: PathExprNode): StructSymbol? {
        val first = node.segments.first()
        val owner: StructSymbol?

        if (first.name == "Self") {
            owner = ctx.currentSelfType
                ?: throw SemanticError(node.span, "`Self` is not allowed here")
            if (first.genericArgs.isNotEmpty()) {
                throw SemanticError(node.span, "`Self` takes no type arguments")
            }
        } else {
            val symbol = ctx.currentScope.lookupType(first.name)
                ?: throw SemanticError(
                    node.span,
                    "unresolved path '${first.name}'",
                )

            owner = when (symbol) {
                is BuiltinTypeSymbol -> {
                    when (symbol.kind) {
                        BuiltinTypeKind.BOX,
                        BuiltinTypeKind.VEC -> {
                            // 表达式位置：Box::new / Vec::new 缺少类型实参是 UB，
                            // 规范不要求报错；但多于一个一定是错误。
                            if (first.genericArgs.size > 1) {
                                throw SemanticError(
                                    node.span,
                                    "'${first.name}' takes at most one type argument here",
                                )
                            }
                        }

                        else -> {
                            if (first.genericArgs.isNotEmpty()) {
                                throw SemanticError(
                                    node.span,
                                    "primitive type '${first.name}' takes no type arguments",
                                )
                            }
                        }
                    }
                    null
                }

                is StructSymbol -> {
                    if (first.genericArgs.isNotEmpty()) {
                        throw SemanticError(
                            node.span,
                            "struct '${first.name}' has no type parameters",
                        )
                    }
                    symbol
                }

                else -> throw SemanticError(
                    node.span,
                    "unresolved path '${first.name}'",
                )
            }
        }

        // 第二段及以后：关联函数/常量/方法都没有类型参数
        for (segment in node.segments.drop(1)) {
            if (segment.genericArgs.isNotEmpty()) {
                throw SemanticError(
                    node.span,
                    "associated items have no type parameters",
                )
            }
        }

        // 泛型实参里的类型名仍然要递归检查
        for (segment in node.segments) {
            for (arg in segment.genericArgs) {
                ctx.checkTypeRef(arg)
            }
        }

        return owner
    }

    private fun checkMultiSegmentPath(node: PathExprNode): Type {
        val owner = resolvePathOwner(node)
        val member = owner?.associatedValues?.get(node.segments.last().name)
        return when (member) {
            is ConstantSymbol -> ctx.resolveType(member.node.type)
            else -> UnknownType      // 关联函数作值 / 内建构造器：批次 D
        }
    }

    /**
     * 调用表达式：先解析被调者，再用同一张参数表检查实参。
     * 调用位置不再把 callee 当"值"检查——函数在这门语言里不是值。
     */
    private fun checkCallExpr(node: CallExprNode): Type {
        val callee = node.callee
        if (callee !is PathExprNode) {
            check(callee)                                  // 先报子表达式自己的错
            throw SemanticError(callee.span, "expression is not callable")
        }
        if (callee.segments.isEmpty()) {
            throw SemanticError(callee.span, "empty path")
        }

        val signature = resolveCallTarget(callee)
        if (signature == null) {                           // 尚未支持的目标：保持 ?
            for (arg in node.args) check(arg)
            return UnknownType
        }
        checkArguments(node.args, signature, callee.span, describePath(callee))
        return signature.returnType
    }
    

    /**
     * 路径 → 调用签名。
     * 返回 null = 目标暂未支持（内建容器除 new 以外的关联项）；
     * 非法调用（不可调用 / 无此成员）在这里直接报错。
     *
     * 注意 `Type::method(receiver, args)`：这是“把方法当关联函数调用”的形式，
     * self 由调用者显式传第一个实参，不做 autoref/autoderef，所以这里把
     * self 的类型前插到形参表，而不是拒绝。
     */
    private fun resolveCallTarget(callee: PathExprNode): FunctionSignature? {
        // ---- 单段：普通函数 / 内建函数 ----
        if (callee.segments.size == 1) {
            val segment = callee.segments[0]
            if (segment.genericArgs.isNotEmpty()) {
                throw SemanticError(callee.span, "functions and values have no type parameters")
            }
            
            val symbol = ctx.currentScope.lookupValue(segment.name)
            return when (symbol) {
                null -> throw SemanticError(callee.span, "unresolved value '${segment.name}'")
                is FunctionSymbol ->
                    symbol.signature
                        ?: throw SemanticError(callee.span, "function '${segment.name}' has no signature")
                is BuiltinFunctionSymbol ->
                    FunctionSignature(symbol.paramTypes, symbol.returnType, null)
                else -> throw SemanticError(callee.span, "'${segment.name}' is not callable")
            }
        }

        // ---- 多段：关联函数 / 构造器 ----
        val owner = resolvePathOwner(callee)               // 路径校验已经在这里完成
        val name = callee.segments.last().name
        val first = callee.segments.first()

        if (owner == null) {                               // Box / Vec 等内建类型
            val kind = (ctx.globals.lookupType(first.name) as? BuiltinTypeSymbol)?.kind
            val element = first.genericArgs.firstOrNull()
                ?.let { ctx.resolveType(it) } ?: UnknownType   // 缺省 → ?
            val selfType = when (kind) {
                BuiltinTypeKind.BOX -> BoxType(element)
                BuiltinTypeKind.VEC -> VecType(element)
                else -> throw SemanticError(
                    callee.span,
                    "primitive type '${first.name}' has no associated items",
                )
            }
            // new / clone / len / push / remove 共用一张内建方法表
            val signature = Builtins.builtinMethodSignature(selfType, name)
                ?: return null
            // 容器的 clone 要求元素 Clone（空容器也一样）
            if (name == "clone") requireCloneable(selfType, callee.span)
            return withExplicitSelf(signature, selfType)
        }

        val ownerType = StructType(owner.name, owner.node)
        val member = owner.associatedValues[name]
        if (member == null) {
            // derive 生成的方法不在 associatedValues 里，需要单独识别
            val derived = derivedMethodSignature(owner, name)
                ?: throw SemanticError(
                    callee.span,
                    "no associated item '$name' in '${owner.name}'",
                )
            return withExplicitSelf(derived, ownerType)
        }

        return when (member) {
            is FunctionSymbol -> withExplicitSelf(
                member.signature
                    ?: throw SemanticError(callee.span, "'$name' has no signature"),
                ownerType,
            )
            else -> throw SemanticError(callee.span, "'$name' is not callable")
        }
    }

    /**
     * `Type::method(receiver, args)`：self 变成显式的第一个实参。
     * 引用由调用者自己写（S / &S / &mut S），这里不做 autoref。
     */
    private fun withExplicitSelf(
        signature: FunctionSignature,
        selfType: Type,
    ): FunctionSignature {
        val selfParam = signature.selfParam ?: return signature
        val explicit = if (selfParam.byReference) {
            RefType(selfParam.mutable, selfType)
        } else {
            selfType
        }
        return FunctionSignature(
            paramTypes = listOf(explicit) + signature.paramTypes,
            returnType = signature.returnType,
            selfParam = null,                    // 已摊进 paramTypes
        )
    }

    /**
     * derive 自动生成的方法：目前只有 `clone(&self) -> Self`
     * （`derive(Clone)`；`Copy` 隐含 `Clone`）。
     */
    private fun derivedMethodSignature(
        owner: StructSymbol,
        name: String,
    ): FunctionSignature? {
        if (name != "clone") return null
        val derives = owner.node.derives
        if (derives.none { it == Derive.CLONE || it == Derive.COPY }) return null
        return FunctionSignature(
            paramTypes = emptyList(),
            returnType = StructType(owner.name, owner.node),
            selfParam = SelfParam(byReference = true, mutable = false),
        )
    }

    /** 所有调用共用的实参检查：先比数量，再逐个定型并校验可赋值性。 */
    private fun checkArguments(
        args: List<ExprNode>,
        signature: FunctionSignature,
        span: Span,
        what: String,
    ) {
        val params = signature.paramTypes
        if (args.size != params.size) {
            throw SemanticError(
                span,
                "$what expects ${params.size} argument(s), found ${args.size}",
            )
        }
        for ((index, arg) in args.withIndex()) {
            val expected = params[index]
            val actual = check(arg, expected)
            requireAssignable(actual, expected, arg.span, "argument ${index + 1} of $what")
            requireMovable(arg, expected, actual, "argument ${index + 1} of $what")
        }
    }

    /** 只有这几种语法形式能构成 place（`*p` 单独算一种）。 */
    private fun isPlaceShaped(expr: ExprNode): Boolean = when (expr) {
        is PathExprNode, is FieldExprNode, is IndexExprNode -> true
        is UnaryExprNode -> expr.op == UnOp.DEREF
        else -> false
    }

    /**
     * 值被**搬走**时的权限检查（实参传递 / let 初始化）。
     *
     * 只读位置里放着的 `&mut T` 不是你的（[Place.underShared]），它只能被
     * 重借用成 `&T` 交出去；原样传给 `&mut T` 形参、或原样初始化 `&mut T`
     * 变量，等于把那个 `&mut` 整体搬迁，需要可变权限。
     *
     * rej_example
     * let mut value = String::from("hello");
        let mut_ref = &mut value;
        let moved = *mut_ref;
        // 这里默认移动语义，导致value变成空，然后mut_ref指向空

     * 对照：
     *   `fn f(values: Vec<&mut i32>) { write(values[0]); }`      → 拒绝
     *   `fn f(values: [&mut i32; 1]) { write(values[0]); }`      → 允许（拥有权下移）
     *   `fn f(v: Vec<&mut C>) { let s: &C = v[0]; }`             → 允许（重借用为共享）
     */
    fun requireMovable(expr: ExprNode, expected: Type?, type: Type, what: String) {
        if (type !is RefType || !type.mutable) return          // 只有 `&mut` 会被搬走
        if (expected is RefType && !expected.mutable) return   // `&mut` → `&`：重借用，合法
        if (!isPlaceShaped(expr)) return                       // 普通右值：没有位置权限问题

        val place = checkPlace(expr) ?: return
        if (place.underShared) {
            throw SemanticError(
                expr.span,
                "$what cannot move a mutable reference out of a read-only place",
            )
        }
    }

    /** 只用于诊断的路径文本。 */
    private fun describePath(node: PathExprNode): String =
        node.segments.joinToString("::") { it.name }

    private fun checkMethodCall(node: MethodCallExprNode): Type {
        if (node.name.genericArgs.isNotEmpty()) {
            throw SemanticError(node.span, "method segments have no type parameters")
        }

        // 一次遍历：类型 + place（&mut self 判定要用）
        val place = checkPlace(node.receiver)
        val receiverType = node.receiver.type!!

        val signature = resolveMethod(receiverType, node.name.name, node.span)
            ?: run {
                for (arg in node.args) check(arg)        // 脚手架：只检查实参
                return UnknownType
            }

        val selfParam = signature.selfParam
            ?: throw SemanticError(node.span, "'${node.name.name}' is not a method")

        if (selfParam.byReference && selfParam.mutable &&
            !hasMutableAccess(receiverType, place)
        ) {
            throw SemanticError(
                node.receiver.span,
                "method '${node.name.name}' requires mutable access",
            )
        }

        checkArguments(node.args, signature, node.span, "method '${node.name.name}'")
        return signature.returnType
    }

    private fun resolveMethod(
        receiverType: Type,
        name: String,
        span: Span,
    ): FunctionSignature? {
        var current: Type = receiverType
        var sharedRef: RefType? = null       // 走过的共享引用，clone 兜底要用

        while (true) {
            when (current) {
                UnknownType -> return null                       // 脚手架：整体 ?
                is RefType -> {
                    if (!current.mutable) sharedRef = current
                    current = current.inner                      // autoref 的 deref 部分
                }
                is StructType -> {
                    val owner = ctx.globals.lookupType(current.name) as? StructSymbol
                        ?: return null
                    structMethodSignature(owner, name, span)?.let { return it }
                    break                                        // 没找到 → 走 clone 兜底
                }
                is VecType, is BoxType -> {
                    val found = Builtins.builtinMethodSignature(current, name)
                    if (found != null) {
                        if (found.selfParam == null) {
                            throw SemanticError(span, "'$name' is not a method")
                        }
                        // 容器的 clone 要求元素 Clone（空容器也一样）
                        if (name == "clone") requireCloneable(current, span)
                        return found
                    }
                    if (current is BoxType) {
                        current = current.element                // Box 可以再解一层
                    } else {
                        break                                    // Vec 不可解引用
                    }
                }
                is ArrayType -> {
                    // 数组只有 len（元素能力检查留给批次 F）
                    if (name == "len") {
                        return FunctionSignature(emptyList(), UsizeType, SHARED_SELF)
                    }
                    break
                }
                else -> break
            }
        }

        // clone 兜底：标量 / 数组是 Copy；共享引用本身也可克隆
        if (name == "clone") {
            if (current is IntegerType || current == BoolType || current is ArrayType) {
                requireCloneable(current, span)          // 数组要求元素 Clone
                return FunctionSignature(emptyList(), current, SHARED_SELF)
            }
            // 引用复制：被指向的类型可不可 clone 与此无关，直接豁免能力检查
            sharedRef?.let { return FunctionSignature(emptyList(), it, SHARED_SELF) }
        }
        throw SemanticError(span, "type '$receiverType' has no method '$name'")
    }

    /** 内建容器 / 数组的 `clone` 要求元素 Clone（空容器也一样）。 */
    private fun requireCloneable(type: Type, span: Span) {
        if (!supportsClone(type)) {
            throw SemanticError(
                span,
                "clone requires Clone, but '$type' does not implement it",
            )
        }
    }

    private fun structMethodSignature(
        owner: StructSymbol,
        name: String,
        span: Span,
    ): FunctionSignature? {
        val member = owner.associatedValues[name]
        val signature = when (member) {
            is FunctionSymbol -> member.signature ?: return null
            null -> derivedMethodSignature(owner, name) ?: return null   // derive 生成
            else -> return null                                          // 常量不是方法
        }
        if (signature.selfParam == null) {
            throw SemanticError(span, "'$name' is not a method")          // 关联函数不能点调用
        }
        return signature
    }

    private fun hasMutableAccess(type: Type, place: Place?): Boolean {
        var current = type
        var throughRef = false
        while (current is RefType) {
            if (!current.mutable) return false
            if (place?.underShared == true) return false
            throughRef = true
            current = current.inner
        }
        return if (throughRef) true else (place?.mutable ?: true)
    }

    private fun checkStructLitExpr(node: StructLitNode): Type {
        val name = node.path.lastOrNull()
            ?: throw SemanticError(node.span, "empty struct path")

        val symbol: StructSymbol = if (name == "Self") {
            ctx.currentSelfType
                ?: throw SemanticError(node.span, "`Self` is not allowed here")
        } else {
            val looked = ctx.currentScope.lookupType(name)
            if (looked !is StructSymbol) {
                throw SemanticError(node.span, "unresolved struct type '$name'")
            }
            looked
        }

        val seen = HashSet<String>()
        for (field in node.fields) {
            // ① 字段必须存在
            val declared = symbol.fields[field.name]
                ?: throw SemanticError(
                    field.value.span,
                    "struct '${symbol.name}' has no field '${field.name}'",
                )
            // ② 同名字段不能写两次
            if (!seen.add(field.name)) {
                throw SemanticError(
                    field.value.span,
                    "duplicate field '${field.name}' in '${symbol.name}'",
                )
            }
            // ③ 字段类型必须匹配（declared 当 expected，正好给字面量定型）
            val actual = check(field.value, declared)     // declared 当 expected，字面量才好定型
            requireAssignable(actual, declared, field.value.span, "field '${field.name}'")
        }

        // 少了字段就是非法初始化（本语言没有 ..Default 之类的语法）
        val missing = symbol.fields.keys - seen
        if (missing.isNotEmpty()) {
            throw SemanticError(
                node.span,
                "missing field(s) ${missing.joinToString { "'$it'" }} in '${symbol.name}'",
            )
        }

        return StructType(symbol.name, symbol.node)
    }

    /**
     * 数组字面量。两种形式：
     *
     *   `[a, b, c]` → 元素取公共类型（[join]），长度 = 元素个数；
     *   `[e; n]`    → 同值重复 n 次，n > 1 时要求元素可以 Copy。
     *
     * 有 `[T; N]` 期望类型时，用它给元素定型（`1` 才能变成 `usize`），
     * 并校验元素个数——这就是形状比较的位点。
     */
    private fun checkArrayLit(node: ArrayLitNode, expected: Type?): Type {
        val expectedArray = expected as? ArrayType

        val repeat = node.repeat
        if (repeat != null) { // 第一种形式，用重复的方式定义
            val length = ctx.constEval.requireUsize(repeat.length)
            val hint = expectedArray?.takeIf { it.length == length }?.element
            val elementType = check(repeat.element, hint)

            // 如果这个数组长度大于一，要求赋值，则要满足isCopy否则直接throw
            if (length > 1 && !isCopy(elementType)) {
                throw SemanticError(
                    node.span,
                    "repeating an array more than once requires a Copy element type, found $elementType",
                )
            }
            return ArrayType(elementType, length)
        }
        
        // 第二种形式，无重复
        var elementType: Type? = null
        for (element in node.elements) { // 内部element类型要求统一
            val type = check(element, expectedArray?.element)
            elementType = elementType
                ?.let { join(it, type, element.span, "array elements") }
                ?: type
        }

        val length = node.elements.size.toLong()
        if (expectedArray != null && expectedArray.length != length) {
            throw SemanticError(
                node.span,
                "array literal has $length element(s), expected ${expectedArray.length}",
            )
        }
        return ArrayType(elementType ?: UnknownType, length)
    }
}
