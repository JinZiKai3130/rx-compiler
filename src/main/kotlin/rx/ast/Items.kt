package rx.ast

/** 根节点：整个源文件（文法入口 crate），里面是一串顶层声明。 */
class CrateNode(
    val items: List<ItemNode>,
    span: Span,
) : AstNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** use 声明：use a::b; / use a::b as c; / use a::*;（花括号组留到第三刀） */
class UseNode(
    val tree: UseTree,
    span: Span,
) : ItemNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** use 的目标：路径 + 是否通配（*）+ 可选别名（as c）。 */
data class UseTree(
    val path: List<String>,
    val glob: Boolean,
    val alias: String?,
)

/** 函数参数：普通参数或 self 参数。 */
sealed interface Param

/** 普通参数：x: i32（mut x: i32 也合法，mut 留给语义检查用） */
data class FnParam(
    val name: String,
    val mutable: Boolean,
    val type: TypeRefNode,
) : Param

/** self 参数：self / &self / &mut self —— 出现它说明这个 FnNode 是"方法"。 */
data class SelfParam(
    val byReference: Boolean,
    val mutable: Boolean,
) : Param

/** 函数定义：fn f(x: i32) -> i32 { ... }；impl 里的方法也用这个节点。 */
class FnNode(
    val name: String,
    val params: List<Param>,
    val returnType: TypeRefNode?,  // 没写 -> T 时为 null
    val body: BlockExprNode,
    span: Span,
) : ItemNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 结构体字段：a: i32。 */
data class StructField(
    val name: String,
    val type: TypeRefNode,
)

/** #[derive(...)] 支持的四个名字。 */
enum class Derive { COPY, CLONE, PARTIAL_EQ, EQ }

/** 结构体定义：#[derive(Copy)] struct S { a: i32, b: i32 } */
class StructNode(
    val name: String,
    val derives: List<Derive>,
    val fields: List<StructField>,
    span: Span,
) : ItemNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 常量定义：const N: i32 = 1 + 2;（常量值复用 Expr 的一小撮：整数/布尔/路径/负号） */
class ConstNode(
    val name: String,
    val type: TypeRefNode,
    val value: ExprNode,
    span: Span,
) : ItemNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** impl 块：impl S { fn m(&self) { ... } const N: i32 = 1; }（文法里只允许 fn 和 const） */
class ImplNode(
    val target: TypeRefNode,
    val items: List<ItemNode>,
    span: Span,
) : ItemNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}
