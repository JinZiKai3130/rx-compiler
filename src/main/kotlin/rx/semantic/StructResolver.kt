package rx.semantic

import rx.ast.*

/**
 * 阶段 5：结构体字段（StructResolver）。
 *
 * 把每个 struct 的字段类型解析成内部 [Type]，按声明顺序存进 [StructSymbol.fields]。
 * 字段里可以写 `Self` / `Vec<Self>`——解析期间把 [SemanticContext.currentSelfType]
 * 指向当前结构体。
 *
 * 本阶段只建立字段表：递归布局/有限大小留给阶段 7，字段访问留给阶段 8。
 */
class StructResolver(private val ctx: SemanticContext) {

    fun resolve(items: List<ItemNode>) {
        for (item in items) {
            if (item !is StructNode) continue
            val symbol = ctx.globals.lookupType(item.name) as? StructSymbol ?: continue

            val savedSelf = ctx.currentSelfType
            ctx.currentSelfType = symbol
            try {
                symbol.fields.clear()
                for (field in item.fields) {
                    if (symbol.fields.containsKey(field.name)) {
                        throw SemanticError(field.type.span, "duplicate field '${field.name}'")
                    }
                    symbol.fields[field.name] = ctx.resolveType(field.type)
                }
            } finally {
                ctx.currentSelfType = savedSelf
            }
        }
    }
}
