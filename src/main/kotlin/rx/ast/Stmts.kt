package rx.ast

/** 变量绑定语句：let x: i32 = 1 + 2; / let mut y = 0; */
class LetNode(
    val name: String,
    val mutable: Boolean,
    val declaredType: TypeRefNode?,  // 没写类型注解时为 null
    val initializer: ExprNode,
    span: Span,
) : StmtNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 表达式语句：x = 5; / f(x); / if c { ... } —— 只要副作用、不关心值的表达式包一层。 */
class ExprStmtNode(
    val expr: ExprNode,
    span: Span,
) : StmtNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}
