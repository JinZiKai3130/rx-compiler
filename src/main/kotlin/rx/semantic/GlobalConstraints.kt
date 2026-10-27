package rx.semantic

import rx.ast.*

class GlobalConstraints(private val ctx: SemanticContext) {
    private enum class State { IN_PROGRESS, DONE }

    fun check(items: List<ItemNode>) {
        val structs = items.filterIsInstance<StructNode>()
            .mapNotNull { ctx.globals.lookupType(it.name) as? StructSymbol }

        structs.forEach { checkDeriveList(it) }              // 7.1 纯列表检查
        structs.forEach { checkLayout(it, mutableListOf()) } // 7.2 内联环
        structs.forEach { checkDeriveCapability(it) }        // 7.3 递归能力
    }

    private val layoutState = HashMap<StructSymbol, State>()
    private val deriveStates  = HashMap<Pair<StructSymbol, Derive>, State>()
    private val deriveResults = HashMap<Pair<StructSymbol, Derive>, Boolean>()

    /** 7.1 只读 derive 列表本身：重复项 + 隐式前置要求。 */
    private fun checkDeriveList(s: StructSymbol) {
        val seen = mutableSetOf<Derive>()
        for (d in s.node.derives) {
            if (!seen.add(d)) {
                throw SemanticError(s.node.span, "duplicate derive '${nameOf(d)}'")
            }
        }
        if (Derive.COPY in seen && Derive.CLONE !in seen) {
            throw SemanticError(s.node.span, "'Copy' requires an explicit 'Clone' derive")
        }
        if (Derive.EQ in seen && Derive.PARTIAL_EQ !in seen) {
            throw SemanticError(s.node.span, "'Eq' requires an explicit 'PartialEq' derive")
        }
    }

    // 7.2 check环
    private fun checkLayout(s: StructSymbol, stack: MutableList<StructSymbol>) {
        when (layoutState[s]) {
            State.IN_PROGRESS -> {          // 撞到 GRAY = 环
                val cycle = stack.dropWhile { it !== s } + s
                throw SemanticError(
                    s.node.span,
                    "struct '${s.name}' has infinite size (${cycle.joinToString(" -> ") { it.name }})",
                )
            }
            State.DONE -> return
            null -> {}
        }
        layoutState[s] = State.IN_PROGRESS
        stack.add(s)
        // 把当前这个struct中field的所有内容拿出来，检查任何一个出现的structtype的变量，继续检查，防止出现环
        s.fields.values.forEach { field ->
            inlineStructs(field).forEach { checkLayout(it, stack) }   // 只沿内联边
        }
        stack.removeAt(stack.lastIndex)
        layoutState[s] = State.DONE
    }

    /** 只把"内联"的结构体吐出来；&T / Box / Vec 是句柄，到此为止。 */
    private fun inlineStructs(type: Type): List<StructSymbol> = when (type) {
        is StructType -> listOfNotNull(ctx.globals.lookupType(type.name) as? StructSymbol)
        is ArrayType  -> inlineStructs(type.element)
        else          -> emptyList()
    }

    private fun structSupports(s: StructSymbol, d: Derive): Boolean {
        if (d !in s.node.derives) return false
        val key = s to d
        when (deriveStates[key]) {
            // 如果说当前检查到的内容也在被检查，说明两者一样，直接置为true
            State.IN_PROGRESS -> return true            // ← 协归纳假设
            State.DONE        -> return deriveResults.getValue(key)
            null -> {}
        }
        deriveStates[key] = State.IN_PROGRESS
        val ok = s.fields.values.all { supports(it, d) }
        deriveStates[key] = State.DONE
        deriveResults[key] = ok
        return ok
    }

    private fun supports(type: Type, d: Derive): Boolean = when (type) {
        is IntegerType, is BoolType -> true
        is RefType -> when {
            // &mut T：Copy/Clone 一律不行；相等性穿透到 T
            type.mutable -> d != Derive.COPY && d != Derive.CLONE && supports(type.inner, d)
            // &T：Copy/Clone 无条件成立，不穿透
            else -> d == Derive.COPY || d == Derive.CLONE || supports(type.inner, d)
        }
        is BoxType -> d != Derive.COPY && supports(type.element, d)
        is VecType -> d != Derive.COPY && supports(type.element, d)
        is ArrayType  -> supports(type.element, d)
        is StructType -> (ctx.globals.lookupType(type.name) as? StructSymbol)
            ?.let { structSupports(it, d) } ?: false
        else -> true
    }

    /** 7.3 逐字段递归验证：本结构体请求的每个 derive，所有字段都要能提供。 */
    private fun checkDeriveCapability(s: StructSymbol) {
        for (d in s.node.derives) {
            if (structSupports(s, d)) continue        // ← 唯一的判定入口

            // 走到这里才付第二次遍历的代价（错误路径，一次就抛）
            val bad = s.node.fields.firstOrNull { field ->
                s.fields[field.name]?.let { !supports(it, d) } == true
            }
            throw SemanticError(
                bad?.let { it.type.span } ?: s.node.span,
                if (bad != null)
                    "field '${bad.name}' of struct '${s.name}' does not support derive '${nameOf(d)}'"
                else
                    "struct '${s.name}' cannot derive '${nameOf(d)}'",
            )
        }
    }

    private fun nameOf(d: Derive): String = when (d) {
        Derive.COPY -> "Copy"
        Derive.CLONE -> "Clone"
        Derive.PARTIAL_EQ -> "PartialEq"
        Derive.EQ -> "Eq"
    }

}