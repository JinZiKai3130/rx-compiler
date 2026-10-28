package rx.ast

/**
 * 编译器"内部类型"（语义阶段使用），与 TypeRefNode（源码写法）不是一回事：
 * TypeRefNode 是用户写出来的字，Type 是编译器理解的意思。
 * 2.5 语义阶段使用：把 TypeRefNode 翻译成 Type 填到 ExprNode.type（决策 D），
 * 供类型检查与代码生成使用（semantic_design.md §2.2）。
 * WP0 补全类型集合；相等性按结构，结构体按名字（名义类型）。
 */
sealed interface Type

/** 四种整数类型的公共接口（isize/usize 按 RV32 目标 = 32 位）。 */
sealed interface IntegerType : Type {
    val signed: Boolean
    val bits: Int
}

/** i32 */
data object I32Type : IntegerType {
    override val signed: Boolean get() = true
    override val bits: Int get() = 32
    override fun toString() = "i32"
}

/** u32 */
data object U32Type : IntegerType {
    override val signed: Boolean get() = false
    override val bits: Int get() = 32
    override fun toString() = "u32"
}

/** isize */
data object IsizeType : IntegerType {
    override val signed: Boolean get() = true
    override val bits: Int get() = 32
    override fun toString() = "isize"
}

/** usize */
data object UsizeType : IntegerType {
    override val signed: Boolean get() = false
    override val bits: Int get() = 32
    override fun toString() = "usize"
}

/** bool */
data object BoolType : Type {
    override fun toString() = "bool"
}

/** () —— 单元类型 */
data object UnitType : Type {
    override fun toString() = "()"
}

/** ! —— never：return / break / continue 这类"永不正常返回"的表达式 */
data object NeverType : Type {
    override fun toString() = "!"
}

/**
 * 结构体类型：名义类型——相等只看名字（形状相同的两个结构体仍是不同类型），与 Rust 一致。
 * [decl] 指向结构体声明（WP1 起由符号表填充；代码生成做布局时也要用）。
 */
class StructType(
    val name: String,
    val decl: StructNode? = null,
) : Type {
    override fun equals(other: Any?): Boolean = other is StructType && other.name == name
    override fun hashCode(): Int = name.hashCode()
    override fun toString(): String = name
}

/** &T / &mut T */
data class RefType(val mutable: Boolean, val inner: Type) : Type {
    override fun toString(): String = if (mutable) "&mut $inner" else "&$inner"
}

/** [T; N]：N 由常量求值得出（WP5），这里存最终长度。 */
data class ArrayType(val element: Type, val length: Long) : Type {
    override fun toString(): String = "[$element; $length]"
}

/** Box<T> 内建容器（元素类型是唯一实参）。 */
data class BoxType(val element: Type) : Type {
    override fun toString(): String = "Box<$element>"
}

/** Vec<T> 内建容器。 */
data class VecType(val element: Type) : Type {
    override fun toString(): String = "Vec<$element>"
}

data object UnknownType : Type { override fun toString() = "?" }