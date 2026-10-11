package rx

import org.antlr.v4.runtime.Token
import rx.ast.AstBuilder
import rx.ast.AstPrinter
import rx.frontend.ENTRY_RULES
import rx.frontend.ParserDriver
import rx.frontend.SyntaxDiagnostic
import rx.gen.RxLexer
import rx.gen.RxParser
import java.io.IOException
import kotlin.system.exitProcess

private const val USAGE =
    "usage: rxc [--stage lex|parse] [--entry crate|item|expression|letStatement|typeRef]\n" +
        "           [--dump-cst] [--dump-ast] [--tokens] <file.rx>"

/**
 * Rx compiler CLI.
 *
 * Exit codes:
 *   0 = accepted (with --dump-ast: the AST was also built successfully)
 *   1 = lexing / parsing errors
 *   2 = usage / I/O error
 *   3 = parsed cleanly, but the AST builder does not support the construct yet
 */
fun main(args: Array<String>) {
    var stage = "parse"
    var entry = "crate"
    var dumpCst = false
    var dumpAst = false
    var dumpTokens = false
    val files = mutableListOf<String>()

    var index = 0
    while (index < args.size) {
        when (args[index]) {
            "--stage" -> {
                index++
                if (index >= args.size) usageError()
                stage = args[index]
            }
            "--entry" -> {
                index++
                if (index >= args.size) usageError()
                entry = args[index]
            }
            "--dump-cst" -> dumpCst = true
            "--dump-ast" -> dumpAst = true
            "--tokens" -> dumpTokens = true
            "-h", "--help" -> {
                println(USAGE)
                return
            }
            else -> {
                if (args[index].startsWith("--")) usageError()
                files += args[index]
            }
        }
        index++
    }

    if (stage != "lex" && stage != "parse") usageError()
    if (entry !in ENTRY_RULES) usageError()
    if (stage == "lex" && (dumpAst || dumpCst)) usageError()
    if (files.size != 1) usageError()

    val file = files[0]

    if (stage == "lex") {
        val result = try {
            ParserDriver().lexOnly(file)
        } catch (e: IOException) {
            ioError(file, e)
        }

        if (dumpTokens) dumpTokenList(result.tokens.tokens)
        if (result.diagnostics.isNotEmpty()) report(file, result.diagnostics)
        return
    }

    val result = try {
        ParserDriver().parseEntry(file, entry)
    } catch (e: IOException) {
        ioError(file, e)
    }

    if (dumpTokens) dumpTokenList(result.tokens.tokens)
    if (dumpCst) println(result.tree.toStringTree(result.parser))
    if (result.diagnostics.isNotEmpty()) report(file, result.diagnostics)

    if (dumpAst) {
        val root = try {
            AstBuilder().visit(result.tree)
        } catch (e: RuntimeException) {
            System.err.println("$file: error: [ast] ${e.message}")
            exitProcess(3)
        }

        if (root == null) {
            // use 声明按规范整条丢弃、不建树：这是预期行为，不算失败。
            val item = result.tree as? RxParser.ItemContext
            if (item != null && item.useDeclaration() != null) {
                println("(use declaration discarded: no AST node)")
            } else {
                System.err.println("$file: error: [ast] builder produced no node for entry '$entry'")
                exitProcess(3)
            }
        } else {
            print(AstPrinter().print(root))
        }
    }
}

private fun usageError(): Nothing {
    System.err.println(USAGE)
    exitProcess(2)
}

private fun ioError(file: String, e: IOException): Nothing {
    System.err.println("error: cannot read '$file': ${e.message}")
    exitProcess(2)
}

private fun report(file: String, diagnostics: List<SyntaxDiagnostic>): Nothing {
    for (d in diagnostics) System.err.println(d.render(file))
    exitProcess(1)
}

private fun dumpTokenList(tokens: List<Token>) {
    for (t in tokens) {
        val name = RxLexer.VOCABULARY.getSymbolicName(t.type) ?: t.type.toString()
        val text = t.text?.replace("\n", "\\n") ?: ""
        println("${t.tokenIndex}\t$name\t'$text'\t@${t.line}:${t.charPositionInLine}")
    }
}
