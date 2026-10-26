package rx.semantic
import rx.ast.*

/**
 * 语义分析器（2.5）总入口：对整棵 AST 查名字、查类型（semantic_design.md）。
 *
 * WP0 状态：空壳——只搭好入口与全局作用域；检查内容按 WP 分批接入：
 * - WP1 全局收集（struct/fn/const/impl → globals 的两个名字空间）、重名/保护名/main/impl 合法性；
 * - WP2 函数体作用域与名字解析（块级进出、未定义名、shadowing）；
 * - WP3+ 表达式类型检查（check(expr, expected)）、place 与可变性、常量求值、derive、布局……
 *
 * 约定：发现语义错误立即抛 [SemanticError]（fail-fast），CLI 捕获后报告并 exit 1。
 */


class SemanticAnalyzer {

    /**
     * 全局作用域（parent = null）。
     * WP1 起：先预注册内建环境（i32 等类型名、print_i32/get_i32 等函数），再收集顶层声明。
     */
    val globals: Scope = Scope(null)

    private var currentSelfType: StructSymbol? = null

    private var currentScope: Scope = globals

    /** 当前是否在带 self 参数的方法里；顶层函数为 false。 */
    private var currentHasSelfParam: Boolean = false

    // private val BUILTIN_TYPE_NAMES = listOf(
    //     "i32", "u32", "isize", "usize", "bool", "Box", "Vec",
    // )

    private val BUILTIN_VALUE_NAMES = listOf(
        "get_i32", "print_i32", "println_i32",
    )

    private val constEval: ConstEval by lazy { ConstEval(globals, ::resolveType) }

    /* 检查顶层函数体 */
    private fun checkFunctionBody(node: FnNode) {
        val savedScope = currentScope
        val savedHasSelf = currentHasSelfParam

        currentScope = globals.child()   // 函数作用域：放参数
        currentHasSelfParam = false      // 顶层函数没有 receiver

        try {
            for (param in node.params) {
                if (param is FnParam) {
                    currentScope.declareValue(
                        LocalSymbol(param.name, param.mutable, null)
                    )
                }
            }

            checkBlock(node.body)        // 函数体块，会在里面再建 child
        } finally {
            currentScope = savedScope
            currentHasSelfParam = savedHasSelf
        }
    }

    private fun checkBlock(block: BlockExprNode) {
        val savedScope = currentScope
        currentScope = savedScope.child()

        try {
            for (stmt in block.statements) {
                checkStatement(stmt)
            }
            block.tail?.let { checkExpr(it) }
        } finally {
            currentScope = savedScope
        }
    }

    private fun checkStatement(stmt: StmtNode) {
        when (stmt) {
            is LetNode -> {
                checkExpr(stmt.initializer)                 // 先查 initializer
                stmt.declaredType?.let { checkTypeRef(it) } // 可选
                currentScope.defineLocal(
                    LocalSymbol(stmt.name, stmt.mutable, stmt.span)
                )
            }
            is ExprStmtNode -> checkExpr(stmt.expr)
            else -> Unit
        }
    }

    private fun checkExpr(expr: ExprNode) {
        when (expr) {
            is PathExprNode -> checkPathExpr(expr)
            is CallExprNode -> checkCallExpr(expr)
            is StructLitNode -> checkStructLitExpr(expr)
            is BlockExprNode -> checkBlock(expr)

            is IfExprNode -> {
                checkExpr(expr.condition)
                checkBlock(expr.thenblock)
                expr.elseBranch?.let { checkExpr(it) }
            }

            is WhileExprNode -> {
                checkExpr(expr.condition)
                checkBlock(expr.block)
            }

            is LoopExprNode -> checkBlock(expr.block)
            is UnaryExprNode -> checkExpr(expr.operand)
            is ReferenceExprNode -> checkExpr(expr.operand)

            is BinaryExprNode -> {
                checkExpr(expr.left)
                checkExpr(expr.right)
            }

            is AssignExprNode -> {
                checkExpr(expr.target)
                checkExpr(expr.value)
            }

            is FieldExprNode -> checkExpr(expr.receiver)

            is IndexExprNode -> {
                checkExpr(expr.receiver)
                checkExpr(expr.index)
            }

            is MethodCallExprNode -> {
                if (expr.name.genericArgs.isNotEmpty()) {
                    throw SemanticError(
                        expr.span,
                        "method segments have no type parameters",
                    )
                }

                checkExpr(expr.receiver)
                for (arg in expr.args) checkExpr(arg)
            }

            is CastExprNode -> {
                checkExpr(expr.expr)
                checkTypeRef(expr.targetType)
            }
            is BreakExprNode -> expr.value?.let { checkExpr(it) }
            is ReturnExprNode -> expr.value?.let { checkExpr(it) }

            is ArrayLitNode -> {
                for (element in expr.elements) checkExpr(element)
                expr.repeat?.let {
                    checkExpr(it.element)
                    constEval.requireUsize(it.length)
                }
            }

            is IntLitNode,
            is BoolLitNode,
            is UnitLitNode,
            is ContinueExprNode -> Unit

            else -> Unit
        }
    }

    private fun checkPathExpr(node: PathExprNode) {
        val first = node.segments.firstOrNull()
            ?: throw SemanticError(node.span, "empty path")

        if (node.segments.size == 1) {
            checkSingleSegmentValuePath(node, first)
            return
        }

        checkMultiSegmentPath(node)
    }

    private fun checkSingleSegmentValuePath(
        node: PathExprNode,
        segment: PathSegment,
    ) {
        val name = segment.name

        if (name == "self") {
            if (!currentHasSelfParam) {
                throw SemanticError(node.span, "`self` is only allowed inside a method")
            }
            if (segment.genericArgs.isNotEmpty()) {
                throw SemanticError(node.span, "`self` takes no type arguments")
            }
            return
        }

        currentScope.lookupValue(name)
            ?: throw SemanticError(node.span, "unresolved value '$name'")

        if (segment.genericArgs.isNotEmpty()) {
            throw SemanticError(
                node.span,
                "functions and values have no type parameters",
            )
        }
    }

    private fun checkMultiSegmentPath(node: PathExprNode) {
        val first = node.segments.first()

        if (first.name == "Self") {
            if (currentSelfType == null) {
                throw SemanticError(node.span, "`Self` is not allowed here")
            }
            if (first.genericArgs.isNotEmpty()) {
                throw SemanticError(node.span, "`Self` takes no type arguments")
            }
        } else {
            val symbol = currentScope.lookupType(first.name)
                ?: throw SemanticError(
                    node.span,
                    "unresolved path '${first.name}'",
                )

            when (symbol) {
                is BuiltinTypeSymbol -> when (symbol.kind) {
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

                is StructSymbol -> {
                    if (first.genericArgs.isNotEmpty()) {
                        throw SemanticError(
                            node.span,
                            "struct '${first.name}' has no type parameters",
                        )
                    }
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
                checkTypeRef(arg)
            }
        }
    }

    private fun checkCallExpr(node: CallExprNode) {
        checkExpr(node.callee)

        // 只对单段路径做可调用性检查，后续再做多段路径
        if (node.callee is PathExprNode && node.callee.segments.size == 1) {
            val name = node.callee.segments[0].name
            val symbol = currentScope.lookupValue(name)

            if (symbol != null &&
                symbol !is FunctionSymbol &&
                symbol !is BuiltinFunctionSymbol
            ) {
                throw SemanticError(node.callee.span, "'$name' is not callable")
            }
        }

        for (arg in node.args) {
            checkExpr(arg)
        }
    }

    private fun checkStructLitExpr(node: StructLitNode) {
        val name = node.path.lastOrNull()
            ?: throw SemanticError(node.span, "empty struct path")

        if (name == "Self") {
            if (currentSelfType == null) {
                throw SemanticError(node.span, "`Self` is not allowed here")
            }
        } else {
            val symbol = currentScope.lookupType(name)
            if (symbol !is StructSymbol) {
                throw SemanticError(node.span, "unresolved struct type '$name'")
            }
        }

        for (field in node.fields) {
            checkExpr(field.value)
        }
    }

    private val BUILTIN_TYPES = mapOf(
        "i32"   to BuiltinTypeKind.I32,
        "u32"   to BuiltinTypeKind.U32,
        "isize" to BuiltinTypeKind.ISIZE,
        "usize" to BuiltinTypeKind.USIZE,
        "bool"  to BuiltinTypeKind.BOOL,
        "Box"   to BuiltinTypeKind.BOX,
        "Vec"   to BuiltinTypeKind.VEC,
    )

    private fun installBuiltins(scope: Scope) {
        for ((name, kind) in BUILTIN_TYPES) {
            scope.declareType(BuiltinTypeSymbol(name, kind))
        }
        for (name in BUILTIN_VALUE_NAMES) {
            scope.declareValue(BuiltinFunctionSymbol(name))
        }
    }

    /** 检查整个 crate。WP0：暂不产生任何错误。 */
    fun analyze(crate: CrateNode) {
        installBuiltins(globals)

        collectTopLevel(crate.items)
        registerImpls(crate.items)
        checkEntry(crate)
        constEval.evaluateAll(crate)
        checkDeclarations(crate.items)
    }

    private fun registerImpls(items: List<ItemNode>) {
        for (item in items) if (item is ImplNode) checkImpl(item)
    }

    private fun collectTopLevel(items: List<ItemNode>) {
        for (item in items) collectItem(item)
    }

    private fun collectItem(item: ItemNode) {
        when (item) {
            is StructNode -> { 
                if (!globals.declareType(StructSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate struct '${item.name}'")
                }
            }
            is FnNode     -> { 
                if (!globals.declareValue(FunctionSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate function '${item.name}'")
                }
            }
            is ConstNode  -> { 
                if (!globals.declareValue(ConstantSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate const '${item.name}'")
                }
            }
            is ImplNode   -> { /* 这一小步先什么都不做 */ }
            is UseNode    -> { /* 理论上不会出现，忽略 */ }
        }
    }

    private fun checkEntry(crate: CrateNode) {
        val main = crate.items
            .filterIsInstance<FnNode>()
            .firstOrNull { it.name == "main" }
            ?: throw SemanticError(
                crate.span,
                "an executable needs a top-level `main` function",
            )

        if (main.params.isNotEmpty()) {
            throw SemanticError(
                main.span,
                "`main` cannot have parameters",
            )
        }

        if (main.hasGenericParams) {
            throw SemanticError(
                main.span,
                "`main` cannot have generic parameters",
            )
        }

        val returnType = main.returnType
        if (returnType != null && returnType !is UnitTypeNode) {
            throw SemanticError(
                main.span,
                "`main` must return `()`",
            )
        }
    }

    private fun checkDeclarations(items: List<ItemNode>) {
        for (item in items) {
            when (item) {
                is StructNode -> checkStructFields(item)
                is FnNode     -> {
                    checkTopLevelFunction(item)
                    checkFunctionBody(item)
                }
                is UseNode    -> {}
            }
        }
    }

    private fun checkStructFields(node: StructNode) {
        val seen  = HashSet<String>()

        for(field in node.fields) {
            if (!seen.add(field.name)) {
                throw SemanticError(node.span, "duplicate field '${field.name}'")
            }
        }
    }
 
    // 这个是顶层函数（不在任何struct下的）
    private fun checkTopLevelFunction(node: FnNode) {
        checkFunctionParams(node) // 当然首先要检查重名问题

        if (node.params.any { it is SelfParam }) {
            // 检查是否有Self这个参量
            throw SemanticError(
                node.span,
                "a top-level function cannot have a receiver",
            )
        }

        for (param in node.params) {
            if (param is FnParam) {
                checkTypeRef(param.type)
            }
        }

        node.returnType?.let { checkTypeRef(it) }
    }

    // 这个函数用来检查重名
    private fun checkFunctionParams(node: FnNode) {
        val seen = HashSet<String>()          // 只在这次调用里存在

        for (param in node.params) {
            if (param !is FnParam) continue
            if (!seen.add(param.name)) {
                throw SemanticError(node.span, "duplicate parameter '${param.name}'")
            }
        }
    }

    // 这里检查参数是否是正常的Type
    fun resolveType(ref: TypeRefNode): Type = when (ref) {
        is UnitTypeNode  -> UnitType
        is RefTypeNode   -> RefType(ref.mutable, resolveType(ref.inner))
        is ArrayTypeNode -> ArrayType(
            resolveType(ref.element),
            constEval.requireUsize(ref.length),
        )
        is PathTypeNode  -> resolvePathType(ref)
        else -> throw SemanticError(ref.span, "unsupported type reference")
    }

    // 兼容旧调用点：只关心校验的地方不用改
    private fun checkTypeRef(ref: TypeRefNode) { resolveType(ref) }

    private fun resolvePathType(ref: PathTypeNode): Type {
        // 类型位置只能写单个类型名；关联项只有常量/函数，没有关联类型。
        if (ref.segments.size > 1) {
            throw SemanticError(ref.span, "qualified type paths cannot name a type")
        }

        val segment = ref.segments.firstOrNull()
            ?: throw SemanticError(ref.span, "empty type path")
        val name = segment.name

        if (name == "Self") {
            val self = currentSelfType
                ?: throw SemanticError(ref.span, "`Self` is only allowed inside a struct or impl")
            if (segment.genericArgs.isNotEmpty())
                throw SemanticError(ref.span, "`Self` takes no type arguments")
            return StructType(self.name, self.node)
        }

        val symbol = globals.lookupType(name)
            ?: throw SemanticError(ref.span, "unresolved type '$name'")

        return when (symbol) {
            is BuiltinTypeSymbol -> when (symbol.kind) {
                BuiltinTypeKind.BOX, BuiltinTypeKind.VEC -> {
                    if (segment.genericArgs.size != 1)
                        throw SemanticError(ref.span, "'$name' requires exactly one concrete type argument")
                    val element = resolveType(segment.genericArgs[0])   // 递归 + 校验
                    if (symbol.kind == BuiltinTypeKind.BOX) BoxType(element) else VecType(element)
                }
                BuiltinTypeKind.I32   -> { requireNoArgs(ref, name, segment); I32Type }
                BuiltinTypeKind.U32   -> { requireNoArgs(ref, name, segment); U32Type }
                BuiltinTypeKind.ISIZE -> { requireNoArgs(ref, name, segment); IsizeType }
                BuiltinTypeKind.USIZE -> { requireNoArgs(ref, name, segment); UsizeType }
                BuiltinTypeKind.BOOL  -> { requireNoArgs(ref, name, segment); BoolType }
            }

            is StructSymbol -> {
                if (segment.genericArgs.isNotEmpty())
                    throw SemanticError(ref.span, "struct '$name' has no type parameters")
                StructType(name, symbol.node)
            }

            else -> throw SemanticError(ref.span, "unresolved type '$name'")
        }
    }

    /** 原生类型不能带泛型实参。 */
    private fun requireNoArgs(
        ref: PathTypeNode,
        name: String,
        segment: PathSegment,
    ) {
        if (segment.genericArgs.isNotEmpty()) {
            throw SemanticError(ref.span, "primitive type '$name' takes no type arguments")
        }
    }

    private fun checkImpl(node: ImplNode) {
        val target = node.target

        // &S、[i32; 2]、() 等直接拒绝
        if (target !is PathTypeNode) {
            throw SemanticError(
                target.span,
                "impl target must be a user-defined struct",
            )
        }
        // 这里只留下合法的structTypeNode
        // 先检查类型名和泛型实参
        checkTypeRef(target)

        val name = target.segments.firstOrNull()?.name
            ?: throw SemanticError(target.span, "empty impl target")

        val symbol = globals.lookupType(name)
        if (symbol !is StructSymbol) {
            throw SemanticError(
                target.span,
                "impl target must be a user-defined struct",
            )
        }

        for (item in node.items) {
            val associated = when (item) {
                is FnNode -> FunctionSymbol(
                    name = item.name,
                    node = item,
                    receiver = node.target,
                )

                is ConstNode -> ConstantSymbol(
                    name = item.name,
                    node = item,
                    owner = symbol,
                )

                else -> null
            } ?: continue

            if (symbol.associatedValues.containsKey(associated.name)) {
                throw SemanticError(
                    item.span,
                    "duplicate associated item '${associated.name}'",
                )
            }

            symbol.associatedValues[associated.name] = associated
        }
        // 可选：这里设置 currentSelfType，收集 associated items
    }
}