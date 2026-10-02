package rx.ast

/** 路径类型：i32、S（段名列表；泛型实参留到第三刀） */
class PathTypeNode(
    val segments: List<String>,
    span: Span,
) : TypeRefNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 引用类型：&T、&mut T；&&T 拆成两层（生命周期写法可丢弃，忽略）。 */
class RefTypeNode(
    val mutable: Boolean,
    val inner: TypeRefNode,
    span: Span,
) : TypeRefNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 数组类型：[T; N]，长度 N 是常量值（用表达式形态存）。 */
class ArrayTypeNode(
    val element: TypeRefNode,
    val length: ExprNode,
    span: Span,
) : TypeRefNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 单元类型：() */
class UnitTypeNode(
    span: Span,
) : TypeRefNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}
