package rx.semantic

/**
 * 作用域（手册 §2.5.2）：链式父子结构，每个作用域内分两个名字空间——
 * - 值：函数、常量、局部变量、参数（函数与常量共享值空间，官方用例
 *   `namespace-errors/rej-function-and-constant-share-the-value-namespace`）；
 * - 类型：结构体。
 *
 * 查找沿 parent 链上溯；`declareXxx` 只写当前层，返回 false 表示本层已重名
 * （重名检查在 WP1/WP2 使用，配合 `lookupXxxHere` 找到"先声明"的符号）。
 */
class Scope(val parent: Scope?) {

    private val values = LinkedHashMap<String, Symbol>()
    private val types = LinkedHashMap<String, Symbol>()

    /** 创建以此作用域为父的新子作用域（进入块时使用）。 */
    fun child(): Scope = Scope(this)

    /** 在当前层声明一个值名字空间的符号；本层已存在同名符号时返回 false。 */
    fun declareValue(symbol: Symbol): Boolean {
        if (values.containsKey(symbol.name)) return false
        values[symbol.name] = symbol
        return true
    }

    /** 局部绑定：允许同层遮蔽（let 语义）。 */
    fun defineLocal(symbol: Symbol) {
        values[symbol.name] = symbol
    }

    /** 在当前层声明一个类型名字空间的符号；本层已存在同名符号时返回 false。 */
    fun declareType(symbol: Symbol): Boolean {
        if (types.containsKey(symbol.name)) return false
        types[symbol.name] = symbol
        return true
    }

    /** 查值名字空间：本层没有就沿 parent 上溯。 */
    fun lookupValue(name: String): Symbol? =
        values[name] ?: parent?.lookupValue(name)

    /** 查类型名字空间：本层没有就沿 parent 上溯。 */
    fun lookupType(name: String): Symbol? =
        types[name] ?: parent?.lookupType(name)

}
