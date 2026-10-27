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
 * 待接入（批次 A/C）：
 * - 记录 `currentReturnType`，供 `return` 检查；
 * - 维护 `loopStack`（`LoopContext { kind, breakTypes }`），供 break/continue 与 loop 定型。
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

        ctx.currentScope = ctx.globals.child()   // 函数作用域：放参数
        ctx.currentHasSelfParam = node.params.any { it is SelfParam }
        ctx.currentSelfType = owner

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
            ctx.currentSelfType = savedSelfType
        }
    }

    /** 块作用域：进入压一层、退出弹回。表达式层处理块表达式时回调这里。 */
    internal fun checkBlock(block: BlockExprNode) {
        val savedScope = ctx.currentScope
        ctx.currentScope = savedScope.child()

        try {
            for (stmt in block.statements) {
                checkStatement(stmt)
            }
            block.tail?.let { exprs.checkExpr(it) }
        } finally {
            ctx.currentScope = savedScope
        }
    }

    private fun checkStatement(stmt: StmtNode) {
        when (stmt) {
            is LetNode -> {
                // TODO(批次 A)：先解析 declaredType，再把它当 expected 传给 initializer。
                exprs.checkExpr(stmt.initializer)
                stmt.declaredType?.let { ctx.checkTypeRef(it) }
                ctx.currentScope.defineLocal(
                    LocalSymbol(stmt.name, stmt.mutable, stmt.span)
                )
            }
            is ExprStmtNode -> exprs.checkExpr(stmt.expr)
            else -> Unit
        }
    }
}
