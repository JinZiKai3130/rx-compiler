package rx.ast

/**
 * 编译器"内部类型"（语义阶段使用），与 TypeRefNode（源码写法）不是一回事：
 * TypeRefNode 是用户写出来的字，Type 是编译器理解的意思。
 * 2.4 阶段先占坑；2.5 起逐步填充（数组、结构体、引用等类型后面再加）。
 */
sealed interface Type

/** i32 */
data object IntType : Type

/** bool */
data object BoolType : Type

/** () —— 单元类型 */
data object UnitType : Type

/** ! —— never：return / break / continue 这类"永不正常返回"的表达式 */
data object NeverType : Type
