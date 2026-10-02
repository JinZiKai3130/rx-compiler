package rx

import rx.frontend.ParserDriver
import rx.gen.RxLexer
import java.io.IOException
import kotlin.system.exitProcess

private const val USAGE = "usage: rxc [--dump-cst] [--tokens] <file.rx>"

/**
 * Rx compiler CLI.
 *
 * Exit codes: 0 = parsed without syntax errors, 1 = syntax errors, 2 = usage / I/O error.
 * (Later stages will add --stage semantic/ir/codegen; for now the front end only parses.)
 */
fun main(args: Array<String>) {
    var dumpCst = false
    var dumpTokens = false
    val files = mutableListOf<String>()

    for (arg in args) {
        when (arg) {
            "--dump-cst" -> dumpCst = true
            "--tokens" -> dumpTokens = true
            "-h", "--help" -> {
                println(USAGE)
                return
            }
            else -> files += arg
        }
    }

    if (files.size != 1) {
        System.err.println(USAGE)
        exitProcess(2)
    }

    val result = try {
        ParserDriver().parseCrate(files[0])
    } catch (e: IOException) {
        System.err.println("error: cannot read '${files[0]}': ${e.message}")
        exitProcess(2)
    }

    if (dumpTokens) {
        for (t in result.tokens.tokens) {
            val name = RxLexer.VOCABULARY.getSymbolicName(t.type) ?: t.type.toString()
            val text = t.text?.replace("\n", "\\n") ?: ""
            println("${t.tokenIndex}\t$name\t'$text'\t@${t.line}:${t.charPositionInLine}")
        }
    }

    if (dumpCst) {
        println(result.tree.toStringTree(result.parser))
    }

    val diagnostics = result.diagnostics
    if (diagnostics.isNotEmpty()) {
        for (d in diagnostics) System.err.println(d.render(files[0]))
        exitProcess(1)
    }
}
