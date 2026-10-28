package rx.semantic

import rx.ast.*

/**
 * 阶段 8（一）：函数体骨架。
 *
 * 只管"语句层"：函数体作用域、参数绑定、块作用域进出、`let` 的声明。
 * 表达式的一切判定（名字解析、可调用性，将来的类型 / place）在 [ExprChecker]。
 *
 * 块本身也是表达式（[BlockExprNode]），所以 [ExprChecker] 会回调 [checkBlock]：
 * 语句层管作用域，表达式层管值，两边互相持有引用是刻意的。
 *
 * 已完成（批次 A）：
 * - `currentReturnType`：`return` 载荷与函数体类型都对着它检查；
 * - 块类型：尾表达式为值，块内出现发散语句（`!`）时整块为 `!`；
 * - 无分号的 block-like 语句必须是 `()`。
 *
 * 待接入：循环栈由 [ExprChecker] 自己 push/pop，本类无需维护。
 */
class BodyChecker(private val ctx: SemanticContext) {

    private val exprs = ExprChecker(ctx, this)

    fun check(items: List<ItemNode>) {
        for (item in items) {
            when (item) {
                is FnNode -> checkFunctionBody(item, owner = null)

                is ImplNode -> {
                    val owner = implOwner(item)
                    for (member in item.items) {
                        if (member is FnNode) checkFunctionBody(member, owner)
                    }
                }

                // 不含运行时表达式，由别的阶段负责：
                is UseNode -> Unit      // use 语义上完全丢弃
                is StructNode -> Unit   // 字段类型：阶段 5 StructResolver
                is ConstNode -> Unit    // 初值：阶段 4 ConstEval（受限常量形式）
            }
        }
    }

    /** impl 目标在阶段 2 已校验为 struct；这里重新解析一次只为拿到符号。 */
    private fun implOwner(node: ImplNode): StructSymbol? {
        val target = ctx.resolveType(node.target) as? StructType ?: return null
        return ctx.globals.lookupType(target.name) as? StructSymbol
    }

    /**
     * [owner] 为 null = 顶层函数；非空 = impl 方法/关联函数。
     * 方法体里允许写 `self`（
     * [SemanticContext.currentHasSelfParam]）和 `Self`
     * （[SemanticContext.currentSelfType]），二者都由本函数设置。
     */
    private fun checkFunctionBody(node: FnNode, owner: StructSymbol?) {
        val savedScope = ctx.currentScope
        val savedHasSelf = ctx.currentHasSelfParam
        val savedSelfType = ctx.currentSelfType
        val savedReturnType = ctx.currentReturnType

        ctx.currentScope = ctx.globals.child()   // 函数作用域：放参数
        ctx.currentHasSelfParam = node.params.any { it is SelfParam }
        ctx.currentSelfType = owner

        try {
            ctx.currentReturnType =
                node.returnType?.let { ctx.resolveType(it) } ?: UnitType

            for (param in node.params) {
                if (param is FnParam) {
                    ctx.currentScope.declareValue(
                        LocalSymbol(
                            name = param.name,
                            mutable = param.mutable,
                            type = ctx.resolveType(param.type),
                            span = null,
                        )
                    )
                }
            }

            val bodyType = checkBlock(node.body, ctx.currentReturnType)
            requireAssignable(
                bodyType,
                ctx.currentReturnType,
                node.body.span,
                "function body",
            )
        } finally {
            ctx.currentScope = savedScope
            ctx.currentHasSelfParam = savedHasSelf
            ctx.currentSelfType = savedSelfType
            ctx.currentReturnType = savedReturnType
        }
    }

    /** 块作用域：进入压一层、退出弹回。表达式层处理块表达式时回调这里。 */
    internal fun checkBlock(block: BlockExprNode, expected: Type? = null): Type {
        val savedScope = ctx.currentScope
        ctx.currentScope = savedScope.child()

        try {
            var diverges = false
            for (stmt in block.statements) {
                if (checkStatement(stmt)) diverges = true
            }
            val tailType = block.tail?.let { exprs.check(it, expected) } ?: UnitType
            return if (diverges) NeverType else tailType
        } finally {
            ctx.currentScope = savedScope
        }
    }

    /** 返回 true 表示这条语句之后不可达（语句的值类型是 `!`）。 */
    private fun checkStatement(stmt: StmtNode): Boolean {
        when (stmt) {
            is LetNode -> {
                // 先解析 declaredType，再把它当 expected 传给 initializer
                val declared = stmt.declaredType?.let { ctx.resolveType(it) }
                val initType = exprs.check(stmt.initializer, declared)
                if (declared != null) {
                    requireAssignable(
                        initType,
                        declared,
                        stmt.initializer.span,
                        "let initializer",
                    )
                }
                ctx.currentScope.defineLocal(
                    LocalSymbol(
                        name = stmt.name,
                        mutable = stmt.mutable,
                        type = declared ?: initType,
                        span = stmt.span,
                    )
                )
                return false
            }

            is ExprStmtNode -> {
                // 带分号：值被丢弃，任意类型都行；不带分号：必须 ()
                val expected = if (stmt.hasSemicolon) null else UnitType
                val type = exprs.check(stmt.expr, expected)
                if (!stmt.hasSemicolon) {
                    requireAssignable(type, UnitType, stmt.span, "block statement")
                }
                return type == NeverType
            }

            else -> return false
        }
    }
}
