package rx.ast

import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.RuleNode
import org.antlr.v4.runtime.tree.TerminalNode
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
            return buildLoopExpression(ctx.blockExpression(), spanOf(ctx))
        }

        if (ctx.WHILE() != null) {
            return buildWhileExpression(
                ctx.conditionExpression(),
                ctx.blockExpression(),
                spanOf(ctx),
            )
        }

        val ifContext = ctx.ifExpression()
        if (ifContext != null) {
            return buildIfExpression(ifContext)
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

    // ===== 块状表达式（if / loop / while） =====

    /**
     * ifExpression : IF conditionExpression blockExpression
     *                (ELSE (blockExpression | ifExpression))?。
     * blockExpression 在规则里出现两次，生成的是列表访问器：
     * blockExpression(0) 是 then 块；else 块才是 blockExpression(1)。
     */
    private fun buildIfExpression(
        ctx: RxParser.IfExpressionContext
    ): IfExprNode {
        val condition = visit(ctx.conditionExpression()) as? ExprNode
            ?: throw IllegalArgumentException(
                "If expression '${ctx.text}' has an unsupported condition"
            )

        val thenBlock = visit(ctx.blockExpression(0)) as? BlockExprNode
            ?: throw IllegalArgumentException(
                "If expression '${ctx.text}' has an unsupported then block"
            )

        val innerIfContext = ctx.ifExpression()
        val elseBranch: ExprNode? = when {
            ctx.ELSE() == null -> null
            // else if 链：递归展开，每一层用自己的 ifExpression 上下文
            innerIfContext != null -> buildIfExpression(innerIfContext)
            else -> visit(ctx.blockExpression(1)) as? BlockExprNode
                ?: throw IllegalArgumentException(
                    "If expression '${ctx.text}' has an unsupported else block"
                )
        }

        return IfExprNode(
            condition = condition,
            thenblock = thenBlock,
            elseBranch = elseBranch,
            span = spanOf(ctx),
        )
    }

    /** LOOP blockExpression（语句与条件两处入口共用）。 */
    private fun buildLoopExpression(
        blockContext: RxParser.BlockExpressionContext?,
        span: Span,
    ): LoopExprNode {
        val block = blockContext?.let { visit(it) as? BlockExprNode }
            ?: throw IllegalArgumentException(
                "Loop expression has an unsupported body"
            )

        return LoopExprNode(
            block = block,
            span = span,
        )
    }

    /** WHILE conditionExpression blockExpression（同上，两处入口共用）。 */
    private fun buildWhileExpression(
        conditionContext: RxParser.ConditionExpressionContext?,
        blockContext: RxParser.BlockExpressionContext?,
        span: Span,
    ): WhileExprNode {
        val condition = conditionContext?.let { visit(it) as? ExprNode }
            ?: throw IllegalArgumentException(
                "While expression has an unsupported condition"
            )

        val block = blockContext?.let { visit(it) as? BlockExprNode }
            ?: throw IllegalArgumentException(
                "While expression has an unsupported body"
            )

        return WhileExprNode(
            block = block,
            condition = condition,
            span = span,
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
        "assignmentExpression" to this::buildAssignmentExpression,
        "logicalOrExpression" to this::buildBinaryExpression,
        "logicalAndExpression" to this::buildBinaryExpression,
        "comparisonExpression" to this::buildBinaryExpression,
        "bitOrExpression" to this::buildBinaryExpression,
        "bitXorExpression" to this::buildBinaryExpression,
        "bitAndExpression" to this::buildBinaryExpression,
        "shiftExpression" to this::buildBinaryExpression,
        "additiveExpression" to this::buildBinaryExpression,
        "multiplicativeExpression" to this::buildBinaryExpression,
        "castExpression" to this::buildCastExpression,
        "unaryExpression" to this::buildUnaryExpression,
        "postfixExpression" to this::buildPostfixExpression,
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

        val ifContext = ctx.ifExpression()
        if (ifContext != null) {
            return buildIfExpression(ifContext)
        }

        if (ctx.LOOP() != null) {
            return buildLoopExpression(ctx.blockExpression(), spanOf(ctx))
        }

        if (ctx.WHILE() != null) {
            return buildWhileExpression(
                ctx.conditionExpression(),
                ctx.blockExpression(),
                spanOf(ctx),
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

    // ===== postfix 层（4c）：调用 / 下标 / 字段 / 方法 =====

    /**
     * postfixExpression : 基底 postfixSuffix*（四家族变体已归一化）。
     * 第一个孩子是基底（primary 或块），其余孩子从左到右包上去。
     */
    private fun buildPostfixExpression(ctx: ParserRuleContext): AstNode {
        val base = ctx.getChild(0)

        if (base !is ParserRuleContext) {
            throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )
        }

        var accumulator = visit(base) as? ExprNode
            ?: throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )

        for (index in 1 until ctx.childCount) {
            /* 每次取一个后缀出来，进行出来，放到accumulator累加 */
            when (val suffix = ctx.getChild(index)) {
                is RxParser.PostfixSuffixContext ->
                    accumulator = buildPostfixSuffix(accumulator, suffix)

                is RxParser.DotSuffixContext ->
                    accumulator = buildDotSuffix(accumulator, suffix)

                else -> throw IllegalArgumentException(
                    "Unsupported expression '${ctx.text}'"
                )
            }
        }

        return accumulator
    }

    private fun buildPostfixSuffix(
        receiver: ExprNode,
        ctx: RxParser.PostfixSuffixContext
    ): ExprNode {
        if (ctx.callArguments() != null) {
            return CallExprNode(
                callee = receiver,
                args = buildCallArguments(ctx.callArguments()),
                span = spanFrom(receiver.span, ctx),
            )
        }

        if (ctx.LBRACKET() != null) {
            val index = visit(ctx.expression()) as? ExprNode
                ?: throw IllegalArgumentException(
                    "Unsupported index expression '${ctx.text}'"
                )

            return IndexExprNode(
                receiver = receiver,
                index = index,
                span = spanFrom(receiver.span, ctx),
            )
        }

        val dotSuffix = ctx.dotSuffix()
            ?: throw IllegalArgumentException(
                "Unsupported postfix '${ctx.text}'"
            )

        return buildDotSuffix(receiver, dotSuffix)
    }

    /** dotSuffix : DOT pathExprSegment callArguments | DOT identifier */
    private fun buildDotSuffix(
        receiver: ExprNode,
        ctx: RxParser.DotSuffixContext
    ): ExprNode {
        val segment = ctx.pathExprSegment()
        val span = spanFrom(receiver.span, ctx)

        if (segment != null) {
            // 方法调用：DOT pathExprSegment callArguments
            if (segment.genericArgs() != null) {
                throw IllegalArgumentException(
                    "Generic method arguments are not supported yet"
                )
            }

            val callArguments = ctx.callArguments()
                ?: throw IllegalArgumentException(
                    "Unsupported method call '${ctx.text}'"
                )

            return MethodCallExprNode(
                receiver = receiver,
                name = segment.pathIdentSegment().text,
                args = buildCallArguments(callArguments),
                span = span,
            )
        }

        val identifier = ctx.identifier()
            ?: throw IllegalArgumentException(
                "Unsupported field access '${ctx.text}'"
            )

        return FieldExprNode(
            receiver = receiver,
            name = identifier.text,
            span = span,
        )
    }

    private fun buildCallArguments(
        ctx: RxParser.CallArgumentsContext
    ): List<ExprNode> {
        val arguments = mutableListOf<ExprNode>()

        for (expressionContext in ctx.expression()) {
            val argument = visit(expressionContext) as? ExprNode
                ?: throw IllegalArgumentException(
                    "Unsupported argument '${expressionContext.text}'"
                )

            arguments.add(argument)
        }

        return arguments
    }

    // ===== unary + cast（4d） =====

    private fun buildUnaryExpression(ctx: ParserRuleContext): AstNode {
        if (ctx.childCount == 1) {
            return buildPassThroughExpression(ctx)
        }

        val operatorContext = ctx.getChild(0)
        val operandContext = ctx.getChild(1)

        if (operatorContext !is RxParser.UnaryOperatorContext ||
            operandContext !is ParserRuleContext
        ) {
            throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )
        }

        val operand = visit(operandContext) as? ExprNode
            ?: throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )

        return buildUnaryOperatorExpression(operatorContext, operand, spanOf(ctx))
    }

    private fun buildUnaryOperatorExpression(
        ctx: RxParser.UnaryOperatorContext,
        operand: ExprNode,
        span: Span,
    ): ExprNode {
        if (ctx.MINUS() != null) {
            return UnaryExprNode(UnOp.NEG, operand, span)
        }

        if (ctx.NOT() != null) {
            return UnaryExprNode(UnOp.NOT, operand, span)
        }

        if (ctx.STAR() != null) {
            return UnaryExprNode(UnOp.DEREF, operand, span)
        }

        if (ctx.ANDAND() != null) {
            // &&x 拆两层：外层不可变引用，MUT 旗子属于内层
            val inner = ReferenceExprNode(
                mutable = ctx.MUT() != null,
                operand = operand,
                span = span,
            )
            return ReferenceExprNode(mutable = false, operand = inner, span = span)
        }

        if (ctx.AMP() != null) {
            return ReferenceExprNode(
                mutable = ctx.MUT() != null,
                operand = operand,
                span = span,
            )
        }

        throw IllegalArgumentException(
            "Unsupported unary operator '${ctx.text}'"
        )
    }

    /**
     * castExpression : unaryExpression (AS typeRef)* | castExpression AS closedCastType。
     * 结构式折叠：第一个子规则是操作数，之后每个子规则都是一次 as 的目标类型。
     */
    private fun buildCastExpression(ctx: ParserRuleContext): AstNode {
        val parts = ctx.children.filterIsInstance<ParserRuleContext>()

        val first = parts.firstOrNull()
            ?: throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )

        var accumulator = visit(first) as? ExprNode
            ?: throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )

        for (index in 1 until parts.size) {
            val targetType = buildCastTarget(parts[index])
            accumulator = CastExprNode(
                expr = accumulator,
                targetType = targetType,
                span = spanFrom(accumulator.span, parts[index]),
            )
        }

        return accumulator
    }

    private fun buildCastTarget(ctx: ParserRuleContext): TypeRefNode {
        if (ctx is RxParser.TypeRefContext) {
            return visit(ctx) as? TypeRefNode
                ?: throw IllegalArgumentException(
                    "Unsupported cast target '${ctx.text}'"
                )
        }

        if (ctx is RxParser.ClosedCastTypeContext) {
            return buildClosedCastType(ctx)
        }

        throw IllegalArgumentException(
            "Unsupported cast target '${ctx.text}'"
        )
    }

    /**
     * closedCastType 只出现在 closed 链里的 as 目标（`b as Vec<i32> < c` 场景）：
     * (T) / () / &closedCastType / [T; N] / 带泛型实参的段（暂不支持）。
     */
    private fun buildClosedCastType(
        ctx: RxParser.ClosedCastTypeContext
    ): TypeRefNode {
        if (ctx.typeRef() != null) {
            return visit(ctx.typeRef()) as? TypeRefNode
                ?: throw IllegalArgumentException(
                    "Unsupported cast target '${ctx.text}'"
                )
        }

        if (ctx.LPAREN() != null) {
            return UnitTypeNode(spanOf(ctx))
        }

        if (ctx.arrayType() != null) {
            throw IllegalArgumentException(
                "Array types are not supported yet"
            )
        }

        if (ctx.AMP() != null || ctx.ANDAND() != null) {
            val innerContext = ctx.closedCastType()
                ?: throw IllegalArgumentException(
                    "Unsupported cast target '${ctx.text}'"
                )

            return RefTypeNode(
                mutable = ctx.MUT() != null,
                inner = buildClosedCastType(innerContext),
                span = spanOf(ctx),
            )
        }

        if (ctx.genericArgs() != null) {
            throw IllegalArgumentException(
                "Generic type arguments are not supported yet"
            )
        }

        throw IllegalArgumentException(
            "Unsupported cast target '${ctx.text}'"
        )
    }

    // ===== 二元梯子 + 赋值（4e） =====

    /** 运算符文本 → BinOp；注意 >> 等由多个 token 拼成，按整段文本映射。 */
    private val binaryOperators: Map<String, BinOp> = mapOf(
        "+" to BinOp.ADD,
        "-" to BinOp.SUB,
        "*" to BinOp.MUL,
        "/" to BinOp.DIV,
        "%" to BinOp.MOD,
        "<<" to BinOp.SHL,
        ">>" to BinOp.SHR,
        "&" to BinOp.BIT_AND,
        "^" to BinOp.BIT_XOR,
        "|" to BinOp.BIT_OR,
        "==" to BinOp.EQ,
        "!=" to BinOp.NE,
        "<" to BinOp.LT,
        "<=" to BinOp.LE,
        ">" to BinOp.GT,
        ">=" to BinOp.GE,
        "&&" to BinOp.AND,
        "||" to BinOp.OR,
    )

    private val assignmentOperators: Map<String, AssignOp> = mapOf(
        "=" to AssignOp.ASSIGN,
        "+=" to AssignOp.ADD_ASSIGN,
        "-=" to AssignOp.SUB_ASSIGN,
        "*=" to AssignOp.MUL_ASSIGN,
        "/=" to AssignOp.DIV_ASSIGN,
        "%=" to AssignOp.MOD_ASSIGN,
        "&=" to AssignOp.BIT_AND_ASSIGN,
        "|=" to AssignOp.BIT_OR_ASSIGN,
        "^=" to AssignOp.BIT_XOR_ASSIGN,
        "<<=" to AssignOp.SHL_ASSIGN,
        ">>=" to AssignOp.SHR_ASSIGN,
    )

    /** 各家族共用的运算符辅助规则名（用于区分"运算符孩子"和"操作数孩子"）。 */
    private val operatorHelperRules = setOf(
        "additiveOperator",
        "multiplicativeOperator",
        "shiftRight",
        "comparisonExceptLt",
        "assignmentOperator",
        "equalsSign",
    )

    /**
     * 二元层通用折叠：按子节点顺序扫描——运算符待用、操作数结合（左结合）。
     * 对开放/closed 两种顺序（(op 操作数)* vs (操作数 op)*）都成立。
     */
    private fun buildBinaryExpression(ctx: ParserRuleContext): AstNode {
        var accumulator: ExprNode? = null
        var pendingOperator: BinOp? = null

        for (index in 0 until ctx.childCount) {
            val child = ctx.getChild(index)

            if (isOperatorChild(child)) {
                if (pendingOperator != null) {
                    throw IllegalArgumentException(
                        "Unsupported expression '${ctx.text}'"
                    )
                }

                pendingOperator = binaryOperators[child.text]
                    ?: throw IllegalArgumentException(
                        "Unsupported operator '${child.text}'"
                    )
                continue
            }

            if (child !is ParserRuleContext) {
                throw IllegalArgumentException(
                    "Unsupported expression '${ctx.text}'"
                )
            }

            val operand = visit(child) as? ExprNode
                ?: throw IllegalArgumentException(
                    "Unsupported expression '${ctx.text}'"
                )

            val left = accumulator

            if (left == null) {
                accumulator = operand
            } else {
                val operator = pendingOperator
                    ?: throw IllegalArgumentException(
                        "Unsupported expression '${ctx.text}'"
                    )

                accumulator = BinaryExprNode(
                    op = operator,
                    left = left,
                    right = operand,
                    span = spanOf(left, operand),
                )
                pendingOperator = null
            }
        }

        return accumulator ?: throw IllegalArgumentException(
            "Unsupported expression '${ctx.text}'"
        )
    }

    private fun isOperatorChild(child: ParseTree): Boolean {
        if (child is TerminalNode) {
            // 二元层里直接出现的终结符只有运算符（&& || << < 等）
            return true
        }

        if (child is ParserRuleContext) {
            return RxParser.ruleNames[child.ruleIndex] in operatorHelperRules
        }

        return false
    }

    /**
     * assignmentExpression : logicalOrExpression (assignmentOperator expression)?
     * 右结合由文法递归保证（右侧走完整 expression），这里只折叠一次。
     */
    private fun buildAssignmentExpression(ctx: ParserRuleContext): AstNode {
        if (ctx.childCount == 1) {
            return buildPassThroughExpression(ctx)
        }

        if (ctx.childCount != 3) {
            throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )
        }

        val targetContext = ctx.getChild(0)
        val operatorContext = ctx.getChild(1)
        val valueContext = ctx.getChild(2)

        if (targetContext !is ParserRuleContext ||
            operatorContext !is RxParser.AssignmentOperatorContext ||
            valueContext !is ParserRuleContext
        ) {
            throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )
        }

        val target = visit(targetContext) as? ExprNode
            ?: throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )

        val value = visit(valueContext) as? ExprNode
            ?: throw IllegalArgumentException(
                "Unsupported expression '${ctx.text}'"
            )

        val operator = assignmentOperators[operatorContext.text]
            ?: throw IllegalArgumentException(
                "Unsupported assignment operator '${operatorContext.text}'"
            )

        return AssignExprNode(
            op = operator,
            target = target,
            value = value,
            span = spanOf(target, value),
        )
    }

    // ===== span 组合工具 =====

    /** 从已有节点的起点到 CST 节点的终点（后缀/包一层的情形）。 */
    private fun spanFrom(start: Span, ctx: ParserRuleContext): Span {
        val stop = ctx.stop ?: ctx.start

        return Span(
            startLine = start.startLine,
            startColumn = start.startColumn,
            endLine = stop.line,
            endColumn = stop.charPositionInLine + (stop.text?.length ?: 0),
        )
    }

    /** 两个 AST 节点拼起来的范围（二元/赋值折叠）。 */
    private fun spanOf(start: AstNode, stop: AstNode): Span {
        return Span(
            startLine = start.span.startLine,
            startColumn = start.span.startColumn,
            endLine = stop.span.endLine,
            endColumn = stop.span.endColumn,
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
