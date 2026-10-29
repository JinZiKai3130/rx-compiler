package rx.semantic

import rx.ast.*

/**
 * 类型可赋值性 / 公共类型（阶段 8 共用）。
 *
 * 这里只处理"类型 → 类型"的规则。"无后缀字面量按 expected 定型"发生在
 * [ExprChecker.check] 的 IntLitNode 分支，不在这里。
 *
 * 批次 A 只放必需的三条；批次 B–F 再按需扩展（引用层级、容器、结构体名义类型）。
 */

/** [from] 能否直接当作 [to] 使用。 */
internal fun assignable(from: Type, to: Type): Boolean = when {
    from == to -> true
    // `!` 可以出现在任何期望类型的位置（return / break / 发散块）
    from == NeverType -> true
    // 脚手架：尚未实现类型推导的表达式一律放行
    from == UnknownType || to == UnknownType -> true
    // &mut T → &T（只允许顶层弱化，内层不改写）
    from is RefType && to is RefType -> from.mutable && !to.mutable && from.inner == to.inner
    else -> false
}

/** [a]、[b] 的公共类型：`!` 与 `?` 都吸收到另一侧。 */
internal fun join(a: Type, b: Type, span: Span, what: String): Type = when {
    a == NeverType -> b
    b == NeverType -> a
    a == UnknownType -> b
    b == UnknownType -> a
    a == b -> a
    else -> throw SemanticError(span, "$what: incompatible types $a vs $b")
}

/** 不满足 [assignable] 就报错。[what] 用于诊断措辞（如 "if condition"）。 */
internal fun requireAssignable(from: Type, to: Type, span: Span, what: String) {
    if (!assignable(from, to)) {
        throw SemanticError(span, "$what must have type $to, found $from")
    }
}

/**
 * 剥掉一层**共享**引用（`&T → T`）。
 *
 * 算术 / 位运算 / 移位 / 一元运算最多允许一层 `&`：
 * - `&mut T` 直接拒绝（`-&mut a` 就是用例）；
 * - `&&T` 剥一层后仍是 `&T`，后面的"必须是整数"检查自然会失败，不需要单独数层数。
 */
internal fun stripSharedRef(type: Type, span: Span, what: String): Type = when {
    type == UnknownType -> UnknownType
    type is RefType && !type.mutable -> type.inner
    type is RefType -> throw SemanticError(span, "$what does not accept a mutable reference")
    else -> type
}