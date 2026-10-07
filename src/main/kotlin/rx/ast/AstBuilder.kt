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

    override fun visitFunctionDefinition(
        ctx: RxParser.FunctionDefinitionContext
    ): FnNode {
        val functionName = ctx.identifier().text

        if (ctx.genericParams() != null) {
            throw IllegalArgumentException(
                "Function '$functionName' has generic parameters, which are not supported yet"
            )
        }

        if (ctx.whereClause() != null) {
            throw IllegalArgumentException(
                "Function '$functionName' has a where clause, which is not supported yet"
            )
        }

        val parameters = mutableListOf<Param>()
        val parameterContext = ctx.functionParameters()

        if (parameterContext != null) {
            val selfParameter = parameterContext.selfParam()

            if (selfParameter != null) {
                val parameter = buildSelfParam(selfParameter)
                parameters.add(parameter)
            }

            for (functionParameter in parameterContext.functionParam()) {
                val parameter = buildFunctionParam(functionParameter)
                parameters.add(parameter)
            }
        }

        val returnType = if (ctx.typeRef() == null) {
            null
        } else {
            visit(ctx.typeRef()) as? TypeRefNode
        }

        if (ctx.typeRef() != null && returnType == null) {
            throw IllegalArgumentException(
                "Function '$functionName' has an unsupported return type"
            )
        }

        val functionBody = visit(ctx.blockExpression())
        val blockBody = functionBody as? BlockExprNode

        if (blockBody == null) {
            throw IllegalArgumentException(
                "Function '$functionName' has an unsupported body"
            )
        }

        return FnNode(
            name = functionName,
            params = parameters,
            returnType = returnType,
            body = blockBody,
            span = spanOf(ctx),
        )
    }

    private fun buildFunctionParam(
        ctx: RxParser.FunctionParamContext
    ): FnParam {
        val binding = ctx.identifierBinding()
        val parameterType = visit(ctx.typeRef()) as? TypeRefNode

        if (parameterType == null) {
            throw IllegalArgumentException(
                "Function parameter '${binding.identifier().text}' has an unsupported type"
            )
        }

        return FnParam(
            name = binding.identifier().text,
            mutable = binding.MUT() != null,
            type = parameterType,
        )
    }

    private fun buildSelfParam(
        ctx: RxParser.SelfParamContext
    ): SelfParam {
        return SelfParam(
            byReference = ctx.AMP() != null, /* 是否是引用：&self */
            mutable = ctx.MUT() != null,
        )
    }

    override fun visitTypeRef(
        ctx: RxParser.TypeRefContext
    ): TypeRefNode {
        if (ctx.typePath() != null) {
            return visit(ctx.typePath()) as PathTypeNode
        }

        if (ctx.referenceType() != null) {
            return visit(ctx.referenceType()) as RefTypeNode
        }

        if (ctx.arrayType() != null) {
            throw IllegalArgumentException(
                "Array types are not supported yet"
            )
        }

        if (ctx.LPAREN() != null && ctx.typeRef() == null) {
            return UnitTypeNode(spanOf(ctx))
        }

        if (ctx.typeRef() != null) {
            return visit(ctx.typeRef()) as TypeRefNode
        }

        throw IllegalArgumentException(
            "Unsupported type reference '${ctx.text}'"
        )
    }

    override fun visitTypePath(
        ctx: RxParser.TypePathContext
    ): PathTypeNode {
        val segments = mutableListOf<PathSegment>()

        for (segmentContext in ctx.typePathSegment()) {
            if (segmentContext.genericArgs() != null) {
                throw IllegalArgumentException(
                    "Generic type arguments are not supported yet"
                )
            }

            val segmentName = segmentContext.pathIdentSegment().text
            segments.add(PathSegment(name = segmentName))
        }

        return PathTypeNode(
            segments = segments,
            span = spanOf(ctx),
        )
    }

    override fun visitReferenceType(
        ctx: RxParser.ReferenceTypeContext
    ): RefTypeNode {
        val innerType = visit(ctx.typeRef()) as? TypeRefNode

        if (innerType == null) {
            throw IllegalArgumentException(
                "Reference type '${ctx.text}' has an unsupported inner type"
            )
        }

        return RefTypeNode(
            mutable = ctx.MUT() != null,
            inner = innerType,
            span = spanOf(ctx),
        )
    }

    override fun visitBlockExpression(
        ctx: RxParser.BlockExpressionContext
    ): BlockExprNode {
        val statements = mutableListOf<StmtNode>()

        for (statementContext in ctx.statement()) {
            // 空语句 `;` 返回 null：纯语法，按设计丢弃
            val statementNode = visit(statementContext) as? StmtNode

            if (statementNode != null) {
                statements.add(statementNode)
            }
        }

        val statementExpressionContext = ctx.statementExpression()
        val tail = if (statementExpressionContext == null) {
            null
        } else {
            visit(statementExpressionContext) as? ExprNode
                ?: throw IllegalArgumentException(
                    "Unsupported block tail '${statementExpressionContext.text}'"
                )
        }

        return BlockExprNode(
            statements = statements,
            tail = tail,
            span = spanOf(ctx),
        )
    }

    // statement : SEMI | letStatement | expressionWithBlock SEMI? | statementExpression SEMI
    // 返回 null 表示只有分号的空语句，由 block 负责丢弃。
    override fun visitStatement(
        ctx: RxParser.StatementContext
    ): StmtNode? {
        if (ctx.letStatement() != null) {
            return visit(ctx.letStatement()) as? StmtNode
                ?: throw IllegalArgumentException(
                    "Unsupported let statement '${ctx.text}'"
                )
        }

        if (ctx.expressionWithBlock() != null) {
            val expression = visit(ctx.expressionWithBlock()) as? ExprNode
                ?: throw IllegalArgumentException(
                    "Unsupported statement '${ctx.text}'"
                )

            return ExprStmtNode(expression, spanOf(ctx))
        }

        if (ctx.statementExpression() != null) {
            val expression = visit(ctx.statementExpression()) as? ExprNode
                ?: throw IllegalArgumentException(
                    "Unsupported statement expression '${ctx.text}'"
                )

            return ExprStmtNode(expression, spanOf(ctx))
        }

        // 到此只剩 `;`
        return null
    }

    // expressionWithBlock : blockExpression | ifExpression | LOOP blockExpression
    //                     | WHILE conditionExpression blockExpression
    // LOOP/WHILE 的备选里也含 blockExpression，必须先排除再落到纯块分支。
    override fun visitExpressionWithBlock(
        ctx: RxParser.ExpressionWithBlockContext
    ): ExprNode {
        if (ctx.LOOP() != null) {
            throw IllegalArgumentException(
                "Loop expressions are not supported yet"
            )
        }

        if (ctx.WHILE() != null) {
            throw IllegalArgumentException(
                "While expressions are not supported yet"
            )
        }

        if (ctx.ifExpression() != null) {
            throw IllegalArgumentException(
                "If expressions are not supported yet"
            )
        }

        val blockContext = ctx.blockExpression()
            ?: throw IllegalArgumentException(
                "Unsupported expression-with-block '${ctx.text}'"
            )

        return visit(blockContext) as? BlockExprNode
            ?: throw IllegalArgumentException(
                "Unsupported block expression '${blockContext.text}'"
            )
    }

    override fun visitLetStatement(
        ctx: RxParser.LetStatementContext
    ): LetNode {
        val binding = ctx.identifierBinding()
        val name = binding.identifier().text

        val declaredType = if (ctx.typeRef() == null) {
            null
        } else {
            visit(ctx.typeRef()) as? TypeRefNode
                ?: throw IllegalArgumentException(
                    "Let '$name' has an unsupported type annotation"
                )
        }

        val initializer = visit(ctx.expression()) as? ExprNode
            ?: throw IllegalArgumentException(
                "Let '$name' has an unsupported initializer"
            )

        return LetNode(
            name = name,
            mutable = binding.MUT() != null,
            declaredType = declaredType,
            initializer = initializer,
            span = spanOf(ctx),
        )
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
