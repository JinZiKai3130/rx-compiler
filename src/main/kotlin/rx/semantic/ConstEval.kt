package rx.semantic

import rx.ast.*

/**
 * 常量求值：三个常量上下文（const 初值、数组长度、repeat 长度）共用。
 *
 * 只接受受限形式：整数/布尔字面量、常量路径、-Magnitude、括号（括号已被 AstBuilder 拆掉）。
 * 状态表用来检测常量依赖环（三色 DFS）。
 */
class ConstEval(
    private val globals: Scope,
    private val resolveType: (TypeRefNode) -> Type,
) {
    sealed interface ConstValue {
        val type: Type

        data class IntValue(
            override val type: IntegerType,
            val value: Long,
        ) : ConstValue

        data class BoolValue(
            val value: Boolean,
        ) : ConstValue {
            override val type: Type get() = BoolType
        }
    }

    private enum class State { IN_PROGRESS, DONE }

    private val states = HashMap<ConstantSymbol, State>()
    private val values = HashMap<ConstantSymbol, ConstValue>()
    private val declaredTypes = HashMap<ConstantSymbol, Type>()

    /** 正在求值的常量所属的 struct；`Self::N` 靠它解析。force 里 save/restore。 */
    private var currentOwner: StructSymbol? = null

    // ------------------------------------------------------------------
    // 对外入口
    // ------------------------------------------------------------------

    /** 阶段 4：强制求值所有常量（包括从未被使用的），错误必须报。 */
    fun evaluateAll(crate: CrateNode) {
        for (item in crate.items) {
            when (item) {
                is ConstNode -> { // 全局constant
                    val symbol = globals.lookupValue(item.name) as? ConstantSymbol
                    if (symbol != null) force(symbol)
                }
                is ImplNode -> { // 对于impl中的任何一个constant进行检查
                    val targetName = (item.target as? PathTypeNode)
                        ?.segments?.firstOrNull()?.name
                    val owner = targetName
                        ?.let { globals.lookupType(it) as? StructSymbol }
                        ?: continue
                    owner.associatedValues.values
                        .filterIsInstance<ConstantSymbol>()
                        .forEach { force(it) }
                }
                else -> {}
            }
        }
    }

    /** 数组长度 / repeat 长度：期望类型 usize，返回 Long。 */
    fun requireUsize(expr: ExprNode): Long {
        val v = evaluate(expr, UsizeType) // 数组的常量必须是Usize
        if (v.type != UsizeType) {
            throw SemanticError(expr.span, "array length must have type usize")
        }
        return (v as ConstValue.IntValue).value
    }

    // ------------------------------------------------------------------
    // 核心
    // ------------------------------------------------------------------

    private fun force(symbol: ConstantSymbol): ConstValue {
        when (states[symbol]) {
            State.DONE -> return values[symbol]!!
            State.IN_PROGRESS -> throw SemanticError(
                symbol.node.span, "constant dependency cycle",
            )
            null -> {}
        }

        states[symbol] = State.IN_PROGRESS

        val savedOwner = currentOwner
        currentOwner = symbol.owner
        try {
            val declared = declaredTypes.getOrPut(symbol) {
                resolveType(symbol.node.type)
            }
            val value = evaluate(symbol.node.value, declared)
            if (value.type != declared) {
                throw SemanticError(
                    symbol.node.value.span,
                    "constant initializer must match declared type",
                )
            }
            values[symbol] = value
            states[symbol] = State.DONE
            return value
        } finally {
            currentOwner = savedOwner
        }
    }

    private fun evaluate(expr: ExprNode, expected: Type?): ConstValue = when (expr) {
        is IntLitNode -> ConstValue.IntValue(literalType(expr, expected), expr.value)

        is BoolLitNode -> ConstValue.BoolValue(expr.value)

        is PathExprNode -> {
            val target = resolveConstantPath(expr)
            force(target)          // 递归；环在这里被发现
        }

        is UnaryExprNode -> {
            if (expr.op != UnOp.NEG) {
                throw SemanticError(expr.span, "expected a constant value")
            }
            // 传播排除：右操作数不带 expected
            val operand = evaluate(expr.operand, expected = null) as? ConstValue.IntValue
                ?: throw SemanticError(expr.span, "expected a constant value")
            if (!operand.type.signed) {
                throw SemanticError(expr.span, "negative forms require a signed integer type")
            }
            ConstValue.IntValue(operand.type, -operand.value)
        }

        else -> throw SemanticError(expr.span, "expected a constant value")
    }

    private fun literalType(expr: IntLitNode, expected: Type?): IntegerType =
        when (expr.suffix) {
            "i32"   -> I32Type
            "u32"   -> U32Type
            "isize" -> IsizeType
            "usize" -> UsizeType
            null    -> expected as? IntegerType ?: I32Type   // 期望类型 → 默认 i32
            else    -> throw SemanticError(expr.span, "invalid integer suffix")
        }
    
    private fun resolveConstantPath(node: PathExprNode): ConstantSymbol {
        val segments = node.segments

        if (segments.size == 1) {
            val symbol = globals.lookupValue(segments[0].name)
            return symbol as? ConstantSymbol
                ?: throw SemanticError(node.span, "constant path must resolve to a constant")
        }

        if (segments.size != 2) {
            throw SemanticError(node.span, "constant path must resolve to a constant")
        }

        val head = segments[0]
        if (head.genericArgs.isNotEmpty()) {
            throw SemanticError(node.span, "constant path takes no type arguments")
        }

        val owner = if (head.name == "Self") {
            currentOwner
                ?: throw SemanticError(node.span, "`Self` is not allowed here")
        } else {
            globals.lookupType(head.name) as? StructSymbol
                ?: throw SemanticError(node.span, "constant path must resolve to a constant")
        }

        return owner.associatedValues[segments[1].name] as? ConstantSymbol
            ?: throw SemanticError(node.span, "constant path must resolve to a constant")
    }
}