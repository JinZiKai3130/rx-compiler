package rx.frontend

import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer

/**
 * Collects ANTLR syntax errors instead of printing them, so the compiler controls
 * diagnostics and exit codes. Attached to both the lexer and the parser.
 */
class CollectingErrorListener(
    private val stage: String,
    private val sink: MutableList<SyntaxDiagnostic>,
) : BaseErrorListener() {

    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String?,
        e: RecognitionException?,
    ) {
        sink.add(SyntaxDiagnostic(stage, line, charPositionInLine, msg ?: "syntax error"))
    }
}
