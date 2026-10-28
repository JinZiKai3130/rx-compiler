package rx.semantic

import rx.ast.*

/**
 * 语义分析的共享上下文。
 *
 * 这里只放"多个阶段都要用"的东西：
 * - [globals]：全局作用域（阶段 1 收集，阶段 2 挂关联项，之后只读）；
 * - [currentSelfType]：当前 `Self` 指向的 struct（阶段 5/6/8 用 save/restore 维护）；
 * - [currentScope] / [currentHasSelfParam]：函数体检查的局部状态（阶段 8）；
 * - [constEval]：常量求值器（阶段 4 调用；阶段 5/8 求长度时也会用到）；
 * - [resolveType]：`TypeRefNode` → 内部 `Type` 的唯一入口（阶段 2/5/6/8 共用）。
 */
class SemanticContext(
    val globals: Scope = Scope(null),
) {
    var currentSelfType: StructSymbol? = null

    var currentScope: Scope = globals

    /** 当前是否在带 self 参数的方法里；顶层函数为 false。 */
    var currentHasSelfParam: Boolean = false

    /** 阶段 8：当前函数的返回类型（`return` 检查用）；顶层函数为 `()`。 */
    var currentReturnType: Type = UnitType

    /** 阶段 8：活跃循环栈（最内层在末尾）；`break` / `continue` 的归属与 `loop` 定型靠它。 */
    val loopStack: ArrayDeque<LoopContext> = ArrayDeque()

    val constEval: ConstEval by lazy { ConstEval(globals, ::resolveType) }

    // ------------------------------------------------------------------
    // 类型解析：源码写法（TypeRefNode）→ 内部类型（Type）
    // ------------------------------------------------------------------

    /** 把源码里的类型写法翻译成内部类型；所有类型合法性检查都在这里。 */
    fun resolveType(ref: TypeRefNode): Type = when (ref) {
        is UnitTypeNode  -> UnitType
        is RefTypeNode   -> RefType(ref.mutable, resolveType(ref.inner))
        is ArrayTypeNode -> ArrayType(
            resolveType(ref.element),
            constEval.requireUsize(ref.length),   // 数组长度是常量上下文
        )
        is PathTypeNode  -> resolvePathType(ref)
        else -> throw SemanticError(ref.span, "unsupported type reference")
    }

    /** 兼容只关心校验、不关心结果的调用点。 */
    fun checkTypeRef(ref: TypeRefNode) {
        resolveType(ref)
    }

    private fun resolvePathType(ref: PathTypeNode): Type {
        // 类型位置只能写单个类型名；关联项只有常量/函数，没有关联类型。
        if (ref.segments.size > 1) {
            throw SemanticError(ref.span, "qualified type paths cannot name a type")
        }

        val segment = ref.segments.firstOrNull()
            ?: throw SemanticError(ref.span, "empty type path")
        val name = segment.name

        if (name == "Self") {
            val self = currentSelfType
                ?: throw SemanticError(ref.span, "`Self` is only allowed inside a struct or impl")
            if (segment.genericArgs.isNotEmpty()) {
                throw SemanticError(ref.span, "`Self` takes no type arguments")
            }
            return StructType(self.name, self.node)
        }

        val symbol = globals.lookupType(name)
            ?: throw SemanticError(ref.span, "unresolved type '$name'")

        return when (symbol) {
            is BuiltinTypeSymbol -> when (symbol.kind) {
                BuiltinTypeKind.BOX, BuiltinTypeKind.VEC -> {
                    if (segment.genericArgs.size != 1) {
                        throw SemanticError(
                            ref.span,
                            "'$name' requires exactly one concrete type argument",
                        )
                    }
                    val element = resolveType(segment.genericArgs[0])   // 递归 + 校验
                    if (symbol.kind == BuiltinTypeKind.BOX) BoxType(element) else VecType(element)
                }

                BuiltinTypeKind.I32   -> { requireNoArgs(ref, name, segment); I32Type }
                BuiltinTypeKind.U32   -> { requireNoArgs(ref, name, segment); U32Type }
                BuiltinTypeKind.ISIZE -> { requireNoArgs(ref, name, segment); IsizeType }
                BuiltinTypeKind.USIZE -> { requireNoArgs(ref, name, segment); UsizeType }
                BuiltinTypeKind.BOOL  -> { requireNoArgs(ref, name, segment); BoolType }
            }

            is StructSymbol -> {
                if (segment.genericArgs.isNotEmpty()) {
                    throw SemanticError(ref.span, "struct '$name' has no type parameters")
                }
                StructType(name, symbol.node)
            }

            else -> throw SemanticError(ref.span, "unresolved type '$name'")
        }
    }

    /** 原生类型不能带泛型实参。 */
    private fun requireNoArgs(
        ref: PathTypeNode,
        name: String,
        segment: PathSegment,
    ) {
        if (segment.genericArgs.isNotEmpty()) {
            throw SemanticError(ref.span, "primitive type '$name' takes no type arguments")
        }
    }
}

/**
 * 一个活跃的循环（阶段 8）。
 *
 * - [acceptsValue] = true 表示 `loop`（可以 `break value` 并用载荷定型）；`while` 为 false。
 * - [expected] 来自外层目标类型（如 `let x: u32 = loop { ... }`），用来检查 break 载荷。
 * - [span] 指向循环本身，为空 break 列表之类的诊断用。
 */
class LoopContext(
    val acceptsValue: Boolean,
    val expected: Type?,
    val span: Span,
) {
    /** `loop` 收集到的所有 break 载荷类型；空 = 循环永不正常结束（`!`）。 */
    val breakTypes: MutableList<Type> = mutableListOf()
}
