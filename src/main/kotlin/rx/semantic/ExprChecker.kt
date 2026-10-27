package rx.semantic

import rx.ast.*

/**
 * 阶段 8（二）：表达式检查。
 *
 * 当前只做名字解析与结构递归（恒返回 `Unit`）。批次 A 起升级为
 *
 *     fun check(expr: ExprNode, expected: Type? = null): Type
 *
 * 并把结果写回 [ExprNode.type]；届时 place（左值 / 可变性）与类型调整
 * （assignable / join）分别放进 `Place.kt`、`Coercion.kt`。
 *
 * 块也是表达式（[BlockExprNode]），但块的作用域与语句归 [BodyChecker] 管，
 * 这里只回调 `body.checkBlock`，两边互相调用所以持有它的引用。
 */
class ExprChecker(
    private val ctx: SemanticContext,
    private val body: BodyChecker,
) {

    /** 表达式入口：递归检查全部子表达式。 */
    fun checkExpr(expr: ExprNode) {
        when (expr) {
            is PathExprNode -> checkPathExpr(expr)
            is CallExprNode -> checkCallExpr(expr)
            is StructLitNode -> checkStructLitExpr(expr)
            is BlockExprNode -> body.checkBlock(expr)

            is IfExprNode -> {
                checkExpr(expr.condition)
                body.checkBlock(expr.thenblock)
                expr.elseBranch?.let { checkExpr(it) }
            }

            is WhileExprNode -> {
                checkExpr(expr.condition)
                body.checkBlock(expr.block)
            }

            is LoopExprNode -> body.checkBlock(expr.block)
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
