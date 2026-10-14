package rx.semantic

import rx.ast.CrateNode

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

    /** 检查整个 crate。WP0：暂不产生任何错误。 */
    fun analyze(crate: CrateNode) {
        // WP1: collect(crate.items)
        // WP2+: 逐函数体检查（globals 的生命周期随本次分析）
    }
}
