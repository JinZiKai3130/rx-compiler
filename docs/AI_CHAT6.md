# AI_CHAT6 —— 语义检查（WP1 收尾与常量求值）对话记录

- 导出日期：2026-10-26
- 仓库：`/home/kai/ACM_class_project/rx-compiler`
- 内容：按时间顺序导出本会话中「你」与「Copilot」的对话文本。
- 说明：工具调用、思考过程与终端输出未收录；代码和命令按对话原文保留。
  - 会话第一条提问未被日志记录，其回复单独列在第 0 节。
  - 被「Try Again」重试掉的回合会表现为只有提问、没有回复。
  - 第 36 轮回复的正文未被日志保存，已按对话原文补录。
  - 为保持层级清晰，回复正文中的标题已整体降两级（代码块内不动）。
  - 提出本次导出请求的这一轮不在日志中，见文末附录。

---

## 0. 会话开场（原始提问未被日志记录）

**Copilot**

I'll look at your design docs, current semantic code, and the related tests first, then tell you what to write.

我先看了 `docs/semantic_design.md`、`rx/semantic/` 现有骨架、`namespace-errors` 的 16 条用例和 `names` 规范。结论：**这个目录不是写 15 个独立检查，而是先把三类基础设施搭起来**。

#### 一、先认清这个目录考什么

`namespace-errors` = 1 条 accept + 15 条 reject，实际分三组：

| 组 | 用例 | 需要的机制 |
| --- | --- | --- |
| 顶层重名 | duplicate-top-level-function、duplicate-constant、function-and-constant-share-the-value-namespace、duplicate-struct、duplicate-field、duplicate-parameter | 两遍收集 + 两个名字空间 |
| 声明侧名字 | unresolved-signature-type、self-outside-a-struct-impl、a-top-level-function-cannot-have-a-receiver | 类型名解析 + `Self` 上下文 |
| 函数体名字/作用域 | unresolved-value-name、binding-is-invisible-in-its-own-initializer、block-local-name-cannot-escape、local-shadows-function-without-callable-fallback、named-field-structs-are-not-callable-constructors、self-outside-a-method | 最小 WP2：块作用域 + 表达式名字解析 |

> 注意：设计文档把 `namespace-errors` 整体记在 WP1，但其中 **6 条其实需要最小 WP2 的作用域链和名字解析**。你不可能只写“收集 pass”就全绿。

另外，accept 用例 `acc-named-field-struct-and-function-may-share-a-spelling` 是关键约束：`struct Same` 和 `fn Same` 必须能共存，且 `Same { ... }` 走类型名字空间、`Same()` 走值名字空间。

#### 二、需要动哪些文件、写哪些东西

##### 1. `Symbols.kt`：补内建符号

现在只有用户符号，没有内建环境。建议加：

- `BuiltinTypeSymbol(name)`：`i32/u32/isize/usize/bool/Box/Vec`
- `BuiltinFunctionSymbol(name)`：`get_i32/print_i32/println_i32`
- 可选：一个 `CallableSymbol` 标记接口（`FunctionSymbol` + `BuiltinFunctionSymbol`），后面 `CallExprNode` 判断“可调用”会干净很多

`Self` **不要**注册进全局类型表，它只在上下文里出现。

##### 2. `Scope.kt`：补一个“允许遮蔽”的局部声明

现有 `declareValue` 遇到同名返回 `false`，适合顶层重名检测，**但不能直接用于 `let`**：规范明确允许同一块里连续的 `let` 遮蔽之前的局部/参数。

建议加一个例如 `defineLocal(symbol)` 的方法：直接覆盖当前层的值表。区分：

- `declareValue` → 顶层函数/常量、参数：重复是错误
- `defineLocal` → `let`：重复是合法遮蔽

##### 3. `SemanticAnalyzer.kt`：主体工作

建议按函数拆成下面这些（不用一次全写）：

**入口与状态**

- `analyze(crate)`：内建环境 → 收集 → 检查
- 状态字段：`currentScope`、`currentSelfType: StructSymbol?`、`currentHasSelfParam: Boolean`
- 进出上下文时保存/恢复这些字段（`impl` 嵌套、函数体嵌套）

**收集 pass**

- `installBuiltins(globals)`
- `collectTopLevel(items)` / `collectItem(item)`
  - `StructNode` → `declareType(StructSymbol)`，失败 = duplicate struct
  - `FnNode` → `declareValue(FunctionSymbol)`，失败 = duplicate function / 与常量冲突
  - `ConstNode` → `declareValue(ConstantSymbol)`，失败 = duplicate constant
  - `ImplNode` → **不要把方法注册进 globals**；方法属于结构体的关联值名字空间
- 结构体字段重名用 `MutableSet<String>` 单独查（字段不在 `Scope` 里）

收集必须**先完整走一遍再检查**，否则前向引用会误报。

**声明检查 pass**

- `checkStruct(node)`：设置 `currentSelfType = 自己`，再解析每个字段类型。这样 `Vec<Self>`（`recursive-traits` 的 accept）才不会被误杀
- `checkFn(fn, inImpl)`：
  - 顶层 `FnNode` 的 `params` 里有 `SelfParam` → 报错（top-level receiver）
  - 解析参数类型和返回类型；顶层时 `currentSelfType = null`
- `checkConst(node)`：只解析声明类型；**常量初值先不要走表达式解析**，否则 `Self::B`、常量路径这类会误报，留给 WP5
- `checkImpl(node)`：解析 impl 目标类型，然后逐个关联项设置 `currentSelfType = 目标结构体` 再检查签名/函数体

**类型名解析**

- `resolveTypeRef(ref): Type`
  - `PathTypeNode`：单段名 → 内建类型表/`lookupType`；`Self` → `currentSelfType`，为空则报错
  - `RefTypeNode` / `ArrayTypeNode` / `UnitTypeNode` → 递归
  - 泛型实参递归解析（`Box<Self>`、`Vec<Missing>` 都能覆盖）
  - 多段路径先定一个保守策略（见下面的坑）
- `resolveTypeRef` 的调用点：函数参数/返回、结构体字段、const 类型、impl 目标。**`let` 的类型注解可以暂缓**，因为 `namespace-errors` 只考函数签名里的 `Missing`

**函数体与作用域**

- `checkFunctionBody(fn)`：
  - 函数作用域 = `globals.child()`，逐个声明参数；`declareValue` 失败 = duplicate parameter
  - 函数体块再建一个 `child()`，这样 `let` 可以合法遮蔽参数
- `checkBlock(block)`：每个 `BlockExprNode` 建子作用域，离开时丢弃
- `checkStatement(stmt)` / `checkLet(let)`：
  - **先检查 initializer，再声明新绑定**——这一条直接决定 `let x = x;` 是否报错
  - 再解析可选的类型注解
  - 最后 `defineLocal(LocalSymbol)`
- 尾表达式也要检查

**最小表达式名字解析**

- `checkExpr(expr)` 或 `walkExpr(expr)`：只做递归 + 路径解析，**不做类型检查**
- `PathExprNode` 单段：
  - `"self"` → 要求 `currentHasSelfParam == true`
  - 其他 → `lookupValue`，找不到 = unresolved value
- `CallExprNode`：
  - 先解析被调者；如果它是 `PathExprNode` 且解析到 `LocalSymbol`/`ConstantSymbol` → “not callable”（这条专门消灭 `let f = 1; f();`）
  - 参数继续递归
- `StructLitNode`：
  - 用**类型名字空间**解析路径；`"Self"` → `currentSelfType`
  - 必须解析到 `StructSymbol`，不能因为值名字空间里有个同名函数就放行
  - 字段值继续递归
- `FieldExprNode` / `MethodCallExprNode`：只递归 receiver/args，字段名和方法名先不校验（WP4/WP5）
- 其他所有表达式节点：递归每个子表达式/块。**不要做不可达剪枝**，规范要求不可达代码里的名字错误也报

> 实现方式：顶层和语句可以走 `AstVisitor`；表达式建议用显式 `when` 递归。WP3 的 `check(expr, expected)` 需要“期望类型”参数，Visitor 的 `visit(n)` 签名不够用，最终还是要手写递归。

#### 三、建议的实现顺序（每步都能单独回归）

1. **内建环境 + 收集 + 顶层重名** → 消灭 6 条 duplicate
2. **`Self` 上下文 + 签名类型解析 + 顶层 receiver** → 消灭 unresolved-signature-type、self-outside-a-struct-impl、top-level-receiver
3. **函数体作用域 + `let` 顺序 + 块作用域** → 消灭 initializer、block-escape
4. **路径解析 + 可调用性 + 结构体字面量类型解析** → 消灭 unresolved-value-name、local-shadow、struct-not-callable、self-outside-method；同时保住 accept 用例

#### 四、最容易踩的坑

- **两个名字空间必须严格分开**：`declareValue` / `declareType` 已经分开了，但结构体字面量如果错误地查值名字空间，accept 用例会挂（因为有同名函数 `Same`）。
- **`Self` 和 `self` 是两套上下文**：`Self` 是类型名，`impl` 和结构体字段里合法；`self` 是值，只有带 `SelfParam` 的方法里合法。`fn new() -> Self` 合法，但里面不能写 `self`。
- **多段路径要保守**：accept 里有 `Self::START`、`Box::<u32>::new`、`Vec::<i32>::new` 等。建议对 `N>1` 的路径只解析第一段（`Self` 或类型名字空间），后面的关联成员先不校验；否则很容易误伤 69 条 accept。
- **同块 `let` 重名不是错误**，只有参数重名、顶层重名、字段重名是错误。
- **方法不要进 globals**：`impl` 里的 `fn` 名字属于结构体的关联命名空间，进了全局会导致奇怪的冲突/误判。
- **`PathSegment` 没有 span**：报错时用整个 `PathExprNode`/`PathTypeNode` 的 span 即可，消息措辞不限。
- **accept 优先**：每写完一步都跑全量 runner，不能只看 `namespace-errors` 全绿；69 条 accept 一条都不能误报。

#### 五、验收方式

```text
make compiler
python3 scripts/check_semantic.py namespace-errors -v
python3 scripts/check_semantic.py
```

目标：

- `namespace-errors`：16/16（1 acc + 15 rej）
- 全量：**accept 69/69 不动**，reject 从 0/167 至少推进到 15/167

做完这个目录后，同一套收集 pass 可以直接复用到 `invalid-impls-and-generics`、`entry`、`protected-names`，但不要在 `namespace-errors` 里提前写类型规则、常量求值或 derive——那是 WP3–WP5 的事。

## 1. 2026-10-21 15:06 — 我不太明白你说的这个Symbol.kt中所谓内建符号是什么意思？

**你**

我不太明白你说的这个Symbol.kt中所谓内建符号是什么意思？

**Copilot**

“内建符号”不是要你实现什么新语法，而是**往符号表里预置一批编译器自带的名字**。

#### 1. 先回到符号表的本质

你的 `Scope` 现在干的事是：

```text
名字  →  它代表什么
```

比如用户写了：

```rust
struct Same { x: i32 }
fn Same() -> i32 { 1 }
```

收集 pass 会往 `globals` 里放：

| 名字空间 | 名字 | 符号 |
| --- | --- | --- |
| 类型 | `Same` | `StructSymbol("Same", node)` |
| 值 | `Same` | `FunctionSymbol("Same", node)` |

这就是“用户声明的符号”。它们的特点是：**每个符号都能对应到一个 AST 节点**（`StructSymbol` 有 `node: StructNode`，`FunctionSymbol` 有 `node: FnNode`）。

#### 2. 内建符号是什么

`i32`、`bool`、`Box`、`Vec`、`print_i32`、`println_i32` 这些名字，**用户从来没有声明过**，但每个测试文件都在用：

```rust
use rx::core::*;          // 这条会被整条丢弃，不引入任何名字

fn main() {
    let x: i32 = 1;       // i32 从哪来？
    println_i32(x);       // println_i32 从哪来？
}
```

`use` 声明按规范丢弃，不做 import 解析。所以如果 `globals` 里只有用户声明，那么：

- `resolveTypeRef(i32)` → `lookupType("i32")` → `null` → 误报“未定义类型”
- `checkExpr(println_i32)` → `lookupValue("println_i32")` → `null` → 误报“未定义值”

结果就是 **69 条 accept 会被大面积误杀**。

所谓“预注册内建环境”，就是在检查用户代码之前，先往 `globals` 里塞好：

| 名字空间 | 名字 | 符号 |
| --- | --- | --- |
| 类型 | `i32`, `u32`, `isize`, `usize`, `bool`, `Box`, `Vec` | `BuiltinTypeSymbol` |
| 值 | `get_i32`, `print_i32`, `println_i32` | `BuiltinFunctionSymbol` |

这样查找路径就统一了：用户名字和内建名字都走 `lookupType` / `lookupValue`，不需要在每个检查点写 `if (name == "i32") ...` 这种散落的特判。

#### 3. 为什么现有 Symbol 类装不下它

你现在的 `Symbols.kt`：

- `StructSymbol` 必须带 `StructNode`
- `FunctionSymbol` 必须带 `FnNode`
- `ConstantSymbol` 必须带 `ConstNode`

但 `i32` 没有 `StructNode`，`println_i32` 也没有 `FnNode`，它们是编译器内置的。所以需要两个不依赖 AST 的符号类型，例如：

- `BuiltinTypeSymbol(name)` —— 只表示“这个类型名存在”
- `BuiltinFunctionSymbol(name)` —— 只表示“这个值名字是一个可调用的内建函数”

现在不需要存参数类型/返回类型；等 WP3 做 builtin-io 的类型规则时，再给 `BuiltinFunctionSymbol` 加签名字段（或者用一张单独的签名表）。

#### 4. 这些内建符号会在哪里被用到

| 使用点 | 作用 |
| --- | --- |
| `resolveTypeRef` 解析 `PathTypeNode` | `i32` → 命中 `BuiltinTypeSymbol` → 返回 `I32Type`；`Box`/`Vec` 同理 |
| `checkExpr` 解析 `PathExprNode` | `println_i32` → 命中 `BuiltinFunctionSymbol`，不再报“未定义值” |
| `CallExprNode` 判断可调用 | 被调者是 `FunctionSymbol` 或 `BuiltinFunctionSymbol` 才放行；`LocalSymbol` 不行（这条顺便解决 `rej-a-local-shadows-a-function-without-callable-fallback`） |
| 后续 codegen | 能区分“用户函数”和“内建函数”，决定调用运行时还是生成代码 |

#### 5. 两个容易混淆的概念

**内建符号 ≠ 保护名。**

- 内建符号：符号表里的条目，解决“这个名字能不能被解析到”。
- 保护名：规范禁止用户用 `u32`、`Box`、`Vec`、`get_i32` 等名字去声明东西，解决“你能不能占用这个名字”。

`protected-names` 目录才需要做保护名检查；`namespace-errors` 只需要内建符号存在。

**`Self` 不是内建符号。**

`Self` 的含义取决于上下文（当前结构体 / impl 目标），不是一个全局固定的名字。所以它不注册进 `globals`，而是通过 `currentSelfType` 这个上下文字段解析。这样顶层 `fn f(x: Self)` 自然会因为 `currentSelfType == null` 而报错。

#### 6. 一句话总结

`Symbols.kt` 里加内建符号，就是给 `Scope` 准备“编译器自带的户口本”：

```text
globals
├── 类型名字空间：i32/u32/isize/usize/bool/Box/Vec → BuiltinTypeSymbol
└── 值名字空间：  get_i32/print_i32/println_i32 → BuiltinFunctionSymbol
```

它不是额外功能，而是让“未定义名字”的检查有一个正确的基线：**只有查不到用户符号、也查不到内建符号时，才是真的未定义。**

## 2. 2026-10-21 16:06 — ok，那我应该写一些什么样的代码来定义它们呢呢？请你不要直接该我的文件

**你**

ok，那我应该写一些什么样的代码来定义它们呢呢？请你不要直接该我的文件

**Copilot**

可以。你只需要在 `Symbols.kt` 里补两种“没有 AST 声明”的符号，然后在 `SemanticAnalyzer` 里预注册。下面给你两个版本，先写简单版就够了。

#### 版本一：最小可用（建议先写这个）

在 `Symbols.kt` 里，和现有 `StructSymbol` / `FunctionSymbol` 放在一起：

```kotlin
/** 编译器自带的类型名（i32、Box、Vec……）：没有对应的 AST 声明。 */
class BuiltinTypeSymbol(
    override val name: String,
) : Symbol

/** 编译器自带的函数（print_i32……）：没有对应的 FnNode。 */
class BuiltinFunctionSymbol(
    override val name: String,
) : Symbol
```

就这些。它们只回答一个问题：**“这个内建名字存在，并且属于哪个名字空间。”**

`Symbol` 是 `sealed interface`，所以新实现要放在同一个包/模块里；直接写在 `Symbols.kt` 最省事。

#### 版本二：加一个“可调用”标记（可选，但推荐）

后面 `CallExprNode` 要判断“被调者是不是函数”，用类型判断比字符串判断干净：

```kotlin
/** 值名字空间里可以出现在调用位置的符号。 */
sealed interface CallableSymbol : Symbol
```

然后把已有的：

```kotlin
class FunctionSymbol(...) : Symbol
```

改成：

```kotlin
class FunctionSymbol(...) : CallableSymbol
```

再让内建函数也实现它：

```kotlin
class BuiltinFunctionSymbol(
    override val name: String,
) : CallableSymbol
```

之后检查调用时就可以写：

```kotlin
if (calleeSymbol !is CallableSymbol) {
    throw SemanticError(span, "'$name' is not callable")
}
```

`LocalSymbol` / `ConstantSymbol` 自然不是 `CallableSymbol`，正好消灭 `rej-a-local-shadows-a-function-without-callable-fallback`。

如果你现在不想动 `FunctionSymbol`，也可以先不建这个接口，等写调用检查时再加，改动很小。

#### 然后在 `SemanticAnalyzer.kt` 里预注册

在 `analyze` 的最开头、收集用户声明之前调用：

```kotlin
private val BUILTIN_TYPE_NAMES = listOf(
    "i32", "u32", "isize", "usize", "bool", "Box", "Vec",
)

private val BUILTIN_VALUE_NAMES = listOf(
    "get_i32", "print_i32", "println_i32",
)

private fun installBuiltins(scope: Scope) {
    for (name in BUILTIN_TYPE_NAMES) {
        scope.declareType(BuiltinTypeSymbol(name))
    }
    for (name in BUILTIN_VALUE_NAMES) {
        scope.declareValue(BuiltinFunctionSymbol(name))
    }
}
```

调用顺序很重要：

```text
installBuiltins(globals)
    ↓
collectTopLevel(crate.items)   // 再收集用户声明
    ↓
check...
```

这样 `lookupType("i32")` 和 `lookupValue("println_i32")` 才有东西可查。

> `declareType` / `declareValue` 会返回 `Boolean`，这里可以忽略；预注册阶段不存在重名。

#### 使用时的两个典型位置

**类型解析：**

```kotlin
val symbol = globals.lookupType(name)
    ?: throw SemanticError(span, "unresolved type '$name'")

val type = when (symbol) {
    is BuiltinTypeSymbol -> builtinType(symbol.name, genericArgs)
    is StructSymbol -> StructType(symbol.name, symbol.node)
    else -> throw SemanticError(span, "unresolved type '$name'")
}
```

`builtinType` 就是把你 `Type.kt` 里的类型对应起来：

```text
"i32"  -> I32Type
"u32"  -> U32Type
"isize"-> IsizeType
"usize"-> UsizeType
"bool" -> BoolType
"Box"  -> BoxType(解析泛型实参)
"Vec"  -> VecType(解析泛型实参)
```

**值名解析：**

```kotlin
val symbol = currentScope.lookupValue(name)
    ?: throw SemanticError(span, "unresolved value '$name'")
```

`println_i32` 会命中 `BuiltinFunctionSymbol`，不会再被当成未定义名字。

#### 几个设计上的注意点

1. **不要让内建符号带 AST 节点**
   不要为了复用 `FunctionSymbol` 把 `node` 改成可空。内建符号本来就没有源码声明，单独建类更清晰，codegen 也能靠类型区分。

2. **`Box` / `Vec` 只进类型名字空间**
   它们不是值，不能直接 `Box(1)`。`Box::new` 是多段路径，属于后面的关联函数解析，不要在这里把 `Box` 注册成可调用值。

3. **`Self` 不注册**
   `Self` 依赖于当前结构体/impl 上下文，应该用 `currentSelfType` 解析，而不是放进 `globals`。

4. **内建符号 ≠ 保护名**
   这里只是让名字能被解析到。`protected-names` 目录才需要专门检查“用户不能声明 `u32` / `Box` / `get_i32` 等”。
   顺带一提：因为 `i32` 已经预注册，用户再写 `struct i32` 时 `declareType` 会返回 `false`，相当于自然报重名，方向是对的。

5. **这一步不会立刻改变测试结果**
   它只是基础设施；要让 `namespace-errors` 的 reject 开始减少，还需要后面的收集 pass 和名字解析。但先做这一步是安全的，不会破坏现有 69 条 accept。

建议动手顺序：先只加 `BuiltinTypeSymbol` + `BuiltinFunctionSymbol` 两个类，再加 `installBuiltins`，最后跑一次 `make compiler` 确认编译通过。

## 3. 2026-10-21 16:17 — 好的，那现在是不是应该在某一个地方调用这个installBuiltins这个函数

**你**

好的，那现在是不是应该在某一个地方调用这个installBuiltins这个函数

**Copilot**

对，需要在某个地方调用。调用点就是 `SemanticAnalyzer.analyze` 的**最开头**，因为：

- `Main.kt` 里语义阶段的入口只有 `SemanticAnalyzer().analyze(root)`；
- `analyze` 是这次分析的“生命周期起点”，内建环境必须早于用户声明收集。

#### 应该写成的顺序

```kotlin
fun analyze(crate: CrateNode) {
    installBuiltins(globals)

    // 下一步再写：
    // collectTopLevel(crate.items)
    // checkTopLevel(crate.items)
}
```

顺序不能反：

```text
installBuiltins(globals)     // 先有 i32 / Box / Vec / print_i32 ...
        ↓
collectTopLevel(...)         // 再收集用户 struct / fn / const
        ↓
check...                     // 最后做重名、类型名、名字解析检查
```

如果先收集用户声明再装内建，会出现两个问题：

1. 用户写的 `struct i32` 会先占住类型名字空间，内建 `i32` 反而装不进去；
2. 后续 `lookupType("i32")` 的行为取决于调用顺序，很难排查。

#### 为什么不用 `init { }`

写成这样也能跑：

```kotlin
init {
    installBuiltins(globals)
}
```

但更推荐放在 `analyze` 里，原因是：

- `SemanticAnalyzer` 的职责是“分析一个 crate”，内建环境属于这次分析的准备阶段；
- `init` 会把“构造对象”和“准备语义环境”混在一起；
- 将来如果要做多次分析或测试复用，放在 `analyze` 里更容易看出生命周期。

现阶段 `Main.kt` 每次运行都会 `new SemanticAnalyzer()`，所以两种写法结果一样，但 `analyze` 开头更直观。

#### 调用后的可见效果

这一步本身**不会改变任何测试结果**：现在还没有任何检查会去查 `lookupType` / `lookupValue`，所以 69 条 accept 仍然是 69 条 accept，167 条 reject 仍然全被误收。

它只是把“户口本”准备好：

```text
globals
├── 类型：i32, u32, isize, usize, bool, Box, Vec
└── 值：  get_i32, print_i32, println_i32
```

等你写收集 pass 和名字解析时，`i32`、`println_i32` 就不会再被误报为未定义。

#### 建议的验证方式

写完调用后：

```text
make compiler
./scripts/rxc --stage semantic tests/official/semantic/namespace-errors/acc-named-field-struct-and-function-may-share-a-spelling.rx
```

能正常 exit 0 就说明接线没问题。接下来再写 `collectTopLevel`，才真正开始消灭 reject。

## 4. 2026-10-21 16:25 — 接下来进行下一步，请你先告诉我准备些什么，起到什么作用，用教学的方式告诉我

**你**

接下来进行下一步，请你先告诉我准备些什么，起到什么作用，用教学的方式告诉我

**Copilot**

现在 `analyze` 里只剩这行注释：

```kotlin
// WP1: collect(crate.items)
```

下一步就是把它变成**顶层收集 pass（collection pass）**。我先讲清楚它是什么、为什么需要、要准备哪些东西，你再动手写。

#### 一、为什么需要“先收集，后检查”

假设有这样一个合法程序：

```rust
fn main() {
    let s = S { x: 1 };
}

struct S {
    x: i32,
}
```

`main` 在 `S` 声明之前就用了 `S`，但规范明确说：**顶层查找与声明顺序无关**。所以你不能一边遍历一边检查，否则走到 `main` 时 `S` 还没进符号表，就会误报“未定义”。

因此语义分析天然是两遍：

```text
第一遍：收集（collection）
    只回答“这个 crate 里有哪些顶层名字？”
    把 struct / fn / const 登记进 globals

第二遍：检查（checking）
    才回答“每个名字用得对不对？”
    解析类型、查重名、查函数体、查表达式
```

可以类比成图书馆：

- **收集** = 先给所有书编目录；
- **检查** = 读者来借书时，再按目录查“这本书存不存在、能不能借”。

收集阶段**不做类型检查、不看函数体**，只建目录。

#### 二、这一阶段要收集什么、不收集什么

| AST 节点 | 登记到哪 | 用什么符号 | 说明 |
| --- | --- | --- | --- |
| `StructNode` | `globals` 类型名字空间 | `StructSymbol` | 结构体名是类型 |
| `FnNode`（顶层） | `globals` 值名字空间 | `FunctionSymbol` | 函数是值 |
| `ConstNode` | `globals` 值名字空间 | `ConstantSymbol` | 常量也是值 |
| `ImplNode` | **不登记到 globals** | — | 方法/关联常量属于结构体自己的关联名字空间 |
| `StructField` | **不进 globals** | — | 字段属于该结构体自己的字段名字空间 |
| `FnParam` | **不进 globals** | — | 参数属于该函数自己的局部名字空间 |
| `UseNode` | 忽略 | — | AST builder 已经整条丢弃，不会出现 |
| 内建名字 | 已由 `installBuiltins` 装好 | `BuiltinTypeSymbol` / `BuiltinFunctionSymbol` | 不用重复处理 |

这里最重要的两个概念是：

1. **两个全局名字空间**：类型 / 值。  
   `struct Same` 和 `fn Same` 能共存，就是因为一个进 `declareType`，一个进 `declareValue`。
2. **名字空间是有层级的**：  
   - 顶层名字 → `globals`
   - 结构体字段 → 那个结构体自己的命名空间
   - 函数参数 → 那个函数自己的命名空间  
   所以 `struct A { x: i32 }` 和 `struct B { x: i32 }` 不会冲突，`fn f(x: i32)` 和 `fn g(x: i32)` 也不会冲突。

#### 三、建议你准备哪些函数

在 `SemanticAnalyzer` 里加这几个函数（先写骨架，不急着全实现）：

```kotlin
fun analyze(crate: CrateNode) {
    installBuiltins(globals)

    collectTopLevel(crate.items)    // 第一遍：只建目录
    checkDeclarations(crate.items)  // 第二遍：声明层面的检查
}

private fun collectTopLevel(items: List<ItemNode>) {
    for (item in items) collectItem(item)
}

private fun collectItem(item: ItemNode) {
    when (item) {
        is StructNode -> { /* declareType(StructSymbol(...)) */ }
        is FnNode     -> { /* declareValue(FunctionSymbol(...)) */ }
        is ConstNode  -> { /* declareValue(ConstantSymbol(...)) */ }
        is ImplNode   -> { /* 这一小步先什么都不做 */ }
        is UseNode    -> { /* 理论上不会出现，忽略 */ }
    }
}

private fun checkDeclarations(items: List<ItemNode>) {
    for (item in items) {
        when (item) {
            is StructNode -> { /* 字段重名检查 */ }
            is FnNode     -> { /* 参数重名检查 */ }
            is ConstNode  -> { /* 暂不检查 */ }
            is ImplNode   -> { /* 下一步处理 */ }
            is UseNode    -> {}
        }
    }
}
```

职责划分：

- `collectTopLevel` / `collectItem`：**只负责登记全局名字 + 检测全局重名**；
- `checkDeclarations`：**负责声明自身内部的检查**（字段重名、参数重名；下一步还会放“顶层函数不能有 receiver”和签名类型解析）。

这样拆的好处是：收集 pass 保持很纯粹，后面加 impl、加签名检查时不会把“建目录”和“查目录”混在一起。

#### 四、重名检查分三个层次，不要混

| 层次 | 检查对象 | 用什么结构 | 用例 |
| --- | --- | --- | --- |
| 全局 | `struct` / `fn` / `const` | `globals.declareType` / `declareValue` 返回 `false` | duplicate-struct、duplicate-function、duplicate-constant、function-and-constant-share-the-value-namespace |
| 结构体内部 | 字段名 | 一个局部 `HashSet<String>` | duplicate-field |
| 函数内部 | 参数名 | 一个局部 `HashSet<String>` | duplicate-parameter |

关键写法：**`declareXxx` 返回 `false` 时必须抛 `SemanticError`，不能忽略返回值。**

例如收集 struct：

```kotlin
is StructNode -> {
    if (!globals.declareType(StructSymbol(item.name, item))) {
        throw SemanticError(item.span, "duplicate struct '${item.name}'")
    }
}
```

收集 fn / const 时都走 `declareValue`，所以：

- `fn f` 重复 → 第二次 `declareValue` 返回 false；
- `const f` 与 `fn f` 冲突 → 也是第二次 `declareValue` 返回 false；
- 而 `struct f` 与 `fn f` 不冲突 → 因为一个走 `declareType`，一个走 `declareValue`。

这就是“函数和常量共享值名字空间”这条规则在代码里的自然体现。

#### 五、字段和参数为什么不用 `Scope`

`Scope` 是用来做**名字查找**的；字段和参数在这一阶段只需要“查重”，不需要被谁 lookup。

- 字段：以后通过 `receiver.field` 查，不是全局 lookup；
- 参数：以后进入函数体时，会另外建一个函数作用域，把它们声明进去。

所以现在用一个局部 `HashSet` 就够：

```kotlin
val seen = HashSet<String>()
for (field in node.fields) {
    if (!seen.add(field.name)) {
        throw SemanticError(node.span, "duplicate field '${field.name}'")
    }
}
```

参数同理：

```kotlin
val seen = HashSet<String>()
for (param in node.params) {
    if (param is FnParam && !seen.add(param.name)) {
        throw SemanticError(node.span, "duplicate parameter '${param.name}'")
    }
}
```

注意 `SelfParam` 没有名字字段，直接跳过即可。

#### 六、错误与 span 的准备

- 统一抛 `SemanticError`，不要 `println`、不要 `exitProcess`，退出码由 `Main.kt` 处理；
- `StructField` / `FnParam` 目前**没有自己的 span**，所以字段/参数重名只能报在外层 `StructNode.span` / `FnNode.span` 上，这是可接受的；
- 消息措辞不限，但建议带上名字，方便调试。

#### 七、这一阶段能消灭哪些用例

写完收集 + 字段/参数查重后，`namespace-errors` 里应该能消掉 6 条：

- `rej-duplicate-top-level-function`
- `rej-duplicate-constant`
- `rej-function-and-constant-share-the-value-namespace`
- `rej-duplicate-struct`
- `rej-duplicate-field`
- `rej-duplicate-parameter`

还剩下的 9 条属于下一阶段：

- 签名类型解析（`unresolved-signature-type`）
- `Self` / `self` 上下文（`self-outside-*`）
- 顶层 receiver
- 函数体作用域与表达式名字解析

不要在这一步提前做这些，否则容易把 69 条 accept 弄挂。

#### 八、常见坑

- **不要把 `ImplNode` 里的方法递归登记进 `globals`**，否则方法名会变成全局函数，后面会出各种诡异冲突；
- **不要跨结构体共用字段 `HashSet`**，也不要把字段塞进 `Scope`；
- **不要跨函数共用参数 `HashSet`**；
- **不要用 `declareValue` 去声明 `let` 局部变量**：`let` 允许同块遮蔽，重复不是错误，后面要用允许覆盖的 `defineLocal`；
- **不要忽略 `declareValue` / `declareType` 的返回值**；
- **不要在收集阶段解析类型**，此时只保证名字都在表里。

#### 九、验收

```text
make compiler
./scripts/rxc --stage semantic tests/official/semantic/namespace-errors/rej-duplicate-struct.rx   # 应 exit 1
python3 scripts/check_semantic.py namespace-errors -v
python3 scripts/check_semantic.py
```

目标：

- `namespace-errors` 从 1/16 变成 7/16（accept + 6 条 reject）；
- 全量 accept 仍然 69/69；
- 其他目录暂时不要出现新的 accept 误报。

建议动手顺序：**先只写 `collectTopLevel` 处理 `StructNode` / `FnNode` / `ConstNode`，跑 4 条顶层重名用例；确认没问题后，再补字段和参数查重。**

## 5. 2026-10-21 17:01 — 这个局部的Hashset是写在哪里的？

**你**

这个局部的Hashset是写在哪里的？

## 6. 2026-10-21 17:03 — Try Again

**你**

Try Again

## 7. 2026-10-21 17:06 — Try Again

**你**

Try Again

## 8. 2026-10-21 17:16 — Try Again

**你**

Try Again

**Copilot**

这个 `HashSet` 应该写在**每个结构体/每个函数各自的检查函数内部**，也就是 `checkDeclarations` 所调用的辅助函数里。关键是：**每次调用都新建一个，不能做成类的字段**。

#### 推荐的位置

```text
SemanticAnalyzer
├── analyze
│    ├── installBuiltins
│    ├── collectTopLevel
│    └── checkDeclarations
│         ├── checkDuplicateFields(node: StructNode)      ← HashSet 在这里
│         └── checkDuplicateParameters(node: FnNode)      ← HashSet 在这里
```

具体写法：

```kotlin
private fun checkDeclarations(items: List<ItemNode>) {
    for (item in items) {
        when (item) {
            is StructNode -> checkDuplicateFields(item)
            is FnNode     -> checkDuplicateParameters(item)
            is ConstNode  -> Unit
            is ImplNode   -> Unit
            is UseNode    -> Unit
        }
    }
}

private fun checkDuplicateFields(node: StructNode) {
    val seen = HashSet<String>()          // 每个结构体一个，函数返回就丢弃

    for (field in node.fields) {
        if (!seen.add(field.name)) {
            throw SemanticError(node.span, "duplicate field '${field.name}'")
        }
    }
}

private fun checkDuplicateParameters(node: FnNode) {
    val seen = HashSet<String>()          // 每个函数一个，函数返回就丢弃

    for (param in node.params) {
        if (param is FnParam && !seen.add(param.name)) {
            throw SemanticError(node.span, "duplicate parameter '${param.name}'")
        }
    }
}
```

`seen.add(x)` 的返回值：

- `true`：这个元素之前不在集合里，添加成功；
- `false`：已经存在，说明重复。

所以 `if (!seen.add(name))` 就是“添加失败 = 重复”。

#### 为什么必须写在函数内部

因为 Kotlin 里局部变量的生命周期是**一次函数调用**：

```text
checkDuplicateFields(struct A)   → 新建 seen_A，检查完丢弃
checkDuplicateFields(struct B)   → 新建 seen_B，检查完丢弃
```

这样 `struct A { x: i32 }` 和 `struct B { x: i32 }` 不会互相干扰。

如果你写成类的字段：

```kotlin
private val seen = HashSet<String>()   // ❌ 错误位置
```

那么检查完 `struct A` 后，`x` 还留在 `seen` 里；再检查 `struct B` 时就会误报 `x` 重复。同理，参数也不能跨函数共用。

#### 也可以直接写在 `when` 分支里

如果暂时不想拆辅助函数，这样也对：

```kotlin
private fun checkDeclarations(items: List<ItemNode>) {
    for (item in items) {
        when (item) {
            is StructNode -> {
                val seen = HashSet<String>()
                for (field in item.fields) {
                    if (!seen.add(field.name)) {
                        throw SemanticError(item.span, "duplicate field '${field.name}'")
                    }
                }
            }
            is FnNode -> {
                val seen = HashSet<String>()
                for (param in item.params) {
                    if (param is FnParam && !seen.add(param.name)) {
                        throw SemanticError(item.span, "duplicate parameter '${param.name}'")
                    }
                }
            }
            is ConstNode -> Unit
            is ImplNode  -> Unit
            is UseNode   -> Unit
        }
    }
}
```

两种写法语义完全一样。拆成辅助函数的好处是：`checkDeclarations` 后面还要加“顶层函数不能有 receiver”“解析签名类型”等检查，函数多了以后不会挤在一个 `when` 里。

#### 不要放的位置

| 位置 | 为什么不行 |
| --- | --- |
| `SemanticAnalyzer` 的字段 | 生命周期太长，会跨结构体/函数污染 |
| `globals` 的 `Scope` | 字段和参数不是全局名字，只是各自 owner 内部的名字 |
| `collectTopLevel` 里共用一个 | 收集阶段按顶层项循环，同样会跨结构体/函数 |
| 字段和参数共用同一个 `HashSet` | 它们是两个不同的名字空间，互不相干 |

#### 需要补的 import

你现在文件顶部只有：

```kotlin
import rx.ast.CrateNode
```

写这些函数还需要：

```kotlin
import rx.ast.ConstNode
import rx.ast.FnNode
import rx.ast.FnParam
import rx.ast.ImplNode
import rx.ast.ItemNode
import rx.ast.StructNode
import rx.ast.UseNode
```

`HashSet` 是 Kotlin 标准库的，不需要 import。

#### 一句话总结

`HashSet` 是**“某个结构体/某个函数自己的字段/参数表”**，所以它应该出现在处理那个节点的函数体内部，而不是类字段或全局作用域里。

## 9. 2026-10-21 17:29 — 我刚刚完成了上一步，请你先检查是否有问题。有一个问题：现在这个Scope是不是少…

**你**

我刚刚完成了上一步，请你先检查是否有问题。有一个问题：现在这个Scope是不是少了一个declareValue的问题，因为value似乎可能被声明，是现在修改还是什么时候修改，接下来一步做什么

**Copilot**

我跑了两组测试，结果如下：

```text
namespace-errors:  accept 1/1, reject 9/15   → 10/16
full semantic:     accept 69/69, reject 10/167
```

所以 **accept 没有回归**，问题只在于 `namespace-errors` 还是原来的 10/16。

#### 根因：`checkFunctionBody` 根本没有被调用

你已经写好了 `checkFunctionBody`，但 `checkDeclarations` 里仍然是：

```kotlin
is FnNode -> checkTopLevelFunction(item)
```

`checkTopLevelFunction` 只检查参数重名、receiver 和签名类型，**不会进入函数体**。`checkFunctionBody` 目前是死代码，所以：

- `let x = x;`
- 块局部变量外逃
- `f()` 被局部变量遮蔽
- `absent`
- `self`

这些函数体里的错误都还没被检查到。

#### 修复：在 `checkDeclarations` 里补一行调用

```kotlin
is FnNode -> {
    checkTopLevelFunction(item)
    checkFunctionBody(item)
}
```

顺序保持这样：

```text
先检查声明头
    ↓
再检查函数体
```

声明头有错时先报，不会进入函数体。

#### 另外一个小问题

`checkStructFields` 里的错误消息写成了：

```kotlin
"duplicate parameter '${field.name}'"
```

应该是：

```kotlin
"duplicate field '${field.name}'"
```

这个不影响退出码，但诊断信息会误导人。

#### 补上后的预期

```text
make compiler
python3 scripts/check_semantic.py namespace-errors -v
```

应该看到：

```text
accept: 1/1 passed
reject: 15/15 properly rejected
```

也就是 **16/16**。

然后跑全量：

```text
python3 scripts/check_semantic.py
```

预期：

- accept 仍然 **69/69**；
- reject 会从 10 增加，至少到 16/167；
- 其他目录的 reject 也可能顺带增加，因为函数体检查开始生效了。

如果补上调用后 `namespace-errors` 还有个别不通过，把具体用例和诊断贴出来，我再帮你定位。

## 10. 2026-10-24 14:47 — 接下来写：invalid-impls-and-generics，它解决了哪些错误…

**你**

接下来写：invalid-impls-and-generics，它解决了哪些错误，总体上应该如何实现

## 11. 2026-10-24 14:47 — Try Again

**你**

Try Again

## 12. 2026-10-24 16:49 — Try Again

**你**

Try Again

**Copilot**

`Vec` 在 Rx 里**确实是内置类型**，只是它不是“原始类型”，而是“内置容器类型”。

#### 1. 先看它有没有用户声明

你的符号表里，`StructSymbol` 代表：

```rust
struct S { ... }
```

它必须对应一个 `StructNode`，有字段、有声明位置、是名义类型。

但 `Vec` 在任何测试文件里都没有：

```rust
struct Vec { ... }   // 没有这种声明
```

而所有文件都在直接使用：

```rust
let v: Vec<i32> = Vec::<i32>::new();
```

如果 `Vec` 不是编译器内置的，`lookupType("Vec")` 就永远找不到它，所有 accept 都会挂。  
`use rx::core::*;` 也不能帮忙：规范规定 `use` 整条丢弃，不参与名字解析。

所以 `Vec` 必须由编译器预先放进类型名字空间。

#### 2. 规范里 `Vec` 的身份

规范有三处直接说明：

##### `types.html`

支持的类型表里写着：

```text
Box<T>, Vec<T>   Owned heap object or dynamic sequence
```

它们是语言支持的类型，不是某个用户结构体。

##### `names.html`

受保护的类型名包括：

```text
i32, u32, isize, usize, bool, Box, Vec, Copy, Clone, PartialEq, Eq
```

`Vec` 和 `i32` 一样，都是编译器保留的名字。

##### `paths.html`

```text
Box::<T>::new
Vec::<T>::new
```

这些是**内置关联操作**，不是用户定义的方法。

##### `implementations.html`

inherent impl 只能作用于用户定义的 named-field struct。  
所以 `impl Vec { ... }` 是非法的，这也说明 `Vec` 不是 `StructSymbol`。

#### 3. 所以 `BuiltinTypeSymbol` 不是“原始类型”的意思

这里的 `BuiltinType` 应该理解成：

> **编译器内置的类型名**

它包含两类：

| 类别 | 例子 | 语义类型 |
| --- | --- | --- |
| 原始类型 | `i32`、`u32`、`bool` | `I32Type`、`U32Type`、`BoolType` |
| 内置容器 | `Box`、`Vec` | `BoxType`、`VecType` |

它们都没有 `StructNode`，所以不能放进 `StructSymbol`。

用户结构体则是另一类：

| 符号 | 有 AST 声明吗 | 类型种类 |
| --- | --- | --- |
| `BuiltinTypeSymbol` | 没有 | 由编译器固定解释 |
| `StructSymbol` | 有 `StructNode` | 名义类型 `StructType` |

所以分类是：

```text
类型名字空间
├── BuiltinTypeSymbol
│   ├── 原始类型：i32 / u32 / isize / usize / bool
│   └── 内置容器：Box / Vec
└── StructSymbol
    └── 用户 struct
```

#### 4. 为什么不能把 `Vec` 当 `StructSymbol`

即使 `Vec` 在概念上“像一个结构体”，它和用户 struct 有本质区别：

| 特性 | 用户 struct | `Vec` |
| --- | --- | --- |
| AST 里有 `StructNode` | 有 | 没有 |
| 字段可访问 | `s.x` | 不存在用户字段 |
| 类型相等规则 | 名义类型（按名字） | 结构类型（按元素类型） |
| 泛型实参 | 不允许类型参数 | 必须恰好一个类型参数 |
| inherent impl | 允许 | 禁止 |
| 构造方式 | `S { ... }` | `Vec::<T>::new()` |
| 方法 | 用户定义 | 编译器内置方法 |
| 名字保护 | 不保护 | 受保护 |

如果强行把 `Vec` 建成 `StructSymbol`，你就需要：

- 伪造一个 `StructNode`；
- 处理它没有字段的事实；
- 阻止用户 `impl Vec`；
- 特殊处理 `Vec<T>` 的元数和 `VecType`；
- 区分用户方法和内置方法。

反而更麻烦。给它一个独立的 `BuiltinTypeSymbol` 更符合它的本质。

#### 5. 建议：让 `BuiltinTypeSymbol` 带上 kind

现在你的 `BuiltinTypeSymbol` 只有名字，检查 `Box`/`Vec` 时只能靠字符串比较：

```kotlin
if (name == "Box" || name == "Vec") { ... }
```

更好的做法是加一个枚举：

```kotlin
enum class BuiltinTypeKind {
    I32, U32, ISIZE, USIZE, BOOL,
    BOX, VEC,
}

class BuiltinTypeSymbol(
    override val name: String,
    val kind: BuiltinTypeKind,
) : Symbol
```

这样 `checkPathType` 可以写成：

```kotlin
when (symbol.kind) {
    BuiltinTypeKind.BOX,
    BuiltinTypeKind.VEC -> {
        if (segment.genericArgs.size != 1) { ... }
    }
    else -> {
        if (segment.genericArgs.isNotEmpty()) { ... }
    }
}
```

以后生成类型时也方便：

```text
BuiltinTypeKind.I32  → I32Type
BuiltinTypeKind.BOOL → BoolType
BuiltinTypeKind.BOX  → BoxType(element)
BuiltinTypeKind.VEC  → VecType(element)
```

#### 6. 一句话总结

`Vec` 不是用户结构体，而是**语言内置的容器类型**：

- 没有 `StructNode`；
- 由编译器预注册；
- 有专门的泛型规则和内置方法；
- 不能用户实现；
- 语义类型是 `VecType`，不是 `StructType`。

所以它归入 `BuiltinTypeSymbol` 是对的。更准确地说，`BuiltinTypeSymbol` 应该表示“编译器内置的类型名”，再用 `kind` 区分它是原始类型还是容器。

## 13. 2026-10-24 19:29 — 请你回答我一下，这个check

**你**

请你回答我一下，这个check

## 14. 2026-10-24 19:33 — 请你回答我一下，这个checkImpl中的checkTypeRef是对于添加方法…

**你**

请你回答我一下，这个checkImpl中的checkTypeRef是对于添加方法的对象（包括结构体等）进行类型检查吗，请你举一个简单的例子，说明这个数据经过了哪些函数，完成这个检查过程，后续为什么又取了target.segments的第一个作为name是意思是一定要检查第一个是否是一个struct对吗

**Copilot**

这两个问题是同一件事的两个层面，拆开看就清楚了：

```text
checkTypeRef(target)  = 这个类型写法本身合法吗？
后续的 StructSymbol 检查 = 这个合法类型能作为 impl 目标吗？
```

`checkTypeRef` **不负责**判断“目标必须是结构体”，它只负责检查类型名、泛型实参和 `Self` 上下文。`impl i32` 和 `impl Box<i32>` 都能通过 `checkTypeRef`，因为 `i32` 和 `Box<i32>` 都是合法类型；但它们不能作为 inherent impl 的目标，这由后面的 `StructSymbol` 检查负责。

#### 一、用 `impl Box<i32> { ... }` 走一遍

源码：

```rust
impl Box<i32> {
    fn f(&self) {}
}
```

##### 1. AstBuilder 产出的 AST

```text
ImplNode(
    target = PathTypeNode(
        segments = [
            PathSegment(
                name = "Box",
                genericArgs = [
                    PathTypeNode(
                        segments = [PathSegment("i32")]
                    )
                ]
            )
        ]
    ),
    items = [FnNode(...)]
)
```

注意：`ImplNode.target` 是 `TypeRefNode`，不是已经解析好的符号。

##### 2. `checkDeclarations` 分派到 `checkImpl`

```text
checkDeclarations
    └── is ImplNode -> checkImpl(node)
```

##### 3. `checkImpl` 先检查 target 的形状

```kotlin
if (target !is PathTypeNode) {
    throw SemanticError(target.span, "impl target must be a user-defined struct")
}
```

这一步拒绝：

```text
impl &S        -> RefTypeNode
impl [i32; 2]  -> ArrayTypeNode
impl ()        -> UnitTypeNode
```

它们连 `segments` 都没有，没法继续做路径解析。

##### 4. 调用 `checkTypeRef(target)`

```text
checkImpl
    └── checkTypeRef(PathTypeNode("Box"))
            └── checkPathType(ref)
```

`checkPathType` 做的事情：

```text
name = "Box"
globals.lookupType("Box")
    -> BuiltinTypeSymbol(kind = BOX)

genericArgs.size == 1 ?
    -> 是，合法

递归检查实参：
    checkTypeRef(PathTypeNode("i32"))
        -> checkPathType("i32")
        -> BuiltinTypeSymbol(kind = I32)
        -> genericArgs 为空，合法
```

到这里，`Box<i32>` 被确认是**一个合法的类型写法**。  
但注意：`checkTypeRef` 并没有说“Box 可以作为 impl 目标”。

##### 5. 回到 `checkImpl`，做目标种类检查

```kotlin
val name = target.segments.first().name   // "Box"

val symbol = globals.lookupType(name)
if (symbol !is StructSymbol) {
    throw SemanticError(target.span, "impl target must be a user-defined struct")
}
```

此时：

```text
symbol = BuiltinTypeSymbol("Box")
symbol !is StructSymbol
    -> 抛 SemanticError
```

所以 `impl Box<i32>` 最终被拒绝，但拒绝它的是**目标种类检查**，不是 `checkTypeRef`。

#### 二、`impl S` 的流程

源码：

```rust
impl (S) {
    fn f(&self) {}
}
```

AstBuilder 会把括号拆掉，target 变成：

```text
PathTypeNode(
    segments = [PathSegment("S", genericArgs = [])]
)
```

流程：

```text
checkImpl
    ├── target is PathTypeNode                 ✓
    ├── checkTypeRef(target)
    │      └── checkPathType("S")
    │             ├── lookupType("S") -> StructSymbol
    │             └── genericArgs 为空          ✓
    └── lookupType("S") -> StructSymbol
           └── symbol is StructSymbol           ✓
```

所以 accept。

#### 三、`impl i32` 的流程

```text
checkImpl
    ├── target is PathTypeNode                 ✓
    ├── checkTypeRef(target)
    │      └── checkPathType("i32")
    │             ├── lookupType("i32") -> BuiltinTypeSymbol(I32)
    │             └── genericArgs 为空          ✓
    └── lookupType("i32") -> BuiltinTypeSymbol
           └── symbol !is StructSymbol          ✗ 报错
```

这就是为什么两个检查必须分开：

- `i32` 是合法类型 → `checkTypeRef` 通过；
- `i32` 不是用户结构体 → impl 目标检查失败。

#### 四、`target.segments.first().name` 到底是什么意思

`PathTypeNode.segments` 是类型路径的段列表：

```text
S              -> [PathSegment("S")]
Box<i32>       -> [PathSegment("Box", [i32])]
i32            -> [PathSegment("i32")]
```

所以：

```kotlin
val name = target.segments.first().name
```

就是取路径的第一段名字：

- `S` → `"S"`
- `Box<i32>` → `"Box"`
- `i32` → `"i32"`

然后：

```kotlin
globals.lookupType(name)
```

用这个名字查类型名字空间，拿到对应的符号：

- `"S"` → `StructSymbol`
- `"Box"` → `BuiltinTypeSymbol`
- `"i32"` → `BuiltinTypeSymbol`

最后检查：

```kotlin
symbol !is StructSymbol
```

这不是“检查第一个段是不是 struct”，而是：

> **用第一段名字解析出的符号，必须是一个用户结构体。**

对于当前 Rx 语料，类型路径实际上都是单段：

```text
S
Box<i32>
i32
Self
```

所以 `first()` 够用。更严谨的写法是：

```kotlin
val segment = target.segments.singleOrNull()
    ?: throw SemanticError(target.span, "impl target must be a simple struct name")
val name = segment.name
```

这样就把“当前只支持单段类型路径”这个假设写清楚了。

如果以后支持 `module::S` 这种路径，那么真正的类型名通常是**最后一段**，应该用 `last()` 或完整路径解析；但在 Rx 里 `use` 被丢弃、没有模块路径，所以现在不需要。

#### 五、为什么查了两次 `lookupType`？

一次在 `checkTypeRef -> checkPathType` 里，一次在 `checkImpl` 里：

```text
checkTypeRef：确认名字存在、泛型实参合法
checkImpl：确认解析出来的符号是 StructSymbol
```

`checkTypeRef` 目前只返回 `Unit`，不返回符号，所以 `checkImpl` 只能再查一次。

两种改进方式：

1. 保持现状：多一次哈希查找，代码最简单；
2. 写一个返回符号的辅助函数，例如：

```kotlin
private fun resolveTypeSymbol(ref: PathTypeNode): Symbol? {
    ...
}
```

然后 `checkPathType` 和 `checkImpl` 共用它。等类型系统变大后再重构也不迟。

#### 六、一句话总结

- `checkTypeRef(target)`：检查 **target 这个类型写法本身**是否合法（名字存在、泛型个数正确、`Self` 上下文正确）。
- `target.segments.first().name` + `lookupType` + `is StructSymbol`：检查 **这个合法类型能不能作为 inherent impl 的目标**。
- `impl i32`、`impl Box<i32>` 能通过前者，但会被后者拒绝。
- `impl S`、`impl (S)` 两者都通过，所以 accept。

## 15. 2026-10-24 21:19 — 你现在看一下，现在完成了什么，还需要补充，修改什么内容

**你**

你现在看一下，现在完成了什么，还需要补充，修改什么内容

**Copilot**

我看了当前代码，也实际跑了构建。现在分两部分说：**已经完成的** 和 **还缺的**。

#### 一、现在编译不过

```text
SemanticAnalyzer.kt:285:31: error: no value passed for parameter 'kind'.
scope.declareType(BuiltinTypeSymbol(name))
```

原因是你把 `BuiltinTypeSymbol` 改成了：

```kotlin
class BuiltinTypeSymbol(
    override val name: String,
    val kind: BuiltinTypeKind,
) : Symbol
```

但 `installBuiltins` 还在用旧的构造方式。

需要把 `BUILTIN_TYPE_NAMES` 改成“名字 → kind”的映射：

```kotlin
private val BUILTIN_TYPES = mapOf(
    "i32"   to BuiltinTypeKind.I32,
    "u32"   to BuiltinTypeKind.U32,
    "isize" to BuiltinTypeKind.ISIZE,
    "usize" to BuiltinTypeKind.USIZE,
    "bool"  to BuiltinTypeKind.BOOL,
    "Box"   to BuiltinTypeKind.BOX,
    "Vec"   to BuiltinTypeKind.VEC,
)

private fun installBuiltins(scope: Scope) {
    for ((name, kind) in BUILTIN_TYPES) {
        scope.declareType(BuiltinTypeSymbol(name, kind))
    }
    for (name in BUILTIN_VALUE_NAMES) {
        scope.declareValue(BuiltinFunctionSymbol(name))
    }
}
```

这是当前唯一的编译错误。

#### 二、已经完成的部分

##### 1. 内建类型分类

`Symbols.kt` 已经有：

```kotlin
enum class BuiltinTypeKind {
    I32, U32, ISIZE, USIZE, BOOL, BOX, VEC,
}
```

并且 `BuiltinTypeSymbol` 带上了 `kind`，这样 `checkPathType` 能区分原生类型和容器。

##### 2. `checkPathType` 的泛型规则

已经实现：

- `Self`：必须有上下文，且不接受类型实参；
- `Box` / `Vec`：类型实参必须恰好 1 个；
- 原生类型：不允许类型实参；
- 用户结构体：不允许类型实参；
- 递归检查泛型实参里的类型名。

这部分应该能消灭：

- `rej-box-requires-exactly-one-concrete-type-argument`
- `rej-missing-concrete-box-type-argument`
- `rej-vec-requires-exactly-one-concrete-type-argument`
- `rej-primitives-take-no-type-arguments`
- `rej-structs-have-no-type-parameters`

##### 3. `checkImpl` 和接线

`checkDeclarations` 已经：

```kotlin
is ImplNode -> checkImpl(item)
```

`checkImpl` 已经做到：

```text
target 必须是 PathTypeNode
    ↓
checkTypeRef(target)     检查类型名和泛型实参
    ↓
lookupType(name)
    ↓
必须是 StructSymbol
```

这部分应该能消灭：

- `rej-a-reference-is-not-a-struct-impl-target`
- `rej-impl-target-must-be-a-user-struct`
- `rej-no-array-inherent-impl`
- `rej-no-container-inherent-impl`

并且 `acc-parenthesized-struct-impl` 能通过，因为括号类型在 AstBuilder 里已经被拆掉。

##### 4. 单段表达式路径的泛型检查

`checkPathExpr` 的单段分支已经：

```kotlin
val symbol = currentScope.lookupValue(name) ?: error
if (segment.genericArgs.isNotEmpty()) {
    throw SemanticError(node.span, "functions and values have no type parameters")
}
```

这应该能消灭：

- `rej-functions-have-no-type-parameters` 里的 `f::<i32>()`

#### 三、还缺的关键一项

##### `rej-method-segments-have-no-type-parameters` 还没处理

源码：

```rust
v.len::<i32>();
```

它的 AST 不是 `PathExprNode`，而是：

```text
MethodCallExprNode(
    receiver = PathExpr(v),
    name = PathSegment("len", genericArgs = [i32]),
    args = []
)
```

当前 `checkExpr` 的 `MethodCallExprNode` 分支只递归了 receiver 和 args，没有检查 `name.genericArgs`，所以这个用例仍会被接受。

需要补：

```kotlin
is MethodCallExprNode -> {
    if (expr.name.genericArgs.isNotEmpty()) {
        throw SemanticError(
            expr.span,
            "method segments have no type parameters",
        )
    }

    checkExpr(expr.receiver)
    for (arg in expr.args) checkExpr(arg)
}
```

补完这一条，`invalid-impls-and-generics` 预期就是 **12/12**。

#### 四、可以顺手补的健壮性问题（不影响本目录）

##### 1. 多段表达式路径的泛型检查

现在 `checkPathExpr` 的多段分支只检查第一段类型是否存在，没有处理：

```rust
S::<i32>::new()        // 结构体不能带类型实参
i32::<bool>::MAX       // 原生类型不能带类型实参
S::f::<i32>()          // 关联函数段不能带类型实参
Box::<i32, bool>::new()// 容器实参个数不对
```

本目录没有这些用例，但 `methods-and-self`、`invalid-impls` 以后可能会用到。建议在多段分支补：

```text
第一段：
    Box / Vec  → 最多一个（表达式位置 0 个属于 UB，可以不报）
    原生类型    → 必须为空
    Struct      → 必须为空
    Self        → 必须为空
后续段：
    关联函数/方法 → 必须为空
```

##### 2. `self::<i32>` 目前会漏

单段分支里 `self` 在检查泛型之前就 `return` 了。严格一点应该：

```kotlin
if (name == "self") {
    if (!currentHasSelfParam) { ... }
    if (segment.genericArgs.isNotEmpty()) {
        throw SemanticError(node.span, "`self` takes no type arguments")
    }
    return
}
```

##### 3. `checkImpl` 还没有处理关联项

现在只校验了 target，没有：

- 给 `StructSymbol` 建立关联项表；
- 检查同一个 struct 的多个 `impl` 块之间的关联项重名；
- 设置 `currentSelfType` 检查方法签名。

这些属于 WP1 剩余部分，`methods-and-self` 会用到；本目录不需要。

##### 4. `CastExprNode` 的 target 类型没检查

```kotlin
is CastExprNode -> checkExpr(expr.expr)
```

如果 cast 目标是 `Box<i32, bool>` 这类，目前不会检查泛型个数。可以补：

```kotlin
is CastExprNode -> {
    checkExpr(expr.expr)
    checkTypeRef(expr.targetType)
}
```

本目录没考，但和 `checkPathType` 的规则保持一致比较好。

##### 5. 结构体字段类型还没检查

`checkStructFields` 目前只查字段重名，没有对 `field.type` 调 `checkTypeRef`。  
如果以后加，要记得：

```text
检查 struct S 的字段时
    currentSelfType = S
否则 Vec<Self> 这种 accept 会被误杀
```

#### 五、当前进度判断

| 项目 | 状态 |
| --- | --- |
| `BuiltinTypeKind` | 已完成 |
| 类型位置泛型规则 | 已完成 |
| `checkImpl` target 校验 | 已完成 |
| 单段表达式泛型检查 | 已完成 |
| 方法段泛型检查 | **缺少** |
| 编译 | **失败**：`installBuiltins` 未传 kind |

#### 六、修复顺序

```text
1. 修 installBuiltins 的 BuiltinTypeSymbol(name, kind)
2. make compiler
3. 补 MethodCallExprNode.name.genericArgs 检查
4. python3 scripts/check_semantic.py invalid-impls-and-generics -v
5. 跑全量，确认 accept 69/69
```

预期结果：

```text
invalid-impls-and-generics: accept 1/1, reject 11/11  → 12/12
```

## 16. 2026-10-24 21:34 — 我觉得这个地方is MethodCallExprNode -> { if (ex…

**你**

我觉得这个地方is MethodCallExprNode -> {
                if (expr.name.genericArgs.isNotEmpty()) {
                    throw SemanticError(
                        expr.span,
                        "method segments have no type parameters",
                    )
                }

                checkExpr(expr.receiver)
                for (arg in expr.args) checkExpr(arg)
            }不太对吧，这里的逻辑是一旦methodcallexpr有泛型，那就直接报错，为什么这样呢

**Copilot**

你的疑问很合理：**“方法调用带泛型”本身并不一定非法**。真正被禁止的是“方法段带**类型实参**”。

关键点在 AST 里：

```text
v.len::<i32>()
```

会被建成：

```text
MethodCallExprNode(
    receiver = PathExpr(v),
    name = PathSegment(
        name = "len",
        genericArgs = [PathTypeNode("i32")]
    ),
    args = []
)
```

而 `PathSegment.genericArgs` 的类型是：

```kotlin
val genericArgs: List<TypeRefNode>
```

也就是说，**它里面只会放类型实参**。生命周期实参在 AstBuilder 的 `buildGenericArgs` 里已经被丢弃了：

```kotlin
for (argumentContext in ctx.genericArg()) {
    val typeContext = argumentContext.typeRef() ?: continue  // lifetime 直接跳过
    arguments.add(visit(typeContext) as TypeRefNode)
}
```

所以：

```text
genericArgs.isNotEmpty()
```

在当前 AST 里的准确含义是：

> **这个方法段带了至少一个类型实参。**

而 Rx 的方法只有生命周期参数，没有类型参数。类型实参没有对应的声明参数可以绑定，所以非法。

#### 几种写法对照

| 写法 | AST 里的 `genericArgs` | 结果 |
| --- | --- | --- |
| `v.len()` | `[]` | 合法 |
| `v.len::<'a>()` | `[]`（生命周期被丢弃） | 合法（生命周期合法性属 UB，不检查） |
| `v.len::<i32>()` | `[i32]` | 非法：方法没有类型参数 |
| `v.len::<'a, i32>()` | `[i32]` | 非法：剩下的类型实参非法 |

所以这个检查不是“只要写了 `::<...>` 就报错”，而是：

> **生命周期实参已经被消掉；如果还能剩下东西，那一定是类型实参，而方法没有类型参数。**

#### 为什么不在语法层就拒绝

文法里的方法调用段复用了 `pathExprSegment`：

```text
MethodCallExpression → Expression . PathExprSegment CallArguments
PathExprSegment → PathIdentSegment ( :: GenericArgs )?
```

文法允许 `::<...>` 出现在方法段上，是因为同一个规则也用于函数路径，而函数/方法都允许生命周期实参。  
但“允许这种语法”不等于“允许所有实参”：语义阶段负责拒绝其中的类型实参。

#### 和函数、结构体的规则对照

| 写法 | 属于哪种路径 | 规则 |
| --- | --- | --- |
| `f::<i32>()` | 单段表达式路径 / 函数 | 函数没有类型参数 → 拒绝 |
| `S::<i32>::new()` | 多段表达式路径 / 结构体 | 结构体没有类型参数 → 拒绝 |
| `v.len::<i32>()` | 方法段 | 方法没有类型参数 → 拒绝 |
| `Box::<i32>::new()` | 多段表达式路径 / 内置容器 | 恰好一个类型实参 → 接受 |

它们本质上都是同一条规则的三个位置：

> **Rx 没有泛型类型参数；只有生命周期参数。生命周期实参在 AST 里已被丢弃，所以任何剩余的 `genericArgs` 都是非法的类型实参。**

#### 如果以后 AST 改成保留生命周期实参

那这个检查就要写得更精确，例如：

```kotlin
if (expr.name.genericArgs.any { it is TypeRefNode }) {
    throw SemanticError(expr.span, "method segments have no type parameters")
}
```

但现在 `genericArgs` 的元素类型就是 `TypeRefNode`，所以：

```kotlin
if (expr.name.genericArgs.isNotEmpty()) { ... }
```

已经等价于“存在类型实参”，是正确且最简单的写法。

#### 一句话总结

这个 `if` 检查的不是“方法调用有没有泛型语法”，而是：

> **生命周期实参已被丢弃；如果方法段还剩下泛型实参，那它们只能是类型实参，而方法和函数一样没有类型参数，所以必须报错。**

## 17. 2026-10-24 22:19 — 多段表达式路径的泛型检查要怎么写

**你**

多段表达式路径的泛型检查要怎么写

**Copilot**

多段表达式路径要分两类段来看：

```text
Box::<i32>::new
│       │      │
│       │      └── 第二段：关联函数 new
│       └───────── 第一段的类型实参
└───────────────── 第一段：类型名 Box

Config::N
│       └── 第二段：关联常量 N
└────────── 第一段：结构体 Config
```

规则是：

| 段 | 是什么 | 泛型实参规则 |
| --- | --- | --- |
| 第一段 | 类型名（`Box`/`Vec`/结构体/原生类型/`Self`） | 按这个类型本身的规则检查 |
| 第二段及以后 | 关联函数、关联常量、方法 | **不能有类型实参** |

原因是 Rx 里函数、方法、关联项都只有生命周期参数，没有类型参数。生命周期实参在 AST 构建时已经被丢弃，所以剩下的 `genericArgs` 全是类型实参。

#### 一、第一段的规则

| 第一段 | 类型实参 |
| --- | --- |
| `Box` / `Vec` | 最多一个；表达式位置允许 0 个（`Box::new` 缺类型实参是 UB，不要求报），多于一个报错 |
| `i32` 等原生类型 | 必须为空 |
| 用户结构体 | 必须为空（生命周期实参已被丢弃） |
| `Self` | 必须为空，且 `currentSelfType != null` |
| 找不到的名字 | 报未解析路径 |

#### 二、第二段及以后的规则

```text
S::<i32>::new       // 第一段 S 带了类型实参 → 报错
S::f::<i32>()       // 第二段 f 带了类型实参 → 报错
Box::<i32>::new     // 第一段一个、第二段空 → 合法
Config::N           // 第一段空、第二段空 → 合法
Self::START         // Self 空、START 空 → 合法
```

#### 三、代码写法

建议把 `checkPathExpr` 拆成“单段”和“多段”两个函数，避免逻辑挤在一起：

```kotlin
private fun checkPathExpr(node: PathExprNode) {
    val first = node.segments.firstOrNull()
        ?: throw SemanticError(node.span, "empty path")

    if (node.segments.size == 1) {
        checkSingleSegmentValuePath(node, first)
        return
    }

    checkMultiSegmentPath(node)
}
```

##### 单段值路径

```kotlin
private fun checkSingleSegmentValuePath(
    node: PathExprNode,
    segment: PathSegment,
) {
    val name = segment.name

    if (name == "self") {
        if (!currentHasSelfParam) {
            throw SemanticError(node.span, "`self` is only allowed inside a method")
        }
        if (segment.genericArgs.isNotEmpty()) {
            throw SemanticError(node.span, "`self` takes no type arguments")
        }
        return
    }

    currentScope.lookupValue(name)
        ?: throw SemanticError(node.span, "unresolved value '$name'")

    if (segment.genericArgs.isNotEmpty()) {
        throw SemanticError(
            node.span,
            "functions and values have no type parameters",
        )
    }
}
```

##### 多段路径

```kotlin
private fun checkMultiSegmentPath(node: PathExprNode) {
    val first = node.segments.first()

    if (first.name == "Self") {
        if (currentSelfType == null) {
            throw SemanticError(node.span, "`Self` is not allowed here")
        }
        if (first.genericArgs.isNotEmpty()) {
            throw SemanticError(node.span, "`Self` takes no type arguments")
        }
    } else {
        val symbol = currentScope.lookupType(first.name)
            ?: throw SemanticError(
                node.span,
                "unresolved path '${first.name}'",
            )

        when (symbol) {
            is BuiltinTypeSymbol -> when (symbol.kind) {
                BuiltinTypeKind.BOX,
                BuiltinTypeKind.VEC -> {
                    // 表达式位置：Box::new / Vec::new 缺少类型实参是 UB，
                    // 规范不要求报错；但多于一个一定是错误。
                    if (first.genericArgs.size > 1) {
                        throw SemanticError(
                            node.span,
                            "'${first.name}' takes at most one type argument here",
                        )
                    }
                }

                else -> {
                    if (first.genericArgs.isNotEmpty()) {
                        throw SemanticError(
                            node.span,
                            "primitive type '${first.name}' takes no type arguments",
                        )
                    }
                }
            }

            is StructSymbol -> {
                if (first.genericArgs.isNotEmpty()) {
                    throw SemanticError(
                        node.span,
                        "struct '${first.name}' has no type parameters",
                    )
                }
            }

            else -> throw SemanticError(
                node.span,
                "unresolved path '${first.name}'",
            )
        }
    }

    // 第二段及以后：关联函数/常量/方法都没有类型参数
    for (segment in node.segments.drop(1)) {
        if (segment.genericArgs.isNotEmpty()) {
            throw SemanticError(
                node.span,
                "associated items have no type parameters",
            )
        }
    }

    // 泛型实参里的类型名仍然要递归检查
    for (segment in node.segments) {
        for (arg in segment.genericArgs) {
            checkTypeRef(arg)
        }
    }
}
```

#### 四、为什么第一段和第二段规则不同

##### 第一段是类型

```text
Box::<i32>::new
↑
这是类型 Box，i32 是它的类型实参
```

所以第一段的 `genericArgs` 是“这个类型的实参”，允许 `Box`/`Vec` 有一个。

##### 第二段是关联项

```text
Box::<i32>::new
         ↑
    这是关联函数 new 的方法名
```

`new`、`clone`、`START` 这些关联项都没有类型参数，所以第二段一旦有 `genericArgs` 就是错的。

#### 五、几个边界

##### 1. `Box::new` 缺类型实参

```rust
let b = Box::new(1);   // 规范说这是 UB，不要求报错
```

所以表达式位置第一段 `Box`/`Vec` 允许 0 个类型实参，只拒绝“多于一个”。

而在**类型位置**：

```rust
let b: Box = ...;      // 编译错误，测试要求拒绝
```

`checkPathType` 里仍然要求恰好一个。这两个位置规则不同。

##### 2. `Self` 是特殊情况

`Self` 不在全局类型表里，所以不能走 `lookupType`：

```kotlin
if (first.name == "Self") {
    // 看 currentSelfType
}
```

顶层函数体里 `currentSelfType == null`，所以 `Self::START` 会被拒绝；将来检查 impl 方法体时，设置 `currentSelfType` 后就能通过。

##### 3. 多段路径不检查成员是否存在

例如：

```rust
Config::N
Node::leaf
```

现在只检查：

- `Config` / `Node` 是已知类型；
- 泛型实参格式正确；
- 第一段和第二段的类型实参规则。

`N` 是不是 Config 的关联常量、`leaf` 是不是 Node 的关联函数，属于后面的关联项解析，不在这一步做。这样不会误伤当前的 accept。

#### 六、这一部分对当前测试的影响

- `rej-functions-have-no-type-parameters` 是**单段** `f::<i32>()`，由 `checkSingleSegmentValuePath` 处理，不是多段。
- `rej-method-segments-have-no-type-parameters` 是 `MethodCallExprNode`，由方法段检查处理，也不是多段路径。
- 多段检查目前主要防止 `S::<i32>::new`、`S::f::<i32>` 这类未来可能出现的错误，同时保证 `Box::<i32>::new`、`Config::N`、`Self::START` 不被误杀。

所以它对 `invalid-impls-and-generics` 的 12/12 不是必需的，但属于同一套泛型规则，建议一起补上。

## 18. 2026-10-24 22:32 — 我们先来回顾一下目前为止的进度，为什么我的一些同学他们似乎是这样编写的，他们先进…

**你**

我们先来回顾一下目前为止的进度，为什么我的一些同学他们似乎是这样编写的，他们先进行了collect，然后接下来还处理了const的expr计算，似乎是根据一定的逻辑顺序来的，我们似乎是按照测试点来的？

**Copilot**

#### 一、先把当前进度说清楚

我刚跑了全量语义测试：

```text
accept: 69/69
reject: 30/167
```

已经全绿的目录：

| 目录 | 结果 |
| --- | --- |
| `namespace-errors` | 16/16 |
| `invalid-impls-and-generics` | 12/12 |
| `names-and-shadowing` | 3/3 |
| `protected-names` | 2/2 |
| `unreachable-checks` | accept 2/2，reject 1/4 |

目前真正建好的基础设施是：

```text
1. 顶层收集：struct / fn / const / impl target
2. 两个名字空间：类型 / 值
3. 内建类型：i32 等 + Box/Vec
4. 顶层函数签名检查
5. 函数体作用域、块作用域、let 顺序
6. 表达式名字解析（只查名字，不做类型）
7. impl 目标合法性
8. 泛型实参的 arity/kind 检查
```

还没开始的是：

```text
常量求值
表达式类型检查（check(expr, expected) -> Type）
引用与可变性
结构体/数组/容器/derive/布局
```

#### 二、你同学的做法为什么看起来更“有逻辑”

因为编译器前端天然适合按**pass（阶段）**组织：

```text
1. collect            建立全局符号表
2. declaration check  声明层面的检查
3. const eval         常量求值
4. type check         表达式类型检查
5. place/mutability   引用与可变性
6. layout/derive      聚合类型与布局
```

`collect` 之后马上做 `const eval`，有几个直接的依赖原因：

##### 1. 常量路径需要先有符号表

```rust
const A: usize = B;
const B: usize = 64;
```

`A` 引用 `B`，即使 `B` 写在后面，`collect` 之后也能找到。  
如果没有符号表，常量求值连“`B` 是哪个常量”都不知道。

##### 2. 数组长度是常量上下文

```rust
let a: [i32; N] = [0; N];
```

`[i32; N]` 的数组长度、`[0; N]` 的重复长度都需要先求出 `N` 的值。  
如果没有常量求值，类型检查遇到数组类型时只能跳过长度，后面还得回来补。

##### 3. 常量依赖需要环检测

```rust
const A: usize = B;
const B: usize = A;
```

这必须在求值前检测出来，否则会递归死循环。  
环检测天然属于 `ConstEval` 这个 pass，而不是散落在类型检查里。

##### 4. 常量初始化器本身也有类型

```rust
const N: i32 = -1;
const M: usize = 64;
```

求值不仅要知道“值是多少”，还要知道“它是 `i32` 还是 `usize`”。  
所以 `ConstEval` 通常保存 `(类型, 值)`，或者至少保存整数的符号性。

因此，**先 collect、再 const eval** 是一种很自然的架构顺序。

#### 三、我们是不是在按测试点走？

有一部分是。更准确地说，我们采用的是：

> **测试驱动地推进，但每一步尽量沉淀成可复用的 pass。**

例如：

- 做 `namespace-errors` 时，顺手建了符号表和函数体作用域；
- 做 `invalid-impls-and-generics` 时，顺手建了 `checkTypeRef` 的泛型规则和 impl target 检查；
- 这些不是只为某一个测试写的，后面的目录都会复用。

优点：

- 每一步都有明确的验收标准；
- 69 条 accept 一直保持全绿；
- 不会过早实现大量暂时用不到的规则。

缺点：

- 容易出现“测试目录需要什么就补什么”的顺序；
- 某些跨目录的基础设施（比如常量求值）会被推迟；
- 后面做类型检查时，可能需要回头重构 `checkExpr`。

#### 四、两种顺序的对比

| 策略 | 优点 | 风险 |
| --- | --- | --- |
| 按测试目录推进 | 反馈快、进度清晰、accept 不易回归 | 基础设施可能滞后，后期要补课 |
| 按编译 pass 推进 | 架构稳定、依赖顺序自然 | 早期没有测试反馈，容易过度设计 |

你同学的做法是第二种；我们的做法更接近第一种。  
但走到现在，**继续纯按目录推进的收益在下降**，因为接下来几个大目录都需要同一套东西：

```text
constant-errors      9 条  → 需要 ConstEval
arrays               5 条  → 需要 ConstEval + 数组类型
constants-and-paths  2 条  → 需要 ConstEval + 关联常量
expected-types       4 条  → 需要 check(expr, expected)
integer-arithmetic   4 条  → 需要表达式类型检查
loops-and-jumps      7 条  → 需要 never/类型合并
```

所以现在是切换到“逻辑顺序”的好时机。

#### 五、建议的下一步路线

##### 第一步：收尾声明层（WP1 剩余）

- `entry`：`main` 的签名、参数、泛型参数检查；
- `impl` 关联项：方法/关联常量进入 `StructSymbol` 的关联名字空间；
- 顺便为 `methods-and-self` 准备 `currentSelfType` 和 `currentHasSelfParam`。

这一步做完，WP1 基本完整。

##### 第二步：新建 `ConstEval.kt`

建议单独一个文件，而不是继续往 `SemanticAnalyzer` 里堆：

```text
ConstEval.kt
├── ConstValue（类型 + 值）
├── 求值状态（未访问 / 访问中 / 已完成）
├── evaluate(ConstNode)
├── evaluateConstValue(ExprNode)
├── resolveConstantPath(PathExprNode)
└── 环检测
```

需要的数据支持：

- `ConstantSymbol` 增加声明类型和已求值结果；
- `StructSymbol` 增加关联常量/方法表；
- 支持 `LIMIT`、`Config::LIMIT`、`Self::LIMIT` 三种常量路径；
- 数组长度和数组重复长度接入求值；
- 常量初始化器先求值，再做类型检查。

##### 第三步：把 `checkExpr` 升级成类型检查

现在：

```kotlin
private fun checkExpr(expr: ExprNode)
```

后面需要：

```kotlin
private fun check(expr: ExprNode, expected: Type? = null): Type
```

这是最大的一次重构，因为：

- 整数字面量需要“后缀 > 期望类型 > i32”；
- `if` 两分支需要 LUB；
- `loop` 的 `break` 需要合并类型；
- 数组长度需要 `usize`；
- 函数调用需要参数类型；
- `never` 要参与类型合并。

建议分小步：

```text
1. 字面量 / 路径 / 单元 / never
2. 一元、二元、比较、逻辑运算
3. 赋值和复合赋值
4. if / block / loop / break / return
5. 调用、方法调用、cast
6. 数组、结构体字面量、字段、索引
```

##### 第四步：WP4 / WP5

- 引用、可变性、place 分析；
- `&` / `&mut` / `*`；
- 方法查找与 autoref；
- 结构体字段、数组、Box/Vec、derive、递归布局。

#### 六、结论

- 你同学“先 collect，再 const eval”不是偶然，而是按编译器 pass 的依赖顺序走。
- 我们之前按测试目录推进，是为了快速建立符号表、作用域和名字解析；这些基础设施现在已经在。
- 接下来不建议继续单纯追测试点，而是按下面的逻辑顺序走：

```text
声明层收尾
    ↓
ConstEval
    ↓
check(expr, expected) 类型检查
    ↓
place / mutability
    ↓
聚合类型 / 容器 / derive / layout
```

这样既能保留已有的测试驱动成果，也能避免后面反复补基础设施。

## 19. 2026-10-24 22:57 — ok，那现在开始对于WP1收尾吧

**你**

ok，那现在开始对于WP1收尾吧

## 20. 2026-10-24 22:57 — Try Again

**你**

Try Again

**Copilot**

WP1 现在只剩两块：

```text
1. entry：main 的契约检查
2. impl：关联项（方法/关联常量）进入结构体的关联名字空间，并查重
```

其余 namespace、impl target、protected names 已经完成。

#### 一、当前 WP1 状态

| WP1 内容 | 状态 |
| --- | --- |
| 顶层收集 struct/fn/const | 已完成 |
| 两个名字空间 | 已完成 |
| 前向引用 | 已完成 |
| 重名：struct/fn/const | 已完成 |
| 重名：字段/参数 | 已完成 |
| impl 目标合法性 | 已完成 |
| 泛型实参 arity/kind | 已完成 |
| 保护名 | 通过内建类型/值间接完成 |
| `main` 入口检查 | 未完成 |
| impl 关联项名字空间 | 未完成 |

完成这两块后：

```text
entry 目录            5/5
methods-and-self      2/6（1 accept + 1 个关联项重名 reject）
```

`methods-and-self` 剩下的 4 条属于 WP4：

- 方法查找；
- receiver 自动 borrow；
- `&mut self` 需要 mutable place；
- 找不到方法。

#### 二、第一部分：`main` 入口检查

##### 1. 规范要求

```rust
fn main() -> () { ... }
```

- 必须有且只有一个顶层 `fn main`；
- 参数列表必须为空（不能有普通参数，也不能有 `self`）；
- 不能有泛型参数（包括生命周期参数 `<'a>`）；
- 返回类型必须是 `()`：省略返回类型或写 `-> ()` 都合法；
- `main` 必须是顶层函数，`impl S { fn main() {} }` 不算入口。

##### 2. 实现位置

在 `analyze` 中，收集完顶层名字后立刻检查：

```kotlin
fun analyze(crate: CrateNode) {
    installBuiltins(globals)

    collectTopLevel(crate.items)
    checkEntry(crate.items)
    checkDeclarations(crate.items)
}
```

`checkEntry` 放在 `collectTopLevel` 之后，是因为：

- 重名的 `fn main` 会先被收集 pass 拒绝；
- 此时所有顶层声明已经知道，不用再查符号表。

##### 3. 代码骨架

```kotlin
private fun checkEntry(items: List<ItemNode>) {
    val main = items
        .filterIsInstance<FnNode>()
        .firstOrNull { it.name == "main" }
        ?: throw SemanticError(
            items.firstOrNull()?.span ?: /* crate span */ ...,
            "an executable needs a top-level `main` function",
        )

    if (main.params.isNotEmpty()) {
        throw SemanticError(
            main.span,
            "`main` cannot have parameters",
        )
    }

    if (main.hasGenericParams) {
        throw SemanticError(
            main.span,
            "`main` cannot have generic parameters",
        )
    }

    val returnType = main.returnType
    if (returnType != null && returnType !is UnitTypeNode) {
        throw SemanticError(
            main.span,
            "`main` must return `()`",
        )
    }
}
```

几个细节：

- `main.hasGenericParams` 是 `FnNode` 已有的字段，专门为这个检查准备；
- `main.params` 包含 `SelfParam`，所以 `fn main(self)` 也会被“不能有参数”拒绝；
- 只查找 `crate.items` 里的 `FnNode`，因此 `impl` 里的 `fn main` 不会被误认为入口；
- 如果只有 `const main` 或 `struct main`，`filterIsInstance<FnNode>()` 找不到，报“缺少 main”；
- 重复的 `fn main` 已经在 collect 阶段报重名，不会走到这里。

##### 4. 对应用例

| 用例 | 触发点 |
| --- | --- |
| `rej-an-executable-needs-a-top-level-main` | 找不到 `FnNode("main")` |
| `rej-main-cannot-have-value-parameters` | `main.params.isNotEmpty()` |
| `rej-main-cannot-have-generic-parameters` | `main.hasGenericParams` |
| `rej-main-must-return-unit` | `returnType is PathTypeNode("i32")` |
| `acc-explicit-unit-result-and-early-completion` | `returnType is UnitTypeNode`，通过 |

#### 三、第二部分：`impl` 关联项名字空间

##### 1. 数据结构

给 `StructSymbol` 加一个关联值表：

```kotlin
class StructSymbol(
    override val name: String,
    val node: StructNode,
) : Symbol {
    /** 方法和关联常量共享一个关联值名字空间，多个 impl 块共用这张表。 */
    val associatedValues: MutableMap<String, Symbol> = LinkedHashMap()
}
```

注意：

- `associatedValues` 属于**结构体**，不是 `Scope`；
- 同一个结构体的多个 `impl` 块必须共用同一张表；
- 方法和关联常量**共享**这张表；
- 字段不在这张表里，字段属于另一个名字空间。

##### 2. 在 `checkImpl` 里登记关联项

目标校验通过后，拿到 `StructSymbol`：

```kotlin
val struct = globals.lookupType(name) as? StructSymbol
    ?: throw SemanticError(
        target.span,
        "impl target must be a user-defined struct",
    )
```

然后遍历 `node.items`：

```kotlin
for (item in node.items) {
    val symbol = when (item) {
        is FnNode -> FunctionSymbol(
            name = item.name,
            node = item,
            receiver = node.target,
        )

        is ConstNode -> ConstantSymbol(
            name = item.name,
            node = item,
        )

        else -> null
    } ?: continue

    if (struct.associatedValues.containsKey(symbol.name)) {
        throw SemanticError(
            item.span,
            "duplicate associated item '${symbol.name}'",
        )
    }

    struct.associatedValues[symbol.name] = symbol
}
```

关键点：

- `FunctionSymbol.receiver` 非空表示“属于某个 impl”，**不代表它一定有 `self` 参数**；
- 是否有 `self` 参数仍然看 `item.params.any { it is SelfParam }`；
- `fn f` 和 `const f` 都写进同一张表，所以跨种类的重名也能抓到；
- 两个不同的 `impl S` 块拿到的是同一个 `StructSymbol`，所以跨块重名也能抓到；
- **不要把关联项写进 `globals`**，否则会污染顶层值名字空间。

##### 3. 这一步先不要做

- 不要检查方法体；
- 不要检查方法签名里的 `Self`，除非你同时设置 `currentSelfType`；
- 不要做方法查找/autoref。

`acc-receiver-auto-borrows-...` 里有：

```rust
impl Counter {
    const START: i32 = 3;
    fn new() -> Self { ... }
    fn value(&self) -> i32 { self.value }
    ...
}
```

如果现在检查方法体，会遇到 `self`、`Self::START`、`self.value` 等，需要 WP4 的上下文和字段解析。当前只登记名字即可，accept 不受影响。

##### 4. 对应用例

| 用例 | 触发点 |
| --- | --- |
| `rej-associated-values-share-a-namespace-across-impl-blocks` | 第二个 impl 的 `const f` 与第一个 impl 的 `fn f` 重名 |
| `acc-receiver-auto-borrows-...` | 两个 impl 块名字不冲突，全部登记成功 |

#### 四、执行顺序

```text
1. StructSymbol 加 associatedValues
2. 新增 checkEntry(items)
3. analyze 里接 checkEntry
4. checkImpl 里登记 FnNode / ConstNode 并查重
5. make compiler
6. 跑 entry
7. 跑 methods-and-self
8. 跑 namespace-errors / invalid-impls 回归
9. 跑全量
```

验收命令：

```bash
make compiler
python3 scripts/check_semantic.py entry -v
python3 scripts/check_semantic.py methods-and-self -v
python3 scripts/check_semantic.py namespace-errors -v
python3 scripts/check_semantic.py invalid-impls-and-generics -v
python3 scripts/check_semantic.py
```

预期：

```text
entry                     accept 1/1, reject 4/4   → 5/5
methods-and-self          accept 1/1, reject 1/5   → 2/6
namespace-errors          accept 1/1, reject 15/15
invalid-impls-and-generics accept 1/1, reject 11/11
full                      accept 69/69
full reject               至少 35/167
```

`methods-and-self` 剩下的 4 条不要现在硬做，它们需要：

```text
方法查找（点调用只找带 receiver 的方法）
receiver 自动 borrow / autoref
&mut self 需要 mutable place
显式 S::f(s) 不自动借
```

这些属于 WP4。WP1 收尾只需要把“关联项名字空间”建好。

## 21. 2026-10-25 00:46 — 这样对吗

**你**

这样对吗

**Copilot**

整体逻辑是对的，但现在**编译不过**。我跑了 `make compiler`，错误是：

```text
SemanticAnalyzer.kt:425:63: error: syntax error: Expecting an element.
items.firstOrNull()?.span ?: /* crate span */ ...,
                                              ^^^
```

`checkEntry` 里缺少“整个 crate 的 span”时没有可用的值。

#### 一、必须修的地方

##### 1. 让 `checkEntry` 接收 `CrateNode`

现在的签名是：

```kotlin
private fun checkEntry(items: List<ItemNode>)
```

但缺少 main 时，你需要的其实是 `crate.span`。改成：

```kotlin
private fun checkEntry(crate: CrateNode) {
    val main = crate.items
        .filterIsInstance<FnNode>()
        .firstOrNull { it.name == "main" }
        ?: throw SemanticError(
            crate.span,
            "an executable needs a top-level `main` function",
        )

    if (main.params.isNotEmpty()) {
        throw SemanticError(
            main.span,
            "`main` cannot have parameters",
        )
    }

    if (main.hasGenericParams) {
        throw SemanticError(
            main.span,
            "`main` cannot have generic parameters",
        )
    }

    val returnType = main.returnType
    if (returnType != null && returnType !is UnitTypeNode) {
        throw SemanticError(
            main.span,
            "`main` must return `()`",
        )
    }
}
```

然后 `analyze` 里改成：

```kotlin
collectTopLevel(crate.items)
checkEntry(crate)
checkDeclarations(crate.items)
```

##### 2. 删除不再使用的 `BUILTIN_TYPE_NAMES`

你已经改成了 `BUILTIN_TYPES` 映射，原来的：

```kotlin
private val BUILTIN_TYPE_NAMES = listOf(
    "i32", "u32", "isize", "usize", "bool", "Box", "Vec",
)
```

现在没有用了，可以删掉，避免以后误用。

#### 二、语义上正确的部分

##### `checkEntry` 的检查项是对的

- `filterIsInstance<FnNode>()` 只找顶层函数，不会把 impl 里的 `fn main` 当入口；
- 参数列表非空 → `main cannot have parameters`；
- `hasGenericParams` → `main cannot have generic parameters`；
- 返回类型为 `null` 或 `UnitTypeNode` 才通过；
- 缺 main 报错。

修好 `crate.span` 后，`entry` 的 5 条应该全部正确：

```text
acc-explicit-unit-result-and-early-completion   accept
rej-an-executable-needs-a-top-level-main        reject
rej-main-cannot-have-value-parameters           reject
rej-main-cannot-have-generic-parameters         reject
rej-main-must-return-unit                       reject
```

##### 关联项登记也是对的

`StructSymbol.associatedValues` 加得正确：

- 方法和关联常量共享一张表；
- 多个 `impl S` 块拿到同一个 `StructSymbol`，所以跨块重名能抓到；
- `fn f` 和 `const f` 会冲突；
- 关联项不会污染 `globals`。

`rej-associated-values-share-a-namespace-across-impl-blocks` 应该能过。

#### 三、可以顺手清理的地方

`checkImpl` 里现在查了两次 struct：

```kotlin
val symbol = globals.lookupType(name)
if (symbol !is StructSymbol) { ... }

val struct = globals.lookupType(name) as? StructSymbol ?: ...
```

可以直接写成一次：

```kotlin
val struct = globals.lookupType(name) as? StructSymbol
    ?: throw SemanticError(
        target.span,
        "impl target must be a user-defined struct",
    )
```

另外，循环里又定义了一个 `val symbol`，和外层同名：

```kotlin
for (item in node.items) {
    val symbol = when (item) { ... }
}
```

虽然 Kotlin 允许遮蔽，但建议改名为 `associated`，以后读起来更清楚：

```kotlin
val associated = when (item) { ... } ?: continue
if (struct.associatedValues.containsKey(associated.name)) { ... }
struct.associatedValues[associated.name] = associated
```

这不是错误，只是可读性。

#### 四、修完后的验收

```bash
make compiler
python3 scripts/check_semantic.py entry -v
python3 scripts/check_semantic.py methods-and-self -v
python3 scripts/check_semantic.py namespace-errors -v
python3 scripts/check_semantic.py invalid-impls-and-generics -v
python3 scripts/check_semantic.py
```

预期：

```text
entry                       accept 1/1, reject 4/4   → 5/5
methods-and-self            accept 1/1, reject 1/5   → 2/6
namespace-errors            accept 1/1, reject 15/15
invalid-impls-and-generics  accept 1/1, reject 11/11
full                        accept 69/69
full reject                 至少 35/167
```

`methods-and-self` 剩下的 4 条是因为还没有方法查找、receiver 自动借用和 mutable place 分析，属于 WP4，不需要在 WP1 收尾里补。

先修 `checkEntry` 的 `crate.span` 和调用参数，再跑一次构建即可。

## 22. 2026-10-25 00:52 — 请你自行检验一下

**你**

请你自行检验一下

**Copilot**

我实际跑了构建和测试，结论是：**`checkEntry` 已经正确，但 `checkImpl` 里有关联项 key 的 bug，导致 4 条 accept 被误杀。**

#### 一、构建

```text
make compiler → 成功
```

#### 二、entry 目录

`entry` 本身是 **5/5**：

```text
accept 1/1
reject 4/4
```

你看到的 `rej-repeated-derive-entry` 是因为过滤字符串 `entry` 同时命中了文件名里的 `derive-entry`，不是 entry 目录的失败。

#### 三、methods-and-self：accept 被误杀

现在的结果：

```text
methods-and-self accept 0/1
methods-and-self reject 1/5
```

错误信息：

```text
acc-receiver-...rx:9:5:
error: [semantic] duplicate associated item 'Counter'
```

第 9 行是：

```rust
impl Counter {
    const START: i32 = 3;   // 第一个关联项
    fn new() -> Self { ... } // 第二个关联项，这里报重复
}
```

#### 四、根因：key 用错了

现在 `checkImpl` 里写的是：

```kotlin
if (symbol.associatedValues.containsKey(symbol.name)) {
    throw SemanticError(
        item.span,
        "duplicate associated item '${symbol.name}'",
    )
}

symbol.associatedValues[symbol.name] = associated
```

这里 `symbol` 是 `StructSymbol("Counter")`，所以：

```text
第一个关联项 START：
    containsKey("Counter") -> false
    写入 associatedValues["Counter"] = START

第二个关联项 new：
    containsKey("Counter") -> true
    误报 duplicate associated item 'Counter'
```

也就是说，所有名字都被写成了结构体名，第二个关联项必然冲突。

#### 五、修复

把两处 `symbol.name` 改成 `associated.name`：

```kotlin
if (symbol.associatedValues.containsKey(associated.name)) {
    throw SemanticError(
        item.span,
        "duplicate associated item '${associated.name}'",
    )
}

symbol.associatedValues[associated.name] = associated
```

这样：

```text
Counter::START -> key "START"
Counter::new   -> key "new"
Counter::value -> key "value"
...
```

不会再误报。

而 `rej-associated-values-share-a-namespace-across-impl-blocks`：

```rust
impl S { fn f(&self) {} }
impl S { const f: i32 = 1; }
```

会正确地比较：

```text
第一个 impl: key "f"
第二个 impl: containsKey("f") -> true -> 报重复
```

#### 六、全量影响

当前全量结果：

```text
accept: 65/69
reject: 36/167
```

4 条 accept 误杀全部是同一个 bug：

```text
comprehensive-owned-tree/acc-clone-mutate-remove-tree
constants-and-paths/acc-array-lengths-...
lifetimes-and-use/acc-lifetimes-and-...
methods-and-self/acc-receiver-auto-borrows-...
```

它们的 impl 里都有多个关联项，所以第一个之后全部误报。

修复后预期：

```text
entry                       accept 1/1, reject 4/4   → 5/5
methods-and-self            accept 1/1, reject 1/5   → 2/6
full                        accept 69/69
full reject                 约 36/167
```

`methods-and-self` 剩下的 4 条 reject 仍然属于 WP4：

- 关联函数不能被点调用；
- `S::f(s)` 不自动 autoref；
- `&mut self` 需要 mutable place；
- 找不到方法。

#### 七、验证命令

```bash
make compiler
python3 scripts/check_semantic.py 'entry/' -v
python3 scripts/check_semantic.py methods-and-self -v
python3 scripts/check_semantic.py
```

用 `'entry/'` 而不是 `entry`，可以避免误命中文件名里的 `derive-entry`。

修完 `associated.name` 这两处，WP1 就收尾完成。

## 23. 2026-10-25 00:59 — 你再验证一下

**你**

你再验证一下

**Copilot**

验证完成，WP1 收尾结果正常。

#### 构建

```text
make compiler → 成功
```

#### 分目录结果

| 目录 | 结果 |
| --- | --- |
| `entry` | accept 1/1，reject 4/4 → **5/5** |
| `methods-and-self` | accept 1/1，reject 1/5 → **2/6** |
| `namespace-errors` | accept 1/1，reject 15/15 → **16/16** |
| `invalid-impls-and-generics` | accept 1/1，reject 11/11 → **12/12** |
| `names-and-shadowing` | accept 1/1，reject 2/2 → **3/3** |
| `protected-names` | accept 1/1，reject 1/1 → **2/2** |

#### 全量

```text
accept: 69/69 passed
reject: 35/167 properly rejected
```

对比 WP1 收尾前：

```text
accept: 69/69     （没有回归）
reject: 30/167 → 35/167
```

增加的 5 条正好是：

- entry 的 4 条 reject；
- `rej-associated-values-share-a-namespace-across-impl-blocks` 1 条。

#### `methods-and-self` 剩下的 4 条

```text
rej-an-associated-function-is-not-a-dot-call-method
rej-explicit-associated-call-does-not-autoref-an-owned-argument
rej-mutable-receiver-needs-a-mutable-place
rej-no-matching-method
```

这 4 条都需要方法查找 / receiver 自动借用 / mutable place 分析，属于 WP4，不是 WP1 的遗漏。

#### WP1 状态

```text
收集、重名、前向引用          ✓
两个名字空间                  ✓
impl 目标合法性                ✓
泛型实参 arity/kind            ✓
main 入口检查                  ✓
impl 关联项名字空间与重名      ✓
保护名                         ✓（通过内建环境）
```

WP1 收尾完成。现有代码还有两个小清理项，不影响测试：

- `BUILTIN_TYPE_NAMES` 已经被注释掉，可以直接删除；
- `checkImpl` 里外层变量 `symbol` 已经是 `StructSymbol` 智能转换后的结果，可以直接用，不必再查一次。

接下来建议按之前说的逻辑顺序进入 **ConstEval（常量求值）**，然后升级 `checkExpr` 为 `check(expr, expected): Type`，这样后面 `constant-errors`、`arrays`、`constants-and-paths` 和类型规则目录可以成片推进。

## 24. 2026-10-25 11:14 — 以下是图片中包含的文字内容，按流程图的结构顺序整理： **主流程（1-4 横向向…

**你**

以下是图片中包含的文字内容，按流程图的结构顺序整理：

**主流程（1-4 横向向右，5-8 横向向左）：**

1. 声明收集
   CrateIndex
   ↓
2. 结构体身份
   登记 impl 头部
   ↓
3. 常量与函数
   登记并检查入口
   ↓
4. 常量求值
   解析类型与值
   ↓
5. 结构体字段
   StructResolver
   ←
6. 函数签名
   FunctionResolver
   ←
7. 全局约束
   有限大小与能力
   ←
8. 函数体检查
   BodyChecker

**下方分支：**

8. 函数体检查向下引出：
↓
各检查阶段报告诊断
DiagnosticCollector
→ （诊断）→
SemanticResult
成功标记、模型、诊断

**底部备注文字：**
阶段顺序对应当前 analyze。关键错误检查点提前结束后续阶段，并保留已收集的诊断。这是我们已经写好的同学给出的思路，按照这个思路，我们现在完成了什么？接下来做什么部分？

**Copilot**

#### 结论

你同学这张图其实是把我们已经做的 WP1 再切细成 8 个阶段。对照下来：**阶段 1–3 已完成并通过验收，阶段 4（常量求值）完全没动——这就是下一步。**

（刚重跑了一遍全量：`accept 69/69`、`reject 35/167`，62.9s。）

#### 8 阶段 vs 你的代码

| # | 阶段 | 你现在的实现 | 状态 |
|---|---|---|---|
| 1 | 声明收集 `CrateIndex` | `installBuiltins` + `collectTopLevel`/`collectItem`，`globals` 双名字空间 + 重名拒绝 | ✅ namespace-errors 15/15 |
| 2 | 结构体身份 / 登记 impl 头部 | `checkImpl`：目标必须是 `PathTypeNode`→用户 `StructSymbol`；关联项挂 `StructSymbol.associatedValues`，跨 impl 共享去重 | ✅ invalid-impls 11/11 |
| 3 | 常量与函数登记 + 入口 | fn/const 在阶段 1 已登记；`checkEntry` 查 `main` 无参/无泛型/返回 `()` | ✅ entry 4/4 |
| 4 | 常量求值（类型 + 值） | **没有**。`checkDeclarations` 里 `ConstNode -> /* 暂不检查 */`，`checkTypeRef` 里 `ArrayTypeNode -> 只查 element`，`ArrayLitNode.repeat.length` 只 `checkExpr` | ❌ **下一步** |
| 5 | 结构体字段 `StructResolver` | 只有 `checkStructFields` 查字段重名；字段**类型没解析**，不产出 `StructType`/字段表 | ⚠️ structs-and-fields 0/6（但这 6 个其实是 BodyChecker 的活） |
| 6 | 函数签名 `FunctionResolver` | 只有顶层：`checkTopLevelFunction`（参数重名、拒收 receiver、`checkTypeRef` 校验）。impl 方法签名完全没查；**不产出 `Type`** | ⚠️ 部分 |
| 7 | 全局约束（有限大小/能力） | 无 | ❌ recursive-layout 0/4、copy-clone 0/9 |
| 8 | 函数体 `BodyChecker` | 只有名字解析骨架：`checkFunctionBody`/`checkBlock`/`checkStatement`/`checkExpr`，无类型、无 place/可变性/方法查找 | ⚠️ names-and-shadowing 2/2，unreachable 1/4 |
| — | `DiagnosticCollector → SemanticResult` | 我们是 fail-fast `throw SemanticError` → CLI exit 1 | 设计差异，见下 |

#### 两个必须先想清楚的结构问题

**① `analyze()` 的调用顺序，目前和这张图不一致**

现在是 `installBuiltins → collectTopLevel → checkEntry → checkDeclarations`，而 `checkDeclarations` **按 item 源顺序**把阶段 5/6/8 混在一起，`checkImpl` 排在最后。

这已经是个真隐患，`constants-and-paths/acc-array-lengths-...` 就是活证据：

```rust
const COUNT: usize = Config::N;   // 先在源文件里出现
struct Config { x: i32 }          // 阶段 1 只登记名字
impl Config { const N: usize = (3); }  // 阶段 2 才挂到 StructSymbol 上
```

如果按"遇到 ConstNode 就求值"接在 `checkDeclarations` 里，`Config::N` 此时还不存在 → 会误报。所以**要按这张图改成显式 8 步**：`checkImpl`（登记）必须提前到常量求值之前，函数体必须最后。

**② 诊断模型**

图里是 `DiagnosticCollector → SemanticResult`（累积多条诊断）。你现在是 fail-fast。官方 runner 只看 exit code，语义阶段没有"必须报几条"的要求，所以**建议继续 fail-fast**（省事、够用）；`SemanticResult` 那层等以后想给人看多条错误再重构，现在不要为它分心。

#### 下一步：阶段 4 `ConstEval` 具体写什么

新建 `src/main/kotlin/rx/semantic/ConstEval.kt`：

1. **数据**：`data class ConstValue(val type: Type, val value: Any)`（整数用 `Long`，bool 用 `Boolean`）。落点建议放 ConstEval 内部的两张表（`Map<ConstantSymbol, ConstValue>` + `Map<ConstantSymbol, State>`），**不要**往 `ConstantSymbol` 上塞字段——符号表只该有"名字/声明"。
2. **状态机**：`enum State { UNVISITED, IN_PROGRESS, DONE }`。进入求值置 `IN_PROGRESS`，算完置 `DONE`；如果遇到已 `IN_PROGRESS` 的常量 → 报环。这必须**在求值期**做，不能收集期做，因为 `const B: i32 = -A; const A: i32 = 3;` 必须允许前向引用。
3. **入口** `evaluateConst(node: ConstNode)`：先 `resolveType(node.type)` 得声明类型 → 用它作期望类型求初始值 → 比对类型。
4. **受限形式** `evaluateConstExpr(expr, expected: Type?)`：只接受整数字面量、`true`/`false`、常量路径、一元负号、括号（AstBuilder 已拆括号）。其余一律报"not a constant expression"。
5. **常量路径解析**：`N` / `Config::N` / `Self::N` 都必须解析到 `ConstantSymbol`，否则报 `rej-a-constant-path-must-resolve-to-a-constant`（那里写的是 `const N: i32 = value;`，`value` 是个函数）。`Config::N` 走 `StructSymbol.associatedValues`，`Self::N` 走 `currentSelfType`。
6. **四处接线**：`checkDeclarations` 的 `ConstNode` 分支、`checkImpl` 里的关联常量、`checkTypeRef` 的 `ArrayTypeNode.length`、`checkExpr` 的 `ArrayLitNode.repeat.length`。

##### 从测试里读出来的硬约束（这些细节决定你写对没写对）

- 三种环都要抓：`const A: i32 = A;`、`const A = B; const B = A;`、`impl S { const A = Self::B; const B = Self::A; }`。
- 常量路径不能指向函数 → 报错（见上）。
- 类型必须一致：`const N: i32 = true;` 报错。
- **长度类必须是 `usize`**：`[i32; N]`（`N: i32` 或 `N: bool`）报错；`[1; N]`（`N: i32`）同样报错。
- **无符号不能取负**：`const N: u32 = -1u32;` 报错。这里有个 AST 缺口——`IntLitNode` 现在只有 `value: Long`，后缀在 `buildLiteralExpression` 里被剥掉了。这个用例可以靠"期望类型 `u32` + 一元负号"抓到，但以后 WP3 做类型规则时 `1u32 + 2i32` 这类判断必须有后缀，**建议现在就给 `IntLitNode` 加 `suffix: String?`**。
- **数组长度按值比较，不按符号**：`[i32; COUNT]` 和 `[VALUE; Config::N]` 能匹配，是因为都算成 `3`；你的 `ArrayType.length: Long` 正是这个语义，别改成存常量名。
- **关联常量不占据无限定名**：`Config::N` 不等于裸 `N`（`let N = 9;` 合法）。你现在只把关联项挂在 `StructSymbol` 上，这一点已经天然正确。

#### 顺带铺路的一个重构（阶段 5/6 的前提）

`checkTypeRef(ref)` 现在只**校验**、丢弃结果。阶段 5/6 都要"把 `TypeRefNode` 变成 `Type` 存起来"（字段表、参数表、返回类型）。所以建议把它改成 `resolveType(ref): Type`（`checkTypeRef = resolveType 后忽略返回值`），这是后面所有阶段的地基，越早改越便宜。改的时候注意两个坑：

- 结构体字段解析前要先设 `currentSelfType`，否则 `field: Vec<Self>` 的 accept 会挂。
- impl 方法签名要先 `currentSelfType = symbol` 再解析参数/返回。

#### 之后的顺序

**4 常量求值 → 5 字段签名（产出 `StructType` + 字段表）→ 6 函数签名（含 impl 方法，产出参数/返回 `Type`）→ 7 有限大小环 + derive 能力 → 8 BodyChecker（`check(expr, expected): Type` + place/可变性 + 方法查找/autoref）。**

注意 5、6 单独做几乎不消灭 reject（`structs-and-fields` 的 6 个 reject 全是字面量/字段访问，属于阶段 8），它们的价值是给 8 准备类型模型；真正开始大批消灭 reject 是在 8。这也解释了为什么 `methods-and-self` 那 4 个（autoref / 可变 place / no matching method / associated fn 不能点调用）现在动不了——它们全是阶段 8 的事。

可选的小清理：删掉 `BUILTIN_TYPE_NAMES` 注释块；`checkImpl` 里第二次 `lookupType` 可以直接用已 smart-cast 的 `symbol`；`checkStructFields` 的报错 span 用 `field.span` 而不是 `node.span`。

## 25. 2026-10-25 11:18 — 现在请你详细讲一下常量求值

**你**

现在请你详细讲一下常量求值

## 26. 2026-10-25 11:19 — 现在请你详细设计一下常量求值，需要做什么工作，应该如何实现

**你**

现在请你详细设计一下常量求值，需要做什么工作，应该如何实现

**Copilot**

### 常量求值（ConstEval）详细设计

#### 一、先把边界钉死

规范里"常量上下文"只有三处，求值器只服务这三处：

| 上下文 | AST 位置 | 期望类型 | 结果要落到哪 |
|---|---|---|---|
| const 初值 | `ConstNode.value` | 声明类型 | 常量表（值 + 类型） |
| 数组类型长度 | `ArrayTypeNode.length` | `usize` | `ArrayType(element, length: Long)` |
| 数组重复长度 | `ArrayRepeat.length` | `usize` | 供 BodyChecker 判断长度、Copy 要求、长度匹配 |

三条必须写进代码注释的边界：

1. **常量值只有两种：整数和布尔。** 因为受限形式只允许 `INTEGER_LITERAL`、`true/false`、常量路径（其目标也是整数/布尔）、`-Magnitude`、括号。所以 `const X: [i32; 2] = ...` 不可能有合法初值——`ConstValue` 只需要 `Int` / `Bool` 两个变体，别设计成通用值。
2. **求值器不负责普通表达式的类型检查。** `let n = 2usize;` 的类型由 BodyChecker 管；ConstEval 只回答"这个受限形式的值和类型是什么"。
3. **求值器不能看见局部作用域。** 这是 `rej-repeat-length-cannot-name-a-local` 的要害：`[1; n]` 里的 `n` 是局部变量，必须报"路径必须指向常量"，而不是"类型不对"。所以路径解析只查 `globals`（以及 struct 的关联项表），**绝不要用 `currentScope`**。

前置条件（决定了 `analyze()` 的排法）：阶段 1 的顶层常量已登记、阶段 2 的 impl 关联项已挂到 `StructSymbol.associatedValues` 上，才能跑阶段 4。`constants-and-paths/acc-array-lengths-...` 就是证据：`const COUNT: usize = Config::N;` 写在 `impl Config` **之前**。

---

#### 二、数据结构（新文件 `src/main/kotlin/rx/semantic/ConstEval.kt`）

```
┌─ ConstValue ────────────────────────────────┐
│ sealed interface ConstValue { val type: Type }│
│   Int(type: IntegerType, value: Long)        │
│   Bool(value: Boolean)                       │
└──────────────────────────────────────────────┘
```

内部三张表，全部挂在 ConstEval 实例上（一次 `analyze()` 一个实例）：

| 表 | 类型 | 作用 |
|---|---|---|
| `states` | `MutableMap<ConstantSymbol, State>` | `IN_PROGRESS` / `DONE`，缺省 = 未访问。**环检测** |
| `values` | `MutableMap<ConstantSymbol, ConstValue>` | 求值结果缓存 |
| `declaredTypes` | `MutableMap<ConstantSymbol, Type>` | 声明类型缓存，避免重复 `resolveType` |

`enum class State { IN_PROGRESS, DONE }` —— 只需要两个值，因为"未访问"= map 里没有这个 key。

**为什么 key 用 `ConstantSymbol` 而不是名字**：顶层 `A` 和 `S::A` 是两个不同的常量；用名字会串。`ConstantSymbol` 没有重写 `equals`，天然是身份相等，正合适。

**`Self::B` 的归属问题（重要设计点）**：求值 `impl S` 里的 `const A = Self::B` 时，`Self` 应该解析到 `S`。但递归求值 `B` 时可能跳进别的常量。所以**不要**用可变的 `currentSelfType` 环境变量，而是给 `ConstantSymbol` 加一个 `owner: StructSymbol?` 字段（顶层 = null，`checkImpl` 里创建时传 `symbol`）。求值时用 `symbol.owner` 解析 `Self`，无状态、不会泄漏。

```kotlin
class ConstantSymbol(
    override val name: String,
    val node: ConstNode,
    val owner: StructSymbol? = null,   // ← 新增
) : Symbol
```

---

#### 三、核心算法

##### 3.1 环检测：三色 DFS 就够，但必须"求值前判定"

规范原文："The dependency graph must be acyclic... The compiler must detect a cycle before attempting to evaluate the involved constants."

实现上不需要单独建图：**递归求值 + `IN_PROGRESS` 标记** 就是标准的三色 DFS，等价且更简单。

```
force(symbol):
    state = states[symbol]
    if state == DONE       -> return values[symbol]
    if state == IN_PROGRESS -> SEMANTIC ERROR: constant dependency cycle
    states[symbol] = IN_PROGRESS
    declared = declaredTypes.getOrPut(symbol) { resolveType(symbol.node.type) }
    value    = evalConstValue(symbol.node.value, expected = declared, ctx = symbol.owner)
    if value.type != declared -> ERROR: constant initializer must match declared type
    values[symbol] = value
    states[symbol] = DONE
    return value
```

三个测试正好覆盖三档：

- `const A: i32 = A;` → 自环
- `const A: i32 = B; const B: i32 = A;` → 间接环
- `impl S { const A = Self::B; const B = Self::A; }` → 关联环（靠 `owner` 找到 `S`）

注意**前向引用必须能过**：`const B: i32 = -A; const A: i32 = 3;` 在 `force(B)` 时会递归 `force(A)`，A 那时是"未访问"，正常求值。

##### 3.2 受限形式求值

```kotlin
fun evalConstValue(expr: ExprNode, expected: Type?, owner: StructSymbol?): ConstValue
```

规则表（**这张表是设计核心**）：

| 形式 | 类型规则 | 备注 |
|---|---|---|
| `IntLitNode` | 有后缀 → 后缀类型；无后缀且 `expected` 是整数类型 → `expected`；否则默认 `i32` | 越界 = UB，**不报错** |
| `BoolLitNode` | `bool`；`expected` 非空且不是 `bool` → 报错 | |
| `PathExprNode` | 目标常量的**声明类型**；必须匹配 `expected`（若非空） | 递归 `force` |
| `UnaryExprNode(NEG, operand)` | 操作数**不带 expected** 求值（传播排除），结果类型 = 操作数类型，且必须 `signed` | `-1` → i32；`-1u32` → 报错"negative forms require a signed integer type" |
| 其他一切 | 静态形式错误 | 包括 `CallExpr`、`BinaryExpr`、`UnitLit`、block/if/loop、array/struct 字面量、field/index/method/cast/reference |

括号不用管：`buildConstValue` 已经把 `( ConstValue )` 和 `( Magnitude )` 拆掉了，`((true))` 到达时就是 `BoolLitNode`。

**"传播排除"是这里最容易写错的一条**（`const/const_eval` 规范明写）：

- `const N: i32 = -1;` 合法：`-1` 整体默认 i32。
- `const N: isize = -1;` 是 **UB**（需要 expected 穿过一元负号）。所以 `-` 的**右操作数不接受 expected**，无后缀就默认 i32 → 得到 i32，和声明 `isize` 不匹配 → 我们报错。规范说这是 UB，报错是允许的（语料里没有这个 accept 用例）。
- 想要 `isize` 就写 `-1isize` —— **这就直接要求 `IntLitNode` 保留后缀**（见第五节）。

##### 3.3 常量路径解析

```kotlin
fun resolveConstantPath(node: PathExprNode, owner: StructSymbol?): ConstantSymbol
```

| 路径形态 | 解析方式 | 失败情形 |
|---|---|---|
| `N`（1 段） | `globals.lookupValue(name)` | 是 `FunctionSymbol`/`BuiltinFunctionSymbol` → "constant path must resolve to a constant"；查不到 → 同错；是 `null` 且本应是局部 → 同错（**不要回退到 currentScope**） |
| `Config::N`（2 段） | `globals.lookupType("Config")` 必须是 `StructSymbol` → `associatedValues["N"]` 必须是 `ConstantSymbol` | 目标是函数/不存在 → 报错 |
| `Self::N`（2 段，首段 `Self`） | `owner` 非空否则报错；`owner.associatedValues["N"]` | |
| 段数 > 2 | 直接报错 | Rx 只有一层关联项 |
| 任一段带 `genericArgs` | 报错 | 常量路径没有类型参数 |

解析到 `ConstantSymbol` 后调 `force(symbol)`。

---

#### 四、三个入口与接线

##### 4.1 `runAll`：强制全部求值（不能懒）

`rej-constant-initializer-must-match-declared-type` 里 `const N: i32 = true;` **从来没被用到**，仍然必须报错。所以阶段 4 要主动遍历：

```
runAll(crate):
    for (item in crate.items) if (item is ConstNode) force(globals 里对应的符号)
    for (struct in globals 的 StructSymbol) for (sym in struct.associatedValues)
        if (sym is ConstantSymbol) force(sym)
```

##### 4.2 `requireUsize`：两个长度上下文共用

```kotlin
fun requireUsize(expr: ExprNode): Long {
    val v = evalConstValue(expr, expected = UsizeType, owner = null)
    if (v.type != UsizeType) ERROR("array length must have type usize")
    ...
}
```

因为 `expected = usize`，裸字面量 `4` 会变成 `usize`；而路径 `N` 的类型来自它自己的声明，所以 `const N: i32 = 3;` 用在长度上会报错（覆盖 `rej-array-length-must-have-usize-type` 和 `rej-an-i32-constant-cannot-be-a-usize-repeat-count`，以及 `N: bool` 那例）。

##### 4.3 接线点（共 4 处，其中 1 处要删代码）

| 位置 | 现在 | 改成 |
|---|---|---|
| `checkDeclarations` 的 `is ConstNode -> { /* 暂不检查 */ }` | 空 | 删掉，统一由 `runAll` 管（或直接调 `constEval.runAll`） |
| `checkImpl` 注册完关联项之后 | — | 关联常量在 `runAll` 里已被覆盖，无需重复 |
| `checkTypeRef` 的 `is ArrayTypeNode -> checkTypeRef(ref.element)` | 忽略长度 | `constEval.requireUsize(ref.length)`；（升级为 `resolveType` 后直接构造 `ArrayType(element, n)`） |
| `checkExpr` 的 `ArrayLitNode` 分支 | `expr.repeat?.let { checkExpr(it.element); checkExpr(it.length) }` | **`checkExpr(it.length)` 要删掉**，换成 `constEval.requireUsize(it.length)`。否则 `[1; n]`（n 是局部）会被普通名字解析当成合法局部 |

##### 4.4 `analyze()` 必须调整顺序

现在是 `collectTopLevel → checkEntry → checkDeclarations`，而 `checkImpl` 混在 `checkDeclarations` 里并且靠后。改成：

```
installBuiltins
collectTopLevel(crate.items)     // 阶段 1
registerImpls(crate.items)       // 阶段 2：把 checkImpl 从 checkDeclarations 里拎出来
checkEntry(crate)                // 阶段 3
constEval.runAll(crate)          // 阶段 4 ← 必须在 2 之后
checkDeclarations(...)           // 阶段 5/6/8（以后再拆）
```

---

#### 五、必须顺手改的三个东西

1. **`checkTypeRef` → `resolveType(ref: TypeRefNode): Type`**
   常量声明类型要变成 `Type` 才能比对；数组长度也要挂到 `ArrayType` 上。现在 `checkTypeRef` 只校验、丢弃结果，是阶段 5/6 的共同地基。保留 `checkTypeRef(ref) = resolveType(ref)`（忽略返回值）可以避免大改调用点。
   ⚠️ 互递归没问题：`const A: [i32; 2] = 1;` → `resolveType([i32;2])` → `requireUsize(2)` → 求值字面量。不会死循环。

2. **`IntLitNode` 加 `suffix: String?`**
   现在 `parseIntegerLiteral` 把后缀剥了，只剩 `value: Long`。本题只有 `-1u32` 一个用例（靠"unsigned 不能取负"也能过），但：
   - `const N: usize = 3usize;` 这类写法没有后缀就无法正确判型；
   - WP3 的 `1u32 + 2i32`、`rej-an-explicit-mismatched-suffix-cannot-coerce` 等完全依赖后缀（语料里 i32/u32/usize/isize 后缀遍地都是）。
   加字段 ≈ 5 行，现在加最便宜。

3. **`ConstantSymbol.owner`**（见 3.1）

---

#### 六、错误清单（建议消息）

| 场景 | 消息 | span |
|---|---|---|
| 环 | `constant dependency cycle` | 常量名或初值表达式 |
| 路径不是常量 | `constant path must resolve to a constant` | 路径表达式 |
| 初值类型不符 | `constant initializer must match declared type` | 初值 |
| 非受限形式 | `expected a constant value` | 表达式 |
| 无符号取负 | `negative forms require a signed integer type` | 一元负号 |
| 长度不是 usize | `array length must have type usize` | 长度表达式 |
| `Self` 不在 impl 里 | `\`Self\` is not allowed here` | 路径 |

全部走现有 `SemanticError`，保持 fail-fast。

---

#### 七、这份设计能吃掉哪些用例

| 用例 | 依靠的机制 |
|---|---|
| `acc-forward-constant-reference-and-negation` | 未访问即求值 + 传播排除 |
| `rej-a-constant-path-must-resolve-to-a-constant` | 只认 `ConstantSymbol` |
| `rej-direct-constant-dependency-cycle` | `IN_PROGRESS` |
| `rej-indirect-constant-cycle` | `IN_PROGRESS` |
| `rej-associated-constant-cycle` | `IN_PROGRESS` + `Self::` via `owner` |
| `rej-constant-initializer-must-match-declared-type` | expected 传播 + 比对 |
| `rej-unsigned-constant-negation-is-invalid` | NEG 要求 signed |
| `rej-array-length-must-be-usize`（bool 常量） | `requireUsize` |
| `rej-an-i32-constant-cannot-be-a-usize-repeat-count` | `requireUsize` |
| `rej-repeat-length-cannot-name-a-local` | 路径只查 `globals` |
| `constants-and-paths` 2 个 reject | 同上 |
| `acc-array-lengths...` | 前向引用 + `Config::N` + 值相等（`ArrayType.length` 是 `Long`，不是符号） |

预期：**constant-errors 9/9、constants-and-paths 2/2 清空**。`arrays` 的 5 个里能顺带吃到 `rej-array-length-mismatch`、`rej-repeating-more-than-once-requires-copy` 的**长度前提**，但真正的判定要等 BodyChecker。

---

#### 八、建议的落地顺序（5 小步，每步可编译可跑）

1. `IntLitNode.suffix` + `parseIntegerLiteral` 保留后缀（改 AstBuilder 一处）。
2. `resolveType`：把 `checkTypeRef` 拆成"返回 `Type`"版；`checkTypeRef` 变薄包装。跑全量确认 69/69 不回归。
3. 新建 `ConstEval.kt`：`ConstValue` + 三张表 + `force` + `evalConstValue` + `resolveConstantPath`。
4. 接入口：`runAll`、`checkImpl` 提前、`checkDeclarations` 里 ConstNode 分支删除。
5. 接长度：`ArrayTypeNode.length` 与 `ArrayRepeat.length` → `requireUsize`（删掉 repeat 的 `checkExpr`）。

验证：

```
make compiler
python3 scripts/check_semantic.py constant-errors constants-and-paths -v
python3 scripts/check_semantic.py          # 确认 accept 仍是 69/69
```

---

#### 九、几个坑，提前知道

- **不要用 `currentScope` 查常量路径**，否则 `[1; n]` 会静默通过。
- **`force` 必须幂等**：同一个常量会被多个上下文引用，`DONE` 后必须直接返回缓存，否则环检测会误报。
- **`values` 里存"声明类型"**，不是"推导出来的类型"——规范说"常量路径具有目标项的声明类型和值"。
- **越界字面量、零长数组、`const N: isize = -1;` 都是 UB，不要主动报错**（`-1` 那条按传播排除会自然报 i32/isize 不匹配，属于允许的拒绝，但别为它写特判）。
- **`ArrayTypeNode.length` 现在只在 `checkTypeRef` 里被忽略**，改完后它是唯一入口；别在两处各求一次（虽然幂等，但错误会重复报）。
- 顶层 `A` 与 `S::A` 必须能共存（`namespace-errors` 已经保证这一点），这要求 `values` 的 key 是符号而不是字符串。

## 27. 2026-10-25 11:51 — `IntLitNode.suffix` + `parseIntegerLiter…

**你**

`IntLitNode.suffix` + `parseIntegerLiteral` 保留后缀（改 AstBuilder 一处）这个要怎么修改，为什么要修改

**Copilot**

#### 为什么必须保留后缀

`INTEGER_LITERAL` 这个 token 本身是带后缀的（`grammar/Lexer.g4:62`）：

```
INTEGER_LITERAL : (DECIMAL | BINARY | OCTAL | HEXADECIMAL) INTEGER_SUFFIX? ;
fragment INTEGER_SUFFIX : 'i32' | 'u32' | 'isize' | 'usize' ;
```

但 `AstBuilder.parseIntegerLiteral` 把它**丢掉**了，只返回 `Long`。后果是 `1`、`1i32`、`1u32`、`1usize` 在 AST 里完全一样。这会直接卡住三件事：

**1. 后缀是字面量"自带"的类型，期望类型不能覆盖它**

规范的字面量规则是：有后缀 → 类型就是后缀；无后缀 → 用期望类型，没有期望类型则默认 `i32`。丢掉后缀后你无法区分：

- `let x: u32 = 1i32;` —— 应该报错（后缀 i32 不匹配），但会变成"无后缀 + 期望 u32"，被错误接受；
- `let x: i32 = 1u32;` —— 同理。

`compound-assignment/rej-an-explicit-mismatched-suffix-cannot-coerce.rx` 里就是 `1u32` 和 `1i32` 相加，靠的正是后缀。

**2. 常量求值里的 `-1u32` 必须按"无符号不能取负"报错**

规范的 `const_eval` 明写：负数形式要求有符号整数类型。有后缀时，`-1u32` 的 magnitude 是 `u32`，可以直接判定；没后缀时它变成 `-1`（i32），只能靠"和声明的 u32 不匹配"间接拒绝——用例结论虽然对，但机制错了，而且 `const N: u32 = -1;` 和 `= -1u32;` 变得无法区分。

**3. 传播排除的表述依赖后缀**

规范的原话是：`const N: isize = -1;` 是 UB（因为需要期望类型穿过一元负号），"Write `-1isize` instead"。也就是说 `-1isize` 和 `-1` 必须是两种不同的 AST 事实。没有后缀字段，这条规则根本没法表达。

**4. 后面 WP3 还要用**：`8i32` / `8isize`（`acc-literal-boundaries-and-bool-casts`）、`1u32`（`rej-unsigned-integer-types-remain-distinct`、`rej-i-o-requires-i32...`）、`2u32` / `1usize`（compound-assignment）、`1i32`（bitwise 类型不匹配）——全部靠后缀定类型；字面量越界判定也需要知道目标宽度。

---

#### 改哪 4 处

##### ① `src/main/kotlin/rx/ast/Exprs.kt` — 加字段

```kotlin
class IntLitNode(
    val value: Long,
    val suffix: String? = null,   // "i32" / "u32" / "isize" / "usize"；null = 无后缀
    span: Span,
) : ExprNode(span) {
    override fun accept(visitor: AstVisitor) = visitor.visit(this)
}
```

`null` 表示"无后缀"，不要用 `""`——语义阶段要靠 `null` 走期望类型/默认 `i32` 分支。现有唯一构造点是 `buildLiteralExpression`，用的是具名参数，加这个字段不会破坏它；`dev/ast-smoke/AstSmoke.kt` 只读 `.value`，也不受影响。

##### ② `AstBuilder.parseIntegerLiteral` — 把后缀一起带出来

现在它 `dropLast` 之后就扔了。改成返回一个小的内部数据类：

```kotlin
private data class ParsedInteger(val value: Long, val suffix: String?)

private fun parseIntegerLiteral(text: String): ParsedInteger {
    var body = text
    var suffix: String? = null

    for (candidate in listOf("isize", "usize", "i32", "u32")) {
        if (body.endsWith(candidate)) {
            body = body.dropLast(candidate.length)
            suffix = candidate
            break
        }
    }

    body = body.replace("_", "")
    // ... 原来的 radix 判定与 toLongOrNull 不变 ...

    val value = digits.toLongOrNull(radix)
        ?: throw IllegalArgumentException("Integer literal '$text' is out of range")

    return ParsedInteger(value, suffix)
}
```

**顺序千万别倒**：必须先剥后缀、再删下划线，否则 `1_000i32` 会变成 `1_000i32` 删下划线后仍带后缀，但 `1_0i32` 这种没问题——真正的坑是如果你先删下划线也无所谓，但如果先判后缀再删下划线是现行顺序，保持它即可。关键是 `body` 的判后缀要在 `replace("_","")` 之前，因为后缀里没有下划线，两者其实互换也安全，但保持原顺序最省心。

后缀不用自己校验合法性：`INTEGER_SUFFIX` 只允许这 4 个，而 `1u33` 会被 `INVALID_NUMBER`（`[0-9] ASCII_CONTINUE*`，更长匹配）吃掉，在词法阶段就报错了。

##### ③ `buildLiteralExpression` — 传下去

```kotlin
if (ctx.INTEGER_LITERAL() != null) {
    val literal = parseIntegerLiteral(ctx.INTEGER_LITERAL().text)
    return IntLitNode(
        value = literal.value,
        suffix = literal.suffix,
        span = spanOf(ctx),
    )
}
```

##### ④ `AstPrinter` — 顺手让 dump 能看见（两处）

```kotlin
override fun visit(n: IntLitNode) {
    nodeLine(n, "IntLitNode(value = ${n.value}, suffix = ${n.suffix})")
}
```

```kotlin
// describeConst 里
is IntLitNode -> "${expr.value}${expr.suffix ?: ""}"
```

不加也能编译（有默认值），但加了以后 `--dump-ast` 能直接看出 `1_000i32 → value = 1000, suffix = i32`，调试常量求值时省事。

---

#### 语义阶段怎么用（下一步 ConstEval 的落地方式）

```kotlin
private fun literalType(suffix: String?, expected: Type?): IntegerType = when (suffix) {
    "i32"   -> I32Type
    "u32"   -> U32Type
    "isize" -> IsizeType
    "usize" -> UsizeType
    null    -> (expected as? IntegerType) ?: I32Type   // 期望类型 → 默认 i32
    else    -> error("unreachable: lexer guarantees the suffix set")
}
```

注意这条规则只对**裸字面量**生效。`UnaryExprNode(NEG, ...)` 的右操作数必须传 `expected = null`（传播排除），所以 `-1` 走默认 `i32`，`-1isize` 靠后缀得到 `isize`，`-1u32` 得到 `u32` 然后被"负数要求有符号"拦下。

---

#### 验证

```
make compiler
./scripts/rxc --dump-ast tests/official/semantic/casts-and-literals/acc-literal-boundaries-and-bool-casts.rx
python3 scripts/check_frontend.py parse        # 前端回归不能掉
```

`check_frontend.py` 只比 exit code，所以 AST dump 文本变化不会影响它；但如果你有自己比对 dump 的脚本，记得同步。

一个提醒：`value` 继续用 `Long` 存，**不要**在这里做范围检查。`4294967295u32` 是合法的，`2147483648i32` 是 UB——宽度判定属于类型检查阶段，而且 UB 不该报错。

## 28. 2026-10-25 13:21 — 现在我改好了ast，请你检查一下，如果有问题和我说，接下来我们进行下一步，请你告…

**你**

现在我改好了ast，请你检查一下，如果有问题和我说，接下来我们进行下一步，请你告诉我要做什么内容，是不是好像还要用一个图来判断是否形成一个环？

## 29. 2026-10-25 13:21 — Try Again

**你**

Try Again

**Copilot**

#### 一、检查结果

| 文件 | 结论 |
|---|---|
| `src/main/kotlin/rx/ast/Exprs.kt` | ✅ 字段加对了，`null` 表示无后缀，位置也对 |
| `src/main/kotlin/rx/ast/AstPrinter.kt` | ✅ 两处都改了 |
| `src/main/kotlin/rx/ast/AstBuilder.kt` | ❌ **编译不过**：`parseIntegerLiteral` 改成返回 `ParsedInteger` 之后，只改了 `buildLiteralExpression` 一处，漏了两处调用 |

实际报错（`make compiler`）：

```
AstBuilder.kt:961:25: error: argument type mismatch: actual type is 'AstBuilder.ParsedInteger', but 'Long' was expected.
AstBuilder.kt:1009:25: error: argument type mismatch: ...
```

漏掉的两处恰好**都在常量上下文**里，而且正是最需要后缀的地方：

- `AstBuilder.kt:956` 的 `buildConstValue`（`const` 初值、`[T; N]` 长度、`[x; N]` 重复长度）
- `AstBuilder.kt:1004` 的 `buildMagnitude`（`-1u32` 的 magnitude）

> 我刚才那次 `--dump-ast` 看到的是**旧 class**（make 失败后 `build/kotlin-classes` 没更新），所以显示 `IntLitNode(value = 1000)` 没有 suffix，不能作为验证结果。

两处都改成和 `buildLiteralExpression` 同样的写法：

```kotlin
if (ctx.INTEGER_LITERAL() != null) {
    val literal = parseIntegerLiteral(ctx.INTEGER_LITERAL().text)
    return IntLitNode(
        value = literal.value,
        suffix = literal.suffix,
        span = spanOf(ctx),
    )
}
```

**建议顺手抽一个小助手**，否则以后第四个构造点还会漏：

```kotlin
private fun buildIntLiteral(
    token: TerminalNode,
    span: Span,
): IntLitNode {
    val literal = parseIntegerLiteral(token.text)
    return IntLitNode(literal.value, literal.suffix, span)
}
```

然后三处都变成 `buildIntLiteral(ctx.INTEGER_LITERAL(), spanOf(ctx))`。

小瑕疵（不影响编译）：`private fun parseIntegerLiteral(text: String): ParsedInteger{` 少个空格；`?:` 那行的续行缩进建议对齐到 `digits.toLongOrNull(...)`。

##### 修完这样验证

```
const N: usize = 4usize;
fn main() {
    let a: [i32; N] = [0; 4usize];
    let b = -1u32;
}
```

```
make compiler
./scripts/rxc --dump-ast /tmp/x.rx | grep -n "IntLit\|\[i32"
```

期望看到 `IntLitNode(value = 4, suffix = usize)` 出现在**数组类型**和**repeat 长度**两处，`NEG` 的操作数是 `suffix = u32`。这才证明三个入口全都保住了后缀。

---

#### 二、是不是要"画一张图"判断环？

**不需要显式建图，递归求值栈本身就是那张图。**

- 图的节点 = 常量符号（`ConstantSymbol`，不是名字）
- 图的边 = 某个常量的初值里引用到的常量路径

要显式建图，你得先把每个初值里的路径扫出来——那本身就是一次受限形式遍历；建完再 DFS 一遍，等于把同一件事做两次。**用"递归 + 三色标记"一趟就够**：

```
白（未访问）  = states 里没有这个符号
灰（在栈上）  = IN_PROGRESS
黑（已完成）  = DONE
```

```
force(sym):
    when (states[sym]) {
        DONE        -> return values[sym]          // 缓存，幂等
        IN_PROGRESS -> throw SemanticError(…, "constant dependency cycle")   // 遇到灰 = 环
        null        -> { }
    }
    states[sym] = IN_PROGRESS
    …求值（期间递归 force 引用到的常量）…
    states[sym] = DONE
```

三种测试用例都能抓：

| 用例 | 走到哪一步发现灰 |
|---|---|
| `const A: i32 = A;` | `force(A)` 递归到自身 |
| `const A: i32 = B; const B: i32 = A;` | `force(A)→force(B)→force(A)` |
| `impl S { const A = Self::B; const B = Self::A; }` | `force(S::A)→force(S::B)→force(S::A)` |

两个必须注意的点：

1. **环检测只能在求值期做，不能在收集期做。** 因为前向引用合法（`const B = -A; const A = 3;`），收集时根本不知道谁依赖谁。
2. **`states` 的 key 必须是符号身份**，不能是名字，否则顶层 `A` 和 `S::A` 会串（`namespace-errors` 已经要求两者能共存）。

如果你以后想在报错里打印"A → B → A"这条链，可以再维护一个 `parent: Map<ConstantSymbol, ConstantSymbol>`，遇到灰时沿 parent 回溯。**但不是现在必须的**，报告一个 "constant dependency cycle" 就够 runner 判定了。

---

#### 三、下一步做什么

##### Step 1（立刻）：补上那两处调用，`make compiler` 转绿

##### Step 2：`checkTypeRef` → `resolveType`（ConstEval 的地基）

现在 `checkTypeRef` 只校验、把结果扔掉。常量求值需要：
- 声明类型（`Type`）来当 expected type；
- 数组类型长度求值后要构造 `ArrayType(element, length: Long)`。

所以把核心改成返回 `Type`：

```kotlin
fun resolveType(ref: TypeRefNode): Type = when (ref) {
    is UnitTypeNode  -> UnitType
    is RefTypeNode   -> RefType(ref.mutable, resolveType(ref.inner))
    is ArrayTypeNode -> ArrayType(
        resolveType(ref.element),
        constEval.requireUsize(ref.length),   // 长度是常量上下文
    )
    is PathTypeNode  -> resolvePathType(ref)
    else -> throw SemanticError(ref.span, "unsupported type reference")
}

// 兼容旧调用点：只关心校验的地方不用改
private fun checkTypeRef(ref: TypeRefNode) { resolveType(ref) }
```

`resolvePathType` 就是把你现有 `checkPathType` 的 `when` 分支从"只报错"改成"报错 + 返回值"：

| 分支 | 返回 |
|---|---|
| `Self` | `StructType(currentSelfType.name, currentSelfType.node)` |
| `Box<T>` / `Vec<T>`（恰好一个实参） | `BoxType(resolveType(arg))` / `VecType(resolveType(arg))` |
| 四种原生类型（无实参） | `I32Type` / `U32Type` / `IsizeType` / `UsizeType` |
| `StructSymbol`（无实参） | `StructType(name, symbol.node)` |
| 其他 | 抛 `SemanticError` |

⚠️ **这里会出现互递归**：`resolveType` 的数组分支要调 `constEval.requireUsize`，而 `constEval.force` 又要调 `resolveType` 取声明类型。用 lazy 打破初始化顺序即可：

```kotlin
private val constEval: ConstEval by lazy {
    ConstEval(globals) { ref -> resolveType(ref) }
}
```

（`const A: [i32; 2] = 1;` 这种会在 `resolveType` 里求值字面量 `2`，只是普通递归，不会死循环。）

##### Step 3：新建 `ConstEval.kt`

内容按上一轮的设计：`ConstValue`（`Int` / `Bool`）、三张表、`force`、`evalConstValue(expr, expected)`、`resolveConstantPath`、`requireUsize`、`runAll`。

两个实现细节先记住：

- **`Self::N` 的 owner 用"进入 force 时设置、退出时恢复"**，不要用环境变量式 `currentSelfType` 长期挂着：

  ```kotlin
  fun force(sym: ConstantSymbol): ConstValue {
      …状态检查…
      val savedSelf = currentSelfType
      currentSelfType = sym.owner          // 顶层常量 = null
      try { … } finally { currentSelfType = savedSelf }
  }
  ```

  这样 `S::A` 里引用顶层常量时，嵌套调用会自动把 `Self` 清掉，不会泄漏。
- **`ConstantSymbol` 加 `owner: StructSymbol?`**，`checkImpl` 创建关联常量时传 `owner = symbol`，顶层传默认 `null`。

##### Step 4：接线 + 调整 `analyze()` 顺序

```
installBuiltins
collectTopLevel(crate.items)   // 阶段 1
registerImpls(crate.items)     // 阶段 2：把 checkImpl 从 checkDeclarations 拎出来提前
checkEntry(crate)              // 阶段 3
constEval.runAll(crate)        // 阶段 4
checkDeclarations(...)         // 之后的阶段
```

以及 `checkExpr` 里 `ArrayLitNode` 分支：**把 `checkExpr(it.length)` 删掉**，换成 `constEval.requireUsize(it.length)`，否则 `[1; n]`（n 是局部）会被普通名字解析放过。

---

**现在的最小动作**：补 `buildConstValue` / `buildMagnitude` 两处 → `make compiler` → 按上面的 `/tmp/x.rx` 验证三类常量上下文都有 suffix。转绿之后我们开始 Step 2 的 `resolveType`。

## 30. 2026-10-25 13:42 — ok

**你**

ok

## 31. 2026-10-25 13:42 — ok，我第一步已经做好了，然后现在在第二部，但是这个resolveType放进去…

**你**

ok，我第一步已经做好了，然后现在在第二部，但是这个resolveType放进去以后有很多报错，是因为比如UnitType还没有定义是吗，这是在哪里处理呢？

## 32. 2026-10-25 13:43 — Try Again

**你**

Try Again

**Copilot**

#### 结论：`UnitType` 已经定义了，是**没 import**

`UnitType`、`RefType`、`ArrayType`、`Type` 都在 `src/main/kotlin/rx/ast/Type.kt` 里（package `rx.ast`），和 `TypeRefNode` 是同一个包、不同文件。你现在的 import 块只引了"源码写法"那一族（`UnitTypeNode` 等），没引"内部类型"那一族。

所以报错的 4 个 unresolved 全是 import 问题，补上就好：

```kotlin
import rx.ast.Type
import rx.ast.UnitType
import rx.ast.BoolType
import rx.ast.I32Type
import rx.ast.U32Type
import rx.ast.IsizeType
import rx.ast.UsizeType
import rx.ast.RefType
import rx.ast.ArrayType
import rx.ast.BoxType
import rx.ast.VecType
import rx.ast.StructType
```

顺带理解一下这两个世界（后面会一直用）：

| | 类 | 用途 |
|---|---|---|
| 源码写法 | `UnitTypeNode` / `RefTypeNode` / `ArrayTypeNode` / `PathTypeNode` | 用户写的那串字，只记录语法 |
| 编译器理解 | `UnitType` / `RefType` / `ArrayType` / `StructType` … | 语义/代码生成用的真类型 |

`resolveType` 的工作就是**后者由前者翻译而来**。名字长得像但后缀不同，这是故意的。

---

#### 另外两个 unresolved 是两回事

##### ① `resolvePathType` 未定义

你写了 `is PathTypeNode -> resolvePathType(ref)`，但现有函数叫 `checkPathType(ref)` 且返回 `Unit`。这不是改个名就完的——要把每个分支从"检查完什么都不返回"改成"返回 `Type`"：

```kotlin
private fun resolvePathType(ref: PathTypeNode): Type {
    val segment = ref.segments.firstOrNull()
        ?: throw SemanticError(ref.span, "empty type path")
    val name = segment.name

    if (name == "Self") {
        val self = currentSelfType
            ?: throw SemanticError(ref.span, "`Self` is only allowed inside a struct or impl")
        if (segment.genericArgs.isNotEmpty())
            throw SemanticError(ref.span, "`Self` takes no type arguments")
        return StructType(self.name, self.node)
    }

    val symbol = globals.lookupType(name)
        ?: throw SemanticError(ref.span, "unresolved type '$name'")

    return when (symbol) {
        is BuiltinTypeSymbol -> when (symbol.kind) {
            BuiltinTypeKind.BOX, BuiltinTypeKind.VEC -> {
                if (segment.genericArgs.size != 1)
                    throw SemanticError(ref.span, "'$name' requires exactly one concrete type argument")
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
            if (segment.genericArgs.isNotEmpty())
                throw SemanticError(ref.span, "struct '$name' has no type parameters")
            StructType(name, symbol.node)
        }

        else -> throw SemanticError(ref.span, "unresolved type '$name'")
    }
}
```

两个细节：

- **把原来的尾部 `for (arg in segment.genericArgs) checkTypeRef(arg)` 删掉**。Box/Vec 已在分支里 `resolveType(arg)` 递归了；原生类型和 structameter 有实参直接报错。留着会对同一个实参检查两次（而且对已报错的分支再走一遍）。
- `StructType(name, symbol.node)` 的第二个参数是 `StructNode?`，正好用 `StructSymbol.node`；`Self` 用 `currentSelfType` 的 `.name` / `.node`。

##### ② `constEval` 未定义 —— 这是"步与步之间的先后"问题

`resolveType` 的数组分支要调 `constEval.requireUsize`，而 `ConstEval` 要调 `resolveType` 取常量声明类型 —— 这两步本质上是**互递归**，不能严格"先 2 后 3"。你现在有两条路：

**路线 A（推荐，先让 build 转绿）**：暂时把数组长度写成占位，等 ConstEval 写好后一行换掉。

```kotlin
is ArrayTypeNode -> ArrayType(
    resolveType(ref.element),
    0L,   // TODO(ConstEval): 改为 constEval.requireUsize(ref.length)
)
```

现在**没有任何代码消费 `ArrayType.length`**（`checkTypeRef` 只是丢掉返回值），所以 `0L` 不会影响任何用例；等 ConstEval 落地后改成 `constEval.requireUsize(ref.length)` 即可。

**路线 B**：直接先写 `ConstEval.kt`（哪怕只是骨架），让 `constEval` 这个名字先存在：

```kotlin
class ConstEval(
    private val globals: Scope,
    private val resolveType: (TypeRefNode) -> Type,
) {
    fun requireUsize(expr: ExprNode): Long { /* … */ }
}

// SemanticAnalyzer 里
private val constEval: ConstEval by lazy { ConstEval(globals, ::resolveType) }
```

`by lazy` 用来打破初始化顺序：只有真正调用 `requireUsize` 时才会构造 `ConstEval`，构造过程本身不会碰 `resolveType`。

⚠️ 如果走 B，**`requireUsize` 不能只支持字面量**。`constants-and-paths/acc-array-lengths-compare-by-value-associated-constants-do-not-reserve-unqualified-locals.rx` 里有 `let a: [i32; COUNT]`（`COUNT` 是常量路径），字面量版会把它判错，accept 会从 69 掉到 68。要么一次把路径也实现了，要么先走 A。

---

#### 现在的最小动作

1. 补 12 个 import。
2. `checkPathType` → `resolvePathType`，按上表给分支返回值；删掉尾部重复递归。
3. 数组分支先写 `0L` + TODO（路线 A），或者按路线 B 同时开 `ConstEval.kt`。
4. `make compiler`。

```
make compiler
python3 scripts/check_semantic.py 2>&1 | tail -5
```

期望：**编译绿，accept 仍 69/69、reject 仍 35/167**。`resolveType` 目前只是"换个返回值"，行为不该有任何变化——如果 reject 数变了，说明有一处 `when` 分支被写成了"总是返回、不再报错"，回头对照原 `checkPathType` 的 4 个 throw 逐条核。

## 33. 2026-10-25 14:37 — requireNoArgs这个函数似乎还没有

**你**

requireNoArgs这个函数似乎还没有

**Copilot**

是我上一条给的分支代码里用了一个没定义的小助手，抱歉——把它补上就行。定义放在 `resolvePathType` 前面/后面都可以：

```kotlin
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
```

参数顺序要和你现在的调用 `requireNoArgs(ref, name, segment)` 对上；`ref.span` 来自 `AstNode`，所以直接能用。

补完之后 `make compiler` 应该只剩 `else ->` 那个"分支看起来不可达"的提示（不是错误，因为 `Symbol` 还有函数/常量/局部三个值符号，编译器要求写 `else`）。

---

顺带一个可选的简化：其实只有 `Box`/`Vec` 接受实参，所以可以把"无实参"检查提到 `when` 之前，五个原生分支就变成光秃秃的一行：

```kotlin
val acceptsArgs = symbol is BuiltinTypeSymbol &&
    (symbol.kind == BuiltinTypeKind.BOX || symbol.kind == BuiltinTypeKind.VEC)

if (!acceptsArgs && segment.genericArgs.isNotEmpty()) {
    throw SemanticError(ref.span, "type '$name' takes no type arguments")
}
```

这样连 `StructSymbol` 分支里那句检查也能省掉。两种都行，`requireNoArgs` 版本的好处是报错信息更精确（"primitive type" / "struct"），runner 只看退出码，所以纯粹看个人口味。

---

编译绿了之后跑一下基线，确认这次重构**行为零变化**：

```
make compiler
python3 scripts/check_semantic.py 2>&1 | tail -5
```

期望仍是 `accept 69/69`、`reject 35/167`。到这里 Step 2 完成——`resolveType` 已经成为"`TypeRefNode` → `Type`"的唯一入口（数组长度暂时是 `0L` + TODO），接下来就进 Step 3：新建 `ConstEval.kt`，然后把那个 `0L` 换成 `constEval.requireUsize(ref.length)`。

## 34. 2026-10-25 14:49 — 有个问题，现在这个resolvePathType是不是还是只能处理单个segem…

**你**

有个问题，现在这个resolvePathType是不是还是只能处理单个segement的情况

**Copilot**

是的，`resolvePathType` 现在只取了 `segments.firstOrNull()`，后面的段被**静默忽略**。这是一个真实的漏洞，只是暂时没有用例踩到。

先看实测（`--entry typeRef --dump-ast`）：

```
Box::<i32>  →  PathTypeNode(segments = ["Box"::<"i32">])   // 1 段
Box<i32>    →  PathTypeNode(segments = ["Box"::<"i32">])   // 1 段
a::B        →  PathTypeNode(segments = ["a", "B"])          // 2 段
```

所以合法的 turbofish 写法 `Box::<i32>` **不受影响**——`::<` 属于同一个 `PathSegment`（文法里 `typePathSegment: pathIdentSegment (PATHSEP? genericArgs)?`）。真正的问题只有"多段"：

- `a::B`：如果 `a` 是 struct，就返回 `StructType("a")`，把 `B` 丢了；
- `Box::<i32>::Foo`：只看第一段的 `genericArgs`，会当成合法的 `Box<i32>` 接受；
- `impl S::Foo`：`checkImpl` 里 `target.segments.firstOrNull()?.name` 拿 `S`，也会放过。

查了一下语料，**语义测试里没有任何合法的多段类型路径**（`(`、`,` 后面跟类型、`->` 返回类型、`let x:` 四种位置搜 `::` 都是 0 命中；`use rx::core::*` 是被丢弃的 use）。原因是 Rx 没有模块系统，struct 只能顶层声明，也没有 associated type，所以"带 `::` 的类型名"永远不成立。

建议在 `resolvePathType` **最开头**（`Self` 判断之前）加 3 行：

```kotlin
private fun resolvePathType(ref: PathTypeNode): Type {
    if (ref.segments.size > 1) {
        throw SemanticError(ref.span, "qualified type paths are not supported")
    }

    val segment = ref.segments.firstOrNull()
        ?: throw SemanticError(ref.span, "empty type path")
    // …以下不变
}
```

放在 `Self` 之前，这样 `Self::Foo` 出现在类型位置也会被拒绝（Rx 没有关联类型，`Self` 只能单独作为类型名）。

另外提醒一个**容易混淆的不对称**，别顺手一起改：

| 位置 | 多段路径 | 处理 |
|---|---|---|
| 类型位置（`resolvePathType`） | 永远非法 | 现在就拒 |
| 表达式位置（`checkMultiSegmentPath`） | 合法（`Config::N`、`Self::B`、`Box::<u32>::new`） | 留给 ConstEval 和调用检查去解析第二段，现在只做"泛型实参"的浅检查是**故意的** |

补完之后：

```
make compiler
python3 scripts/check_semantic.py 2>&1 | tail -5
```

应该仍是 `accept 69/69`、`reject 35/167`——这个检查目前不会让任何用例从 accept 变 reject（语料里没有多段类型路径），它的价值是堵住"静默放过"的口子。

## 35. 2026-10-25 14:57 — 我肯定需要能够判断多段的路径，而且是不是只有一种情况，就是struct的时候可能…

**你**

我肯定需要能够判断多段的路径，而且是不是只有一种情况，就是struct的时候可能出现除了实参之外的segment比如struct套struct的情况，似乎处理起来比较方便

**Copilot**

先说结论：**"能判断多段"≠"要支持多段类型"**。在 Rx 里，多段路径在**类型位置永远无解**，只需要识别出来并报错；真正要"解析第二段"的地方全在**表达式位置**，那是 ConstEval / 调用检查的活。

#### "struct 套 struct" 不会产生多段

这两个写法看起来像"套"，但 AST 里都是一段：

```rust
struct Inner { x: i32 }
struct Outer { inner: Inner }          // 字段类型就一个段：Inner
struct Grid  { cells: Vec<Box<Inner>> } // 一段 Vec，里面递归 genericArgs
```

`Vec<Box<Inner>>` 的结构是：

```
PathTypeNode(segments = [ "Vec"::< PathTypeNode([ "Box"::< PathTypeNode(["Inner"]) > ]) > ])
```

一个 segment + 递归的 `genericArgs`。你的 `resolveType` 里 `resolveType(segment.genericArgs[0])` 已经在正确递归了，**嵌套容器不需要任何多段逻辑**。

多段 `A::B` 的语义只可能是"A 里面的成员类型"。而 Rx 里：

- `struct` 只能顶层声明（`item: ... | structItem | ...`，`associatedItem: constantItem | functionDefinition`）——**impl 里不能放 struct**；
- 没有 associated type、没有 trait、没有模块；
- `use` 声明被整体丢弃，不引入名字。

规范的名字表也写死了：`Prefix of Type::member → Type`，即 `A::B` 的第二段一定是**值**（常量/函数），不可能是类型。所以类型位置的 `A::B` 就是错误。

#### AST 已经能判断多段（实测）

```
Box::<u32>::new(11)  →  PathExprNode(segments = ["Box"::<"u32">, "new"])     // 2 段
Self::LIMIT          →  PathExprNode(segments = ["Self", "LIMIT"])           // 2 段
Config::<i32>::N     →  PathExprNode(segments = ["Config"::<"i32">, "N"])   // 2 段
Box::<u32>  (type)   →  PathTypeNode(segments = ["Box"::<"u32">])            // 1 段
```

`segments: List<PathSegment>` 完整保留了分段信息，`segments.size` 就是判断依据，不需要改 AST。

#### 所以"判断多段"分两个位置，处理方式不同

| 位置 | 例 | 合法性 | 归属 |
|---|---|---|---|
| 类型 | `A::B`、`Self::T`、`impl S::T` | **永远非法** | `resolvePathType` 直接拒 |
| 表达式 | `Config::N`、`Self::LIMIT` | 合法（关联常量） | ConstEval 解析（Step 3） |
| 表达式 | `Point::new`、`Box::<u32>::new` | 合法（关联函数） | 函数签名/调用检查（WP3 之后） |

`checkMultiSegmentPath` 现在做的"第一段必须是类型名 + 后续段不许带泛型实参"是**正确的浅检查**，多段本身不拒绝；第二段到底指向常量还是函数，由后续阶段决定。这个分工不用改。

#### 建议的代码（还是那 3 行，但理解变了）

```kotlin
private fun resolvePathType(ref: PathTypeNode): Type {
    // 类型位置只能写单个类型名；关联项只有常量/函数，没有关联类型。
    if (ref.segments.size > 1) {
        throw SemanticError(ref.span, "qualified type paths cannot name a type")
    }
    // …下面的单段逻辑不变
}
```

注意放在 `Self` 分支**之前**，这样 `Self::T` 也被拒——`Self` 只能单独作为类型名，`Self::N` 是表达式。

#### 一种你可能会担心的"以后要加"

如果哪天要支持 `mod::Struct`（本语言不会），扩展点就在这个函数：把 `segments.size > 1` 的分支换成一个 `resolveQualifiedType`，其余不变。现在提前为它写代码属于过度设计，语料里一个都没有（我按四种类型位置搜过 `::`，0 命中）。

真正的多段解析工作量在 ConstEval：`force(Config::N)` 要区分"第一段是 `Self` 还是 struct 名"、第二段去 `associatedValues` 找 `ConstantSymbol`、还要处理 `Config::<i32>` 这种带实参的第一段（规范不允许 struct 带类型参数，`checkMultiSegmentPath` 已经会拒）。那块我们下一步写。

## 36. 2026-10-25 15:21 — 接下来我们做什么？是不是要新建一个文件了

**你**

接下来我们做什么？是不是要新建一个文件了

**Copilot**

#### 是的，下一步就是新建 `src/main/kotlin/rx/semantic/ConstEval.kt`

不过在写它之前，你的 `SemanticAnalyzer.kt` / `Symbols.kt` 要先各改一小处，否则 `ConstEval` 接不上。顺序是：**先补两个小洞 → 新建文件 → 接线 → 验证**。

---

#### 第 0 步：先补两个已有的洞

##### `Symbols.kt`：`ConstantSymbol` 加 `owner`

```kotlin
class ConstantSymbol(
    override val name: String,
    val node: ConstNode,
    val owner: StructSymbol? = null,   // 顶层常量 = null；impl 里 = 所属 struct
) : Symbol
```

##### `SemanticAnalyzer.checkImpl`：创建关联常量时传 owner

```kotlin
is ConstNode -> ConstantSymbol(
    name = item.name,
    node = item,
    owner = symbol,
)
```

这个 `owner` 是 `Self::LIMIT` 能解析的唯一依据——求值到 `Self::B` 时，不能靠"当前在哪个 impl 里"这种环境变量（递归求值别的常量会串），只能看"**正在求值的这个常量属于谁**"。

---

#### 第 1 步：新建 `ConstEval.kt`

文件骨架长这样（注释标了每块要做什么）：

```kotlin
package rx.semantic

import rx.ast.*

/**
 * 常量求值：三个常量上下文（const 初值、数组长度、repeat 长度）共用。
 *
 * 只接受受限形式：整数/布尔字面量、常量路径、-Magnitude、括号（括号已被 AstBuilder 拆掉）。
 * 状态表用来检测常量依赖环（三色 DFS）。
 */
class ConstEval(
    private val globals: Scope,
    private val resolveType: (TypeRefNode) -> Type,
) {
    sealed interface ConstValue {
        val type: Type

        data class IntValue(
            override val type: IntegerType,
            val value: Long,
        ) : ConstValue

        data class BoolValue(
            val value: Boolean,
        ) : ConstValue {
            override val type: Type get() = BoolType
        }
    }

    private enum class State { IN_PROGRESS, DONE }

    private val states = HashMap<ConstantSymbol, State>()
    private val values = HashMap<ConstantSymbol, ConstValue>()
    private val declaredTypes = HashMap<ConstantSymbol, Type>()

    /** 正在求值的常量所属的 struct；`Self::N` 靠它解析。force 里 save/restore。 */
    private var currentOwner: StructSymbol? = null

    // ------------------------------------------------------------------
    // 对外入口
    // ------------------------------------------------------------------

    /** 阶段 4：强制求值所有常量（包括从未被使用的），错误必须报。 */
    fun evaluateAll(crate: CrateNode) { /* … */ }

    /** 数组长度 / repeat 长度：期望类型 usize，返回 Long。 */
    fun requireUsize(expr: ExprNode): Long {
        val v = evaluate(expr, UsizeType)
        if (v.type != UsizeType) {
            throw SemanticError(expr.span, "array length must have type usize")
        }
        return (v as ConstValue.IntValue).value
    }
```

`force` + `evaluate` + `literalType` + `resolveConstantPath` 的完整规则见下文（这一段是关键，慢慢写）：

```kotlin
    private fun force(symbol: ConstantSymbol): ConstValue {
        when (states[symbol]) {
            State.DONE -> return values[symbol]!!
            State.IN_PROGRESS -> throw SemanticError(
                symbol.node.span, "constant dependency cycle",
            )
            null -> {}
        }

        states[symbol] = State.IN_PROGRESS

        val savedOwner = currentOwner
        currentOwner = symbol.owner
        try {
            val declared = declaredTypes.getOrPut(symbol) {
                resolveType(symbol.node.type)
            }
            val value = evaluate(symbol.node.value, declared)
            if (value.type != declared) {
                throw SemanticError(
                    symbol.node.value.span,
                    "constant initializer must match declared type",
                )
            }
            values[symbol] = value
            states[symbol] = State.DONE
            return value
        } finally {
            currentOwner = savedOwner
        }
    }

    private fun evaluate(expr: ExprNode, expected: Type?): ConstValue = when (expr) {
        is IntLitNode -> ConstValue.IntValue(literalType(expr, expected), expr.value)

        is BoolLitNode -> ConstValue.BoolValue(expr.value)

        is PathExprNode -> {
            val target = resolveConstantPath(expr)
            force(target)          // 递归；环在这里被发现
        }

        is UnaryExprNode -> {
            if (expr.op != UnOp.NEG) {
                throw SemanticError(expr.span, "expected a constant value")
            }
            // 传播排除：右操作数不带 expected
            val operand = evaluate(expr.operand, expected = null) as? ConstValue.IntValue
                ?: throw SemanticError(expr.span, "expected a constant value")
            if (!operand.type.signed) {
                throw SemanticError(expr.span, "negative forms require a signed integer type")
            }
            ConstValue.IntValue(operand.type, -operand.value)
        }

        else -> throw SemanticError(expr.span, "expected a constant value")
    }

    private fun literalType(expr: IntLitNode, expected: Type?): IntegerType =
        when (expr.suffix) {
            "i32"   -> I32Type
            "u32"   -> U32Type
            "isize" -> IsizeType
            "usize" -> UsizeType
            null    -> expected as? IntegerType ?: I32Type   // 期望类型 → 默认 i32
            else    -> throw SemanticError(expr.span, "invalid integer suffix")
        }
```

`resolveConstantPath` 是"多段判断"真正落地的地方：

```kotlin
    private fun resolveConstantPath(node: PathExprNode): ConstantSymbol {
        val segments = node.segments

        if (segments.size == 1) {
            val symbol = globals.lookupValue(segments[0].name)
            return symbol as? ConstantSymbol
                ?: throw SemanticError(node.span, "constant path must resolve to a constant")
        }

        if (segments.size != 2) {
            throw SemanticError(node.span, "constant path must resolve to a constant")
        }

        val head = segments[0]
        if (head.genericArgs.isNotEmpty()) {
            throw SemanticError(node.span, "constant path takes no type arguments")
        }

        val owner = if (head.name == "Self") {
            currentOwner
                ?: throw SemanticError(node.span, "`Self` is not allowed here")
        } else {
            globals.lookupType(head.name) as? StructSymbol
                ?: throw SemanticError(node.span, "constant path must resolve to a constant")
        }

        return owner.associatedValues[segments[1].name] as? ConstantSymbol
            ?: throw SemanticError(node.span, "constant path must resolve to a constant")
    }
```

`evaluateAll` 的遍历方式——**不要**去遍历 `Scope` 的内部 map，直接走 crate：

```kotlin
    fun evaluateAll(crate: CrateNode) {
        for (item in crate.items) {
            when (item) {
                is ConstNode -> {
                    val symbol = globals.lookupValue(item.name) as? ConstantSymbol
                    if (symbol != null) force(symbol)
                }
                is ImplNode -> {
                    val targetName = (item.target as? PathTypeNode)
                        ?.segments?.firstOrNull()?.name
                    val owner = targetName
                        ?.let { globals.lookupType(it) as? StructSymbol }
                        ?: continue
                    owner.associatedValues.values
                        .filterIsInstance<ConstantSymbol>()
                        .forEach { force(it) }
                }
                else -> {}
            }
        }
    }
```

---

#### 第 2 步：在 `SemanticAnalyzer` 里接线（4 处）

**① 加 lazy 属性**（放在字段区）：

```kotlin
private val constEval: ConstEval by lazy { ConstEval(globals, ::resolveType) }
```

`by lazy` 是必须的：`resolveType` 的数组分支要调 `constEval.requireUsize`，两者互递归，只有真正用到时才构造才不会初始化循环。

**② `analyze` 改成阶段顺序**：

```kotlin
fun analyze(crate: CrateNode) {
    installBuiltins(globals)
    collectTopLevel(crate.items)     // 阶段 1
    registerImpls(crate.items)       // 阶段 2 ← 新增，把 checkImpl 提前
    checkEntry(crate)                // 阶段 3
    constEval.evaluateAll(crate)     // 阶段 4 ← 新增
    checkDeclarations(crate.items)   // 之后的阶段
}

private fun registerImpls(items: List<ItemNode>) {
    for (item in items) if (item is ImplNode) checkImpl(item)
}
```

**③ `checkDeclarations` 里删掉 `is ImplNode -> checkImpl(item)`**。这一步很关键：`checkImpl` 会往 `associatedValues` 里注册，跑第二遍会撞上"duplicate associated item"。同理 `is ConstNode -> { /* 暂不检查 */ }` 可以留着空实现或删掉。

**④ 两个长度入口**：

```kotlin
// resolveType 的数组分支：把 0L 换掉
is ArrayTypeNode -> ArrayType(
    resolveType(ref.element),
    constEval.requireUsize(ref.length),
)

// checkExpr 的 ArrayLitNode 分支：替换，不是新增
is ArrayLitNode -> {
    for (element in expr.elements) checkExpr(element)
    expr.repeat?.let {
        checkExpr(it.element)                    // 元素是普通表达式
        constEval.requireUsize(it.length)        // 长度是常量上下文
    }
}
```

**必须删掉 `checkExpr(it.length)`**，否则 `[1; n]`（n 是局部变量）会被普通名字解析放过，`rej-repeat-length-cannot-name-a-local` 就永远过不了。

---

#### 第 3 步：验证

```
make compiler
python3 scripts/check_semantic.py constant-errors constants-and-paths -v
python3 scripts/check_semantic.py 2>&1 | tail -5
```

期望：

| 目录 | 目标 |
|---|---|
| `constant-errors` | reject **9/9** |
| `constants-and-paths` | reject **2/2** |
| 全量 | `accept 69/69` 不变，reject 从 35 → **46** |

如果 accept 掉了，最可能是这两个点：

1. `constants-and-paths/acc-array-lengths-...` 里 `[i32; COUNT]` → `COUNT` 是常量路径，`evaluateAll` 必须先跑完/或 `force` 能递归（它能的）。如果这里报 "constant path must resolve to a constant"，检查 `checkImpl` 是不是真的在阶段 2 跑过了。
2. `methods-and-self/acc-receiver-...` 里有 `impl` 内的 `const START: i32 = 3;`，`evaluateAll` 会去 `globals.lookupType("Counter")` 找 owner，确认 `checkImpl` 提前后能拿到。

##### 一个已知的小限制（现在不用管）

`ConstEval` 调 `resolveType` 时，analyzer 的 `currentSelfType` 是空的。所以 `impl S { const A: Self = ... }` 这种**类型**里写 `Self` 的常量会报 "`Self` is not allowed here"。语料里没有这种写法，而且常量值只能是整数/布尔，`Self` 类型本来就必然类型不匹配。等以后真需要，再在 `force` 里把 analyzer 的 `currentSelfType` 也 save/restore 一下。

---

## 附录：导出对话（2026-10-26）

**你**

请你将我们当前的聊天记录导出到docs，作为AI_CHAT6.md

**Copilot**

已将本会话导出为 `docs/AI_CHAT6.md`（即本文件）。
