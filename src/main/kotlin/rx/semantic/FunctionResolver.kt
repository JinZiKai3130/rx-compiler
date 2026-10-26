package rx.semantic

import rx.ast.*

/**
 * 阶段 6：函数与方法的签名（FunctionResolver）。
 *
 * 把 [FnNode] 的参数/返回类型解析成内部 [Type]，写进 [FunctionSymbol.signature]。
 * - 顶层函数：`Self` 不可用，`self` 参数非法；
 * - impl 成员：解析期间 [SemanticContext.currentSelfType] 指向所属结构体。
 *
 * 参数重名也在这里检查（此时才第一次遍历到方法的参数表）。
 */
class FunctionResolver(private val ctx: SemanticContext) {

    fun resolve(items: List<ItemNode>) {
        for (item in items) {
            when (item) {
                is FnNode -> resolveTopLevel(item)
                is ImplNode -> resolveImpl(item)
                else -> {}
            }
        }
    }

    private fun resolveTopLevel(fn: FnNode) {
        if (fn.params.any { it is SelfParam }) {
            throw SemanticError(fn.span, "a top-level function cannot have a receiver")
        }

        val symbol = ctx.globals.lookupValue(fn.name) as? FunctionSymbol ?: return

        val savedSelf = ctx.currentSelfType
        ctx.currentSelfType = null                 // 顶层函数里不允许 Self
        try {
            resolveSignature(fn, symbol)
        } finally {
            ctx.currentSelfType = savedSelf
        }
    }

    private fun resolveImpl(node: ImplNode) {
        val ownerName = (node.target as? PathTypeNode)
            ?.segments?.firstOrNull()?.name ?: return
        val owner = ctx.globals.lookupType(ownerName) as? StructSymbol ?: return

        val savedSelf = ctx.currentSelfType
        ctx.currentSelfType = owner               // `-> Self`、`&Self` 靠它
        try {
            for (member in node.items) {
                if (member !is FnNode) continue
                val symbol = owner.associatedValues[member.name] as? FunctionSymbol
                    ?: continue
                resolveSignature(member, symbol)
            }
        } finally {
            ctx.currentSelfType = savedSelf
        }
    }

    private fun resolveSignature(fn: FnNode, symbol: FunctionSymbol) {
        val seen = HashSet<String>()
        val paramTypes = mutableListOf<Type>()
        var selfParam: SelfParam? = null

        for (param in fn.params) {
            when (param) {
                is SelfParam -> selfParam = param          // self 不占参数名
                is FnParam -> {
                    if (!seen.add(param.name)) {
                        throw SemanticError(fn.span, "duplicate parameter '${param.name}'")
                    }
                    paramTypes.add(ctx.resolveType(param.type))
                }
            }
        }

        val returnType = fn.returnType?.let { ctx.resolveType(it) } ?: UnitType
        symbol.signature = FunctionSignature(paramTypes, returnType, selfParam)
    }
}
