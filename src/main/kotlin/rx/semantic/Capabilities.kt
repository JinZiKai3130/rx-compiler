package rx.semantic

import rx.ast.*

/**
 * 类型能力（批次 F）。
 *
 * 批次 A–E 回答"类型算得对不对"，这里回答"这个类型**有没有**这个能力"：
 * - `==` / `!=` 需要 `PartialEq`
 * - `.clone()` 需要 `Clone`
 * - `[e; n]`（n > 1）需要 `Copy`
 *
 * 三条都按**静态类型**递归判断，和运行时内容无关：空的 `Vec<Opaque>` 一样不可克隆。
 * 容器看元素，结构体看 `derive`；引用是例外——引用自身可复制、可比较，
 * 不要求被指向的类型有能力（`&Opaque` 可以 clone，`Opaque` 不必可 clone）。
 */

/** 能否用 `==` / `!=` 比较。 */
internal fun supportsPartialEq(type: Type): Boolean = when (type) {
    is IntegerType, BoolType, UnitType -> true
    UnknownType -> true                       // 脚手架通配，不误伤
    is RefType -> true                        // 引用比的是地址
    is ArrayType -> supportsPartialEq(type.element)
    is VecType -> supportsPartialEq(type.element)
    is BoxType -> supportsPartialEq(type.element)
    is StructType -> type.decl?.derives?.any { it == Derive.PARTIAL_EQ } ?: false
    else -> false                             // Never
}

/** 能否 `clone()`。 */
internal fun supportsClone(type: Type): Boolean = when (type) {
    is IntegerType, BoolType, UnitType -> true
    UnknownType -> true
    is RefType -> !type.mutable               // 复制 `&mut` 会造出第二条可变路径
    is ArrayType -> supportsClone(type.element)
    is VecType -> supportsClone(type.element)
    is BoxType -> supportsClone(type.element)
    is StructType -> type.decl?.derives?.any { it == Derive.CLONE || it == Derive.COPY } ?: false
    else -> false
}

/** 能否在不调用 clone 的情况下按位复制（`[e; n]`，n > 1）。 */
internal fun isCopy(type: Type): Boolean = when (type) {
    is IntegerType, BoolType, UnitType -> true
    UnknownType -> true
    is RefType -> !type.mutable               // `&mut T` 不是 Copy
    is ArrayType -> isCopy(type.element)
    is StructType -> type.decl?.derives?.any { it == Derive.COPY } ?: false
    else -> false                             // Box / Vec / Never
}
