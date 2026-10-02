package rx.ast

/**
 * 源码位置范围：起点 + 终点。
 * 行从 1 数、列从 0 数（与现有诊断输出一致，打印时列 +1）。
 * 由 AstBuilder 从 CST 节点的首尾 token（ctx.start / ctx.stop）直接生成。
 */
data class Span(
    val startLine: Int,
    val startColumn: Int,
    val endLine: Int,
    val endColumn: Int,
)

/** 所有 AST 节点的基类：带位置 + 统一的遍历入口。 */
abstract class AstNode(val span: Span) {
    /** visitor 入口：打印、语义检查、代码生成等阶段实现 AstVisitor 后走这棵树。 */
    abstract fun accept(visitor: AstVisitor)
}

/** 声明族：顶层项目——函数、结构体、常量、impl、use。 */
abstract class ItemNode(span: Span) : AstNode(span)

/** 语句族：块里的语句——let 绑定、表达式语句。 */
abstract class StmtNode(span: Span) : AstNode(span)

/** 表达式族：所有"会出值"的构造（含 if/loop/while/block，见设计文档决策 B）。 */
abstract class ExprNode(span: Span) : AstNode(span) {
    /** 语义阶段（2.5）填入的类型；2.4 阶段保持为 null。 */
    var type: Type? = null
}

/** 类型族：源码里"写出来的类型"（i32、&mut S、[i32; 3]、()），与内部类型 Type 分开。 */
abstract class TypeRefNode(span: Span) : AstNode(span)
