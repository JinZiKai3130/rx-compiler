package rx.semantic

import rx.ast.*

/**
 * 语义错误（2.5）：由 [SemanticAnalyzer] 抛出，CLI 捕获后按统一格式报告并退出码 1。
 *
 * 输出格式与前端诊断一致：
 *   [file]:[line]:[col]: error: [semantic] message
 * 其中 [Span] 的行从 1 数、列从 0 数，这里打印时列 +1（与 SyntaxDiagnostic.render 相同）。
 */
class SemanticError(
    val span: Span,
    message: String,
) : RuntimeException(message) {
    fun render(file: String): String =
        "$file:${span.startLine}:${span.startColumn + 1}: error: [semantic] $message"
}
