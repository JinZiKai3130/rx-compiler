package rx.ast

/**
 * AST 遍历入口（设计文档 §3.2）：
 * 打印、语义检查、代码生成等阶段分别实现这个接口，通过每个节点的 accept 进入。
 * 目前只包含第一批节点；后续批次加节点时在这里补对应方法。
 */
interface AstVisitor {
    fun visit(n: CrateNode)
    fun visit(n: UseNode)
    fun visit(n: FnNode)
    fun visit(n: StructNode)
    fun visit(n: ConstNode)
    fun visit(n: ImplNode)
    fun visit(n: LetNode)
    fun visit(n: ExprStmtNode)
    fun visit(n: BlockExprNode)
    fun visit(n: IntLitNode)
    fun visit(n: BoolLitNode)
    fun visit(n: PathExprNode)
    fun visit(n: UnaryExprNode)
    fun visit(n: ReferenceExprNode)
    fun visit(n: BinaryExprNode)
    fun visit(n: AssignExprNode)
    fun visit(n: CallExprNode)
    fun visit(n: MethodCallExprNode)
    fun visit(n: FieldExprNode)
    fun visit(n: IndexExprNode)
    fun visit(n: ContinueExprNode)
    fun visit(n: BreakExprNode)
    fun visit(n: ReturnExprNode)
    fun visit(n: CastExprNode)
    fun visit(n: LoopExprNode)
    fun visit(n: WhileExprNode)
    fun visit(n: IfExprNode)
    fun visit(n: ArrayLitNode)
    fun visit(n: StructLitNode)
    fun visit(n: PathTypeNode)
    fun visit(n: RefTypeNode)
    fun visit(n: ArrayTypeNode)
    fun visit(n: UnitTypeNode)
}
