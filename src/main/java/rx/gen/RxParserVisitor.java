// Generated from RxParser.g4 by ANTLR 4.13.2
package rx.gen;
import org.antlr.v4.runtime.tree.ParseTreeVisitor;

/**
 * This interface defines a complete generic visitor for a parse tree produced
 * by {@link RxParser}.
 *
 * @param <T> The return type of the visit operation. Use {@link Void} for
 * operations with no return type.
 */
public interface RxParserVisitor<T> extends ParseTreeVisitor<T> {
	/**
	 * Visit a parse tree produced by {@link RxParser#crate}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCrate(RxParser.CrateContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#item}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitItem(RxParser.ItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#useDeclaration}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUseDeclaration(RxParser.UseDeclarationContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#useTree}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUseTree(RxParser.UseTreeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#usePath}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUsePath(RxParser.UsePathContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#usePathSegment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUsePathSegment(RxParser.UsePathSegmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#functionDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionDefinition(RxParser.FunctionDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#functionParameters}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionParameters(RxParser.FunctionParametersContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#selfParam}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitSelfParam(RxParser.SelfParamContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#functionParam}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitFunctionParam(RxParser.FunctionParamContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#structDefinition}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructDefinition(RxParser.StructDefinitionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#structField}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructField(RxParser.StructFieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#outerAttribute}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitOuterAttribute(RxParser.OuterAttributeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#deriveName}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDeriveName(RxParser.DeriveNameContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#constantItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstantItem(RxParser.ConstantItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#inherentImpl}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitInherentImpl(RxParser.InherentImplContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#associatedItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssociatedItem(RxParser.AssociatedItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#genericParams}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGenericParams(RxParser.GenericParamsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#lifetimeParam}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLifetimeParam(RxParser.LifetimeParamContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#lifetime}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLifetime(RxParser.LifetimeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#lifetimeBounds}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLifetimeBounds(RxParser.LifetimeBoundsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#typeParamBounds}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeParamBounds(RxParser.TypeParamBoundsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#whereClause}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhereClause(RxParser.WhereClauseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#whereClauseItem}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitWhereClauseItem(RxParser.WhereClauseItemContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#typeRef}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypeRef(RxParser.TypeRefContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#referenceType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitReferenceType(RxParser.ReferenceTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#arrayType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayType(RxParser.ArrayTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#typePath}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypePath(RxParser.TypePathContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#typePathSegment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitTypePathSegment(RxParser.TypePathSegmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#pathInExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPathInExpression(RxParser.PathInExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#pathExprSegment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPathExprSegment(RxParser.PathExprSegmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#pathIdentSegment}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPathIdentSegment(RxParser.PathIdentSegmentContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#genericArgs}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGenericArgs(RxParser.GenericArgsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#genericArg}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGenericArg(RxParser.GenericArgContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#genericClose}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitGenericClose(RxParser.GenericCloseContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#closedCastType}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedCastType(RxParser.ClosedCastTypeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#constValue}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConstValue(RxParser.ConstValueContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#magnitude}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMagnitude(RxParser.MagnitudeContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#identifierBinding}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifierBinding(RxParser.IdentifierBindingContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#letStatement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLetStatement(RxParser.LetStatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#blockExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBlockExpression(RxParser.BlockExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statement}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatement(RxParser.StatementContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#expressionWithBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpressionWithBlock(RxParser.ExpressionWithBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#ifExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIfExpression(RxParser.IfExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#expression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitExpression(RxParser.ExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#assignmentExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignmentExpression(RxParser.AssignmentExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#logicalOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicalOrExpression(RxParser.LogicalOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#logicalAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLogicalAndExpression(RxParser.LogicalAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#comparisonExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonExpression(RxParser.ComparisonExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#bitOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBitOrExpression(RxParser.BitOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#closedBitOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedBitOrExpression(RxParser.ClosedBitOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#bitXorExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBitXorExpression(RxParser.BitXorExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#closedBitXorExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedBitXorExpression(RxParser.ClosedBitXorExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#bitAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitBitAndExpression(RxParser.BitAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#closedBitAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedBitAndExpression(RxParser.ClosedBitAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#shiftExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitShiftExpression(RxParser.ShiftExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#closedShiftExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedShiftExpression(RxParser.ClosedShiftExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#additiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveExpression(RxParser.AdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#closedAdditiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedAdditiveExpression(RxParser.ClosedAdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#multiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicativeExpression(RxParser.MultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#closedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedMultiplicativeExpression(RxParser.ClosedMultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#castExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCastExpression(RxParser.CastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#closedCastExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitClosedCastExpression(RxParser.ClosedCastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#unaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryExpression(RxParser.UnaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#postfixExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPostfixExpression(RxParser.PostfixExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionExpression(RxParser.ConditionExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionAssignmentExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionAssignmentExpression(RxParser.ConditionAssignmentExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionLogicalOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionLogicalOrExpression(RxParser.ConditionLogicalOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionLogicalAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionLogicalAndExpression(RxParser.ConditionLogicalAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionComparisonExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionComparisonExpression(RxParser.ConditionComparisonExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBitOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBitOrExpression(RxParser.ConditionBitOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionClosedBitOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionClosedBitOrExpression(RxParser.ConditionClosedBitOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBitXorExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBitXorExpression(RxParser.ConditionBitXorExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionClosedBitXorExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionClosedBitXorExpression(RxParser.ConditionClosedBitXorExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBitAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBitAndExpression(RxParser.ConditionBitAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionClosedBitAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionClosedBitAndExpression(RxParser.ConditionClosedBitAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionShiftExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionShiftExpression(RxParser.ConditionShiftExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionClosedShiftExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionClosedShiftExpression(RxParser.ConditionClosedShiftExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionAdditiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionAdditiveExpression(RxParser.ConditionAdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionClosedAdditiveExpression(RxParser.ConditionClosedAdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionMultiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionMultiplicativeExpression(RxParser.ConditionMultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionClosedMultiplicativeExpression(RxParser.ConditionClosedMultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionCastExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionCastExpression(RxParser.ConditionCastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionClosedCastExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionClosedCastExpression(RxParser.ConditionClosedCastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionUnaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionUnaryExpression(RxParser.ConditionUnaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionPostfixExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionPostfixExpression(RxParser.ConditionPostfixExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakExpression(RxParser.ConditionBreakExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakAssignmentExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakAssignmentExpression(RxParser.ConditionBreakAssignmentExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakLogicalOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakLogicalOrExpression(RxParser.ConditionBreakLogicalOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakLogicalAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakLogicalAndExpression(RxParser.ConditionBreakLogicalAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakComparisonExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakComparisonExpression(RxParser.ConditionBreakComparisonExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakBitOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakBitOrExpression(RxParser.ConditionBreakBitOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakClosedBitOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakClosedBitOrExpression(RxParser.ConditionBreakClosedBitOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakBitXorExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakBitXorExpression(RxParser.ConditionBreakBitXorExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakClosedBitXorExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakClosedBitXorExpression(RxParser.ConditionBreakClosedBitXorExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakBitAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakBitAndExpression(RxParser.ConditionBreakBitAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakClosedBitAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakClosedBitAndExpression(RxParser.ConditionBreakClosedBitAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakShiftExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakShiftExpression(RxParser.ConditionBreakShiftExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakClosedShiftExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakClosedShiftExpression(RxParser.ConditionBreakClosedShiftExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakAdditiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakAdditiveExpression(RxParser.ConditionBreakAdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakClosedAdditiveExpression(RxParser.ConditionBreakClosedAdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakMultiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakMultiplicativeExpression(RxParser.ConditionBreakMultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakClosedMultiplicativeExpression(RxParser.ConditionBreakClosedMultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakCastExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakCastExpression(RxParser.ConditionBreakCastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakClosedCastExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakClosedCastExpression(RxParser.ConditionBreakClosedCastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakUnaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakUnaryExpression(RxParser.ConditionBreakUnaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionBreakPostfixExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionBreakPostfixExpression(RxParser.ConditionBreakPostfixExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementExpression(RxParser.StatementExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementAssignmentExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementAssignmentExpression(RxParser.StatementAssignmentExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementLogicalOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementLogicalOrExpression(RxParser.StatementLogicalOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementLogicalAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementLogicalAndExpression(RxParser.StatementLogicalAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementComparisonExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementComparisonExpression(RxParser.StatementComparisonExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementBitOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementBitOrExpression(RxParser.StatementBitOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementClosedBitOrExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementClosedBitOrExpression(RxParser.StatementClosedBitOrExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementBitXorExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementBitXorExpression(RxParser.StatementBitXorExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementClosedBitXorExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementClosedBitXorExpression(RxParser.StatementClosedBitXorExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementBitAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementBitAndExpression(RxParser.StatementBitAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementClosedBitAndExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementClosedBitAndExpression(RxParser.StatementClosedBitAndExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementShiftExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementShiftExpression(RxParser.StatementShiftExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementClosedShiftExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementClosedShiftExpression(RxParser.StatementClosedShiftExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementAdditiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementAdditiveExpression(RxParser.StatementAdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementClosedAdditiveExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementClosedAdditiveExpression(RxParser.StatementClosedAdditiveExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementMultiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementMultiplicativeExpression(RxParser.StatementMultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementClosedMultiplicativeExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementClosedMultiplicativeExpression(RxParser.StatementClosedMultiplicativeExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementCastExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementCastExpression(RxParser.StatementCastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementClosedCastExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementClosedCastExpression(RxParser.StatementClosedCastExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementUnaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementUnaryExpression(RxParser.StatementUnaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#statementPostfixExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStatementPostfixExpression(RxParser.StatementPostfixExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#primaryExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPrimaryExpression(RxParser.PrimaryExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#nonBlockPrimary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitNonBlockPrimary(RxParser.NonBlockPrimaryContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionPrimary}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionPrimary(RxParser.ConditionPrimaryContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#conditionPrimaryWithoutBareBlock}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitConditionPrimaryWithoutBareBlock(RxParser.ConditionPrimaryWithoutBareBlockContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#literalExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitLiteralExpression(RxParser.LiteralExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#structExprFields}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructExprFields(RxParser.StructExprFieldsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#structExprField}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitStructExprField(RxParser.StructExprFieldContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#arrayExpression}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitArrayExpression(RxParser.ArrayExpressionContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#postfixSuffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitPostfixSuffix(RxParser.PostfixSuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#dotSuffix}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitDotSuffix(RxParser.DotSuffixContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#callArguments}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitCallArguments(RxParser.CallArgumentsContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#unaryOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitUnaryOperator(RxParser.UnaryOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#multiplicativeOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitMultiplicativeOperator(RxParser.MultiplicativeOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#additiveOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAdditiveOperator(RxParser.AdditiveOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#shiftRight}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitShiftRight(RxParser.ShiftRightContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#comparisonExceptLt}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitComparisonExceptLt(RxParser.ComparisonExceptLtContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#assignmentOperator}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitAssignmentOperator(RxParser.AssignmentOperatorContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#equalsSign}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitEqualsSign(RxParser.EqualsSignContext ctx);
	/**
	 * Visit a parse tree produced by {@link RxParser#identifier}.
	 * @param ctx the parse tree
	 * @return the visitor result
	 */
	T visitIdentifier(RxParser.IdentifierContext ctx);
}