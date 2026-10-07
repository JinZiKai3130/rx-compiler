package rx.ast

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.RuleNode
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

    // ===== 表达式分派（4a）：四家族归一化 + 中心分派 =====

    /**
     * 表达式梯子每层一个 handler，key 是归一化后的 canonical 名。
     * 直通处理器只覆盖"无运算符/无后缀"的形状，运算符折叠在后续批次替换。
     */
    private val expressionHandlers: Map<String, (ParserRuleContext) -> AstNode> = mapOf(
        "expression" to this::buildPassThroughExpression,
        "assignmentExpression" to this::buildPassThroughExpression,
        "logicalOrExpression" to this::buildPassThroughExpression,
        "logicalAndExpression" to this::buildPassThroughExpression,
        "comparisonExpression" to this::buildPassThroughExpression,
        "bitOrExpression" to this::buildPassThroughExpression,
        "bitXorExpression" to this::buildPassThroughExpression,
        "bitAndExpression" to this::buildPassThroughExpression,
        "shiftExpression" to this::buildPassThroughExpression,
        "additiveExpression" to this::buildPassThroughExpression,
        "multiplicativeExpression" to this::buildPassThroughExpression,
        "castExpression" to this::buildPassThroughExpression,
        "unaryExpression" to this::buildPassThroughExpression,
        "postfixExpression" to this::buildPassThroughExpression,
        "primary" to this::buildPassThroughExpression,           // conditionPrimary
        "primaryExpression" to this::buildPassThroughExpression, // primaryExpression
        "nonBlockPrimary" to { ctx ->
            buildNonBlockPrimary(ctx as RxParser.NonBlockPrimaryContext)
        },
        "primaryWithoutBareBlock" to { ctx ->
            buildConditionPrimaryWithoutBareBlock(
                ctx as RxParser.ConditionPrimaryWithoutBareBlockContext
            )
        },
    )

    /**
     * 4a 中心分派：没有显式 override 的规则都会流到这里。
     * 按 canonical 名查表命中 → 走表达式 handler；否则保持默认行为（null）。
     */
    override fun visitChildren(node: RuleNode): AstNode? {
        if (node is ParserRuleContext) {
            val ruleName = RxParser.ruleNames[node.ruleIndex]
            val handler = expressionHandlers[canonicalRuleName(ruleName)]

            if (handler != null) {
                return handler(node)
            }
        }

        return super.visitChildren(node)
    }

    /**
     * 家族归一化：剥掉 conditionBreak / condition / statement 前缀，
     * 再剥 closed（大小写不敏感）。closed 是 as-cast 的前瞻消歧孪生，
     * 家族前缀只约束起点——语义都与本体相同。
     * 例：statementClosedAdditiveExpression -> additiveExpression
     */
    private fun canonicalRuleName(ruleName: String): String {
        var name = ruleName

        name = when {
            name.startsWith("conditionBreak") -> name.removePrefix("conditionBreak")
            name.startsWith("condition") -> name.removePrefix("condition")
            name.startsWith("statement") -> name.removePrefix("statement")
            else -> name
        }

        if (name.startsWith("closed", ignoreCase = true)) {
            name = name.substring("closed".length)
        }

        if (name.isEmpty()) {
            return ruleName
        }

        return name.replaceFirstChar { it.lowercaseChar() }
    }

    /**
     * 直通：整条梯子每一层都要逐级下到 primary，所以"无运算符"形状
     * （恰好一个子规则）先按原样递归；出现运算符/后缀/cast 时暂时抛错。
     */
    private fun buildPassThroughExpression(ctx: ParserRuleContext): AstNode {
        val onlyChild = if (ctx.childCount == 1) ctx.getChild(0) else null

        if (onlyChild !is ParserRuleContext) {
            throw IllegalArgumentException(
                "Expression '${ctx.text}' is not supported yet"
            )
        }

        return visit(onlyChild) ?: throw IllegalArgumentException(
            "Unsupported expression '${onlyChild.text}'"
        )
    }

    // ===== primary 层（4b） =====

    private fun buildNonBlockPrimary(
        ctx: RxParser.NonBlockPrimaryContext
    ): ExprNode {
        if (ctx.literalExpression() != null) {
            return buildLiteralExpression(ctx.literalExpression())
        }

        if (ctx.pathInExpression() != null) {
            // pathInExpression (LBRACE ...)? —— 带花括号的是结构体字面量
            if (ctx.LBRACE() != null) {
                throw IllegalArgumentException(
                    "Struct literals are not supported yet"
                )
            }
            return buildPathExpression(ctx.pathInExpression())
        }

        if (ctx.LPAREN() != null) {
            return buildParenthesizedExpression(ctx.expression(), ctx)
        }

        if (ctx.arrayExpression() != null) {
            throw IllegalArgumentException(
                "Array literals are not supported yet"
            )
        }

        if (ctx.BREAK() != null) {
            throw IllegalArgumentException(
                "Break expressions are not supported yet"
            )
        }

        if (ctx.RETURN() != null) {
            throw IllegalArgumentException(
                "Return expressions are not supported yet"
            )
        }

        if (ctx.CONTINUE() != null) {
            throw IllegalArgumentException(
                "Continue expressions are not supported yet"
            )
        }

        throw IllegalArgumentException(
            "Unsupported primary expression '${ctx.text}'"
        )
    }

    private fun buildConditionPrimaryWithoutBareBlock(
        ctx: RxParser.ConditionPrimaryWithoutBareBlockContext
    ): ExprNode {
        if (ctx.literalExpression() != null) {
            return buildLiteralExpression(ctx.literalExpression())
        }

        if (ctx.pathInExpression() != null) {
            return buildPathExpression(ctx.pathInExpression())
        }

        if (ctx.LPAREN() != null) {
            return buildParenthesizedExpression(ctx.expression(), ctx)
        }

        if (ctx.ifExpression() != null) {
            throw IllegalArgumentException(
                "If expressions are not supported yet"
            )
        }

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

        if (ctx.arrayExpression() != null) {
            throw IllegalArgumentException(
                "Array literals are not supported yet"
            )
        }

        if (ctx.BREAK() != null) {
            throw IllegalArgumentException(
                "Break expressions are not supported yet"
            )
        }

        if (ctx.RETURN() != null) {
            throw IllegalArgumentException(
                "Return expressions are not supported yet"
            )
        }

        if (ctx.CONTINUE() != null) {
            throw IllegalArgumentException(
                "Continue expressions are not supported yet"
            )
        }

        throw IllegalArgumentException(
            "Unsupported primary expression '${ctx.text}'"
        )
    }

    /** 括号表达式：(e) 拆括号返回内层；() 是单元字面量。 */
    private fun buildParenthesizedExpression(
        expressionContext: RxParser.ExpressionContext?,
        ctx: ParserRuleContext,
    ): ExprNode {
        if (expressionContext == null) {
            return UnitLitNode(spanOf(ctx))
        }

        return visit(expressionContext) as? ExprNode
            ?: throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )
    }

    private fun buildLiteralExpression(
        ctx: RxParser.LiteralExpressionContext
    ): ExprNode {
        if (ctx.INTEGER_LITERAL() != null) {
            return IntLitNode(
                value = parseIntegerLiteral(ctx.INTEGER_LITERAL().text),
                span = spanOf(ctx),
            )
        }

        if (ctx.TRUE() != null) {
            return BoolLitNode(value = true, span = spanOf(ctx))
        }

        if (ctx.FALSE() != null) {
            return BoolLitNode(value = false, span = spanOf(ctx))
        }

        throw IllegalArgumentException(
            "Unsupported literal '${ctx.text}'"
        )
    }

    /** 十进制/0b/0o/0x（允许下划线分隔），可选后缀 i32/u32/isize/usize；先按 Long 存值。 */
    private fun parseIntegerLiteral(text: String): Long {
        var body = text

        for (suffix in listOf("isize", "usize", "i32", "u32")) {
            if (body.endsWith(suffix)) {
                body = body.dropLast(suffix.length)
                break
            }
        }

        body = body.replace("_", "")

        val digits: String
        val radix: Int

        when {
            body.startsWith("0b") -> {
                digits = body.substring(2)
                radix = 2
            }
            body.startsWith("0o") -> {
                digits = body.substring(2)
                radix = 8
            }
            body.startsWith("0x") -> {
                digits = body.substring(2)
                radix = 16
            }
            else -> {
                digits = body
                radix = 10
            }
        }

        return digits.toLongOrNull(radix)
            ?: throw IllegalArgumentException(
                "Integer literal '$text' is out of range"
            )
    }

    private fun buildPathExpression(
        ctx: RxParser.PathInExpressionContext
    ): PathExprNode {
        val segments = mutableListOf<PathSegment>()

        for (segmentContext in ctx.pathExprSegment()) {
            if (segmentContext.genericArgs() != null) {
                throw IllegalArgumentException(
                    "Generic path arguments are not supported yet"
                )
            }

            val segmentName = segmentContext.pathIdentSegment().text
            segments.add(PathSegment(name = segmentName))
        }

        return PathExprNode(
            segments = segments,
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
