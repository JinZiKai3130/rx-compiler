package rx.ast

/** 块表达式：{ 语句; 语句; 尾表达式 } —— 尾表达式的值就是块的值（函数体也是它）。 */
class BlockExprNode(
    val statements: List<StmtNode>,
    val tail: ExprNode?,  // 可选尾表达式；没有时块的值是 ()
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 整数字面量：123（先按 Long 存值，范围检查交给语义阶段） */
class IntLitNode(
    val value: Long,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 布尔字面量：true / false */
class BoolLitNode(
    val value: Boolean,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 路径的单个段：名字 + 该段自带的泛型实参（如 Box::<u32> 的 <u32> 挂在 Box 段上）。 */
data class PathSegment(
    val name: String,
    val genericArgs: List<TypeRefNode> = emptyList(),
)

/** 路径表达式：x、a::b、Self::x、Box::<u32>::new（表达式侧泛型实参必须用 ::< >）。 */
class PathExprNode(
    val segments: List<PathSegment>,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 一元表达式：-x、!x、*x */
class UnaryExprNode(
    val op: UnOp,
    val operand: ExprNode,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 引用表达式：&x、&mut x；&&x 会建成两层嵌套的引用节点。 */
class ReferenceExprNode(
    val mutable: Boolean,
    val operand: ExprNode,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 二元表达式：a + b、a && b……（4 套语法变体统统归到这一个节点）。 */
class BinaryExprNode(
    val op: BinOp,
    val left: ExprNode,
    val right: ExprNode,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 赋值表达式：x = 5; / x += 1;（含复合赋值；值类型永远是 ()） */
class AssignExprNode(
    val op: AssignOp,
    val target: ExprNode,
    val value: ExprNode,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 函数调用：f(a, b)、S::f(x)（被调者本身也是一个表达式） */
class CallExprNode(
    val callee: ExprNode,
    val args: List<ExprNode>,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 方法调用：x.m(a) */
class MethodCallExprNode(
    val receiver: ExprNode,
    val name: String,
    val args: List<ExprNode>,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 字段访问：x.f */
class FieldExprNode(
    val receiver: ExprNode,
    val name: String,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/** 下标访问：x[i] */
class IndexExprNode(
    val receiver: ExprNode,
    val index: ExprNode,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/* continue */
class ContinueExprNode(
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/* break */
class BreakExprNode(
    val value: ExprNode?,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/* return */
class ReturnExprNode(
    val value: ExprNode?,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

/* 类型转换 */
class CastExprNode(
    val expr: ExprNode,
    val targetType: TypeRefNode,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class LoopExprNode(
    val block: BlockExprNode,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class WhileExprNode(
    val block: BlockExprNode,
    val condition: ExprNode,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class IfExprNode(
    val condition: ExprNode,
    val thenblock: BlockExprNode,
    val elseBranch: ExprNode?,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

class ArrayLitNode(
    val elements: List<ExprNode>,
    val repeat: ArrayRepeat?,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

data class ArrayRepeat(
    val element: ExprNode,
    val length: ExprNode,
)

class StructLitNode(
    val path: List<String>,
    val fields: List<StructLitField>,
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}

data class StructLitField(
    val name: String,
    val value: ExprNode,
)