// Generated from RxParser.g4 by ANTLR 4.13.2
package rx.gen;
import org.antlr.v4.runtime.tree.ParseTreeListener;

/**
 * This interface defines a complete listener for a parse tree produced by
 * {@link RxParser}.
 */
public interface RxParserListener extends ParseTreeListener {
	/**
	 * Enter a parse tree produced by {@link RxParser#crate}.
	 * @param ctx the parse tree
	 */
	void enterCrate(RxParser.CrateContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#crate}.
	 * @param ctx the parse tree
	 */
	void exitCrate(RxParser.CrateContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#item}.
	 * @param ctx the parse tree
	 */
	void enterItem(RxParser.ItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#item}.
	 * @param ctx the parse tree
	 */
	void exitItem(RxParser.ItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#useDeclaration}.
	 * @param ctx the parse tree
	 */
	void enterUseDeclaration(RxParser.UseDeclarationContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#useDeclaration}.
	 * @param ctx the parse tree
	 */
	void exitUseDeclaration(RxParser.UseDeclarationContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#useTree}.
	 * @param ctx the parse tree
	 */
	void enterUseTree(RxParser.UseTreeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#useTree}.
	 * @param ctx the parse tree
	 */
	void exitUseTree(RxParser.UseTreeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#usePath}.
	 * @param ctx the parse tree
	 */
	void enterUsePath(RxParser.UsePathContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#usePath}.
	 * @param ctx the parse tree
	 */
	void exitUsePath(RxParser.UsePathContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#usePathSegment}.
	 * @param ctx the parse tree
	 */
	void enterUsePathSegment(RxParser.UsePathSegmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#usePathSegment}.
	 * @param ctx the parse tree
	 */
	void exitUsePathSegment(RxParser.UsePathSegmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#functionDefinition}.
	 * @param ctx the parse tree
	 */
	void enterFunctionDefinition(RxParser.FunctionDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#functionDefinition}.
	 * @param ctx the parse tree
	 */
	void exitFunctionDefinition(RxParser.FunctionDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#functionParameters}.
	 * @param ctx the parse tree
	 */
	void enterFunctionParameters(RxParser.FunctionParametersContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#functionParameters}.
	 * @param ctx the parse tree
	 */
	void exitFunctionParameters(RxParser.FunctionParametersContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#selfParam}.
	 * @param ctx the parse tree
	 */
	void enterSelfParam(RxParser.SelfParamContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#selfParam}.
	 * @param ctx the parse tree
	 */
	void exitSelfParam(RxParser.SelfParamContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#functionParam}.
	 * @param ctx the parse tree
	 */
	void enterFunctionParam(RxParser.FunctionParamContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#functionParam}.
	 * @param ctx the parse tree
	 */
	void exitFunctionParam(RxParser.FunctionParamContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#structDefinition}.
	 * @param ctx the parse tree
	 */
	void enterStructDefinition(RxParser.StructDefinitionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#structDefinition}.
	 * @param ctx the parse tree
	 */
	void exitStructDefinition(RxParser.StructDefinitionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#structField}.
	 * @param ctx the parse tree
	 */
	void enterStructField(RxParser.StructFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#structField}.
	 * @param ctx the parse tree
	 */
	void exitStructField(RxParser.StructFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#outerAttribute}.
	 * @param ctx the parse tree
	 */
	void enterOuterAttribute(RxParser.OuterAttributeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#outerAttribute}.
	 * @param ctx the parse tree
	 */
	void exitOuterAttribute(RxParser.OuterAttributeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#deriveName}.
	 * @param ctx the parse tree
	 */
	void enterDeriveName(RxParser.DeriveNameContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#deriveName}.
	 * @param ctx the parse tree
	 */
	void exitDeriveName(RxParser.DeriveNameContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#constantItem}.
	 * @param ctx the parse tree
	 */
	void enterConstantItem(RxParser.ConstantItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#constantItem}.
	 * @param ctx the parse tree
	 */
	void exitConstantItem(RxParser.ConstantItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#inherentImpl}.
	 * @param ctx the parse tree
	 */
	void enterInherentImpl(RxParser.InherentImplContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#inherentImpl}.
	 * @param ctx the parse tree
	 */
	void exitInherentImpl(RxParser.InherentImplContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#associatedItem}.
	 * @param ctx the parse tree
	 */
	void enterAssociatedItem(RxParser.AssociatedItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#associatedItem}.
	 * @param ctx the parse tree
	 */
	void exitAssociatedItem(RxParser.AssociatedItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#genericParams}.
	 * @param ctx the parse tree
	 */
	void enterGenericParams(RxParser.GenericParamsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#genericParams}.
	 * @param ctx the parse tree
	 */
	void exitGenericParams(RxParser.GenericParamsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#lifetimeParam}.
	 * @param ctx the parse tree
	 */
	void enterLifetimeParam(RxParser.LifetimeParamContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#lifetimeParam}.
	 * @param ctx the parse tree
	 */
	void exitLifetimeParam(RxParser.LifetimeParamContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#lifetime}.
	 * @param ctx the parse tree
	 */
	void enterLifetime(RxParser.LifetimeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#lifetime}.
	 * @param ctx the parse tree
	 */
	void exitLifetime(RxParser.LifetimeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#lifetimeBounds}.
	 * @param ctx the parse tree
	 */
	void enterLifetimeBounds(RxParser.LifetimeBoundsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#lifetimeBounds}.
	 * @param ctx the parse tree
	 */
	void exitLifetimeBounds(RxParser.LifetimeBoundsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#typeParamBounds}.
	 * @param ctx the parse tree
	 */
	void enterTypeParamBounds(RxParser.TypeParamBoundsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#typeParamBounds}.
	 * @param ctx the parse tree
	 */
	void exitTypeParamBounds(RxParser.TypeParamBoundsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#whereClause}.
	 * @param ctx the parse tree
	 */
	void enterWhereClause(RxParser.WhereClauseContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#whereClause}.
	 * @param ctx the parse tree
	 */
	void exitWhereClause(RxParser.WhereClauseContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#whereClauseItem}.
	 * @param ctx the parse tree
	 */
	void enterWhereClauseItem(RxParser.WhereClauseItemContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#whereClauseItem}.
	 * @param ctx the parse tree
	 */
	void exitWhereClauseItem(RxParser.WhereClauseItemContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#typeRef}.
	 * @param ctx the parse tree
	 */
	void enterTypeRef(RxParser.TypeRefContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#typeRef}.
	 * @param ctx the parse tree
	 */
	void exitTypeRef(RxParser.TypeRefContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#referenceType}.
	 * @param ctx the parse tree
	 */
	void enterReferenceType(RxParser.ReferenceTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#referenceType}.
	 * @param ctx the parse tree
	 */
	void exitReferenceType(RxParser.ReferenceTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#arrayType}.
	 * @param ctx the parse tree
	 */
	void enterArrayType(RxParser.ArrayTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#arrayType}.
	 * @param ctx the parse tree
	 */
	void exitArrayType(RxParser.ArrayTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#typePath}.
	 * @param ctx the parse tree
	 */
	void enterTypePath(RxParser.TypePathContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#typePath}.
	 * @param ctx the parse tree
	 */
	void exitTypePath(RxParser.TypePathContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#typePathSegment}.
	 * @param ctx the parse tree
	 */
	void enterTypePathSegment(RxParser.TypePathSegmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#typePathSegment}.
	 * @param ctx the parse tree
	 */
	void exitTypePathSegment(RxParser.TypePathSegmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#pathInExpression}.
	 * @param ctx the parse tree
	 */
	void enterPathInExpression(RxParser.PathInExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#pathInExpression}.
	 * @param ctx the parse tree
	 */
	void exitPathInExpression(RxParser.PathInExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#pathExprSegment}.
	 * @param ctx the parse tree
	 */
	void enterPathExprSegment(RxParser.PathExprSegmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#pathExprSegment}.
	 * @param ctx the parse tree
	 */
	void exitPathExprSegment(RxParser.PathExprSegmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#pathIdentSegment}.
	 * @param ctx the parse tree
	 */
	void enterPathIdentSegment(RxParser.PathIdentSegmentContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#pathIdentSegment}.
	 * @param ctx the parse tree
	 */
	void exitPathIdentSegment(RxParser.PathIdentSegmentContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#genericArgs}.
	 * @param ctx the parse tree
	 */
	void enterGenericArgs(RxParser.GenericArgsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#genericArgs}.
	 * @param ctx the parse tree
	 */
	void exitGenericArgs(RxParser.GenericArgsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#genericArg}.
	 * @param ctx the parse tree
	 */
	void enterGenericArg(RxParser.GenericArgContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#genericArg}.
	 * @param ctx the parse tree
	 */
	void exitGenericArg(RxParser.GenericArgContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#genericClose}.
	 * @param ctx the parse tree
	 */
	void enterGenericClose(RxParser.GenericCloseContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#genericClose}.
	 * @param ctx the parse tree
	 */
	void exitGenericClose(RxParser.GenericCloseContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#closedCastType}.
	 * @param ctx the parse tree
	 */
	void enterClosedCastType(RxParser.ClosedCastTypeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#closedCastType}.
	 * @param ctx the parse tree
	 */
	void exitClosedCastType(RxParser.ClosedCastTypeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#constValue}.
	 * @param ctx the parse tree
	 */
	void enterConstValue(RxParser.ConstValueContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#constValue}.
	 * @param ctx the parse tree
	 */
	void exitConstValue(RxParser.ConstValueContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#magnitude}.
	 * @param ctx the parse tree
	 */
	void enterMagnitude(RxParser.MagnitudeContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#magnitude}.
	 * @param ctx the parse tree
	 */
	void exitMagnitude(RxParser.MagnitudeContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#identifierBinding}.
	 * @param ctx the parse tree
	 */
	void enterIdentifierBinding(RxParser.IdentifierBindingContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#identifierBinding}.
	 * @param ctx the parse tree
	 */
	void exitIdentifierBinding(RxParser.IdentifierBindingContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#letStatement}.
	 * @param ctx the parse tree
	 */
	void enterLetStatement(RxParser.LetStatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#letStatement}.
	 * @param ctx the parse tree
	 */
	void exitLetStatement(RxParser.LetStatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#blockExpression}.
	 * @param ctx the parse tree
	 */
	void enterBlockExpression(RxParser.BlockExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#blockExpression}.
	 * @param ctx the parse tree
	 */
	void exitBlockExpression(RxParser.BlockExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statement}.
	 * @param ctx the parse tree
	 */
	void enterStatement(RxParser.StatementContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statement}.
	 * @param ctx the parse tree
	 */
	void exitStatement(RxParser.StatementContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#expressionWithBlock}.
	 * @param ctx the parse tree
	 */
	void enterExpressionWithBlock(RxParser.ExpressionWithBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#expressionWithBlock}.
	 * @param ctx the parse tree
	 */
	void exitExpressionWithBlock(RxParser.ExpressionWithBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#ifExpression}.
	 * @param ctx the parse tree
	 */
	void enterIfExpression(RxParser.IfExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#ifExpression}.
	 * @param ctx the parse tree
	 */
	void exitIfExpression(RxParser.IfExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#expression}.
	 * @param ctx the parse tree
	 */
	void enterExpression(RxParser.ExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#expression}.
	 * @param ctx the parse tree
	 */
	void exitExpression(RxParser.ExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#assignmentExpression}.
	 * @param ctx the parse tree
	 */
	void enterAssignmentExpression(RxParser.AssignmentExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#assignmentExpression}.
	 * @param ctx the parse tree
	 */
	void exitAssignmentExpression(RxParser.AssignmentExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#logicalOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterLogicalOrExpression(RxParser.LogicalOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#logicalOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitLogicalOrExpression(RxParser.LogicalOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#logicalAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterLogicalAndExpression(RxParser.LogicalAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#logicalAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitLogicalAndExpression(RxParser.LogicalAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#comparisonExpression}.
	 * @param ctx the parse tree
	 */
	void enterComparisonExpression(RxParser.ComparisonExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#comparisonExpression}.
	 * @param ctx the parse tree
	 */
	void exitComparisonExpression(RxParser.ComparisonExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#bitOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterBitOrExpression(RxParser.BitOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#bitOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitBitOrExpression(RxParser.BitOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#closedBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterClosedBitOrExpression(RxParser.ClosedBitOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#closedBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitClosedBitOrExpression(RxParser.ClosedBitOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#bitXorExpression}.
	 * @param ctx the parse tree
	 */
	void enterBitXorExpression(RxParser.BitXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#bitXorExpression}.
	 * @param ctx the parse tree
	 */
	void exitBitXorExpression(RxParser.BitXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#closedBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void enterClosedBitXorExpression(RxParser.ClosedBitXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#closedBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void exitClosedBitXorExpression(RxParser.ClosedBitXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#bitAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterBitAndExpression(RxParser.BitAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#bitAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitBitAndExpression(RxParser.BitAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#closedBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterClosedBitAndExpression(RxParser.ClosedBitAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#closedBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitClosedBitAndExpression(RxParser.ClosedBitAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#shiftExpression}.
	 * @param ctx the parse tree
	 */
	void enterShiftExpression(RxParser.ShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#shiftExpression}.
	 * @param ctx the parse tree
	 */
	void exitShiftExpression(RxParser.ShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#closedShiftExpression}.
	 * @param ctx the parse tree
	 */
	void enterClosedShiftExpression(RxParser.ClosedShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#closedShiftExpression}.
	 * @param ctx the parse tree
	 */
	void exitClosedShiftExpression(RxParser.ClosedShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#additiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterAdditiveExpression(RxParser.AdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#additiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitAdditiveExpression(RxParser.AdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#closedAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterClosedAdditiveExpression(RxParser.ClosedAdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#closedAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitClosedAdditiveExpression(RxParser.ClosedAdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterMultiplicativeExpression(RxParser.MultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitMultiplicativeExpression(RxParser.MultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#closedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterClosedMultiplicativeExpression(RxParser.ClosedMultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#closedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitClosedMultiplicativeExpression(RxParser.ClosedMultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#castExpression}.
	 * @param ctx the parse tree
	 */
	void enterCastExpression(RxParser.CastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#castExpression}.
	 * @param ctx the parse tree
	 */
	void exitCastExpression(RxParser.CastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#closedCastExpression}.
	 * @param ctx the parse tree
	 */
	void enterClosedCastExpression(RxParser.ClosedCastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#closedCastExpression}.
	 * @param ctx the parse tree
	 */
	void exitClosedCastExpression(RxParser.ClosedCastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#unaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterUnaryExpression(RxParser.UnaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#unaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitUnaryExpression(RxParser.UnaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#postfixExpression}.
	 * @param ctx the parse tree
	 */
	void enterPostfixExpression(RxParser.PostfixExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#postfixExpression}.
	 * @param ctx the parse tree
	 */
	void exitPostfixExpression(RxParser.PostfixExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionExpression(RxParser.ConditionExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionExpression(RxParser.ConditionExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionAssignmentExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionAssignmentExpression(RxParser.ConditionAssignmentExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionAssignmentExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionAssignmentExpression(RxParser.ConditionAssignmentExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionLogicalOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionLogicalOrExpression(RxParser.ConditionLogicalOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionLogicalOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionLogicalOrExpression(RxParser.ConditionLogicalOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionLogicalAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionLogicalAndExpression(RxParser.ConditionLogicalAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionLogicalAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionLogicalAndExpression(RxParser.ConditionLogicalAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionComparisonExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionComparisonExpression(RxParser.ConditionComparisonExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionComparisonExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionComparisonExpression(RxParser.ConditionComparisonExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBitOrExpression(RxParser.ConditionBitOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBitOrExpression(RxParser.ConditionBitOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionClosedBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionClosedBitOrExpression(RxParser.ConditionClosedBitOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionClosedBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionClosedBitOrExpression(RxParser.ConditionClosedBitOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBitXorExpression(RxParser.ConditionBitXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBitXorExpression(RxParser.ConditionBitXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionClosedBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionClosedBitXorExpression(RxParser.ConditionClosedBitXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionClosedBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionClosedBitXorExpression(RxParser.ConditionClosedBitXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBitAndExpression(RxParser.ConditionBitAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBitAndExpression(RxParser.ConditionBitAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionClosedBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionClosedBitAndExpression(RxParser.ConditionClosedBitAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionClosedBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionClosedBitAndExpression(RxParser.ConditionClosedBitAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionShiftExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionShiftExpression(RxParser.ConditionShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionShiftExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionShiftExpression(RxParser.ConditionShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionClosedShiftExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionClosedShiftExpression(RxParser.ConditionClosedShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionClosedShiftExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionClosedShiftExpression(RxParser.ConditionClosedShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionAdditiveExpression(RxParser.ConditionAdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionAdditiveExpression(RxParser.ConditionAdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionClosedAdditiveExpression(RxParser.ConditionClosedAdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionClosedAdditiveExpression(RxParser.ConditionClosedAdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionMultiplicativeExpression(RxParser.ConditionMultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionMultiplicativeExpression(RxParser.ConditionMultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionClosedMultiplicativeExpression(RxParser.ConditionClosedMultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionClosedMultiplicativeExpression(RxParser.ConditionClosedMultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionCastExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionCastExpression(RxParser.ConditionCastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionCastExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionCastExpression(RxParser.ConditionCastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionClosedCastExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionClosedCastExpression(RxParser.ConditionClosedCastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionClosedCastExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionClosedCastExpression(RxParser.ConditionClosedCastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionUnaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionUnaryExpression(RxParser.ConditionUnaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionUnaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionUnaryExpression(RxParser.ConditionUnaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionPostfixExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionPostfixExpression(RxParser.ConditionPostfixExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionPostfixExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionPostfixExpression(RxParser.ConditionPostfixExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakExpression(RxParser.ConditionBreakExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakExpression(RxParser.ConditionBreakExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakAssignmentExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakAssignmentExpression(RxParser.ConditionBreakAssignmentExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakAssignmentExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakAssignmentExpression(RxParser.ConditionBreakAssignmentExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakLogicalOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakLogicalOrExpression(RxParser.ConditionBreakLogicalOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakLogicalOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakLogicalOrExpression(RxParser.ConditionBreakLogicalOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakLogicalAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakLogicalAndExpression(RxParser.ConditionBreakLogicalAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakLogicalAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakLogicalAndExpression(RxParser.ConditionBreakLogicalAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakComparisonExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakComparisonExpression(RxParser.ConditionBreakComparisonExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakComparisonExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakComparisonExpression(RxParser.ConditionBreakComparisonExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakBitOrExpression(RxParser.ConditionBreakBitOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakBitOrExpression(RxParser.ConditionBreakBitOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakClosedBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakClosedBitOrExpression(RxParser.ConditionBreakClosedBitOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakClosedBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakClosedBitOrExpression(RxParser.ConditionBreakClosedBitOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakBitXorExpression(RxParser.ConditionBreakBitXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakBitXorExpression(RxParser.ConditionBreakBitXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakClosedBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakClosedBitXorExpression(RxParser.ConditionBreakClosedBitXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakClosedBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakClosedBitXorExpression(RxParser.ConditionBreakClosedBitXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakBitAndExpression(RxParser.ConditionBreakBitAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakBitAndExpression(RxParser.ConditionBreakBitAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakClosedBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakClosedBitAndExpression(RxParser.ConditionBreakClosedBitAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakClosedBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakClosedBitAndExpression(RxParser.ConditionBreakClosedBitAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakShiftExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakShiftExpression(RxParser.ConditionBreakShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakShiftExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakShiftExpression(RxParser.ConditionBreakShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakClosedShiftExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakClosedShiftExpression(RxParser.ConditionBreakClosedShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakClosedShiftExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakClosedShiftExpression(RxParser.ConditionBreakClosedShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakAdditiveExpression(RxParser.ConditionBreakAdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakAdditiveExpression(RxParser.ConditionBreakAdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakClosedAdditiveExpression(RxParser.ConditionBreakClosedAdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakClosedAdditiveExpression(RxParser.ConditionBreakClosedAdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakMultiplicativeExpression(RxParser.ConditionBreakMultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakMultiplicativeExpression(RxParser.ConditionBreakMultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakClosedMultiplicativeExpression(RxParser.ConditionBreakClosedMultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakClosedMultiplicativeExpression(RxParser.ConditionBreakClosedMultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakCastExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakCastExpression(RxParser.ConditionBreakCastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakCastExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakCastExpression(RxParser.ConditionBreakCastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakClosedCastExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakClosedCastExpression(RxParser.ConditionBreakClosedCastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakClosedCastExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakClosedCastExpression(RxParser.ConditionBreakClosedCastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakUnaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakUnaryExpression(RxParser.ConditionBreakUnaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakUnaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakUnaryExpression(RxParser.ConditionBreakUnaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionBreakPostfixExpression}.
	 * @param ctx the parse tree
	 */
	void enterConditionBreakPostfixExpression(RxParser.ConditionBreakPostfixExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionBreakPostfixExpression}.
	 * @param ctx the parse tree
	 */
	void exitConditionBreakPostfixExpression(RxParser.ConditionBreakPostfixExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementExpression(RxParser.StatementExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementExpression(RxParser.StatementExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementAssignmentExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementAssignmentExpression(RxParser.StatementAssignmentExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementAssignmentExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementAssignmentExpression(RxParser.StatementAssignmentExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementLogicalOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementLogicalOrExpression(RxParser.StatementLogicalOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementLogicalOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementLogicalOrExpression(RxParser.StatementLogicalOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementLogicalAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementLogicalAndExpression(RxParser.StatementLogicalAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementLogicalAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementLogicalAndExpression(RxParser.StatementLogicalAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementComparisonExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementComparisonExpression(RxParser.StatementComparisonExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementComparisonExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementComparisonExpression(RxParser.StatementComparisonExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementBitOrExpression(RxParser.StatementBitOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementBitOrExpression(RxParser.StatementBitOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementClosedBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementClosedBitOrExpression(RxParser.StatementClosedBitOrExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementClosedBitOrExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementClosedBitOrExpression(RxParser.StatementClosedBitOrExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementBitXorExpression(RxParser.StatementBitXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementBitXorExpression(RxParser.StatementBitXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementClosedBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementClosedBitXorExpression(RxParser.StatementClosedBitXorExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementClosedBitXorExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementClosedBitXorExpression(RxParser.StatementClosedBitXorExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementBitAndExpression(RxParser.StatementBitAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementBitAndExpression(RxParser.StatementBitAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementClosedBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementClosedBitAndExpression(RxParser.StatementClosedBitAndExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementClosedBitAndExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementClosedBitAndExpression(RxParser.StatementClosedBitAndExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementShiftExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementShiftExpression(RxParser.StatementShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementShiftExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementShiftExpression(RxParser.StatementShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementClosedShiftExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementClosedShiftExpression(RxParser.StatementClosedShiftExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementClosedShiftExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementClosedShiftExpression(RxParser.StatementClosedShiftExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementAdditiveExpression(RxParser.StatementAdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementAdditiveExpression(RxParser.StatementAdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementClosedAdditiveExpression(RxParser.StatementClosedAdditiveExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementClosedAdditiveExpression(RxParser.StatementClosedAdditiveExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementMultiplicativeExpression(RxParser.StatementMultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementMultiplicativeExpression(RxParser.StatementMultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementClosedMultiplicativeExpression(RxParser.StatementClosedMultiplicativeExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementClosedMultiplicativeExpression(RxParser.StatementClosedMultiplicativeExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementCastExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementCastExpression(RxParser.StatementCastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementCastExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementCastExpression(RxParser.StatementCastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementClosedCastExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementClosedCastExpression(RxParser.StatementClosedCastExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementClosedCastExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementClosedCastExpression(RxParser.StatementClosedCastExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementUnaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementUnaryExpression(RxParser.StatementUnaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementUnaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementUnaryExpression(RxParser.StatementUnaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#statementPostfixExpression}.
	 * @param ctx the parse tree
	 */
	void enterStatementPostfixExpression(RxParser.StatementPostfixExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#statementPostfixExpression}.
	 * @param ctx the parse tree
	 */
	void exitStatementPostfixExpression(RxParser.StatementPostfixExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void enterPrimaryExpression(RxParser.PrimaryExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#primaryExpression}.
	 * @param ctx the parse tree
	 */
	void exitPrimaryExpression(RxParser.PrimaryExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#nonBlockPrimary}.
	 * @param ctx the parse tree
	 */
	void enterNonBlockPrimary(RxParser.NonBlockPrimaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#nonBlockPrimary}.
	 * @param ctx the parse tree
	 */
	void exitNonBlockPrimary(RxParser.NonBlockPrimaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionPrimary}.
	 * @param ctx the parse tree
	 */
	void enterConditionPrimary(RxParser.ConditionPrimaryContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionPrimary}.
	 * @param ctx the parse tree
	 */
	void exitConditionPrimary(RxParser.ConditionPrimaryContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#conditionPrimaryWithoutBareBlock}.
	 * @param ctx the parse tree
	 */
	void enterConditionPrimaryWithoutBareBlock(RxParser.ConditionPrimaryWithoutBareBlockContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#conditionPrimaryWithoutBareBlock}.
	 * @param ctx the parse tree
	 */
	void exitConditionPrimaryWithoutBareBlock(RxParser.ConditionPrimaryWithoutBareBlockContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#literalExpression}.
	 * @param ctx the parse tree
	 */
	void enterLiteralExpression(RxParser.LiteralExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#literalExpression}.
	 * @param ctx the parse tree
	 */
	void exitLiteralExpression(RxParser.LiteralExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#structExprFields}.
	 * @param ctx the parse tree
	 */
	void enterStructExprFields(RxParser.StructExprFieldsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#structExprFields}.
	 * @param ctx the parse tree
	 */
	void exitStructExprFields(RxParser.StructExprFieldsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#structExprField}.
	 * @param ctx the parse tree
	 */
	void enterStructExprField(RxParser.StructExprFieldContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#structExprField}.
	 * @param ctx the parse tree
	 */
	void exitStructExprField(RxParser.StructExprFieldContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#arrayExpression}.
	 * @param ctx the parse tree
	 */
	void enterArrayExpression(RxParser.ArrayExpressionContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#arrayExpression}.
	 * @param ctx the parse tree
	 */
	void exitArrayExpression(RxParser.ArrayExpressionContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#postfixSuffix}.
	 * @param ctx the parse tree
	 */
	void enterPostfixSuffix(RxParser.PostfixSuffixContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#postfixSuffix}.
	 * @param ctx the parse tree
	 */
	void exitPostfixSuffix(RxParser.PostfixSuffixContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#dotSuffix}.
	 * @param ctx the parse tree
	 */
	void enterDotSuffix(RxParser.DotSuffixContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#dotSuffix}.
	 * @param ctx the parse tree
	 */
	void exitDotSuffix(RxParser.DotSuffixContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#callArguments}.
	 * @param ctx the parse tree
	 */
	void enterCallArguments(RxParser.CallArgumentsContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#callArguments}.
	 * @param ctx the parse tree
	 */
	void exitCallArguments(RxParser.CallArgumentsContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#unaryOperator}.
	 * @param ctx the parse tree
	 */
	void enterUnaryOperator(RxParser.UnaryOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#unaryOperator}.
	 * @param ctx the parse tree
	 */
	void exitUnaryOperator(RxParser.UnaryOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#multiplicativeOperator}.
	 * @param ctx the parse tree
	 */
	void enterMultiplicativeOperator(RxParser.MultiplicativeOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#multiplicativeOperator}.
	 * @param ctx the parse tree
	 */
	void exitMultiplicativeOperator(RxParser.MultiplicativeOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#additiveOperator}.
	 * @param ctx the parse tree
	 */
	void enterAdditiveOperator(RxParser.AdditiveOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#additiveOperator}.
	 * @param ctx the parse tree
	 */
	void exitAdditiveOperator(RxParser.AdditiveOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#shiftRight}.
	 * @param ctx the parse tree
	 */
	void enterShiftRight(RxParser.ShiftRightContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#shiftRight}.
	 * @param ctx the parse tree
	 */
	void exitShiftRight(RxParser.ShiftRightContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#comparisonExceptLt}.
	 * @param ctx the parse tree
	 */
	void enterComparisonExceptLt(RxParser.ComparisonExceptLtContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#comparisonExceptLt}.
	 * @param ctx the parse tree
	 */
	void exitComparisonExceptLt(RxParser.ComparisonExceptLtContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#assignmentOperator}.
	 * @param ctx the parse tree
	 */
	void enterAssignmentOperator(RxParser.AssignmentOperatorContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#assignmentOperator}.
	 * @param ctx the parse tree
	 */
	void exitAssignmentOperator(RxParser.AssignmentOperatorContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#equalsSign}.
	 * @param ctx the parse tree
	 */
	void enterEqualsSign(RxParser.EqualsSignContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#equalsSign}.
	 * @param ctx the parse tree
	 */
	void exitEqualsSign(RxParser.EqualsSignContext ctx);
	/**
	 * Enter a parse tree produced by {@link RxParser#identifier}.
	 * @param ctx the parse tree
	 */
	void enterIdentifier(RxParser.IdentifierContext ctx);
	/**
	 * Exit a parse tree produced by {@link RxParser#identifier}.
	 * @param ctx the parse tree
	 */
	void exitIdentifier(RxParser.IdentifierContext ctx);
}