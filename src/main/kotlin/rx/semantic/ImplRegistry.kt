package rx.semantic

import rx.ast.*

/**
 * 阶段 2：结构体身份 + 登记 impl 头部。
 *
 * 只做三件事：
 * 1. 校验 impl 目标必须是用户定义的 struct；
 * 2. 为每个关联项建立符号（方法/关联函数、关联常量）；
 * 3. 挂到 [StructSymbol.associatedValues]，跨 impl 块共享并查重。
 *
 * 不解析签名（阶段 6）、不求值常量（阶段 4）、不检查函数体（阶段 8）。
 */
class ImplRegistry(private val ctx: SemanticContext) {

    fun register(items: List<ItemNode>) {
        for (item in items) if (item is ImplNode) registerImpl(item)
    }

    private fun registerImpl(node: ImplNode) {
        val target = node.target

        // &S、[T; N]、() 等写法直接拒绝（不是类型路径）
        if (target !is PathTypeNode) {
            throw SemanticError(target.span, "impl target must be a user-defined struct")
        }

        // 一次解析：顺带拒绝 Box<T>、S<i32>、i32、多段路径等
        val resolved = ctx.resolveType(target)
        if (resolved !is StructType) {
            throw SemanticError(target.span, "impl target must be a user-defined struct")
        }

        val symbol = ctx.globals.lookupType(resolved.name) as? StructSymbol
            ?: throw SemanticError(target.span, "impl target must be a user-defined struct")

        for (item in node.items) {
            val associated: Symbol = when (item) {
                is FnNode -> FunctionSymbol(
                    name = item.name,
                    node = item,
                    receiver = target,
                    owner = symbol,
                )

                is ConstNode -> ConstantSymbol(
                    name = item.name,
                    node = item,
                    owner = symbol,
                )

                else -> continue
            }

            if (symbol.associatedValues.containsKey(associated.name)) {
                throw SemanticError(item.span, "duplicate associated item '${associated.name}'")
            }

            symbol.associatedValues[associated.name] = associated
        }
    }
}
