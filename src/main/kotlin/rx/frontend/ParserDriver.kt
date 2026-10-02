package rx.frontend

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import rx.gen.RxLexer
import rx.gen.RxParser

/**
 * Result of a lexer + parser run over one source file.
 *
 * [tree] is the concrete syntax tree (CST) root; [parser] can be used to print it
 * (e.g. `tree.toStringTree(parser)`).
 */
class ParseResult(
    val tree: ParseTree,
    val parser: RxParser,
    val tokens: CommonTokenStream,
    val diagnostics: List<SyntaxDiagnostic>,
)

/**
 * Front end driver (manual section 2.4, first half): feeds source text through the
 * ANTLR lexer and parser, replaces the default error listeners with collecting ones,
 * and returns the CST. No output is printed here; the caller decides.
 */
class ParserDriver {

    fun parseCrate(path: String): ParseResult {
        val diagnostics = mutableListOf<SyntaxDiagnostic>()

        val lexer = RxLexer(CharStreams.fromFileName(path))
        lexer.removeErrorListeners()
        lexer.addErrorListener(CollectingErrorListener("lexer", diagnostics))

        val tokens = CommonTokenStream(lexer)
        val parser = RxParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(CollectingErrorListener("parser", diagnostics))

        val tree = parser.crate()
        return ParseResult(tree, parser, tokens, diagnostics.toList())
    }
}
