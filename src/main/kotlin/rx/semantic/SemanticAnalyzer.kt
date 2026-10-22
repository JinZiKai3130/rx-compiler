package rx.semantic

import rx.ast.CrateNode
import rx.ast.ConstNode
import rx.ast.FnNode
import rx.ast.FnParam
import rx.ast.ImplNode
import rx.ast.ItemNode
import rx.ast.StructNode
import rx.ast.UseNode
/**
 * 语义分析器（2.5）总入口：对整棵 AST 查名字、查类型（semantic_design.md）。
 *
 * WP0 状态：空壳——只搭好入口与全局作用域；检查内容按 WP 分批接入：
 * - WP1 全局收集（struct/fn/const/impl → globals 的两个名字空间）、重名/保护名/main/impl 合法性；
 * - WP2 函数体作用域与名字解析（块级进出、未定义名、shadowing）；
 * - WP3+ 表达式类型检查（check(expr, expected)）、place 与可变性、常量求值、derive、布局……
 *
 * 约定：发现语义错误立即抛 [SemanticError]（fail-fast），CLI 捕获后报告并 exit 1。
 */


class SemanticAnalyzer {

    /**
     * 全局作用域（parent = null）。
     * WP1 起：先预注册内建环境（i32 等类型名、print_i32/get_i32 等函数），再收集顶层声明。
     */
    val globals: Scope = Scope(null)


    private val BUILTIN_TYPE_NAMES = listOf(
        "i32", "u32", "isize", "usize", "bool", "Box", "Vec",
    )

    private val BUILTIN_VALUE_NAMES = listOf(
        "get_i32", "print_i32", "println_i32",
    )

    private fun installBuiltins(scope: Scope) {
        for (name in BUILTIN_TYPE_NAMES) {
            scope.declareType(BuiltinTypeSymbol(name))
        }
        for (name in BUILTIN_VALUE_NAMES) {
            scope.declareValue(BuiltinFunctionSymbol(name))
        }
    }

    /** 检查整个 crate。WP0：暂不产生任何错误。 */
    fun analyze(crate: CrateNode) {
        installBuiltins(globals)

        collectTopLevel(crate.items)
        checkDeclarations(crate.items)
        // WP2+: 逐函数体检查（globals 的生命周期随本次分析）
    }

    private fun collectTopLevel(items: List<ItemNode>) {
        for (item in items) collectItem(item)
    }

    private fun collectItem(item: ItemNode) {
        when (item) {
            is StructNode -> { 
                if (!globals.declareType(StructSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate struct '${item.name}'")
                }
            }
            is FnNode     -> { 
                if (!globals.declareValue(FunctionSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate function '${item.name}'")
                }
            }
            is ConstNode  -> { 
                if (!globals.declareValue(ConstantSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate const '${item.name}'")
                }
            }
            is ImplNode   -> { /* 这一小步先什么都不做 */ }
            is UseNode    -> { /* 理论上不会出现，忽略 */ }
        }
    }

    private fun checkDeclarations(items: List<ItemNode>) {
        for (item in items) {
            when (item) {
                is StructNode -> checkStructFields(item)
                is FnNode     -> checkFunctionParams(item)
                is ConstNode  -> { /* 暂不检查 */ }
                is ImplNode   -> { /* 下一步处理 */ }
                is UseNode    -> {}
            }
        }
    }

    private fun checkStructFields(node: StructNode) {
        val seen  = HashSet<String>()

        for(field in node.fields) {
            if (!seen.add(field.name)) {
                throw SemanticError(node.span, "duplicate parameter '${field.name}'")
            }
        }
    }
 
    private fun checkFunctionParams(node: FnNode) {
        val seen = HashSet<String>()          // 只在这次调用里存在

        for (param in node.params) {
            if (param !is FnParam) continue
            if (!seen.add(param.name)) {
                throw SemanticError(node.span, "duplicate parameter '${param.name}'")
            }
        }
    }
}