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
                check(expr.target)
                check(expr.value)
                UnitType                       // 赋值表达式的值恒为 ()
            }

            is CallExprNode -> checkCallExpr(expr)
            is StructLitNode -> checkStructLitExpr(expr)

            is UnaryExprNode -> checkUnary(expr)
            is ReferenceExprNode -> checkReference(expr)
            is BinaryExprNode -> checkBinary(expr, expected)
            is FieldExprNode -> { check(expr.receiver); UnknownType }
            is IndexExprNode -> { check(expr.receiver); check(expr.index); UnknownType }
            is MethodCallExprNode -> checkMethodCall(expr)
            is CastExprNode -> checkCast(expr)
            is ArrayLitNode -> {
                for (element in expr.elements) check(element)
                expr.repeat?.let {
                    check(it.element)
                    ctx.constEval.requireUsize(it.length)
                }
                UnknownType
            }

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
        val inner = check(node.operand)
        return RefType(node.mutable, inner)
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

        UnOp.DEREF -> {
            check(node.operand)      // `*p` 的类型依赖 place，批次 C 再补
            UnknownType
        }
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
        return BoolType
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
            return UnknownType       // self 的类型随方法签名，字段访问是批次 E
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

    private fun checkMultiSegmentPath(node: PathExprNode): Type {
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

        val member = owner?.associatedValues?.get(node.segments.last().name)
        return when (member) {
            is ConstantSymbol -> ctx.resolveType(member.node.type)
            else -> UnknownType      // 关联函数作值：批次 D
        }
    }

    private fun checkCallExpr(node: CallExprNode): Type {
        check(node.callee)

        // 只对单段路径做可调用性检查，多段路径留到调用解析
        if (node.callee is PathExprNode && node.callee.segments.size == 1) {
            val name = node.callee.segments[0].name
            val symbol = ctx.currentScope.lookupValue(name)

            if (symbol != null &&
                symbol !is FunctionSymbol &&
                symbol !is BuiltinFunctionSymbol
            ) {
                throw SemanticError(node.callee.span, "'$name' is not callable")
            }
        }

        for (arg in node.args) {
            check(arg)
        }
        return UnknownType           // 返回类型：批次 D
    }

    private fun checkMethodCall(node: MethodCallExprNode): Type {
        if (node.name.genericArgs.isNotEmpty()) {
            throw SemanticError(
                node.span,
                "method segments have no type parameters",
            )
        }

        check(node.receiver)
        for (arg in node.args) check(arg)
        return UnknownType           // 方法查找与返回类型：批次 D
    }

    private fun checkStructLitExpr(node: StructLitNode): Type {
        val name = node.path.lastOrNull()
            ?: throw SemanticError(node.span, "empty struct path")

        if (name == "Self") {
            if (ctx.currentSelfType == null) {
                throw SemanticError(node.span, "`Self` is not allowed here")
            }
        } else {
            val symbol = ctx.currentScope.lookupType(name)
            if (symbol !is StructSymbol) {
                throw SemanticError(node.span, "unresolved struct type '$name'")
            }
        }

        for (field in node.fields) {
            check(field.value)
        }
        return UnknownType           // 结构体字面量类型：批次 E
    }
}
