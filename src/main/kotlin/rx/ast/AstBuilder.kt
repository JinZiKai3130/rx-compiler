package rx.ast

import org.antlr.v4.runtime.ParserRuleContext
import rx.gen.RxParser
import rx.gen.RxParserBaseVisitor

class AstBuilder : RxParserBaseVisitor<AstNode>() {
    override fun visitCrate(ctx: RxParser.CrateContext): CrateNode {
        val items = mutableListOf<ItemNode>()
        for (itemContext in ctx.item()) {
            val visitedNode = visit(itemContext)
            val itemNode = visitedNode as? ItemNode

            if (itemNode == null) {
                throw IllegalArgumentException(
                    "AST builder does not support item '${itemContext.text}' yet"
                )
            }
            items.add(itemNode)
        }

        return CrateNode(items, spanOf(ctx))
    }

    private fun spanOf(ctx: ParserRuleContext): Span {
        val start = ctx.start
        val stop = ctx.stop ?: start

        return Span(
            startLine = start.line,
            startColumn = start.charPositionInLine,
            endLine = stop.line,
            endColumn = stop.charPositionInLine + (stop.text?.length ?: 0),
        )
    }
}
