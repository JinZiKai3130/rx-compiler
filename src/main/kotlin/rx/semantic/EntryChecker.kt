package rx.semantic

import rx.ast.*

/**
 * 阶段 3：入口契约。
 *
 * `main` 必须是顶层函数、没有参数（含 receiver）、没有泛型参数、返回 `()`（可省略）。
 */
object EntryChecker {

    fun check(crate: CrateNode) {
        val main = crate.items
            .filterIsInstance<FnNode>()
            .firstOrNull { it.name == "main" }
            ?: throw SemanticError(
                crate.span,
                "an executable needs a top-level `main` function",
            )

        if (main.params.isNotEmpty()) {
            throw SemanticError(main.span, "`main` cannot have parameters")
        }

        if (main.hasGenericParams) {
            throw SemanticError(main.span, "`main` cannot have generic parameters")
        }

        val returnType = main.returnType
        if (returnType != null && returnType !is UnitTypeNode) {
            throw SemanticError(main.span, "`main` must return `()`")
        }
    }
}
