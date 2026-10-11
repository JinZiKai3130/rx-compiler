package rx.frontend

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
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

/** Result of a lexer-only run (official `lex` stage): all tokens plus lexical diagnostics. */
class LexResult(
    val tokens: CommonTokenStream,
    val diagnostics: List<SyntaxDiagnostic>,
)

/** Entry rules used by the official parser corpus (`metadata.entry` in its manifest). */
val ENTRY_RULES = listOf("crate", "item", "expression", "letStatement", "typeRef")

/** Token types the lexer emits for invalid input (kept on the default channel). */
private val INVALID_TOKEN_TYPES = setOf(
    "INVALID_LIFETIME",
    "INVALID_CHARACTER_LITERAL",
    "INVALID_NUMBER",
    "UNTERMINATED_BLOCK_COMMENT",
    "ERROR_CHAR",
)

/**
 * Front end driver (manual section 2.4, first half): feeds source text through the
 * ANTLR lexer and parser, replaces the default error listeners with collecting ones,
 * and returns the CST. No output is printed here; the caller decides.
 */
class ParserDriver {

    /** Parses the whole file with the default `crate` entry. */
    fun parseCrate(path: String): ParseResult = parseEntry(path, "crate")

    /** Parses with one of the five official entry rules ([ENTRY_RULES]). */
    fun parseEntry(path: String, entry: String): ParseResult {
        val diagnostics = mutableListOf<SyntaxDiagnostic>()
        val tokens = newTokenStream(path, diagnostics)

        val parser = RxParser(tokens)
        parser.removeErrorListeners()
        parser.addErrorListener(CollectingErrorListener("parser", diagnostics))

        val tree = when (entry) {
            "crate" -> parser.crate()
            "item" -> parser.item()
            "expression" -> parser.expression()
            "letStatement" -> parser.letStatement()
            "typeRef" -> parser.typeRef()
            else -> throw IllegalArgumentException(
                "unknown entry rule '$entry' (expected one of ${ENTRY_RULES.joinToString(", ")})"
            )
        }

        // Only `crate` ends with EOF; the other entry rules do not, so a parse that
        // stops early would silently ignore trailing input. Fragments must be consumed
        // completely, otherwise the file is rejected like any other syntax error.
        val nextToken = tokens.LT(1)
        if (nextToken.type != Token.EOF) {
            diagnostics.add(
                SyntaxDiagnostic(
                    stage = "parser",
                    line = nextToken.line,
                    column = nextToken.charPositionInLine,
                    message = "extraneous input '${nextToken.text}' expecting <EOF>",
                )
            )
        }

        return ParseResult(tree, parser, tokens, diagnostics.toList())
    }

    /** Lexes the whole file without parsing (official `lex` stage). */
    fun lexOnly(path: String): LexResult {
        val diagnostics = mutableListOf<SyntaxDiagnostic>()
        val tokens = newTokenStream(path, diagnostics)
        tokens.fill() // pull every token so all lexical errors surface

        // Invalid tokens deliberately stay on the default channel: flag each one explicitly.
        for (token in tokens.tokens) {
            val name = RxLexer.VOCABULARY.getSymbolicName(token.type)
            if (name != null && name in INVALID_TOKEN_TYPES) {
                diagnostics.add(
                    SyntaxDiagnostic(
                        stage = "lexer",
                        line = token.line,
                        column = token.charPositionInLine,
                        message = "invalid token '${token.text}' ($name)",
                    )
                )
            }
        }

        return LexResult(tokens, diagnostics.sortedWith(compareBy({ it.line }, { it.column })))
    }

    /** Lexer with collecting error listeners; shared by both stages. */
    private fun newTokenStream(
        path: String,
        diagnostics: MutableList<SyntaxDiagnostic>,
    ): CommonTokenStream {
        val lexer = RxLexer(CharStreams.fromFileName(path))
        lexer.removeErrorListeners()
        lexer.addErrorListener(CollectingErrorListener("lexer", diagnostics))
        return CommonTokenStream(lexer)
    }
}
