package rx.semantic

import rx.ast.CrateNode
import rx.ast.ConstNode
import rx.ast.FnNode
import rx.ast.FnParam
import rx.ast.ImplNode
import rx.ast.ItemNode
import rx.ast.StructNode
import rx.ast.UseNode

import rx.ast.ArrayTypeNode
import rx.ast.PathTypeNode
import rx.ast.RefTypeNode
import rx.ast.SelfParam
import rx.ast.TypeRefNode
import rx.ast.UnitTypeNode

import rx.ast.StmtNode
import rx.ast.LetNode
import rx.ast.ExprNode
import rx.ast.ExprStmtNode
import rx.ast.BlockExprNode
import rx.ast.PathExprNode
import rx.ast.CallExprNode
import rx.ast.StructLitNode
import rx.ast.IfExprNode
import rx.ast.WhileExprNode
import rx.ast.LoopExprNode
import rx.ast.UnaryExprNode
import rx.ast.ArrayLitNode
import rx.ast.ReferenceExprNode
import rx.ast.BinaryExprNode
import rx.ast.AssignExprNode
import rx.ast.FieldExprNode
import rx.ast.IndexExprNode
import rx.ast.MethodCallExprNode
import rx.ast.CastExprNode
import rx.ast.BreakExprNode
import rx.ast.ContinueExprNode
import rx.ast.ReturnExprNode
import rx.ast.IntLitNode
import rx.ast.BoolLitNode
import rx.ast.UnitLitNode

/**
 * 语义分析器（2.5）总入口：对整棵 AST 查名字、查类型（semantic_design.md）。
 *
 * WP0 状态：空壳——只搭好入口与全局作用域；检查内容按 WP 分批接入：
 * - WP1 全局收集（struct/fn/const/impl → globals 的两个名字空间）、重名/保护名/main/impl 合法性；
 * - WP2 函数体作用域与名字解析（块级进出、未定义名、shadowing）；
 * - WP3+ 表达式类型检查（check(expr, expected)）、place 与可变性、常量求值、derive、布局……
 *
 * 约定：发现语义错误立即抛 [SemanticError]（fail-fast），CLI 捕获后报告并 exit 1。
 */


class SemanticAnalyzer {

    /**
     * 全局作用域（parent = null）。
     * WP1 起：先预注册内建环境（i32 等类型名、print_i32/get_i32 等函数），再收集顶层声明。
     */
    val globals: Scope = Scope(null)

    private var currentSelfType: StructSymbol? = null

    private var currentScope: Scope = globals

    /** 当前是否在带 self 参数的方法里；顶层函数为 false。 */
    private var currentHasSelfParam: Boolean = false

    private val BUILTIN_TYPE_NAMES = listOf(
        "i32", "u32", "isize", "usize", "bool", "Box", "Vec",
    )

    private val BUILTIN_VALUE_NAMES = listOf(
        "get_i32", "print_i32", "println_i32",
    )

    /* 检查顶层函数体 */
    private fun checkFunctionBody(node: FnNode) {
        val savedScope = currentScope
        val savedHasSelf = currentHasSelfParam

        currentScope = globals.child()   // 函数作用域：放参数
        currentHasSelfParam = false      // 顶层函数没有 receiver

        try {
            for (param in node.params) {
                if (param is FnParam) {
                    currentScope.declareValue(
                        LocalSymbol(param.name, param.mutable, null)
                    )
                }
            }

            checkBlock(node.body)        // 函数体块，会在里面再建 child
        } finally {
            currentScope = savedScope
            currentHasSelfParam = savedHasSelf
        }
    }

    private fun checkBlock(block: BlockExprNode) {
        val savedScope = currentScope
        currentScope = savedScope.child()

        try {
            for (stmt in block.statements) {
                checkStatement(stmt)
            }
            block.tail?.let { checkExpr(it) }
        } finally {
            currentScope = savedScope
        }
    }

    private fun checkStatement(stmt: StmtNode) {
        when (stmt) {
            is LetNode -> {
                checkExpr(stmt.initializer)                 // 先查 initializer
                stmt.declaredType?.let { checkTypeRef(it) } // 可选
                currentScope.defineLocal(
                    LocalSymbol(stmt.name, stmt.mutable, stmt.span)
                )
            }
            is ExprStmtNode -> checkExpr(stmt.expr)
            else -> Unit
        }
    }

    private fun checkExpr(expr: ExprNode) {
        when (expr) {
            is PathExprNode -> checkPathExpr(expr)
            is CallExprNode -> checkCallExpr(expr)
            is StructLitNode -> checkStructLitExpr(expr)
            is BlockExprNode -> checkBlock(expr)

            is IfExprNode -> {
                checkExpr(expr.condition)
                checkBlock(expr.thenblock)
                expr.elseBranch?.let { checkExpr(it) }
            }

            is WhileExprNode -> {
                checkExpr(expr.condition)
                checkBlock(expr.block)
            }

            is LoopExprNode -> checkBlock(expr.block)
            is UnaryExprNode -> checkExpr(expr.operand)
            is ReferenceExprNode -> checkExpr(expr.operand)

            is BinaryExprNode -> {
                checkExpr(expr.left)
                checkExpr(expr.right)
            }

            is AssignExprNode -> {
                checkExpr(expr.target)
                checkExpr(expr.value)
            }

            is FieldExprNode -> checkExpr(expr.receiver)

            is IndexExprNode -> {
                checkExpr(expr.receiver)
                checkExpr(expr.index)
            }

            is MethodCallExprNode -> {
                checkExpr(expr.receiver)
                for (arg in expr.args) checkExpr(arg)
            }

            is CastExprNode -> checkExpr(expr.expr)
            is BreakExprNode -> expr.value?.let { checkExpr(it) }
            is ReturnExprNode -> expr.value?.let { checkExpr(it) }

            is ArrayLitNode -> {
                for (element in expr.elements) checkExpr(element)
                expr.repeat?.let {
                    checkExpr(it.element)
                    checkExpr(it.length)
                }
            }

            is IntLitNode,
            is BoolLitNode,
            is UnitLitNode,
            is ContinueExprNode -> Unit

            else -> Unit
        }
    }

    private fun checkPathExpr(node: PathExprNode) {
        val first = node.segments.firstOrNull() 
            ?: throw SemanticError(node.span, "empty path")

        // 解析Path上的泛型
        for (segment in node.segments) {
            for (arg in segment.genericArgs) {
                checkTypeRef(arg)
            }
        }

        if (node.segments.size == 1) {
            val name = first.name

            if (name == "self") {
                if (!currentHasSelfParam) {
                    throw SemanticError(node.span, "`self` is only allowed inside a method")
                }
                return
            }

            currentScope.lookupValue(name)
                ?: throw SemanticError(node.span, "unresolved value '$name'")
            return
        }

        val head = first.name

        if (head == "Self") {
            if (currentSelfType == null) {
                throw SemanticError(node.span, "`Self` is not allowed here")
            }
        } else if (currentScope.lookupType(head) == null) {
            throw SemanticError(node.span, "unresolved path '$head'")
        }
    }

    private fun checkCallExpr(node: CallExprNode) {
        checkExpr(node.callee)

        // 只对单段路径做可调用性检查，后续再做多段路径
        if (node.callee is PathExprNode && node.callee.segments.size == 1) {
            val name = node.callee.segments[0].name
            val symbol = currentScope.lookupValue(name)

            if (symbol != null &&
                symbol !is FunctionSymbol &&
                symbol !is BuiltinFunctionSymbol
            ) {
                throw SemanticError(node.callee.span, "'$name' is not callable")
            }
        }

        for (arg in node.args) {
            checkExpr(arg)
        }
    }

    private fun checkStructLitExpr(node: StructLitNode) {
        val name = node.path.lastOrNull()
            ?: throw SemanticError(node.span, "empty struct path")

        if (name == "Self") {
            if (currentSelfType == null) {
                throw SemanticError(node.span, "`Self` is not allowed here")
            }
        } else {
            val symbol = currentScope.lookupType(name)
            if (symbol !is StructSymbol) {
                throw SemanticError(node.span, "unresolved struct type '$name'")
            }
        }

        for (field in node.fields) {
            checkExpr(field.value)
        }
    }

    private fun installBuiltins(scope: Scope) {
        for (name in BUILTIN_TYPE_NAMES) {
            scope.declareType(BuiltinTypeSymbol(name))
        }
        for (name in BUILTIN_VALUE_NAMES) {
            scope.declareValue(BuiltinFunctionSymbol(name))
        }
    }

    /** 检查整个 crate。WP0：暂不产生任何错误。 */
    fun analyze(crate: CrateNode) {
        installBuiltins(globals)

        collectTopLevel(crate.items)
        checkDeclarations(crate.items)
        // WP2+: 逐函数体检查（globals 的生命周期随本次分析）
    }

    private fun collectTopLevel(items: List<ItemNode>) {
        for (item in items) collectItem(item)
    }

    private fun collectItem(item: ItemNode) {
        when (item) {
            is StructNode -> { 
                if (!globals.declareType(StructSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate struct '${item.name}'")
                }
            }
            is FnNode     -> { 
                if (!globals.declareValue(FunctionSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate function '${item.name}'")
                }
            }
            is ConstNode  -> { 
                if (!globals.declareValue(ConstantSymbol(item.name, item))) {
                    throw SemanticError(item.span, "duplicate const '${item.name}'")
                }
            }
            is ImplNode   -> { /* 这一小步先什么都不做 */ }
            is UseNode    -> { /* 理论上不会出现，忽略 */ }
        }
    }

    private fun checkDeclarations(items: List<ItemNode>) {
        for (item in items) {
            when (item) {
                is StructNode -> checkStructFields(item)
                is FnNode     -> {
                    checkTopLevelFunction(item)
                    checkFunctionBody(item)
                }
                is ConstNode  -> { /* 暂不检查 */ }
                is ImplNode   -> { /* 下一步处理 */ }
                is UseNode    -> {}
            }
        }
    }

    private fun checkStructFields(node: StructNode) {
        val seen  = HashSet<String>()

        for(field in node.fields) {
            if (!seen.add(field.name)) {
                throw SemanticError(node.span, "duplicate field '${field.name}'")
            }
        }
    }
 
    // 这个是顶层函数（不在任何struct下的）
    private fun checkTopLevelFunction(node: FnNode) {
        checkFunctionParams(node) // 当然首先要检查重名问题

        if (node.params.any { it is SelfParam }) {
            // 检查是否有Self这个参量
            throw SemanticError(
                node.span,
                "a top-level function cannot have a receiver",
            )
        }

        for (param in node.params) {
            if (param is FnParam) {
                checkTypeRef(param.type)
            }
        }

        node.returnType?.let { checkTypeRef(it) }
    }

    // 这个函数用来检查重名
    private fun checkFunctionParams(node: FnNode) {
        val seen = HashSet<String>()          // 只在这次调用里存在

        for (param in node.params) {
            if (param !is FnParam) continue
            if (!seen.add(param.name)) {
                throw SemanticError(node.span, "duplicate parameter '${param.name}'")
            }
        }
    }

    // 这里检查参数是否是正常的Type
    private fun checkTypeRef(ref: TypeRefNode) {
        when (ref) {
            is UnitTypeNode -> Unit
            is RefTypeNode -> checkTypeRef(ref.inner)
            is ArrayTypeNode -> checkTypeRef(ref.element)  // 长度留到 WP5
            is PathTypeNode -> checkPathType(ref)
            else -> throw SemanticError(ref.span, "unsupported type reference")
        }
    }

    private fun checkPathType(ref: PathTypeNode) {
        val name = ref.segments.firstOrNull()?.name
            ?: throw SemanticError(ref.span, "empty type path")

        if (name == "Self") {
            if (currentSelfType == null) {
                throw SemanticError(
                    ref.span,
                    "`Self` is only allowed inside a struct or impl",
                )
            }
        } else if (globals.lookupType(name) == null) {
            throw SemanticError(ref.span, "unresolved type '$name'")
        }

        for (segment in ref.segments) {
            for (arg in segment.genericArgs) {
                checkTypeRef(arg)
            }
        }
    }
}