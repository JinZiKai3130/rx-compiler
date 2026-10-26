package rx.semantic

import rx.ast.*

/**
 * 阶段 8：函数体检查（BodyChecker）。
 *
 * 目前只做名字解析：作用域进出、未定义名、`self` 的可用性、路径的泛型实参规则。
 * 表达式类型检查（`check(expr, expected): Type`）、place/可变性、方法查找后续接入。
 */
class BodyChecker(private val ctx: SemanticContext) {

    fun check(items: List<ItemNode>) {
        for (item in items) {
            if (item is FnNode) checkFunctionBody(item)
        }
    }

    private fun checkFunctionBody(node: FnNode) {
        val savedScope = ctx.currentScope
        val savedHasSelf = ctx.currentHasSelfParam

        ctx.currentScope = ctx.globals.child()   // 函数作用域：放参数
        ctx.currentHasSelfParam = false          // 顶层函数没有 receiver

        try {
            for (param in node.params) {
                if (param is FnParam) {
                    ctx.currentScope.declareValue(
                        LocalSymbol(param.name, param.mutable, null)
                    )
                }
            }

            checkBlock(node.body)
        } finally {
            ctx.currentScope = savedScope
            ctx.currentHasSelfParam = savedHasSelf
        }
    }

    private fun checkBlock(block: BlockExprNode) {
        val savedScope = ctx.currentScope
        ctx.currentScope = savedScope.child()

        try {
            for (stmt in block.statements) {
                checkStatement(stmt)
            }
            block.tail?.let { checkExpr(it) }
        } finally {
            ctx.currentScope = savedScope
        }
    }

    private fun checkStatement(stmt: StmtNode) {
        when (stmt) {
            is LetNode -> {
                checkExpr(stmt.initializer)                 // 先查 initializer
                stmt.declaredType?.let { ctx.checkTypeRef(it) }
                ctx.currentScope.defineLocal(
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
                ctx.checkTypeRef(expr.targetType)
            }
            is BreakExprNode -> expr.value?.let { checkExpr(it) }
            is ReturnExprNode -> expr.value?.let { checkExpr(it) }

            is ArrayLitNode -> {
                for (element in expr.elements) checkExpr(element)
                expr.repeat?.let {
                    checkExpr(it.element)
                    ctx.constEval.requireUsize(it.length)
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
            if (!ctx.currentHasSelfParam) {
                throw SemanticError(node.span, "`self` is only allowed inside a method")
            }
            if (segment.genericArgs.isNotEmpty()) {
                throw SemanticError(node.span, "`self` takes no type arguments")
            }
            return
        }

        ctx.currentScope.lookupValue(name)
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
            if (ctx.currentSelfType == null) {
                throw SemanticError(node.span, "`Self` is not allowed here")
            }
            if (first.genericArgs.isNotEmpty()) {
                throw SemanticError(node.span, "`Self` takes no type arguments")
            }
        } else {
            val symbol = ctx.currentScope.lookupType(first.name)
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
                ctx.checkTypeRef(arg)
            }
        }
    }

    private fun checkCallExpr(node: CallExprNode) {
        checkExpr(node.callee)

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
            checkExpr(arg)
        }
    }

    private fun checkStructLitExpr(node: StructLitNode) {
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
            checkExpr(field.value)
        }
    }
}
