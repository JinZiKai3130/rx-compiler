package rx.frontend

/**
 * A lexical or syntactic error found while parsing.
 *
 * [line] is 1-based and [column] is 0-based, following ANTLR's Token convention;
 * [render] prints both as 1-based, the usual compiler convention.
 */
data class SyntaxDiagnostic(
    val stage: String,
    val line: Int,
    val column: Int,
    val message: String,
) {
    fun render(file: String): String = "$file:$line:${column + 1}: error: [$stage] $message"
}
