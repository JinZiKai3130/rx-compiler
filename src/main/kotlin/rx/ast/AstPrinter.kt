package rx.ast

/**
 * AST 调试打印（`rxc --dump-ast`）：每个节点一行，子节点缩进两格。
 *
 * 格式约定：
 * - 节点行 = 节点描述 + 行尾 `@行:列`（行 1 起、打印时列转 1 起）；
 * - 子节点槽位带标签（`body:`、`left:`、`args[0]:` ...）；纯数据行（参数表、字段表）无位置后缀；
 * - 类型（TypeRefNode）不展开成节点，统一一行式内联（如 `Box<i32>`、`&mut [i32; 4]`）。
 *
 * 例：
 *   FnNode(name = "add", returnType = i32) @1:1
 *     params[0]: FnParam(name = "a", mutable = false, type = i32)
 *     body: BlockExprNode @1:40
 *       tail: BinaryExprNode(op = ADD) @1:42
 *         left: PathExprNode(segments = ["a"]) @1:44
 *         right: PathExprNode(segments = ["b"]) @1:48
 */
class AstPrinter : AstVisitor {
    private val out = StringBuilder()
    private var depth = 0

    /** 当前行的槽位标签（由父节点的 child() 设置，nodeLine 消费后清空）。 */
    private var label: String? = null

    /** 打印以 [root] 为根的整棵树；返回值以换行结尾。 */
    fun print(root: AstNode): String {
        out.clear()
        depth = 0
        label = null
        root.accept(this)
        return out.toString()
    }

    // ===== 输出基础设施 =====

    private fun emit(text: String) {
        repeat(depth) { out.append("  ") }
        out.append(text).append('\n')
    }

    /** 节点行：槽位标签 + 描述 + 起点位置。 */
    private fun nodeLine(node: AstNode, description: String) {
        val prefix = label?.let { "$it: " } ?: ""
        label = null
        emit(prefix + description + " @" + node.span.startLine + ":" + (node.span.startColumn + 1))
    }

    /** 数据行（参数表、字段表等非节点的信息），无位置后缀。 */
    private fun dataLine(text: String) = emit(text)

    /** 以 [slot] 为标签打印一个子节点，缩进加深一层。 */
    private fun child(node: AstNode, slot: String) {
        depth++
        label = slot
        node.accept(this)
        label = null
        depth--
    }

    /** 在加深一层缩进的范围内输出若干数据行 / 子节点。 */
    private fun indented(block: () -> Unit) {
        depth++
        block()
        depth--
    }

    // ===== 声明族 =====

    override fun visit(n: CrateNode) {
        nodeLine(n, "CrateNode")
        n.items.forEachIndexed { i, item -> child(item, "items[$i]") }
    }

    override fun visit(n: UseNode) {
        nodeLine(n, "UseNode(tree = ${describeUseTree(n.tree)})")
    }

    override fun visit(n: FnNode) {
        val parts = mutableListOf("name = \"${n.name}\"")
        val returnType = n.returnType
        if (returnType != null) parts += "returnType = ${describeType(returnType)}"
        if (n.hasGenericParams) parts += "genericParams = true"
        nodeLine(n, "FnNode(${parts.joinToString(", ")})")
        indented {
            n.params.forEachIndexed { i, p -> dataLine("params[$i]: ${describeParam(p)}") }
        }
        child(n.body, "body")
    }

    override fun visit(n: StructNode) {
        val derives =
            if (n.derives.isEmpty()) ""
            else ", derives = [${n.derives.joinToString(", ") { deriveName(it) }}]"
        nodeLine(n, "StructNode(name = \"${n.name}\"$derives)")
        indented {
            n.fields.forEachIndexed { i, f ->
                dataLine("fields[$i]: Field(name = \"${f.name}\", type = ${describeType(f.type)})")
            }
        }
    }

    override fun visit(n: ConstNode) {
        nodeLine(n, "ConstNode(name = \"${n.name}\", type = ${describeType(n.type)})")
        child(n.value, "value")
    }

    override fun visit(n: ImplNode) {
        nodeLine(n, "ImplNode(target = ${describeType(n.target)})")
        n.items.forEachIndexed { i, item -> child(item, "items[$i]") }
    }

    // ===== 语句族 =====

    override fun visit(n: LetNode) {
        val type = n.declaredType?.let { ", type = ${describeType(it)}" } ?: ""
        nodeLine(n, "LetNode(name = \"${n.name}\", mutable = ${n.mutable}$type)")
        child(n.initializer, "initializer")
    }

    override fun visit(n: ExprStmtNode) {
        nodeLine(n, "ExprStmtNode")
        child(n.expr, "expr")
    }

    // ===== 表达式族 =====

    override fun visit(n: BlockExprNode) {
        nodeLine(n, "BlockExprNode")
        n.statements.forEachIndexed { i, s -> child(s, "statements[$i]") }
        n.tail?.let { child(it, "tail") }
    }

    override fun visit(n: IntLitNode) {
        nodeLine(n, "IntLitNode(value = ${n.value}, suffix = ${n.suffix})")
    }

    override fun visit(n: BoolLitNode) {
        nodeLine(n, "BoolLitNode(value = ${n.value})")
    }

    override fun visit(n: UnitLitNode) {
        nodeLine(n, "UnitLitNode")
    }

    override fun visit(n: PathExprNode) {
        nodeLine(n, "PathExprNode(segments = [${n.segments.joinToString(", ") { describeSegment(it) }}])")
    }

    override fun visit(n: UnaryExprNode) {
        nodeLine(n, "UnaryExprNode(op = ${n.op})")
        child(n.operand, "operand")
    }

    override fun visit(n: ReferenceExprNode) {
        nodeLine(n, "ReferenceExprNode(mutable = ${n.mutable})")
        child(n.operand, "operand")
    }

    override fun visit(n: BinaryExprNode) {
        nodeLine(n, "BinaryExprNode(op = ${n.op})")
        child(n.left, "left")
        child(n.right, "right")
    }

    override fun visit(n: AssignExprNode) {
        nodeLine(n, "AssignExprNode(op = ${n.op})")
        child(n.target, "target")
        child(n.value, "value")
    }

    override fun visit(n: CallExprNode) {
        nodeLine(n, "CallExprNode")
        child(n.callee, "callee")
        n.args.forEachIndexed { i, a -> child(a, "args[$i]") }
    }

    override fun visit(n: MethodCallExprNode) {
        nodeLine(n, "MethodCallExprNode(name = ${describeSegment(n.name)})")
        child(n.receiver, "receiver")
        n.args.forEachIndexed { i, a -> child(a, "args[$i]") }
    }

    override fun visit(n: FieldExprNode) {
        nodeLine(n, "FieldExprNode(name = \"${n.name}\")")
        child(n.receiver, "receiver")
    }

    override fun visit(n: IndexExprNode) {
        nodeLine(n, "IndexExprNode")
        child(n.receiver, "receiver")
        child(n.index, "index")
    }

    override fun visit(n: ContinueExprNode) {
        nodeLine(n, "ContinueExprNode")
    }

    override fun visit(n: BreakExprNode) {
        nodeLine(n, "BreakExprNode")
        n.value?.let { child(it, "value") }
    }

    override fun visit(n: ReturnExprNode) {
        nodeLine(n, "ReturnExprNode")
        n.value?.let { child(it, "value") }
    }

    override fun visit(n: CastExprNode) {
        nodeLine(n, "CastExprNode(targetType = ${describeType(n.targetType)})")
        child(n.expr, "expr")
    }

    override fun visit(n: LoopExprNode) {
        nodeLine(n, "LoopExprNode")
        child(n.block, "block")
    }

    override fun visit(n: WhileExprNode) {
        nodeLine(n, "WhileExprNode")
        child(n.condition, "condition")
        child(n.block, "block")
    }

    override fun visit(n: IfExprNode) {
        nodeLine(n, "IfExprNode")
        child(n.condition, "condition")
        child(n.thenblock, "thenblock")
        n.elseBranch?.let { child(it, "else") }
    }

    override fun visit(n: ArrayLitNode) {
        val repeat = n.repeat
        if (repeat != null) {
            nodeLine(n, "ArrayLitNode(repeat = true)")
            child(repeat.element, "element")
            child(repeat.length, "length")
        } else {
            nodeLine(n, "ArrayLitNode(elements = ${n.elements.size})")
            n.elements.forEachIndexed { i, e -> child(e, "elements[$i]") }
        }
    }

    override fun visit(n: StructLitNode) {
        nodeLine(n, "StructLitNode(path = [${n.path.joinToString(", ") { "\"$it\"" }}])")
        indented {
            n.fields.forEachIndexed { i, f ->
                dataLine("fields[$i]: Field(name = \"${f.name}\")")
                child(f.value, "value")
            }
        }
    }

    // ===== 类型族（一般内联描述；这里保留独立打印入口以便单独 dump 类型） =====

    override fun visit(n: PathTypeNode) {
        nodeLine(n, "PathTypeNode(segments = [${n.segments.joinToString(", ") { describeSegment(it) }}])")
    }

    override fun visit(n: RefTypeNode) {
        nodeLine(n, "RefTypeNode(mutable = ${n.mutable})")
        child(n.inner, "inner")
    }

    override fun visit(n: ArrayTypeNode) {
        nodeLine(n, "ArrayTypeNode")
        child(n.element, "element")
        child(n.length, "length")
    }

    override fun visit(n: UnitTypeNode) {
        nodeLine(n, "UnitTypeNode")
    }

    // ===== 一行式描述 =====

    private fun describeSegment(segment: PathSegment): String {
        val name = "\"${segment.name}\""
        if (segment.genericArgs.isEmpty()) {
            return name
        }
        return "$name::<${segment.genericArgs.joinToString(", ") { describeType(it) }}>"
    }

    private fun describeType(type: TypeRefNode): String = when (type) {
        is PathTypeNode -> type.segments.joinToString("::") { describeSegment(it) }
        is RefTypeNode -> "&${if (type.mutable) "mut " else ""}${describeType(type.inner)}"
        is ArrayTypeNode -> "[${describeType(type.element)}; ${describeConst(type.length)}]"
        is UnitTypeNode -> "()"
        else -> type::class.simpleName ?: "?"
    }

    /** 数组类型长度、const 值等"常量叶子"的一行式描述（文法上只有整数/布尔/路径/负号）。 */
    private fun describeConst(expr: ExprNode): String = when (expr) {
        is IntLitNode -> "${expr.value}${expr.suffix ?: ""}"
        is BoolLitNode -> expr.value.toString()
        is PathExprNode -> expr.segments.joinToString("::") { it.name }
        is UnaryExprNode -> if (expr.op == UnOp.NEG) "-${describeConst(expr.operand)}" else "?"
        else -> "?"
    }

    private fun describeParam(param: Param): String = when (param) {
        is SelfParam -> "SelfParam(byReference = ${param.byReference}, mutable = ${param.mutable})"
        is FnParam -> "FnParam(name = \"${param.name}\", mutable = ${param.mutable}, type = ${describeType(param.type)})"
    }

    private fun deriveName(derive: Derive): String = when (derive) {
        Derive.COPY -> "Copy"
        Derive.CLONE -> "Clone"
        Derive.PARTIAL_EQ -> "PartialEq"
        Derive.EQ -> "Eq"
    }

    private fun describeUseTree(tree: UseTree): String = when (tree) {
        is UsePath -> tree.path.joinToString("::") + (tree.alias?.let { " as $it" } ?: "")
        is UseGlob -> usePrefix(tree.prefix) + "*"
        is UseGroup -> usePrefix(tree.prefix) + "{" + tree.items.joinToString(", ") { describeUseTree(it) } + "}"
    }

    private fun usePrefix(prefix: List<String>): String =
        if (prefix.isEmpty()) "" else prefix.joinToString("::") + "::"
}
