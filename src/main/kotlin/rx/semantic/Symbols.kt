package rx.semantic

import rx.ast.ConstNode
import rx.ast.FnNode
import rx.ast.Span
import rx.ast.StructNode
import rx.ast.TypeRefNode

/**
 * 符号：名字解析的产物，存进 [Scope] 的两个名字空间（类型 / 值）。
 *
 * WP0 只搭分类骨架；WP1 起补字段（函数签名、derive 集合、常量值、关联项的 owner 等）——
 * 字段加在具体子类上，不动本接口。
 */
sealed interface Symbol {
    val name: String
}

/** 结构体（类型名字空间）。 */
class StructSymbol(
    override val name: String,
    val node: StructNode,
) : Symbol

/**
 * 函数或方法（值名字空间）。
 * [receiver] 非空 = `impl` 块里的方法/关联函数，存 impl 的目标类型写法；
 * 为空 = 顶层普通函数。
 */
class FunctionSymbol(
    override val name: String,
    val node: FnNode,
    val receiver: TypeRefNode? = null,
) : Symbol

/** 常量（值名字空间）：顶层 `const`；impl 里的关联常量（WP1 接线时再补 owner）。 */
class ConstantSymbol(
    override val name: String,
    val node: ConstNode,
) : Symbol

/** 局部变量与函数参数（值名字空间）。[span] = 声明处，供"未定义/重复定义"报错定位。 */
class LocalSymbol(
    override val name: String,
    val mutable: Boolean,
    val span: Span?,
) : Symbol

// 包含i32、Box等等
enum class BuiltinTypeKind {
    I32, U32, ISIZE, USIZE, BOOL, BOX, VEC,
}

class BuiltinTypeSymbol(
    override val name: String,
    val kind: BuiltinTypeKind,
) : Symbol

// 包括print_i32等
class BuiltinFunctionSymbol(
    override val name: String,
) : Symbol

