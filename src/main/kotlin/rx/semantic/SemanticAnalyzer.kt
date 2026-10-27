package rx.semantic

import rx.ast.CrateNode

/**
 * 语义分析（2.5）总入口。
 *
 * 只负责"按固定顺序调用各阶段"；每个阶段一个文件：
 *
 * | # | 文件               | 职责                                                  |
 * |---|--------------------|-------------------------------------------------------|
 * | 0 | [Builtins]         | 内建环境：原生类型、Box/Vec、I/O 函数                  |
 * | 1 | [CrateIndex]       | 声明收集：struct/fn/const → 全局作用域（支持前向引用） |
 * | 2 | [ImplRegistry]     | 结构体身份：impl 目标校验 + 关联项登记（名字层面）      |
 * | 3 | [EntryChecker]     | `main` 契约                                            |
 * | 4 | [ConstEval]        | 常量求值：const 初值 / 数组长度 / repeat 长度 + 环检测  |
 * | 5 | [StructResolver]   | 结构体字段类型（`Vec<Self>` 也在这里解析）              |
 * | 6 | [FunctionResolver] | 函数/方法签名类型                                       |
 * | 7 | （TODO）           | 全局约束：递归布局有限大小、derive 能力                  |
 * | 8 | [BodyChecker]      | 函数体名字解析（类型检查后续接入）                      |
 *
 * 阶段顺序是硬约束：2 必须在 4/5/6/8 之前（关联项在那里登记），
 * 5/6 必须在 8 之前（body 检查要用字段表和签名）。
 *
 * 约定：fail-fast——第一个语义错误立即抛 [SemanticError]，CLI 捕获后 exit 1。
 */
class SemanticAnalyzer {

    private val ctx = SemanticContext()

    fun analyze(crate: CrateNode) {
        Builtins.install(ctx.globals)                  // 0 内建环境

        CrateIndex(ctx).collect(crate.items)           // 1 声明收集
        ImplRegistry(ctx).register(crate.items)        // 2 结构体身份 + impl 头部
        EntryChecker.check(crate)                      // 3 入口契约
        ctx.constEval.evaluateAll(crate)               // 4 常量求值
        StructResolver(ctx).resolve(crate.items)       // 5 结构体字段
        FunctionResolver(ctx).resolve(crate.items)     // 6 函数签名
        GlobalConstraints(ctx).check(crate.items)      // 7 全局约束（递归布局 / derive 能力）
        BodyChecker(ctx).check(crate.items)            // 8 函数体
    }
}
