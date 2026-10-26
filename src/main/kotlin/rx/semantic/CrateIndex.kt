package rx.semantic

import rx.ast.*

/**
 * 阶段 1：声明收集（CrateIndex）。
 *
 * 只把"引入顶层名字"的 item 放进全局作用域：struct / fn / const。
 * 顺序无关，因此支持前向引用。
 *
 * impl 不引入顶层名字（成员只能通过 `S::x` / `Self::x` 访问），
 * 由阶段 2 [ImplRegistry] 挂到结构体身份上。
 */
class CrateIndex(private val ctx: SemanticContext) {

    fun collect(items: List<ItemNode>) {
        for (item in items) collectItem(item)
    }

    private fun collectItem(item: ItemNode) {
        when (item) {
            is StructNode -> {
                if (!ctx.globals.declareType(StructSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate struct '${item.name}'")
                }
            }

            is FnNode -> {
                if (!ctx.globals.declareValue(FunctionSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate function '${item.name}'")
                }
            }

            is ConstNode -> {
                if (!ctx.globals.declareValue(ConstantSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate const '${item.name}'")
                }
            }

            is ImplNode -> { /* 阶段 2 处理 */ }
        }
    }
}
