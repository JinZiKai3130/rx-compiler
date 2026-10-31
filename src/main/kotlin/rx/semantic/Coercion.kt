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
    // 引用：可变性只能弱化（&mut T → &T），内层继续按可赋值性递归；
    // 另外允许 `&Box<...Box<T>>` → `&T` 的解引用收敛（只在引用层之下发生）
    from is RefType && to is RefType ->
        (!to.mutable || from.mutable) &&
            (assignable(from.inner, to.inner) || boxDerefChainMatches(from.inner, to.inner))
    else -> false
}

/**
 * 解引用收敛：沿 `Box` 逐层解引用后能否到达 [to]。
 *
 * - `&Box<Box<i32>>` → `&i32` ✓（连续解盒）
 * - `&Vec<i32>` → `&i32` ✗（Vec 不参与）
 * - `Box<i32>` → `i32` ✗（不在引用层之下，不会被调用到）
 */
private fun boxDerefChainMatches(from: Type, to: Type): Boolean {
    var current = from
    while (current is BoxType) {
        current = current.element
        if (assignable(current, to)) return true
    }
    return false
}

/** [a]、[b] 的公共类型：`!` 与 `?` 都吸收到另一侧。 */
internal fun join(a: Type, b: Type, span: Span, what: String): Type = when {
    a == NeverType -> b
    b == NeverType -> a
    a == UnknownType -> b
    b == UnknownType -> a
    a == b -> a
    // 引用 LUB：`&mut T` 与 `&T` 的公共类型是 `&T`（只能弱化，不能加强）
    a is RefType && b is RefType ->
        RefType(a.mutable && b.mutable, join(a.inner, b.inner, span, what))
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

/**
 * 能否在**不调用 clone** 的情况下按位复制。
 *
 * 目前只被 `[e; n]`（n > 1）使用：同一个值要出现 n 次，就必须能复制。
 * 注意 `&mut T` **不是** Copy（复制会让同一块内存出现两个可变路径），
 * `&T` 是 Copy；结构体只有显式 `#[derive(Copy)]` 才是。
 */
internal fun isCopy(type: Type): Boolean = when (type) {
    is IntegerType, BoolType, UnitType -> true
    UnknownType -> true                       // 脚手架通配，不误伤
    is RefType -> !type.mutable
    is ArrayType -> isCopy(type.element)
    is StructType -> type.decl?.derives?.any { it == Derive.COPY } ?: false
    else -> false                             // Box / Vec / Never
}