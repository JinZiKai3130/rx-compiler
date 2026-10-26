package rx.semantic

import rx.ast.*

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

    fun install(scope: Scope) {
        for ((name, kind) in TYPES) {
            scope.declareType(BuiltinTypeSymbol(name, kind))
        }
        for (fn in FUNCTIONS) {
            scope.declareValue(fn)
        }
    }
}
