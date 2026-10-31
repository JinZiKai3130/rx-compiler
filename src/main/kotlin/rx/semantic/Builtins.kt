package rx.semantic

import rx.ast.*

/** 常用的 self 形态：`&self` / `&mut self`，内建方法表与 clone 兜底共用。 */
internal val SHARED_SELF = SelfParam(byReference = true, mutable = false)
internal val MUT_SELF = SelfParam(byReference = true, mutable = true)

/**
 * 内建环境：把编译器自带的名字预置进全局作用域。
 *
 * 类型名字空间：i32/u32/isize/usize/bool + Box/Vec；
 * 值名字空间：get_i32 / print_i32 / println_i32（签名在此固定）。
 */
object Builtins {

    private val TYPES: Map<String, BuiltinTypeKind> = mapOf(
        "i32"   to BuiltinTypeKind.I32,
        "u32"   to BuiltinTypeKind.U32,
        "isize" to BuiltinTypeKind.ISIZE,
        "usize" to BuiltinTypeKind.USIZE,
        "bool"  to BuiltinTypeKind.BOOL,
        "Box"   to BuiltinTypeKind.BOX,
        "Vec"   to BuiltinTypeKind.VEC,
    )

    private val FUNCTIONS: List<BuiltinFunctionSymbol> = listOf(
        BuiltinFunctionSymbol("get_i32", emptyList(), I32Type),
        BuiltinFunctionSymbol("print_i32", listOf(I32Type), UnitType),
        BuiltinFunctionSymbol("println_i32", listOf(I32Type), UnitType),
    )

    /**
     * 容器（Box / Vec）的关联项表。
     *
     * - `new` 是关联函数（`selfParam = null`）；
     * - 其余是方法：`selfParam` 同时决定接收者要求与 UFCS 调用时前插的类型。
     *
     * 返回 null = 该容器没有这个关联项。
     */
    fun builtinMethodSignature(receiver: Type, name: String): FunctionSignature? = when (receiver) {
        is VecType -> when (name) {
            "new" -> FunctionSignature(emptyList(), receiver, null)   // Vec::new() 不带值参数
            "len" -> FunctionSignature(emptyList(), UsizeType, SHARED_SELF)
            "is_empty" -> FunctionSignature(emptyList(), BoolType, SHARED_SELF)
            "push" -> FunctionSignature(listOf(receiver.element), UnitType, MUT_SELF)
            "remove" -> FunctionSignature(listOf(UsizeType), receiver.element, MUT_SELF)
            "clone" -> FunctionSignature(emptyList(), receiver, SHARED_SELF)
            else -> null
        }

        is BoxType -> when (name) {
            "new" -> FunctionSignature(listOf(receiver.element), receiver, null)
            "clone" -> FunctionSignature(emptyList(), receiver, SHARED_SELF)
            else -> null
        }

        else -> null
    }

    fun install(scope: Scope) {
        for ((name, kind) in TYPES) {
            scope.declareType(BuiltinTypeSymbol(name, kind))
        }
        for (fn in FUNCTIONS) {
            scope.declareValue(fn)
        }
    }
}
