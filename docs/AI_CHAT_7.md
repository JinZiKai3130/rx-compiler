# AI_CHAT_7 —— 阶段 7 与阶段 8（语义检查）的对话记录

- 导出日期：2026-11-03
- 仓库：`/home/kai/ACM_class_project/rx-compiler`
- 范围：`docs/AI_CHAT6.md`（2026-10-26 导出）之后的全部对话，2026-10-26 → 2026-11-03
- 内容：按时间顺序导出本对话框中「你」与「Copilot」的消息。
- 说明：为便于阅读，工具调用过程与中间输出未收录；代码与命令均按对话原文保留。
- 排版：为与导出文档自身的章节号区分，正文里的标题已整体下沉两级。

---

## 1. 2026-10-26 21:21 — 接下来我们考虑第七步，需要写什么，然后解决了什么问题

**你**

接下来我们考虑第七步，需要写什么，然后解决了什么问题

**Copilot**

两个问题其实是同一件事：`check()` 里那三行只是**调用**，你还没写**定义**。

#### 1. 这段代码属于 `checkDeriveCapability`

它不是什么"片段插哪儿都行"的代码，它是 7.3 子步骤的函数体。你现在的文件里 `check()` 已经调了三个不存在的成员，所以 IDE 依次报：

| 被调用 | 状态 | 归属 |
|---|---|---|
| `checkDeriveList` | ❌ 未定义 | 7.1 |
| `checkLayout` | ✅ 已有 | 7.2 |
| `checkDeriveCapability` | ❌ 未定义 | 7.3（就是你贴的那段） |
| `supports` / `structSupports` | ✅ 已有 | 7.3 的递归底层 |
| `nameOf` | ❌ 未定义 | 拼消息用 |

`checkDeriveList` "没有出现过"，是因为它在整个工程里**只有 call site、没有 declaration**——搜定义当然搜不到。补上定义就出现了。

#### 2. 补全这三块

放在 `supports` 后面、类结束前即可（顺序无所谓，Kotlin 不在意成员声明顺序）：

```kotlin
/** 7.1 只读 derive 列表本身：重复项 + 隐式前置要求。 */
private fun checkDeriveList(s: StructSymbol) {
    val seen = mutableSetOf<Derive>()
    for (d in s.node.derives) {
        if (!seen.add(d)) {
            throw SemanticError(s.node.span, "duplicate derive '${nameOf(d)}'")
        }
    }
    if (Derive.COPY in seen && Derive.CLONE !in seen) {
        throw SemanticError(s.node.span, "'Copy' requires an explicit 'Clone' derive")
    }
    if (Derive.EQ in seen && Derive.PARTIAL_EQ !in seen) {
        throw SemanticError(s.node.span, "'Eq' requires an explicit 'PartialEq' derive")
    }
}

/** 7.3 逐字段递归验证：本结构体请求的每个 derive，所有字段都要能提供。 */
private fun checkDeriveCapability(s: StructSymbol) {
    for (d in s.node.derives) {
        for (field in s.node.fields) {
            val t = s.fields[field.name] ?: continue
            if (!supports(t, d)) {
                throw SemanticError(
                    field.type.span,
                    "field '${field.name}' of struct '${s.name}' does not support derive '${nameOf(d)}'",
                )
            }
        }
    }
}

private fun nameOf(d: Derive): String = when (d) {
    Derive.COPY -> "Copy"
    Derive.CLONE -> "Clone"
    Derive.PARTIAL_EQ -> "PartialEq"
    Derive.EQ -> "Eq"
}
```

三个设计点，别写错：

- **`checkDeriveList` 只看 `node.derives`**，不碰 `fields`。所以它能在阶段 5 之前就成立，但为了和内聚性一致还是放这里；`Copy⇒Clone` / `Eq⇒PartialEq` 是"请求本身不自洽"，和能力无关。
- **`checkDeriveCapability` 用 `s.node.fields` 而不是 `s.fields.values`**：前者的顺序和 span 都在，报错才能指到 `x: Inner` 这一行；`s.fields` 只用来取已经解析好的 `Type`。
- **span 选 `field.type.span`**（类型的位置），不是 `s.node.span`。`rej-every-field-must-support-the-requested-derive` 会指到 `Outer` 的 `x: Inner`。
- `?: continue` 是防御：阶段 5 正常跑完时 `fields` 一定齐全，但别让阶段 7 因为一次 `null` 崩成 NPE。

#### 3. 还有一个阻塞点：类名笔误

文件名是 `GloabalConstraints.kt`、类名是 `GloabalConstraints`，而 `SemanticAnalyzer.kt` 第 40 行写的是 `GlobalConstraints(ctx)`——所以现在连编译都过不了：

```
SemanticAnalyzer.kt:40  Unresolved reference: GlobalConstraints
```

把文件改名、类名同步改成 `GlobalConstraints`（`gloabal` → `global`）就对齐了：

```bash
mv src/main/kotlin/rx/semantic/GloabalConstraints.kt src/main/kotlin/rx/semantic/GlobalConstraints.kt
```

> 顺带：`is BoxType, is VecType -> supports((type as? BoxType)?.element ?: (type as VecType).element, d)` 能编译但可读性差——多条件 `is` 分支不会 smart cast，只能靠显式转换。拆成两个分支更清楚：
> ```kotlin
> is BoxType -> d != Derive.COPY && supports(type.element, d)
> is VecType -> d != Derive.COPY && supports(type.element, d)
> ```

#### 4. 验证

```bash
make compiler && python3 scripts/check_semantic.py recursive-layout recursive-traits copy-clone -v
```

期望：`recursive-layout` reject **4/4**、`recursive-traits` **1/1**、`copy-clone-and-equality` **7/9**（剩下 2 个是阶段 8 的 `.clone()` / `==`）。然后跑一次全量，确认 `accept 69/69`、`reject 58/167`。

## 2. 2026-10-27 12:29 — structSupports和checkDeriveCapability有重复啊

**你**

structSupports和checkDeriveCapability有重复啊

**Copilot**

批次 A 一共 9 步，每步都能单独编译。按顺序做，中间任何一步卡住只影响后面几步。

---

#### 第 1 步：`ExprStmtNode` 记录分号（AST）

```
src/main/kotlin/rx/ast/Stmts.kt:15
```
加一个字段：

```kotlin
class ExprStmtNode(
    val expr: ExprNode,
    val hasSemicolon: Boolean,   // ← 新增
    span: Span,
) : StmtNode(span)
```

两处构造点：

| 位置 | 取值 |
|---|---|
| `AstBuilder.kt:407`（`expressionWithBlock` 分支） | `ctx.SEMI() != null` |
| `AstBuilder.kt:416`（`statementExpression` 分支） | `true` |

**为什么**：`if true {10} else {20};`（非尾、有分号）合法，`{ 1 }`（非尾、无分号）必须 unit。两者在 AST 上原本一模一样。

---

#### 第 2 步：`UnknownType`（脚手架哨兵）

```
src/main/kotlin/rx/ast/Type.kt
```

```kotlin
/** 脚手架：尚未实现类型推导的表达式。批次 F 结束后必须零处产生。 */
data object UnknownType : Type { override fun toString() = "?" }
```

放 `rx.ast` 是因为 `Type` 是 `sealed interface`，实现类必须在同包同模块。

**为什么**：批次 A 不实现运算符/调用/字段，如果它们返回 `UnitType`，`if` 的条件检查和分支 `join` 会立刻红掉一批 acc。

---

#### 第 3 步：新建 `Coercion.kt`

```
src/main/kotlin/rx/semantic/Coercion.kt
```

三个顶层函数：

```kotlin
internal fun assignable(from: Type, to: Type): Boolean
internal fun join(a: Type, b: Type, span: Span, what: String): Type   // 不兼容时抛错
internal fun requireAssignable(from: Type, to: Type, span: Span, what: String)
```

规则（批次 A 只要这几条）：

| from → to | 结果 |
|---|---|
| `from == to` | ✔ |
| 任一侧是 `UnknownType` | ✔（脚手架放行） |
| `&mut T` → `&T` | ✔ |
| 其余 | ✘ |

`join` 的优先级：`!` 吸收 → `Unknown` 吸收 → 相等 → 否则抛 `SemanticError(span, "'$what' 类型不一致: $a vs $b")`。

---

#### 第 4 步：`LocalSymbol` 补类型

```
src/main/kotlin/rx/semantic/Symbols.kt:57
```

```kotlin
class LocalSymbol(
    override val name: String,
    val mutable: Boolean,
    val type: Type,          // ← 新增
    val span: Span?,
) : Symbol
```

两处构造点要改：`BodyChecker.kt:67`（参数）、`BodyChecker.kt:102`（let）。

- 参数：`ctx.resolveType(param.type)`
- let：用第 7 步算出的绑定类型

**为什么**：路径表达式要返回类型，局部变量没有类型就推不下去（`let a = 1; a + 1`）。

---

#### 第 5 步：两个新上下文

```
src/main/kotlin/rx/semantic/SemanticContext.kt
```

```kotlin
var currentReturnType: Type = UnitType                                  // 阶段 8
val loopStack: ArrayDeque<LoopContext> = ArrayDeque()                    // 阶段 8

class LoopContext(
    val acceptsValue: Boolean,   // true = loop，false = while
    val expected: Type?,         // 外层给的目标类型（let 注解等）
) {
    val breakTypes = mutableListOf<Type>()
}
```

`currentReturnType` 有默认值，所以构造时不用管。

---

#### 第 6 步：`ExprChecker` 换签名，其余分支先返回 `Unknown`

```
src/main/kotlin/rx/semantic/ExprChecker.kt
```

```kotlin
fun check(expr: ExprNode, expected: Type? = null): Type
```

做法：

1. 每个分支算出 `val t: Type`，返回前 `expr.type = t`。
2. 已经能确定的：`IntLitNode`、`BoolLitNode`、`UnitLitNode`。
3. `IntLitNode` 的选型：`suffix` 优先 → 没有就用 `expected as? IntegerType` → 再兜底 `I32Type`。（和 `ConstEval.literalType` 逻辑一样，先复制，后面再抽公共函数。）
4. **其余分支保持现在的递归，`t = UnknownType`**（`Block`/`If`/`While`/`Loop`/`Break`/`Continue`/`Return`/`Path` 留到第 8、9 步再填）。
5. 现有的名字解析、可调用性检查、泛型实参检查全部保留。

---

#### 第 7 步：`BodyChecker` 接上返回类型和块类型

```
src/main/kotlin/rx/semantic/BodyChecker.kt
```

1. `checkFunctionBody`：
   ```kotlin
   val ret = node.returnType?.let { ctx.resolveType(it) } ?: UnitType
   // 保存/恢复 currentReturnType
   val bodyType = checkBlock(node.body, expected = ret)
   requireAssignable(bodyType, ret, node.body.span, "function body")
   ```
2. `checkBlock(block, expected: Type? = null): Type`：
   ```kotlin
   val tailType = block.tail?.let { exprs.check(it, expected) } ?: UnitType
   return tailType
   ```
3. `checkStatement` 的 `LetNode`（顺序要反过来）：
   ```kotlin
   val declared = stmt.declaredType?.let { ctx.resolveType(it) }
   val initType = exprs.check(stmt.initializer, declared)
   if (declared != null) requireAssignable(initType, declared, stmt.initializer.span, "let initializer")
   ctx.currentScope.defineLocal(LocalSymbol(stmt.name, stmt.mutable, declared ?: initType, stmt.span))
   ```
4. `ExprStmtNode` 的 unit 规则：
   ```kotlin
   val t = exprs.check(stmt.expr, expected = if (stmt.hasSemicolon) null else UnitType)
   if (!stmt.hasSemicolon) requireAssignable(t, UnitType, stmt.span, "block statement")
   ```

---

#### 第 8 步：控制流分支（核心）

```
src/main/kotlin/rx/semantic/ExprChecker.kt 的 check()
```

| 节点 | 规则 | 类型 |
|---|---|---|
| `BlockExprNode` | 委托 `body.checkBlock(expr, expected)` | 块类型 |
| `IfExprNode` | 条件 `requireAssignable(cond, BoolType)`；两支都传 `expected`；有 else → `join(then, else)`；**无 else → then 必须 unit** | 同左 |
| `WhileExprNode` | **先**算条件（此时尚未 push）；条件必须 bool；push(`LoopContext(acceptsValue=false, expected)`)；body 必须 unit；pop | 恒 `UnitType` |
| `LoopExprNode` | push(`acceptsValue=true`)；body 必须 unit；pop 后取 `breakTypes` 的 join，空则 `NeverType` | join / `!` |
| `BreakExprNode` | `loopStack.lastOrNull() ?: 报错"break outside a loop"`；有载荷时：`acceptsValue` 为 false → 报错；否则 `requireAssignable(payload, expected)` 并记入 `breakTypes` | `NeverType` |
| `ContinueExprNode` | 必须在循环内（`loopStack` 非空） | `NeverType` |
| `ReturnExprNode` | 载荷（无则 unit）`requireAssignable(payload, ctx.currentReturnType)` | `NeverType` |

两个细节：

- **body 必须 unit 要用 `assignable`，不是 `==`**：`loop { break 7; }` 的 body 尾表达式是 `break`，类型 `!`，必须放行。
- `push`/`pop` 用 `try/finally`，异常时也要弹。

---

#### 第 9 步：路径表达式返回真实类型

```
src/main/kotlin/rx/semantic/ExprChecker.kt
```

| 情形 | 类型 |
|---|---|
| 单段 = 局部/参数（`LocalSymbol`） | `symbol.type` |
| 单段 = `ConstantSymbol` | `ctx.resolveType(symbol.node.type)` |
| 单段 = `FunctionSymbol` / `BuiltinFunctionSymbol` | `UnknownType`（调用是批次 D） |
| `self` | `UnknownType`（字段访问是批次 E） |
| 多段 `S::C` / `Self::C`（关联常量） | `ctx.resolveType(constSymbol.node.type)` |
| 多段 `S::f`（关联函数）、`Box::<T>::new` | `UnknownType` |

`S::C` 的查法：`first.name`（或 `Self` → `ctx.currentSelfType`）→ `StructSymbol.associatedValues[name]`。

---

#### 收尾：验收

```bash
make compiler
python3 scripts/check_semantic.py blocks-if-and-never loops-and-jumps unreachable-checks -v
python3 scripts/check_semantic.py
```

期望：

| 目录 | 目标 |
|---|---|
| `blocks-if-and-never` | 5/5 |
| `loops-and-jumps` | 7/7 |
| `unreachable-checks` | 4/4（`rej-unknown-name-after-return` 已过） |
| `expected-types` | 4 个里顺带过 2 个（if 目标、loop 目标）→ 2/4 |
| 全量 | **accept 69/69**、reject **58 → 75** |

排错顺序建议：先只跑 `blocks-if-and-never`（纯 if/块/return，不涉及循环栈），绿了再加 `loops-and-jumps`，最后 `unreachable-checks`。

---

#### 三个最容易翻车的点

1. **`while` 的条件在循环之外**：先算条件、再 push。否则 `while break {}` 会被误判合法。
2. **不做不可达剪枝**：`return` 之后、`if false` 分支里都要照常检查。
3. **`assignable` 放行 `Unknown` 必须双向**（`Unknown → X` 和 `X → Unknown`），否则 `let v = if x < 0 { return 90 } else { 7 }` 这类 acc 会红。

写完第 6 步就可以 `make compiler` 跑一次全量——此时应该还是 69/69、58/167（只换了签名，没加规则）。这个"零变化"检查点很重要。

## 3. 2026-10-28 14:03 — 请你把这一部分直接补在工作区中，我等一下学习一下

**你**

请你把这一部分直接补在工作区中，我等一下学习一下

**Copilot**

#### 先给结论

你的观察是对的：**多的这一层在语义上不是必需的**。今天删掉它，`accept 69/69` 和 `reject 80/167` 一个都不会变。它只是一个"建模选择"，不是正确性要求。

#### 为什么现在长这样

原因按硬度排序：

**1. `checkBlock` 是通用函数，它无条件开一层。**
`checkBlock` 被 `if` 分支、`while`/`loop` 体、嵌套 `{}`、函数体共用，它不关心"我是函数体还是普通块"，所以一律 `savedScope.child()`。而函数体恰好就是一个 `BlockExprNode`——如果不想多这一层，就得给 `checkBlock` 加一个"这次别开作用域"的开关，或者把块逻辑复制一份进 `checkFunctionBody`。两种都比多开一层脏。

**2. 参数和局部变量是两种绑定形式。**
参数走 `declareValue`（"本层已存在 → 返回 false"），`let` 走 `defineLocal`（"允许同层覆盖"）。分成两层，两种语义各自成立、互不干扰。

> 但要说实话：这条现在**没实际承担工作**——参数重名在阶段 6 的 `FunctionResolver:62-71` 就用 `HashSet` 查过了，`checkFunctionBody` 里 `declareValue` 的返回值根本没接。所以它只是"结构对称"，不是必需。

**3. 作用域树和语法结构一一对应。**
`globals → fn(参数) → body 块(局部) → 嵌套块`，报错时能一眼说出"现在在哪一层"。将来加"未使用变量"、"参数 vs 局部"诊断，或者把 `self` 绑定成 `LocalSymbol`（它属于参数层而不是 body 层）时，有地方挂。

**4. 恢复逻辑各自独立**：`checkFunctionBody` 的 `finally` 负责参数层，`checkBlock` 的 `finally` 负责块层，互相不依赖。

#### 今天确实没有可观测差异

你担心的"参数只服务 body"是对的，而两层的效果和一层完全一致：

```rust
fn f(x: i32) -> i32 {
    let x = x + 1;      // ① 遮蔽参数：defineLocal 同层覆盖 → 一层两层都行
    {
        let y = x;      // ② 嵌套块看参数：沿 parent 上溯 → 一层两层都行
        println_i32(y);
    }
    x
}
```
→ `exit 0`

关键在 `defineLocal`：它不查重、直接覆盖，所以"遮蔽"不依赖父子层。如果 `let` 用的是 `declareValue`，那两层就是**必需**的（否则 `let x` 撞参数会报重名）——现在不是。

#### 如果你想合并成一层

最小改法是把 `checkBlock` 拆成"开作用域"和"检查内容"两半：

```kotlin
internal fun checkBlock(block: BlockExprNode, expected: Type? = null): Type {
    val saved = ctx.currentScope
    ctx.currentScope = saved.child()
    try {
        return checkBlockContents(block, expected)
    } finally {
        ctx.currentScope = saved
    }
}

/** 不开新作用域，直接在当前层检查语句和尾表达式。 */
private fun checkBlockContents(block: BlockExprNode, expected: Type?): Type {
    // 原 checkBlock 的语句循环 + 尾表达式 + diverges 逻辑
}
```

然后 `checkFunctionBody` 里把 `checkBlock(node.body, ret)` 换成 `checkBlockContents(node.body, ret)`，参数和 body 的 `let` 就共用 `globals.child()` 那一层。

代价：
- 少一个 `LinkedHashMap`（编译期可忽略）；
- 失去"参数层/局部层"的区分，将来做未使用变量统计时要自己标记；
- `checkBlock` 多一个 `private` 兄弟函数，两个函数的职责边界要写注释，否则下一个人不知道该调哪个。

#### 我的建议

**保留现状**。理由很实际：多一层的成本是一次空哈希表；而合并会把"块"这一个概念拆成两个入口（`checkBlock` / `checkBlockContents`），这种"为了省一层而制造两个相似函数"的取舍，后面读代码的人更容易踩错。

如果你觉得这个不对称必须消除，那也不要在语义层补，而是在 `checkBlock` 的文档里写清"函数体额外有一层参数作用域"，把设计意图留下来就够了：

```kotlin
/**
 * 块作用域：进入压一层、退出弹回。
 * 注意：函数体的块是"参数作用域"的子作用域（见 [checkFunctionBody]），
 * 因此参数与 body 的 let 不共享一层——遮蔽由 defineLocal 保证，
 * 互不影响由 parent 链保证。
 */
```

## 4. 2026-10-28 19:47 — 我又发现一个问题，如果一个函数的block内有一个分支，在某种情况下return…

**你**

我又发现一个问题，如果一个函数的block内有一个分支，在某种情况下return，returncheck的返回类型为NeverType，然后反馈到#sym:ExprStmtNode 就是NeverType，但是如果这个block有尾表达式，这种情况下，似乎应该返回时尾表达式的类型，但是按照目前的代码逻辑，似乎返回的是NeverType，你看看呢？

**Copilot**

你发现的确实是一个真问题：之前的代码**把尾表达式的类型丢掉了**。我已按你的判断改掉，并验证过。

#### 问题确认

旧逻辑：

```kotlin
val tailType = block.tail?.let { exprs.check(it, expected) } ?: UnitType
return if (diverges) NeverType else tailType      // ← 有尾也返回 !
```

所以 `{ return 7; 9 }` 这种块，`9` 明明被检查了、类型是 `i32`，但块最终被判成 `!`。

#### 但"永远返回尾类型"也不行

因为有一条**必需**的反例，就是批次 A 的 acc 用例：

```rust
let v = if x < 0 {
    return 90;      // 没有尾表达式，块内发散
} else { 7 };       // i32
```

这个 then 块必须被判成 `!`，否则 `join(!, i32)` 变成 `join((), i32)` → 直接红。

所以规则要**分两种情况**：

| 块 | 有尾表达式 | 内部发散 | 块类型 |
|---|---|---|---|
| `{ }` | 否 | 否 | `()` |
| `{ return 90; }` | 否 | 是 | `!` |
| `{ 1 }` | 是 | 否 | 尾类型 |
| `{ return 1; 2 }` | 是 | 是 | **尾类型**（← 你指出的情况） |

#### 改法（`BodyChecker.checkBlock`）

```kotlin
var diverges = false
for (stmt in block.statements) {
    if (checkStatement(stmt)) diverges = true
}

val tail = block.tail
if (tail != null) return exprs.check(tail, expected)   // 有尾：类型就是尾

return if (diverges) NeverType else UnitType            // 无尾：发散 → !
```

#### 为什么这是"正确"的一侧（而不是随便挑一个）

两种规则都自洽，但证据指向你的版本：

1. `unreachable-checks` 整套用例的主题就是**不可达代码照样参与类型**：`rej-unknown-name-after-return`（名字）、`rej-wrong-type-in-an-untaken-branch`（类型）、`rej-unreachable-break-participates-in-loop-result-typing`（break 载荷）。尾表达式的类型不参与，和这个主题是矛盾的。
2. 用例名 `acc-a-**valid**-unreachable-tail-is-allowed`——强调"**合法的**不可达尾是允许的"，言下之意是不合法的要拒。
3. Rust 也是这个规则：`fn f() -> i32 { return 7; true }` 会报类型错误，而 `fn f() -> i32 { if c { return 1 } else { 2 } }` 能过（因为那时 then 块**没有尾**）。

#### 验证

```
make compiler                 → 通过
python3 scripts/check_semantic.py → accept 69/69, reject 80/167, 67s   ← 与改前完全一致
```

也就是说**现有语料里没有一条用例能区分这两种规则**，改动是"纯语义修正"，没有副作用。三个针对性探针：

| 探针 | 结果 |
|---|---|
| `fn f() -> i32 { return 7; true }` | exit 1 `function body must have type i32, found bool`（旧版会误收） |
| `fn f() -> i32 { return 7; 9 }` | exit 0 ✓（`acc-a-valid-unreachable-tail-is-allowed` 的行为） |
| `let v = if x < 0 { return 90; } else { 7 };` | exit 0 ✓（无尾发散块仍是 `!`，`join` 正常） |

规则已写进 `docs/expr-checking.md` §3 第 8 条。

> 附一句：`checkStatement` 返回的"这条语句之后是否不可达"仍然用 `type == NeverType` 判定，所以 `{ return 1; 2 };`（带分号）这种语句现在不再被算作发散（块类型是 i32）——这跟 Rust 一致：带分号时值被丢弃，但它**不是**一个发散语句。

## 5. 2026-10-28 19:57 — 好的，接下来我们应该写什么部分？

**你**

好的，接下来我们应该写什么部分？

**Copilot**

批次 A 已收尾（`accept 69/69`、`reject 80/167`）。下一步是**批次 B：所有运算符**，我先把用例翻了一遍，有两条规则和我之前给你的表**不一样**，必须先纠正。

#### 批次 B 范围

| 目录 | 剩余 reject |
|---|---|
| `scalar-reference-operators` | 4 |
| `boolean-and-short-circuit` | 3 |
| `integer-arithmetic` | 3 |
| `shifts` | 2 |
| `casts-and-literals`（顺手） | 2 |
| **合计** | **14 + 2 = 16** → 全量 96/167 |

#### 两条必须修正的规则

**① 移位计数可以是任意整数类型，不要求和左操作数同型。**

这些全是 **accept**：

```rust
3i32 << 33u32          // 左 i32，计数 u32
-8i32 >> 34usize       // 左 i32，计数 usize
0x8000_0000u32 >> 33isize
let b = get_i32(); a << b
```

所以移位是唯一"两侧类型可以不相等"的算术类运算符：**左必须是整数，右必须是整数（任意），结果 = 左的类型**。

**② bool 支持位运算和比较，只是不支持算术。**

```rust
mark(9, false) & mark(10, true)   // bool & bool → bool
true ^ false
false < true                       // bool 有序！
true >= false
true + false                       // ← 只有这个错
```

#### 运算符矩阵

记 `strip(x)` = 剥掉**一层共享引用** `&T → T`；遇到 `&mut T` 直接拒绝（`-&mut a` 就是用例），两层引用剥一层后仍是引用，自然过不了整数检查。

| 运算符 | 两侧要求 | 结果 | 备注 |
|---|---|---|---|
| `+ - * / %` | `strip` 后都是**整数**且**同型** | 左的类型 | `1i32 + 2u32` 拒绝；`&a + &b`、`&a * b` 合法 |
| `& \| ^` | `strip` 后同型，且是**整数或 bool** | 左的类型 | `true & 1i32` 拒绝 |
| `<< >>` | 左整数；右整数（**不要求同型**） | 左的类型 | `1i32 << true`、`false >> 1i32` 拒绝 |
| `&& \|\|` | 两侧都**严格 bool**，**不剥引用** | `bool` | `&true && true` 拒绝 |
| `== !=` | 两侧同型（`&mut T` ↔ `&T` 允许弱化） | `bool` | 容器/结构体要 `PartialEq` → 批次 F |
| `< <= > >=` | **左先定型，右向左边靠**；两侧都是整数/布尔，或同深度的引用 | `bool` | `&mut a < &b` 拒绝、`&a < &mut b` 合法、`a < &a` 拒绝 |
| 一元 `-` | `strip` 后是**有符号**整数 | 该整数 | `-1u32` 拒绝 |
| 一元 `!` | `strip` 后是整数或 bool | 该类型 | 整数是按位取反 |
| 一元 `*` | 批次 C（place）再管 | `Unknown` | 先只递归 |
| `as` | 目标必须是**整数类型**；源必须是**整数或 bool** | 目标类型 | `1 as bool`、`&x as i32` 拒绝；`bool as i32` 合法 |

#### 实现清单

**1. `ExprChecker` 里替换 3 个 `UnknownType` 分支**

```kotlin
is UnaryExprNode -> checkUnary(expr, expected)
is BinaryExprNode -> checkBinary(expr, expected)
is CastExprNode -> checkCast(expr)
```

**2. `checkBinary` 的关键顺序（别写反）**

```kotlin
val leftRaw = check(node.left, expected)                    // 期望类型先传给左
val left = stripSharedRef(leftRaw, node.left.span, "left operand")
val rightRaw = check(node.right, left.takeIf { it is IntegerType })  // 用"剥过"的左边当期望
val right = stripSharedRef(rightRaw, node.right.span, "right operand")
```

顺序错在两点：
- **左边要吃到 `expected`**，否则 `let x: u32 = 1 + 2;` 会把两个字面量都推成 i32；
- **右操作数的期望要用剥过引用的左类型**，否则 `&a + 1`（a: u32）会把 `1` 推成 i32 而误报。

**3. `stripSharedRef` 放 `Coercion.kt`**

```kotlin
internal fun stripSharedRef(type: Type, span: Span, what: String): Type = when {
    type == UnknownType -> UnknownType
    type is RefType && !type.mutable -> type.inner        // 只剥一层
    type is RefType -> throw SemanticError(span, "$what does not accept a mutable reference")
    else -> type
}
```

**4. 比较运算符单独一条路（不对称）**

```kotlin
// < <= > >=
val leftType = check(node.left, expected)          // 不剥、先定型
val rightType = check(node.right, leftType)        // 右边向左边调整
requireAssignable(rightType, leftType, node.right.span, "right operand")   // &i32 → &mut i32 走不通
requireComparable(leftType)                        // 去掉所有引用层后必须是整数或 bool
```

`&&a < &&b` 靠"去掉所有引用层后是 i32"过；`a < &a` 靠 `assignable(&i32, i32) == false` 拒。

**5. `checkCast`**（顺手拿 2 个）

```kotlin
val target = ctx.resolveType(node.targetType)
if (target !is IntegerType) throw SemanticError(node.targetType.span, "casts must target an integer type")
val source = check(node.expr)
if (source != UnknownType && source !is IntegerType && source != BoolType)
    throw SemanticError(node.expr.span, "only integers and bool can be cast")
target
```

#### 验收

```bash
make compiler
python3 scripts/check_semantic.py scalar-reference-operators boolean-and-short-circuit integer-arithmetic shifts casts-and-literals -v
python3 scripts/check_semantic.py
```

期望：21/21 reject（4+3+3+2+2 里那 5 个已经过的保持）、**accept 69/69**、全量 **96/167**。

#### 风险点

1. **别把 `&&a` 的层数检查写成显式计数**——`strip` 一次后剩 `&i32`，整数检查自然失败，逻辑更短。
2. **`&& \|\|` 千万别复用 `stripSharedRef`**，否则 `&true && true` 会被误放。
3. **移位不能用"两侧相等"的通用检查**，否则 `3i32 << 33u32` 这个 acc 会红。
4. 实现完先跑 `integer-arithmetic`（4 个 acc，最容易暴露类型推导顺序问题），再跑其余三个。

要我直接把这批写进工作区吗？大概动 `ExprChecker`（+3 个函数）、`Coercion`（+2 个 helper），不新增文件。

## 6. 2026-10-28 20:18 — 请你写一下这三个check函数，不要写在文件里，先写在对话框中

**你**

请你写一下这三个check函数，不要写在文件里，先写在对话框中

**Copilot**

这三个失败全部来自**引用**——批次 B 给 `&expr` 赋予了真实类型 `RefType`，于是之前一直处于"未被触发"状态的引用规则突然变成了承重结构，而 `Coercion.kt` 还停在批次 A 的简化版本。

#### 三个失败的具体原因

**① `reference-lub` / `runtime-value-merges`：`if` 分支是两种引用**

```rust
let r = if first { &mut a } else { &b };     // &mut i32 ⊔ &i32
```

`join` 只有 `a == b` 一条分支，遇到 `&mut i32` 与 `&i32` 直接抛错。这就是引用 LUB 缺失。

**② `reference-coercions`：内层是未知类型**

```rust
let mut b = Box::<Box<i32>>::new(...);   // b: ?（Box::new 尚未定型）
let r: &i32 = &b;                        // 初始化器类型是 &?
```

`assignable` 里引用分支写的是 `from.inner == to.inner`，`?` 和 `i32` 用 `==` 比当然不等。内层应该按**可赋值性递归**（而 `assignable(?, i32)` 本来就是放行的）。

#### 改了什么（都在 `Coercion.kt`，共 4 行）

```kotlin
// assignable：可变性只能弱化，内层递归
from is RefType && to is RefType ->
    (!to.mutable || from.mutable) && assignable(from.inner, to.inner)

// join：引用 LUB，&mut T ⊔ &T = &T
a is RefType && b is RefType ->
    RefType(a.mutable && b.mutable, join(a.inner, b.inner, span, what))
```

两个方向都验证过：

| 用例 | 结果 |
|---|---|
| `&mut T → &T` | ✔（`!to.mutable` 为 true） |
| `&T → &mut T` | ✘ → `rej-shared-reference-cannot-become-mutable` 仍拒 |
| `&mut &i32` → `&mut &mut i32` | ✘（内层递归拒绝）→ `rej-inner-reference-mutability-is-not-rewritten` 仍拒 |
| `join(&i32, &u32)` | 内层 `join` 抛错 → `reference-lub/rej-...` 仍拒 |
| `join(&mut i32, &i32)` | `&i32` |

#### 现在的成绩

```
accept: 69/69 passed
reject: 96/167 properly rejected        ← 80 → 96，批次 B 的目标分毫不差
```

批次 B 的五个目录全部收口：

| 目录 | accept | reject |
|---|---|---|
| `integer-arithmetic` | 4/4 | 4/4 |
| `boolean-and-short-circuit` | 1/1 | 4/4 |
| `shifts` | 5/5 | 2/2 |
| `scalar-reference-operators` | 2/2 | 4/4 |
| `casts-and-literals` | 3/3 | 4/4 |

顺带 `references-and-mutability` 从 0/6 变成 1/6（`rej-scalar-values-cannot-be-dereferenced` 之外的那条被新的引用规则顺出来了）。

#### 一个值得记住的教训

**给一个节点补上真实类型，会连带激活所有依赖它的规则。** `&expr` 从 `Unknown` 变成 `RefType` 之后，`assignable` / `join` 里"引用"这两个分支立刻从死代码变成必答题。下一批（place）会遇到同样的情况：一旦 `*p` 有了真实类型，Box/Vec 的解引用、可变性链都会一起被激活。

#### 下一步

批次 C：`Place` + 赋值/复合赋值 + 引用与解引用 + 可变性链，目标 `references-and-mutability` 5、`compound-assignment` 4、`evaluation-order-and-temporaries` 2、`expected-types/rej-assignment-cannot-change-a-binding-s-type` 1，约 +12。这批要新建 `Place.kt`，是阶段 8 里第一个真正"新机制"的批次。

## 7. 2026-10-29 14:00 — 这个place是做什么的

**你**

这个place是做什么的

**Copilot**

#### 一句话

**类型回答"这个表达式算出什么值"，place 回答"这个表达式指向哪块内存、我能不能改它"。** 这是两条独立的判定通道。

#### 为什么光有类型不够

三个反例，都在批次 C 的用例里：

```rust
1 = 2;            // 1 的类型是 i32，和左边"类型一致"，但它根本不是一块可写内存
(x + 1) = 2;      // 同理：算出来的临时值不能当赋值目标
let p = &x;
*p = 2;           // *p 的类型是 i32、p 的类型是 &i32，类型都对，但 p 指向的是共享引用 → 不可写
```

再看两个"类型都合法、只有一个能写"的对照：

```rust
let x = 1;        x += 1;   // ✘ 绑定不可变
let mut x = 1;    x += 1;   // ✔
```

这些都不是类型能回答的，所以需要一个**第二个判定函数**。

#### Place 是什么

```kotlin
class Place(
    val type: Type,          // 这个位置里存的是什么类型
    val mutable: Boolean,    // 能不能写
)
fun checkPlace(expr: ExprNode): Place?    // null = 不是左值（place expression）
```

四个消费方：

| 语法 | 要求 |
|---|---|
| `place = value` | 是 place + `mutable` + value 可赋给 place 的类型 |
| `place += value` | 是 place + `mutable` + 两边**同型**（不做隐式转换） |
| `&mut place` | 是 place + `mutable`（`&place` 只要求是 place） |
| `receiver.method()` 且方法是 `&mut self` | receiver 是 place + `mutable`（批次 D） |

#### 传播规则（核心）

| 表达式 | 是 place？ | 类型 | 可变性 |
|---|---|---|---|
| 局部变量 / 参数 / `self` | ✔ | 符号上的类型 | `let mut` / `&mut self` |
| `*p` | `p` 的类型是 `&T`/`&mut T`/`Box<T>` 时 ✔ | 去掉一层 | 看下面 |
| `e.f` | ✔（`e` 可以是右值！） | 字段类型 | 继承 `e` |
| `e[i]` | ✔（`e` 可以是右值！） | 元素类型 | 继承 `e` |
| 字面量、运算、调用、`if`、块… | ✘ | — | — |

**关键点 1：`e.f` / `e[i]` 的基座可以是右值（临时量），而临时量是可写的。** 这两条是 acc，不是 rej：

```rust
make().x = mark(41);                    // 字段访问右值的字段并赋值
make_array(30)[0] = mark(11);           // 下标访问临时数组并赋值
let field = &mut make_record(50).data[0];   // 对临时量的字段取 &mut
```

所以 `checkPlace(e.f)` 里不能要求 `checkPlace(e) != null`，而要用"**基座可写性**"：`e` 是 place 就用它的可变性，`e` 是右值就当作 `mutable = true` 的临时量。

**关键点 2：`*p` 的可变性不是"p 能不能重新绑定"，而是"**这条链上有没有经过 `&`**"。**

这四条必须同时满足，是测试里最容易被绕晕的地方：

```rust
let mut x = 1;
let p = &mut x;
*p += 5;            // ✔ p 自己不可重新绑定，但 &mut 允许写穿透
p = &mut y;         // ✘ 重新绑定 p 需要 p 本身是 mut

let q = &p;         // q: &&mut i32
**q = 2;            // ✘ 外层 & 一出现，整条链降级为只读

let r = &x;
*r = 2;             // ✘ 共享引用
```

所以 `Place` 实际需要第三个信息：**这个位置是不是位于某个 `&` 之下**（`underShared`）。

- 局部变量：`underShared = false`
- `*p`：若 `p.type` 是 `&mut T` → `mutable = !p.underShared`；`underShared` 继续往下传
- `e.f` / `e[i]`：`underShared` 继承基座

这样：`*p`（p 是局部 `&mut`）→ `underShared=false` → 可写；`*q`（q: `&&mut`）→ 经过 `&` → `underShared=true` → `**q` 不可写。

#### 为什么这一批才写

因为 place 需要两样东西，现在才齐：

- **绑定可变性**：`LocalSymbol.mutable`（批次 A 加的 `LocalSymbol.type` 旁边）；
- **引用类型**：`RefType(mutable, inner)`（批次 B 刚让 `&expr` 有真实类型）。

在这之前 `&x` 的类型是 `Unknown`，place 就算写出来也无从判断。

#### 批次 C 的分工

```
Place.kt        checkPlace(expr): Place?     ← 位置与可变性
ExprChecker     AssignExprNode / UnOp.DEREF / ReferenceExprNode(补 &mut 校验)
BodyChecker     无需改动（值语义已就绪）
```

对应 12 个 reject：`references-and-mutability` 5（`*p` 类）、`compound-assignment` 4（目标必须是可变 place、不同后缀不能转、引用目标不自动解引用、字面量不是 place）、`evaluation-order` 2（普通赋值/运算结果不是 place）、`expected-types` 1（赋值不能改变绑定类型）。

要开始的话，第一刀我建议先只做 `Place` 的**只读部分**（`checkPlace` 返回 `type` + `mutable`），把 `*p` 和局部变量跑通，再补 `&mut` 和赋值——这样每一步都能单独跑 `references-and-mutability` 验证。

## 8. 2026-10-29 14:16 — 请你告诉我我应该如何一步步完成批次C，

**你**

请你告诉我我应该如何一步步完成批次C，

**Copilot**

批次 C 的目标是把“**place（可写位置）**”这条独立的判定通道接进 `ExprChecker`。它要解决的 13 个 reject 分布在 4 个目录里：

| 目录 | 数量 | 需要的规则 |
|---|---|---|
| `references-and-mutability` | 6（其中 1 个现在已过） | `checkPlace`、`&mut` 校验、`*e` 真实类型、`underShared` |
| `compound-assignment` | 4 | 复合赋值的 place + 分组类型规则 |
| `evaluation-order-and-temporaries` | 2 | 左值与右值的区分 |
| `expected-types` | 1 | 普通赋值的类型不可变 |

预期结局：`accept: 69/69` 不变，`reject: 96 → 108`。

---

#### 步骤 0：先提交批次 A+B

现在的改动（`Coercion.kt` 两次修正、`AstBuilder.kt` 抬尾等）都还没提交。批次 C 会改动 `ExprChecker` 里最敏感的几条路径，先 `git commit` 一次，出问题才能二分。

---

#### 步骤 1：新建 `Place.kt`

`src/main/kotlin/rx/semantic/Place.kt`，和 `Coercion.kt` 一样是包级工具类型（`make compiler` 用 `find` 收集文件，新文件自动编进去）：

```kotlin
package rx.semantic

import rx.ast.Type

internal class Place(
    val type: Type,
    val mutable: Boolean,       // 能不能写：赋值、复合赋值、&mut 借用
    val underShared: Boolean,   // 这个 place 的 type 里的 &mut 是否是"假的"
)
```

三个字段缺一不可，两个维度是独立的：

- `mutable` 回答"**现在能不能写**"；
- `underShared` 回答"**它的类型里那个 `&mut` 还算不算数**"，只在 `*e` 时用来算下一层的 `mutable`。

用 `references-and-mutability/rej-a-shared-outer-layer-prevents-mutable-access` 推一遍定义：

```
let mut x = 1;
let p = &mut x;     // p: &mut i32
let q = &p;         // q: &&mut i32  = &(&mut i32)
**q = 2;            // 要拒绝
```

- `q` 的 place：`Place(&&mut i32, mutable=false, underShared=false)`
- `*q`：外层 ref 的 `mutable=false` ⇒ 新 place `Place(&mut i32, mutable=false, underShared=true)`。注意此时 **type 里写着 `&mut i32`**，如果只看 `RefType.mutable` 就会误判可写——这就是 `underShared` 存在的唯一理由。
- `**q`：内层 `ref.mutable=true`，但 base 的 `underShared=true` ⇒ `mutable=false` ⇒ 拒绝。

---

#### 步骤 2：`ExprChecker` 里的 `checkPlace` 骨架

放在 `checkPathExpr` 附近，**private fun**（目前只有 `ExprChecker` 用；批次 D 的 `&self` 接收者、批次 E 的字段类型都在同一个类里）：

```kotlin
/**
 * 返回 place；返回 null 表示"不是 place，但整个子树已经检查完、type 已写回"。
 * 契约：调用返回后 expr.type 一定非 null。
 */
private fun checkPlace(expr: ExprNode): Place? = when (expr) {
    is PathExprNode   -> placeOfPath(expr)
    is UnaryExprNode  -> if (expr.op == UnOp.DEREF) placeOfDeref(expr)
                         else { check(expr); null }
    is FieldExprNode  -> placeOfField(expr)
    is IndexExprNode  -> placeOfIndex(expr)
    else              -> { check(expr); null }   // 字面量 / 二元 / 调用 / 块 / 强转…
}
```

两个硬性约束：

1. **`else` 分支必须调 `check(expr)`**。`checkPlace` 替代的是 "先 `check(target)` 再判断" 的两趟写法；漏了这一步，`1 = f(bad)` 里的 `f`/`bad` 就永远不会被检查。
2. **别在分支里再调一次 `check(expr)` 自己**，否则重复报错。递归只往下走（`checkPlace(receiver)`、`checkPlace(operand)`），不会死循环。

---

#### 步骤 3：路径的 place

`PathExprNode` 要"类型 + 符号"两个信息。最省事的写法是先用 `check(expr)` 拿类型（顺带复用现有全部错误消息），再查一次符号拿可变性：

```kotlin
private fun placeOfPath(node: PathExprNode): Place? {
    val type = check(node)                       // 写回 node.type，内含泛型实参检查
    val segment = node.segments.singleOrNull() ?: return null
    val symbol = ctx.currentScope.lookupValue(segment.name) as? LocalSymbol ?: return null
    return Place(type, symbol.mutable, underShared = false)
}
```

- `ConstantSymbol` → 不是 place（`C = 1` 由默认的非 place 路径拒绝）；
- `FunctionSymbol` / `BuiltinFunctionSymbol` → 不是 place；
- `self` → `lookupValue("self")` 为 null → 返回 null。**这对批次 C 恰好是对的**：`evaluation-order` 的 accept 里有 `self.x += n`，字段 place 的基准是 null，会落到"临时值可写"，批次 D 再精确。

---

#### 步骤 4：`*e`（`UnOp.DEREF`）

一个 helper，读路径和写路径共用，避免两份逻辑：

```kotlin
private fun placeOfDeref(node: UnaryExprNode): Place {
    val base = checkPlace(node.operand)          // 保证 operand.type 已写回
    val operandType = node.operand.type!!        // 契约：checkPlace 后非 null

    if (operandType == UnknownType) {            // 脚手架通配，见下
        return Place(UnknownType, mutable = true, underShared = false)
    }
    val ref = operandType as? RefType
        ?: throw SemanticError(node.span, "cannot dereference a value of type $operandType")

    val inherited = base?.underShared ?: false
    return Place(
        type = ref.inner,
        mutable = ref.mutable && !inherited,
        underShared = inherited || !ref.mutable,
    )
}
```

两处细节：

- **`base?.mutable` 不参与公式**。`let p = &mut x; *p = 5` 里 `p` 是不可变绑定（`mutable=false`），但 `*p` 必须可写——只看 `RefType.mutable` 和 `underShared`。
- **`UnknownType` 必须短路成可写**。`acc-ordinary-assignment...` 里有 `*dest(&mut x) = mark(10)`，`dest(...)` 是调用，批次 D 之前返回 `UnknownType`；若在这里对 `?` 抛 "cannot dereference"，这个 accept 立刻挂。

然后 `checkUnary` 的 `UnOp.DEREF` 分支从 `check(node.operand); UnknownType` 改成：

```kotlin
UnOp.DEREF -> placeOfDeref(node).type
```

`rej-scalar-values-cannot-be-dereferenced`（`*x`，`x: i32`）由 `as? RefType` 拦下。

---

#### 步骤 5：`e.f` 和 `e[i]`

批次 C 只回答"是不是 place、能不能写"，**字段/元素类型留到批次 E**，这里先返回 `UnknownType`：

```kotlin
private fun placeOfField(node: FieldExprNode): Place? {
    val base = checkPlace(node.receiver)          // rvalue 时返回 null，但已定型
    node.type = UnknownType                       // 批次 E 换成真实字段类型
    return Place(
        UnknownType,
        mutable = base?.mutable ?: true,          // 关键：rvalue 基准 = 临时值，可写
        underShared = base?.underShared ?: false,
    )
}

private fun placeOfIndex(node: IndexExprNode): Place? {
    val base = checkPlace(node.receiver)
    check(node.index)                             // 下标本身是普通表达式
    node.type = UnknownType
    return Place(UnknownType, base?.mutable ?: true, base?.underShared ?: false)
}
```

`?: true` 不是偷懒，是三条 accept 逼出来的：

- `make().x = mark(41);`
- `make_array(30)[0] = mark(11);`
- `let field = &mut make_record(50).data[0];`

基准是 rvalue（调用），place 为 null，但**临时值是可写的**。所以判定基准可写性用的是 `base?.mutable ?: true`，而不是 `base != null`。

同时给 `acc-an-immutable-binding...` 的 `let a = &mut s.x;` 留好接口：`s` 是 `let mut s` ⇒ `placeOfPath` 给 `mutable=true` ⇒ 字段继承 true ⇒ `&mut` 通过。

---

#### 步骤 6：给 `&mut e` 加校验（`checkReference`，第 168 行）

规则只有三条，按顺序：

```kotlin
private fun checkReference(node: ReferenceExprNode): Type {
    val place = checkPlace(node.operand)          // 永远先检查/定型（共用 & 和 &mut）
    val inner = node.operand.type!!

    if (!node.mutable) return RefType(false, inner)   // &e：只要表达式合法即可

    // &mut e：是 place 就必须可写；不是 place = 临时值，可以
    if (place != null && !place.mutable) {
        throw SemanticError(node.operand.span, "cannot mutably borrow an immutable place")
    }
    return RefType(true, inner)
}
```

- `rej-a-named-immutable-local-cannot-be-mutably-borrowed`（`let x = 1; &mut x`）→ place 存在且 `mutable=false`；
- `let temporary = &mut 20;` → `checkPlace(20)` 走 `else` 返回 null → 放行；
- `&mut &mut x` 之类嵌套、`&mut s.x`、`&mut make_array(40)[1]` 全部自然成立；
- `rej-shared-reference-cannot-become-mutable`（`p: &mut i32 = &x`）**不需要新代码**，`assignable` 的 `(!to.mutable || from.mutable)` 已经拦住——这也是现在 1/6 里过的那条。

---

#### 步骤 7：赋值（`AssignExprNode`，第 38 行）

把分支改成 `is AssignExprNode -> checkAssign(expr)`，返回 `UnitType`。

```kotlin
private fun checkAssign(node: AssignExprNode): Type {
    val place = checkPlace(node.target)
        ?: throw SemanticError(node.target.span, "assignment target is not a place")
    if (!place.mutable) {
        throw SemanticError(node.target.span, "cannot assign to an immutable place")
    }

    if (node.op == AssignOp.ASSIGN) {
        val valueType = check(node.value, place.type)        // 让无后缀字面量按目标定型
        requireAssignable(valueType, place.type, node.value.span, "assignment")
        return UnitType
    }
    // 复合赋值 ↓
}
```

普通赋值靠 `check(..., place.type)` 支持 `x = (5)`（`x: u32`）这类 accept；`x = 2u32` 因为后缀优先仍得 `u32`，`requireAssignable` 拒绝（`rej-assignment-cannot-change-a-binding-s-type`）。

复合赋值按 `AssignOp` 分三组，**LHS 一律不剥引用**、RHS 剥一层共享引用：

| 分组 | LHS 要求 | RHS 要求 |
|---|---|---|
| `ADD/SUB/MUL/DIV/MOD_ASSIGN` | 直接是 `IntegerType` | `stripSharedRef(check(value, 期望))` 后 **== LHS** |
| `BIT_AND/OR/XOR_ASSIGN` | `IntegerType` 或 `BoolType` | 同上，== LHS |
| `SHL_ASSIGN / SHR_ASSIGN` | `IntegerType` | 剥后是**任意** `IntegerType`（不必同型） |

三个容易写错的地方：

1. **LHS 不能 `stripSharedRef`**。`rej-reference-destinations-are-not-implicitly-dereferenced`（`let mut p = &mut x; p += 1;`）要求 `&mut i32` 直接判"不是整数"而报错；剥了引用就会误放行。
2. RHS 的 `expected` 用 `place.type`（整数/布尔时），这样 `x += 2` 能定型；但 `x += 1i32`（x 是 u32）后缀优先，仍拒绝。
3. **LHS 是 `UnknownType` 时整体短路**：`check(node.value)` 之后直接返回 `UnitType`。`*a += *b` 里 a 是 `&mut ?`（字段类型批次 E 才填），不短路就会误报。
4. `rhs` 判等时记得 `rhs != UnknownType` 也要放行（同样的脚手架理由）。

对照 accept 自查：`x -= &2`（RHS `&i32` → i32）、`x <<= &2u32`（计数 u32 合法）、`x >>= 1usize`、`b &= &false`（bool 组）都必须通过。

---

#### 步骤 8：分三次跑测试，别一次跑全量

```bash
# ① 路径/字段/下标 place + 普通赋值
python3 scripts/check_semantic.py expected-types evaluation-order-and-temporaries -v
# ② 引用与解引用（此时 references-and-mutability 应到 6/6）
python3 scripts/check_semantic.py references-and-mutability -v
# ③ 复合赋值
python3 scripts/check_semantic.py compound-assignment -v
# ④ 全量
python3 scripts/check_semantic.py
```

| 用例 | 期望被哪条规则拒绝 |
|---|---|
| `evaluation-order/rej-ordinary-assignment-requires-a-place`（`1 = 2`） | `checkPlace` 返回 null |
| `evaluation-order/rej-an-arithmetic-result-is-not-a-place`（`(x+1) = 2`） | 二元节点 → null |
| `compound-assignment/rej-a-literal-is-not-an-assignment-place`（`1 += 2`） | null |
| `compound-assignment/rej-compound-destination-must-be-mutable`（`let x; x += 1`） | `place.mutable` |
| `compound-assignment/rej-an-explicit-mismatched-suffix-cannot-coerce` | RHS 同型 |
| `compound-assignment/rej-reference-destinations-are-not-implicitly-dereferenced` | LHS 不剥引用 |
| `expected-types/rej-assignment-cannot-change-a-binding-s-type` | `requireAssignable` |
| `references/rej-a-named-immutable-local-cannot-be-mutably-borrowed` | `&mut` + `place.mutable` |
| `references/rej-rebinding-an-immutable-reference-binding-is-invalid` | 赋值 + `place.mutable` |
| `references/rej-shared-dereference-is-not-mutable` | `placeOfDeref` 的 `ref.mutable` |
| `references/rej-scalar-values-cannot-be-dereferenced` | `as? RefType` |
| `references/rej-a-shared-outer-layer-prevents-mutable-access` | `underShared` |
| `references/rej-shared-reference-cannot-become-mutable` | 已在 `assignable` 里 |

任何 **accept 回归**大概率来自三处，按此顺序排查：`else` 分支漏了 `check`（不是回归，是漏报）→ `?: true` 写成了 `base != null` → `UnknownType` 没短路。

---

#### 步骤 9：收尾

跑全量确认 `accept 69/69`、`reject ≈108` 后，更新 `docs/expr-checking.md`：

- §2 节点表：`AssignExprNode` / `UnaryExprNode(DEREF)` / `ReferenceExprNode` / `Field` / `Index` 的"已实现"标注；
- §3 横向规则：新增一条"place 与类型是两条独立通道，字段/下标的基准可以是 rvalue"，把 `underShared` 的公式抄进去；
- §4：批次 C 打 ✅，并注明字段/下标类型仍返回 `?`，批次 E 收尾。

---

批次 C 之后剩下的账很清楚：批次 D（调用/内建/方法查找，`self` 的真正类型）→ 批次 E（字段/下标真实类型 + 可变性穿透，顺手把 `placeOfField/Index` 的 `UnknownType` 换掉）→ 批次 F（引用收敛与 derive 运行时语义）。要开始写的时候，建议先做步骤 1–3，跑一次 ①，确认没有 accept 回归再往下。

## 9. 2026-10-29 16:09 — 这个复合赋值部分应该怎么写，需要写什么部分？

**你**

这个复合赋值部分应该怎么写，需要写什么部分？

**Copilot**

现在文件里唯一的编译错误就是 `checkAssign` 缺少返回值（第 358 行）。复合赋值只需要写 **两处**，`Place`、`checkPlace`、`placeOfDeref`、`checkReference` 都不用再动。

#### 一、要写哪两处

1. **`checkAssign` 末尾**的 `// 复合赋值 ↓`：调用新方法并 `return UnitType`。
2. **新增一个 `private fun checkCompoundAssign(node, targetType)`**，放在 `checkAssign` 和 `checkPlace` 之间。

#### 二、先想清楚：复合赋值只剩"类型"这一维

`checkAssign` 开头的 `checkPlace` + `place.mutable` 已经把 **place 维**（是不是可写位置）全部解决。所以 `checkCompoundAssign` 不需要再判断"能不能写"，只剩三件事，按顺序：

1. **检查 RHS**（必须做，哪怕是 `?` 短路也要检查内部错误）；
2. **LHS 类型是否支持这个运算**；
3. **RHS 与 LHS 是否同型**（移位是例外）。

四条规则决定了具体写法：

| 规则 | 依据用例 |
|---|---|
| LHS **不剥引用** | `rej-reference-destinations-are-not-implicitly-dereferenced`：`let mut p = &mut x; p += 1;` |
| RHS **剥一层共享引用** | accept `x -= &2;`、`b &= &false;`；`&mut` 直接拒绝 |
| 移位计数是**任意整数类型**，不必同型 | accept `x <<= &2u32;`、`x >>= 1usize;` |
| `?` 全部短路 | `*a += *b`（a 是 `&mut ?`）、`x += f()`（批次 D 前返回值是 `?`） |

> 不要试图复用 `checkArithmetic` / `checkBitwise` / `checkShift`：它们收的是 `BinaryExprNode`，而且会把**左**操作数剥引用——正好和复合赋值相反。

#### 三、代码骨架

```kotlin
private fun checkAssign(node: AssignExprNode): Type {
    val place = checkPlace(node.target)
        ?: throw SemanticError(node.target.span, "assignment target is not a place")
    if (!place.mutable) {
        throw SemanticError(node.target.span, "cannot assign to an immutable place")
    }

    if (node.op == AssignOp.ASSIGN) {
        val valueType = check(node.value, place.type)        // 让无后缀字面量按目标定型
        requireAssignable(valueType, place.type, node.value.span, "assignment")
        return UnitType
    }

    checkCompoundAssign(node, place.type)                    // ← 补这两行
    return UnitType                                          // ← 注释换成真代码
}

/**
 * `x op= y`：左值可写性已在 checkAssign 判过，这里只剩类型。
 * 与二元运算不同：LHS 不剥引用、无"结果类型"、计数可为任意整数。
 */
private fun checkCompoundAssign(node: AssignExprNode, targetType: Type) {
    // 脚手架：字段/下标/未实现调用给出的是 ?，整条规则放行，但 RHS 仍要检查
    if (targetType == UnknownType) {
        check(node.value)
        return
    }

    // RHS：先按 LHS 期望定型（u32 的 x += 2 能成立），再剥一层共享引用
    val rhs = stripSharedRef(
        check(node.value, targetType.takeIf { it is IntegerType || it == BoolType }),
        node.value.span,
        "right operand of compound assignment",
    )

    when (node.op) {
        AssignOp.ADD_ASSIGN, AssignOp.SUB_ASSIGN, AssignOp.MUL_ASSIGN,
        AssignOp.DIV_ASSIGN, AssignOp.MOD_ASSIGN -> {
            if (targetType !is IntegerType) {
                throw SemanticError(
                    node.target.span,
                    "arithmetic compound assignment requires an integer destination, found $targetType",
                )
            }
            requireSameOperandType(rhs, targetType, node)
        }

        AssignOp.BIT_AND_ASSIGN, AssignOp.BIT_OR_ASSIGN, AssignOp.BIT_XOR_ASSIGN -> {
            if (targetType !is IntegerType && targetType != BoolType) {
                throw SemanticError(
                    node.target.span,
                    "bitwise compound assignment requires an integer or bool destination, found $targetType",
                )
            }
            requireSameOperandType(rhs, targetType, node)
        }

        AssignOp.SHL_ASSIGN, AssignOp.SHR_ASSIGN -> {
            if (targetType !is IntegerType) {
                throw SemanticError(
                    node.target.span,
                    "shift compound assignment requires an integer destination, found $targetType",
                )
            }
            // 计数只要是整数即可，不要求同型
            if (rhs != UnknownType && rhs !is IntegerType) {
                throw SemanticError(
                    node.value.span,
                    "shift count must be an integer, found $rhs",
                )
            }
        }

        AssignOp.ASSIGN -> Unit            // 上面已提前返回；enums 的 when 必须穷尽
    }
}

/** RHS 与 LHS 必须同型；RHS 是脚手架 `?` 时放行。 */
private fun requireSameOperandType(rhs: Type, lhs: Type, node: AssignExprNode) {
    if (rhs != UnknownType && rhs != lhs) {
        throw SemanticError(
            node.value.span,
            "compound assignment operands must have the same type: $lhs vs $rhs",
        )
    }
}
```

#### 四、写的时候容易错的三点

1. **`rhs` 要在 LHS 检查之前算**。这样即使 `p += 1`（LHS 不支持运算）也先把 RHS 检查完，内部错误不会漏报。
2. **`targetType.takeIf { it is IntegerType || it == BoolType }`**：LHS 是 `&mut i32` 时 expected 必须是 `null`，否则 `checkReference` 之类的期望传播会把错误变成另一种错误（虽然仍报错，但不符合"LHS 不剥引用"的语义）。
3. **`AssignOp.ASSIGN -> Unit` 不能省**：Kotlin 2.x 对 enum 的 `when` 语句强制穷尽，漏了会直接编译报错。

#### 五、验证

```bash
make compiler
python3 scripts/check_semantic.py compound-assignment evaluation-order-and-temporaries -v
```

应达到：`compound-assignment` 5/5（1 accept + 4 reject）、`evaluation-order` 4/4。再跑全量：

```bash
python3 scripts/check_semantic.py
```

期望 `accept: 69/69`、`reject: 108/167`。批次 C 到此收尾，下一步就是批次 D（调用与 `self` 的真实类型）。

## 10. 2026-10-29 16:34 — python3 scripts/check_semantic.py compou…

**你**

python3 scripts/check_semantic.py compound-assignment evaluation-order-and-temporaries -v这个跑出来有一个关于index的地方fail了

**Copilot**

批次 C 的三个目标目录已经全绿了；你看到的那条 `index` 失败其实是**过滤器误伤**（`check_semantic.py` 是按路径子串匹配的，`compound-assignment` 同时命中了 `vec-index-mutability/rej-compound-assignment-through-stored-reference.rx` 的文件名）。不过刚才全量跑暴露了真正的回归，我先把现场看清楚。

#### 一、你看到的那条 “index” 失败：过滤器误伤，不是批次 C 的问题

`check_semantic.py` 的过滤是**整条路径的子串匹配**：

```python
return [c for c in cases if any(f in str(c["source"]) for f in filters)]
```

`compound-assignment` 这个串同时命中了文件名 `vec-index-mutability/rej-compound-assignment-through-stored-reference.rx`。那个用例属于 `vec-index-mutability`（批次 E），内容是通过不可变绑定的 `Vec<&mut i32>` 写内部引用，本来就不在批次 C 的范围。

用目录形式过滤就干净了：

```bash
python3 scripts/check_semantic.py compound-assignment/ evaluation-order-and-temporaries/ -v
# accept 3/3, reject 6/6, SUMMARY: OK
```

#### 二、但全量跑暴露了 10 个 accept 回归

`accept: 59/69`，三个根因，全部在刚写的 place 代码里：

| 根因 | 受影响 accept | 数量 |
|---|---|---|
| `placeOfDeref` 没写回 `node.type` → 嵌套解引用 NPE | references 1 个、vec-index 3 个 | 4 |
| 字段/下标用“绑定是否 mut”判可写性，应为“**基准类型**是否给写权限” | quicksort、nested-containers、vec-index 2 个 | 4 |
| `*Box<T>` 未实现 | copy-clone 1 个、vec-index 1 个 | 2 |

##### 根因 1：`placeOfDeref` 违反了自己定的契约

`check` 才会在末尾写 `expr.type`；但 `checkPlace` 直达 `placeOfDeref` 时不经过 `check`。于是 `**nested` 里外层读 `node.operand.type!!` 拿到 `null` → NPE。`checkReference` 第 169 行同样的 `type!!` 也会炸。

修法：`placeOfDeref` 返回前自己写 `node.type`（顺带把 `*Box<T>` 补上，见根因 3）：

```kotlin
private fun placeOfDeref(node: UnaryExprNode): Place {
    val base = checkPlace(node.operand)
    val operandType = node.operand.type!!

    if (operandType == UnknownType) {                 // 脚手架通配
        node.type = UnknownType
        return Place(UnknownType, mutable = true, underShared = false)
    }

    val place = when (operandType) {
        is RefType -> {
            val inherited = base?.underShared ?: false
            Place(
                type = operandType.inner,
                mutable = operandType.mutable && !inherited,
                underShared = inherited || !operandType.mutable,
            )
        }
        // Box<T> 的解引用是"投影"：可写性跟盒子本身走；
        // 盒子里装的是 &mut T 时，写权限来自那层 &mut（下一层 * 再算）
        is BoxType -> Place(
            type = operandType.element,
            mutable = base?.mutable ?: true,
            underShared = base?.underShared ?: false,
        )
        else -> throw SemanticError(node.span, "cannot dereference a value of type $operandType")
    }
    node.type = place.type        // ← 契约：checkPlace 返回后 type 一定非 null
    return place
}
```

##### 根因 2：投影的可写性看的是**基准类型**，不是绑定的 `mut`

测试语义写得很清楚（`acc-immutable-binding-of-mutable-vector-reference`、`acc-mutable-reference-before-vector-indexing`）：参数 `a: &mut Vec<i32>` 的绑定本身不可变，但 `a[i] = x` 合法——写权限来自 `&mut`。现在的 `base?.mutable ?: true` 判成了“绑定不可变 ⇒ 不可写”。

加一个 helper，两个 place 函数都走它：

```kotlin
/**
 * 字段 / 下标的可写性由**基准的类型**决定：
 * - `&mut T` 自动解引用 → 可写（哪怕绑定不可变）；
 * - `&T` → 只读；
 * - 值类型 → 继承基准 place（rvalue 基准 = 临时值，可写）；
 * - `?` → 脚手架放行。
 */
private fun projectionPlace(base: Place?, baseType: Type): Place {
    val writable = when {
        baseType == UnknownType -> true
        baseType is RefType -> baseType.mutable && !(base?.underShared ?: false)
        else -> base?.mutable ?: true
    }
    return Place(
        type = UnknownType,                        // 批次 E 换成真实字段/元素类型
        mutable = writable,
        // 只读路径上的 &mut 是"假的"：批次 E 靠它拒绝 Vec<&mut T> 的穿透写入
        underShared = (base?.underShared ?: false) || !writable,
    )
}

private fun placeOfField(node: FieldExprNode): Place? {
    val base = checkPlace(node.receiver)
    node.type = UnknownType
    return projectionPlace(base, node.receiver.type!!)
}

private fun placeOfIndex(node: IndexExprNode): Place? {
    val base = checkPlace(node.receiver)
    check(node.index)
    node.type = UnknownType
    return projectionPlace(base, node.receiver.type!!)
}
```

注意 `node.type = UnknownType` 必须保留——否则 `*values[0]` 又会踩根因 1 的 NPE。

##### 根因 3：`*Box<T>`

已并入根因 1 的 `when` 分支。可写性跟盒子 place 走：

- `let mut copied: Box<i32>; *copied = 11` → 可写；
- `fn f(v: Box<&mut i32>) { **v = 6 }` → `*v` 不可写，但类型是 `&mut i32`，第二层 `*` 的 `ref.mutable` 给出可写。

#### 三、验证与预期

```bash
make compiler && python3 scripts/check_semantic.py
```

预期：

- `accept: 69/69`（10 个全部回来）；
- `reject` 大约 **113**（现在 115 里有两个 vec-index 用例是**靠 NPE 意外“拒绝”**的：`rej-boxed-reference-does-not-bypass-vector-mutability`、`rej-explicit-mutable-borrow-of-stored-target`。NPE 修掉后它们会回到 backlog，这是正常的，它们属于批次 E）。

那条 `rej-compound-assignment-through-stored-reference` 修完后仍会被接受，也正常：它需要 `VecType.element` 的真实类型 + `underShared` 穿透，正是批次 E 用 `projectionPlace` 里已经预埋的 `|| !writable` 去解决的。

## 11. 2026-10-29 17:33 — accept: 65/69 passed reject: 112/167 pro…

**你**

accept: 65/69 passed
  reject: 112/167 properly rejected
  accept mismatches (4):
    copy-clone-and-equality/acc-container-dot-clone-precedes-dereference-and-keeps-storage-independent.rx: expected accept, rc=1: Exception in thread "main" java.lang.NullPointerException
    vec-index-mutability/acc-mutable-vector-supports-nested-projections.rx: expected accept, rc=1: Exception in thread "main" java.lang.NullPointerException
    vec-index-mutability/acc-mutable-vector-supports-writes-and-mutable-borrows.rx: expected accept, rc=1: Exception in thread "main" java.lang.NullPointerException
    vec-index-mutability/acc-shared-coercions-and-receivers-on-immutable-vector.rx: expected accept, rc=1: Exception in thread "main" java.lang.NullPointerException
  (105.8s)
SUMMARY: FAIL还是有问题

**Copilot**

问题只剩一处：`placeOfDeref` 的 `UnknownType` 提前返回分支漏写了 `node.type`，其余分支都写了。嵌套解引用时外层读 `node.operand.type!!` 就拿到 `null`。

修好了：`placeOfDeref` 的 **`UnknownType` 提前返回分支漏写 `node.type`**。其他分支都在 `node.type = place.type` 处收尾，只有这个 `return` 绕过了它，于是 `*values[0]` 这类“操作数是脚手架 `?`”的解引用节点类型保持 `null`；外层再套一层 `*` / `&` 时读 `node.operand.type!!` 就 NPE。

补上后：

```kotlin
if (operandType == UnknownType) {
    node.type = UnknownType                  // 契约：checkPlace 返回后 type 必须非 null
    return Place(UnknownType, mutable = true, underShared = false)
}
```

#### 全量结果

| 指标 | 修复前 | 现在 |
|---|---|---|
| accept | 65/69 | **69/69** |
| reject | 112/167 | **110/167** |

之前那个 115 是虚高的：有 3 个 `vec-index-mutability` 用例是**靠 NPE 崩掉意外“拒绝”**的（`rej-boxed-reference-...`、`rej-explicit-mutable-borrow-...` 等），NPE 修掉后它们回到 backlog，这是正常的——它们本来就属于批次 E。

批次 C 四个目标目录全部达标：

| 目录 | accept | reject |
|---|---|---|
| `references-and-mutability` | 1/1 | **6/6** |
| `compound-assignment` | 1/1 | **4/4** |
| `evaluation-order-and-temporaries` | 2/2 | **2/2** |
| `expected-types` | 1/1 | 3/4（剩的用例要数组字面量定型，批次 E） |

相比批次 B 结束时（96），净增 **+14** 个 reject，且 accept 零回归。

#### 两个收尾建议

1. **提交这个检查点**。批次 C 涉及 `Place.kt`、`ExprChecker` 里 place/赋值/解引用三块，先 `git commit`，批次 D 才有干净的对比基线。
2. **给 `checkPlace` 加一条防御性收尾**，避免以后再犯同类错误：

```kotlin
private fun checkPlace(expr: ExprNode): Place? {
    val place = when (expr) { /* ... */ }
    if (expr.type == null) expr.type = place?.type ?: UnknownType   // 契约兜底
    return place
}
```

这样任何新加的 place 分支忘了写 `type` 都会变成 `?`（通配放行），而不是运行期 NPE。

接下来就是批次 D：调用返回类型、内建函数、`Box::new`/`Vec::new`、方法查找与 `self` 的真实类型。这一批会同时解锁 `calls-recursion-and-abi` 3、`builtin-io` 4、`box-and-moves` 4、`vec-operations` 5、`methods-and-self` 4 等约 20 个 reject。

## 12. 2026-10-29 19:53 — 接下来我们准备开始写D部分，请你不要看样例和数据点，仅仅根据逻辑思考一下，我们需…

**你**

接下来我们准备开始写D部分，请你不要看样例和数据点，仅仅根据逻辑思考一下，我们需要完成哪些核心函数、需要什么辅助函数进行支持，最后需要

## 13. 2026-10-29 19:53 — 接下来我们准备开始写D部分，请你不要看样例和数据点，仅仅根据逻辑思考一下，我们需…

**你**

接下来我们准备开始写D部分，请你不要看样例和数据点，仅仅根据逻辑思考一下，我们需要完成哪些核心函数、需要什么辅助函数进行支持，最后可能需要哪些细节，请你用教学的方式用简明朴实的语言和我说

**Copilot**

### 批次 D：从逻辑出发的设计

#### 一、先把"调用"归约成一个模型

语法上有五种调用写法：

| 写法 | 例子 |
|---|---|
| 顶层函数 | `f(a, b)` |
| 内建函数 | `print_i32(x)` |
| 关联函数 | `S::f(x)`、`Self::f(x)` |
| 构造器 | `Box::<T>::new(x)`、`Vec::<T>::new()` |
| 方法 | `x.m(a)` |

前四种在类型检查眼里是**同一个东西**：

> 一个名字 + 一张参数类型表 + 一个返回类型。

方法只多一件事：

> 调用方还要满足接收者的要求（`self` / `&self` / `&mut self`）。

所以批次 D 不需要写五套逻辑，只需要写**两类解析**（"路径 → 可调用物"、"接收者 + 名字 → 方法"）和**一个共用的实参检查**。想清楚这一点，代码量就下来了。

---

#### 二、核心函数（真正要写的东西）

##### 1. `checkCallExpr`：路径调用

它做的事只有三步：

1. 把 `callee` 解析成调用目标；
2. 检查实参；
3. 返回目标的返回类型。

关键决定：**callee 不要先走 `check()`**。`check` 是"把表达式当值来定型"的入口，而函数名在这门语言里不是值。先 `check` 再解析，等于同一条路径检查两遍，还容易让"函数作值"的错误混进来。只有当 callee 不是路径（比如 `(x)(y)`）时，才退回 `check` 一遍再报"不可调用"。

##### 2. `resolveCallTarget`：路径 → 可调用物

输入是 `PathExprNode`，输出一个很小的结构：

```kotlin
private class CallTarget(
    val description: String,      // 只用于诊断措辞
    val paramTypes: List<Type>,
    val returnType: Type,
)
```

内部按路径形状分三种：

- **单段**：查值名字空间
  - `FunctionSymbol` → 用它的 `signature`（阶段 6 已填好）
  - `BuiltinFunctionSymbol` → 自带 `paramTypes` / `returnType`
  - 其他（局部变量、常量）→ 不是可调用物
- **多段，第一段是 struct 或 `Self`**：去 `associatedValues` 里找
  - 找到 `FunctionSymbol` 且 `signature.selfParam == null` → 关联函数
  - 找到方法（有 self）→ 不能当关联函数直接调，报错
  - 找到常量 → 不可调用
- **多段，第一段是 `Box` / `Vec`**：构造器，单独处理（见细节 6）

`signature == null` 说明阶段 6 没填（正常代码不会发生），当成 `?` 通配即可。

##### 3. `checkMethodCall`：点调用

逻辑上是四步：

1. **一次**遍历接收者，同时拿到类型和"能不能写"；
2. 顺着类型找到方法；
3. 检查接收者是否满足 `self` 的要求；
4. 检查实参，返回返回类型。

第 1 步是这一批最值得学的设计点：

> 方法调用天然需要 place（因为 `&mut self` 要求"能可变借用接收者"），而 `checkPlace` 恰好同时给出 **类型** 和 **可写性**。

所以这里用 `checkPlace(receiver)`，**不要** `check(receiver)` 之后再 `checkPlace(receiver)` 第二遍。后者会让每个子表达式被检查两次，错误重复、还容易在将来引入副作用。

##### 4. `resolveMethod`：接收者类型 + 方法名 → 方法

顺序固定，先内后外：

1. 接收者类型是 `?` → 直接放弃（返回"未知"，**不报错**）；
2. 剥引用层：`&T` / `&mut T` 自动解引用到 `T`（可能多层）；
3. `StructType` → 去 `StructSymbol.associatedValues` 找同名 `FunctionSymbol`，且必须有 `selfParam`；
4. 容器类型 → 查内建方法表（见辅助 3）；
5. 都找不到 → "类型 T 上没有方法 m"。

> `Box<T>` 是否参与自动解引用（`box.method()` 能不能调到 `T` 的方法）是一个语言规范问题，不是逻辑推导能定的。稳妥做法：引用层必剥；`Box` 先只查它自己的内建方法，等批次 E 处理容器穿透时再统一。

##### 5. `checkArguments`：**最该抽出来的辅助函数**

调用、关联函数、构造器、方法、内建方法，五处共用：

```kotlin
private fun checkArguments(args, paramTypes, what: String)
```

它只做两件事，但顺序很重要：

1. **先比数量**：不等就立刻报错并返回，不再往下检查；
2. 再逐个 `check(arg, paramType)`，然后 `requireAssignable`。

数量不对还继续查类型，会刷出一串噪音错误，学生自己调试时也会被误导。

---

#### 三、辅助函数

1. **`selfValueType(selfParam, owner): Type`**
   把 `SelfParam(byReference, mutable)` + 所属 struct 翻译成方法体内 `self` 的类型：
   - `&self` → `&S`；
   - `&mut self` → `&mut S`；
   - `self` / `mut self` → `S`。

2. **`requireMutableReceiver(receiverPlace, receiverType, selfParam, span)`**
   只有 `&mut self` 需要判定。判据和 `&mut e` 完全同构：
   - 接收者类型本身是 `&mut _` → 允许；
   - 接收者是可变 place → 允许；
   - 接收者是 rvalue（临时值）→ 允许；
   - 其他（不可变绑定、共享引用链）→ 拒绝。
   注意 `underShared`：`&mut` 穿过一层共享引用后就不算数，这点和批次 C 的规则必须一致。

3. **`builtinMethodOf(type, name): FunctionSignature?`**
   容器方法表。它的签名依赖元素类型 `T`，所以不能像内建函数那样预先塞进符号表：
   - `Vec<T>`：`len() -> usize`、`push(T) -> ()`、`remove(usize) -> T`、`clone() -> Vec<T>`；
   - `Box<T>`：`clone() -> Box<T>`。

   放在 `Builtins.kt` 里更好——它是"语言知识"，不该散在 `ExprChecker` 中间。返回值统一用现成的 `FunctionSignature`（`paramTypes` 里**不含 self**，这正好和方法调用共用一套检查）。

---

#### 四、要顺带改的既有代码

1. **`BodyChecker` 里给 `self` 一个真正的绑定**
   现在 `self` 只在 `checkSingleSegmentValuePath` 里被特判成 `?`。批次 D 应该在有 `SelfParam` 时往函数作用域里放一个 `LocalSymbol`：

   ```kotlin
   LocalSymbol("self", mutable = false, type = selfValueType(selfParam, owner), span = null)
   ```

   `mutable = false` 是对的：`self = ...` 应当被拒绝；而 `&mut self` 方法里 `self.field = ...` 能写，是因为**字段投影看类型**（`projectionPlace` 已经按 `&mut` 判可写），不是看绑定是否 mut。这样 `checkSingleSegmentValuePath` 的 `self` 特判就可以删掉，`currentHasSelfParam` 也基本可以被这个绑定取代。

2. **`checkMultiSegmentPath` 里构造器的返回类型**
   它现在已经校验了 `Box` / `Vec` 的泛型实参个数，只差把"构造出来的类型"返回出来。

3. **`checkPathExpr` 里"函数作值"的分支**
   调用位置不再经过它；函数作值本身可以继续返回 `?`，不要顺手加限制。

---

#### 五、细节清单（都是容易漏的）

1. **每个子表达式恰好检查一次**。callee 不走 `check`、接收者只走 `checkPlace` 一次。
2. **expected 要传下去**：`f(1)` 的 `1` 应当按形参类型定型；`&` 会截断 expected，这是既有规则，不用管。
3. **`?` 全线通配**：形参是 `?`、实参是 `?`、接收者类型是 `?`、返回类型是 `?`，全部放行。批次 D 之后仍会有未实现节点（字段、下标），不能因为它们误报。
4. **`self` 不进 `paramTypes`**，实参个数不要把它算进去。
5. **构造器可以缺泛型实参**：`Box::new(x)` 的规范不要求报错，返回 `Box<?>` 即可；写了 `Box::<T>::new` 就用 `T`。`Vec::new` 同理，参数个数是 0。
6. **递归调用不需要特殊处理**：阶段 6 已经把签名填好了，`f` 调 `f` 直接读 `signature`。
7. **关联函数和方法共享一张表**。如果语言允许 `Vec::clone(&v)` 这种 UFCS 写法，类型路径上的查找要能落到方法表，并把 self 当成显式第一个参数——这决定了 `associatedValues` 和 `builtinMethodOf` 要不要合并成"查成员"的单一入口。
8. **报错位置**：数量错误指调用处，实参类型错误指实参，接收者可变性错误指接收者。
9. **单位返回**：`push`、`print_i32` 返回 `()`，别写成 `?`。
10. **方法的返回值也要能参与外层运算**，这和普通表达式没有区别，不需要额外机制。

---

#### 六、建议的落地顺序

| 步骤 | 内容 | 完成标志 |
|---|---|---|
| 1 | `checkArguments` + `checkCallExpr`（顶层函数 + 内建函数） | 普通调用有正确类型 |
| 2 | `resolveCallTarget` 的多段分支：关联函数、`Box::new`、`Vec::new` | 构造器能定型 |
| 3 | `resolveMethod` + autoderef + `requireMutableReceiver`（先只支持 struct 方法） | `x.m()` 与 `&mut self` 判定成立 |
| 4 | `builtinMethodOf`：容器方法表 | `len/push/remove/clone` 有类型 |
| 5 | `BodyChecker` 的 `self` 绑定 + 删掉路径特判 | 方法体内 `self` 有真实类型 |
| 6 | 全量回归 | accept 不降、reject 上升 |

每步做完单独跑一次相关目录再往下走，不要攒到最后。第 5 步是唯一会碰 `BodyChecker` 的地方，也是最容易把已有 accept 弄坏的地方，建议单独提交。

## 14. 2026-10-29 20:36 — 请你先改原代码中需要修改的部分

**你**

请你先改原代码中需要修改的部分

**Copilot**

批次的“地基”已经改好并验证：**accept 69/69，reject 110 不变**。一共动了三处既有代码：

#### 1. `BodyChecker`：把 `self` 变成真正的局部绑定

之前 `self` 在表达式层被特判成 `?`，方法体内没有任何符号。现在进入方法体时按签名声明：

```kotlin
val selfParam = node.params.filterIsInstance<SelfParam>().firstOrNull()
if (selfParam != null && owner != null) {
    val selfStruct = StructType(owner.name, owner.node)
    ctx.currentScope.declareValue(
        LocalSymbol(
            name = "self",
            mutable = selfParam.mutable && !selfParam.byReference,
            type = if (selfParam.byReference) {
                RefType(selfParam.mutable, selfStruct)   // &S / &mut S
            } else {
                selfStruct                              // S
            },
            span = null,
        )
    )
}
```

两个刻意的取舍：

- **`&mut self` 的绑定 `mutable = false`**。`self` 本身不可重新赋值；`self.field = ...` 能写，是因为字段投影看**类型**（`&mut S`），不看绑定 mut。这条规则批次 C 的 `projectionPlace` 已经实现了。
- 声明在函数作用域（`checkBlock` 的子作用域之上），所以方法体任何块里都能查到。

#### 2. `checkSingleSegmentValuePath`：`self` 特判不再“短路返回”

保留了“方法外禁止 `self`”和“`self` 不能带泛型实参”两个诊断，但删掉了 `return UnknownType`，让它落到下面的普通符号查找：

```kotlin
if (name == "self") {
    if (!ctx.currentHasSelfParam) throw ...
    if (segment.genericArgs.isNotEmpty()) throw ...
    // 不再特殊返回：self 已是 LocalSymbol，类型跟着签名走
}

val symbol = ctx.currentScope.lookupValue(name) ?: throw ...
```

副作用（白捡的）：`&self` 方法里写 `self.field = ...` 现在会因 `projectionPlace` 判出 `writable = false` 而被拒绝；`&mut self` 则放行。

#### 3. 抽出 `resolvePathOwner(node): StructSymbol?`

把 `checkMultiSegmentPath` 里那 80 行“第一段解析 + 泛型实参校验 + 类型名递归检查”原样搬进新函数，`checkMultiSegmentPath` 缩成三行：

```kotlin
private fun checkMultiSegmentPath(node: PathExprNode): Type {
    val owner = resolvePathOwner(node)
    val member = owner?.associatedValues?.get(node.segments.last().name)
    return when (member) {
        is ConstantSymbol -> ctx.resolveType(member.node.type)
        else -> UnknownType
    }
}
```

这是给批次 D 的 `resolveCallTarget` 准备的：它需要“路径第一段指向哪个 struct / 是不是 Box、Vec”，但不关心取到的值是什么类型。行为完全没变。

#### 验证

```text
accept: 69/69 passed
reject: 110/167 properly rejected
```

`methods-and-self` 里剩下的 4 个 reject（关联函数当点调用、autoref、`&mut self` 需要可变 place、没有匹配方法）正是批次 D 的 `resolveMethod` 要解决的，现在还没动。

#### 接下来你写批次 D 时，可以直接用的东西

| 已有 | 用途 |
|---|---|
| `resolvePathOwner(node)` | 路径第一段 → `StructSymbol?`（Box/Vec 返回 null），校验已做完 |
| `self` 是 `LocalSymbol` | `placeOfPath` 自动给出 `Place(&mut S, mutable=false)`，接收者可写性直接可查 |
| `FunctionSymbol.signature` | `paramTypes`（不含 self）、`returnType`、`selfParam` |
| `BuiltinFunctionSymbol` | 自带 `paramTypes` / `returnType` |
| `checkPlace(receiver)` | 方法调用一次拿到类型 + 可写性，别再用 `check` 查两遍 |

待写的核心函数还是上次说的那几个：`CallTarget` 数据载体、`resolveCallTarget`、`checkArguments`、`resolveMethod`、`requireMutableReceiver`、`builtinMethodOf`，然后重写 `checkCallExpr` / `checkMethodCall`。

## 15. 2026-10-29 21:04 — CallTarget是不是需要单独写一个文件出来，还是应该写在哪里？

**你**

CallTarget是不是需要单独写一个文件出来，还是应该写在哪里？

**Copilot**

#### 结论先说：**先不要建 `CallTarget`，直接复用现成的 `FunctionSignature`**

你项目里 `Symbols.kt` 已经有：

```kotlin
data class FunctionSignature(
    val paramTypes: List<Type>,   // 不含 self
    val returnType: Type,
    val selfParam: SelfParam?,    // null = 普通函数 / 关联函数
)
```

它和 `CallTarget` 需要的字段**一模一样**，连"不含 self"这个约定都正好是调用时检查实参所需要的形式。

- 顶层函数、内建函数、关联函数、方法 → `signature` 或现场拼一个 `FunctionSignature`；
- 构造器 `Box::<T>::new` → 现场拼 `FunctionSignature(listOf(T), BoxType(T), null)`，`Vec::<T>::new` → `FunctionSignature(emptyList(), VecType(T), null)`。

也就是说：

```kotlin
private fun resolveCallTarget(callee: ExprNode): FunctionSignature?
private fun resolveMethod(receiverType: Type, name: String): FunctionSignature?
```

返回值就够用了。真正需要"描述字符串"时，报错点的 `node` 里有路径/方法名，现场拼即可，没必要为一个诊断措辞常年多带一个字段。

> 我上次提 `CallTarget` 是把它当"载荷"来讲解，属于先给个具体形状方便你理解；看过你已有的 `FunctionSignature` 之后，这个抽象是多余的，应该收回。

#### 为什么 `Place` 能单独成文件，`CallTarget` 就不行

判断标准只有一条：**它是不是一个被多方传递、需要长期存在的概念**。

| | `Place` | `CallTarget` |
|---|---|---|
| 生产者 | `checkPlace` 的所有分支 | 只有 `resolveCallTarget` / `resolveMethod` |
| 消费者 | 赋值、`&mut`、方法接收者、解引用，跨多个函数 | 只有 `checkArguments` + 返回类型 |
| 是否跨阶段 | 批次 E 还要继续用 | 只服务阶段 8 的调用解析 |
| 是否已有等价物 | 无 | 已有 `FunctionSignature` |

`Place` 是"两个维度（类型 + 可写性）"的概念，`FunctionSignature` 是"参数 + 返回 + 接收者"的概念——后者你已经有了。

#### 如果将来确实要加，加在哪里

| 内容 | 位置 | 理由 |
|---|---|---|
| `resolveCallTarget`、`resolveMethod`、`checkArguments`、`requireMutableReceiver` | `ExprChecker.kt` 私有方法 | 它们都依赖 `ctx` 和递归检查，天然属于这个类 |
| 容器方法表 `builtinMethodOf` | `Builtins.kt` | 纯语言知识，不依赖上下文，和已有的内建函数表放在一起 |
| 真的需要 `CallTarget`（比如以后要带"候选方法列表"做多重匹配诊断） | 新文件 `CallTarget.kt`，`internal` | 与 `Place.kt` 同理：一个概念一个文件；**不要**塞进 `Symbols.kt`（那是阶段 1/2 的符号，生命周期和作用域完全不同），也不要塞进 `Coercion.kt`（那是类型规则） |

#### 一句话原则

> 只在 `ExprChecker` 内部流转的中间结果 → 私有方法/文件内私有类；
> 被多个阶段或多个文件读写的概念 → 独立文件放进 `rx.semantic`。

按这个原则，批次 D 其实**一个新文件都不需要**：`ExprChecker.kt` 里加解析函数，`Builtins.kt` 里加方法表，就齐了。等批次 E 把 `projectionPlace` 换成真实字段/元素类型时，再考虑要不要拆文件。

## 16. 2026-10-29 21:06 — ok，那我们接下来写checkArguments+checkCallExpr

**你**

ok，那我们接下来写checkArguments+checkCallExpr

**Copilot**

`signature` 不是从 `member` 现算出来的，而是**阶段 2 建了 `FunctionSymbol` 对象、阶段 6 往同一个对象里写了 `signature` 字段、阶段 8 再读出来**。三段代码分别在三个文件里，串起来看就清楚了。

#### 一、字段在哪、是什么类型

`Symbols.kt`：

```kotlin
class FunctionSymbol(...) : Symbol {
    /** 阶段 6 填充。 */
    var signature: FunctionSignature? = null      // ← 字段在这里，可空
}

data class FunctionSignature(
    val paramTypes: List<Type>,   // 不含 self
    val returnType: Type,
    val selfParam: SelfParam?,
)
```

`member` 的静态类型是 `Symbol`（`associatedValues` 是 `MutableMap<String, Symbol>`），`Symbol` 接口本身**没有** `signature`。所以必须先 `is FunctionSymbol ->` 做智能转换，转换后 `member.signature` 才合法，类型是 `FunctionSignature?`。`ConstantSymbol` 没有这个字段，掉进 `else` 报"不可调用"。

#### 二、具体例子

```rx
struct Pair {
    x: i32,
    y: i32,
}

impl Pair {
    fn new(x: i32) -> Pair { Pair { x, y: 0 } }   // 关联函数：没有 self
    fn sum(&self) -> i32 { self.x + self.y }      // 方法：有 &self
}

fn main() {
    let p = Pair::new(1);                          // ← 这里触发 resolveCallTarget
    println_i32(p.sum());
}
```

##### 阶段 2：`ImplRegistry.registerImpl`（`ImplRegistry.kt` 末尾）

```kotlin
is FnNode -> FunctionSymbol(
    name = item.name,
    node = item,
    receiver = target,
    owner = symbol,
)
...
symbol.associatedValues[associated.name] = associated
```

执行后：

```
PairSymbol.associatedValues = {
    "new" → FunctionSymbol(name="new", node=<AST>, signature=null),
    "sum" → FunctionSymbol(name="sum", node=<AST>, signature=null),
}
```

这时 `signature` 还是 `null`——阶段 2 明确不管签名。

##### 阶段 6：`FunctionResolver.resolveImpl` → `resolveSignature`（`FunctionResolver.kt`）

```kotlin
// resolveImpl 里：
val symbol = owner.associatedValues[member.name] as? FunctionSymbol ?: continue
resolveSignature(member, symbol)          // ← 拿到的是阶段 2 放的**同一个对象**

// resolveSignature 里：
symbol.signature = FunctionSignature(paramTypes, returnType, selfParam)
```

- 对 `new`：`paramTypes = [I32Type]`，`returnType = StructType("Pair")`，`selfParam = null`；
- 对 `sum`：`paramTypes = []`，`returnType = I32Type`，`selfParam = SelfParam(byReference=true, mutable=false)`。

执行后，**同一个对象**变成：

```
"new" → FunctionSymbol(signature = FunctionSignature([i32], Pair, null))
"sum" → FunctionSymbol(signature = FunctionSignature([], i32, &self))
```

`Map` 里存的只是引用，所以阶段 6 改的不是"复制品"，阶段 8 拿到的一定是最新值。

##### 阶段 8：你写的那段

```kotlin
val member = owner.associatedValues[name]      // 还是阶段 2 那个 FunctionSymbol
return when (member) {
    is FunctionSymbol -> {
        val signature = member.signature       // FunctionSignature? —— 阶段 6 填过
        if (signature.selfParam != null) throw ...
        signature                              // 返回给 checkArguments 用
    }
    ...
}
```

`Pair::new(1)` 走到这里时：

- `owner` 是 `Pair` 的 `StructSymbol`；
- `name = "new"`；
- `member.signature` 已经是 `FunctionSignature([i32], Pair, null)`；
- `selfParam == null` → 放行 → `checkArguments([1], [i32])` → 返回 `Pair`。

如果写的是 `Pair::sum(1)`，同一个 `member.signature.selfParam` 就是 `&self`，于是报 "method 'sum' requires a receiver"——这就是那条检查的来源。

#### 三、为什么可以写 `?:`

`Symbols.kt` 里 `signature` 声明成 `FunctionSignature?`，因为阶段 1/2 建符号时确实还没有它，不能写死非空。

而阶段 6 会遍历**所有** `FnNode`：顶层函数走 `resolveTopLevel`，impl 成员走 `resolveImpl`，两条路都调用 `resolveSignature`。所以只要阶段 6 正常跑完，任何被取到的 `FunctionSymbol.signature` 都非空。

`?: throw` 是防御性写法：真出现 `null`，说明管线的假设被破坏了（比如以后加了新的 item 形态但阶段 6 没遍历），此时报个内部错误比在 `checkArguments` 里对着 `null` 解引用好。如果你更想保守，也可以改成 `?: return null`（当成"暂不支持"），两种都说得通。

#### 四、一句话总结

> `member` 是 `Symbol`；`is FunctionSymbol` 分支里它才是 `FunctionSymbol`，`signature` 是这个类上的 `var`，**阶段 6 填、阶段 8 读**，生命周期横跨两个阶段，靠"同一个对象被 `associatedValues` 引用"串起来。

## 17. 2026-10-30 23:11 — 那有没有在impl中没有self的函数，就它不是方法

**你**

那有没有在impl中没有self的函数，就它不是方法

**Copilot**

有，而且这类东西有个专门的名字：**关联函数（associated function）**。

#### 术语与区分

`impl` 里的 `fn` 分两种，区别**只看参数表里有没有 self**：

| | 有 self 参数 | 没有 self 参数 |
|---|---|---|
| 叫法 | 方法（method） | 关联函数（associated function） |
| 例子 | `fn sum(&self) -> i32` | `fn new(x: i32) -> Pair` |
| 调用写法 | `值.sum()` | `Pair::new(1)` |
| 编译器里的标志 | `signature.selfParam != null` | `signature.selfParam == null` |

两者在符号表里是**同一种东西**——都是 `FunctionSymbol`，都挂在 `StructSymbol.associatedValues` 下：

- 阶段 2 `ImplRegistry` 对 `impl` 里每个 `FnNode` 一视同仁地建 `FunctionSymbol`，**不区分**有没有 self；
- 阶段 6 `resolveSignature` 遍历参数时才区分：

```kotlin
for (param in fn.params) {
    when (param) {
        is SelfParam -> selfParam = param          // 有 self → 方法
        is FnParam   -> paramTypes.add(...)        // 普通参数
    }
}
symbol.signature = FunctionSignature(paramTypes, returnType, selfParam)
//                                                    ↑ 没有 SelfParam 时就是 null
```

所以"是不是方法"不是靠 AST 位置决定的，而是靠 `selfParam` 是否为 `null` 这一条信息贯穿后面所有阶段。

#### 四种组合，两种合法

设 `S` 上有一个方法 `fn m(&mut self)` 和一个关联函数 `fn new() -> S`：

| 写法 | 结果 | 由谁判定 |
|---|---|---|
| `S::new()` | ✅ 合法，返回 `S` | `resolveCallTarget`：`selfParam == null` 才放行 |
| `值.m()` | ✅ 合法 | `checkMethodCall`：`selfParam != null` 才放行 |
| `S::m()` | ❌ "method 'm' requires a receiver" | `resolveCallTarget` 里你刚写的那段 |
| `值.new()` | ❌ "is not a method" | 下一步 `checkMethodCall` 要加的对称检查 |

后两条就是"调用形式必须和 self 的存在性匹配"。

#### 两个常见例子

1. **用户写的构造器**：

```rx
impl Pair {
    fn new(x: i32, y: i32) -> Pair {     // 没有 self
        Pair { x, y }
    }
}
fn main() {
    let p = Pair::new(1, 2);             // 关联函数调用
}
```

它的 `signature = FunctionSignature([i32, i32], Pair, selfParam = null)`，`resolveCallTarget` 直接放行。

2. **内建的 `Box::new` / `Vec::new`**：它们本质上就是"内建类型上的关联函数"，只不过没有 `FunctionSymbol` 对象，所以在 `resolveCallTarget` 里现场拼了一个 `FunctionSignature(listOf(element), BoxType(element), null)`——注意最后一个参数也是 `null`，跟用户关联函数的形状完全一致。

#### 顺带两个相关点

- **关联函数里不能写 `self`**：`BodyChecker` 里 `currentHasSelfParam = node.params.any { it is SelfParam }` 对关联函数是 `false`，所以 `self` 会报 "`self` is only allowed inside a method"，也不会往作用域里声明 `self` 绑定。
- **关联函数里 `Self` 仍然可用**：`currentSelfType = owner` 对方法和关联函数都设置，所以 `fn new() -> Self` 合法——这在 Rust 里也是标准写法。

下一小步写 `checkMethodCall` 时，`resolveMethod` 查到的 `FunctionSymbol` 如果 `selfParam == null`，就必须报错，这正好是上表最后一行。

## 18. 2026-10-30 23:15 — 那为什么这个多段中，不考虑关联函数

**你**

那为什么这个多段中，不考虑关联函数

**Copilot**

先分清楚你说的"多段"是哪一段——这段代码里其实有**两个**多段分支，它们对"关联函数"的态度不一样。

#### 1. `owner != null`：**考虑了**关联函数，拒绝的是方法

```kotlin
is FunctionSymbol -> {
    val signature = member.signature ?: ...
    if (signature.selfParam != null) {
        throw SemanticError(callee.span, "method '$name' requires a receiver")
    }
    signature          // selfParam == null → 关联函数，放行
}
```

`Pair::new(1)` 走的就是这里：`member` 是阶段 2 挂上去的 `FunctionSymbol`，`signature.selfParam == null`，直接返回签名。所以"关联函数"是处理了的。

被拒绝的是 `Pair::sum(...)`（`selfParam != null`）——因为**方法必须用点调用**，`::` 只是"类型 → 关联项"的通道，不带接收者。这不是"没考虑"，而是刻意区分两种调用形式。

#### 2. `owner == null`（`Box` / `Vec`）：只认 `new`，其余故意不处理

```kotlin
if (owner == null) {
    val kind = (ctx.globals.lookupType(first.name) as? BuiltinTypeSymbol)?.kind
    ...
    return when {
        kind == BOX && name == "new" -> FunctionSignature(listOf(element), BoxType(element), null)
        kind == VEC && name == "new" -> FunctionSignature(emptyList(), VecType(element), null)
        kind == BOX || kind == VEC  -> null      // ← clone 等，这里"不考虑"
        else -> throw ...
    }
}
```

这才是你感觉"没考虑关联函数"的地方。原因是**数据结构上的**，不是语义上的：

| | 用户类型 | 内建类型 |
|---|---|---|
| 符号 | `StructSymbol` | `BuiltinTypeSymbol` |
| 成员表 | `associatedValues: Map<String, Symbol>` | **没有**，只有一个 `kind` |
| 成员从哪来 | 阶段 2 `ImplRegistry` 逐个建 `FunctionSymbol` | 无 |

`Box::new`、`Vec::new` 在语义上就是"内建类型上的关联函数"，但编译器里没有地方可以"查"它们，所以只能在解析器里手写出来。

而 `Vec::clone(&v)` 这类**也是关联函数**（UFCS 写法），它需要 `Vec<T>` 的元素类型 `T` 来拼签名（`clone(&Vec<T>) -> Vec<T>`），属于计划里的"容器方法表"这一小步。现在返回 `null` 是**故意的脚手架**：

```kotlin
val signature = resolveCallTarget(callee)
if (signature == null) {          // 尚未支持：只检查实参，返回 ?
    for (arg in node.args) check(arg)
    return UnknownType
}
```

如果这里改成抛错，`Vec::clone(&v)` 这类现在能过的代码立刻就挂了。

#### 3. 想让它"统一考虑"的两条路（以后选一条）

1. **给 `BuiltinTypeSymbol` 也挂一张成员表**：`associatedValues: Map<String, FunctionSignature>`，阶段 0 `Builtins.install` 时填。查询就变成"struct 和 builtin 都先找成员表"，分支消失。
2. **保持现在的分支，但把内建表抽成函数**：`builtinMethodOf(kind, name, elementType): FunctionSignature?`，`owner == null` 分支调它，返回 `null` 才落到"暂不支持"。这就是之前说的第 4 小步。

第二种改动小、和现有结构一致，推荐它。

#### 4. 如果你指的是"值位置的 `S::f`"

`checkMultiSegmentPath` 里对 `FunctionSymbol` 成员返回 `UnknownType`（注释"关联函数作值：批次 D"），那是因为**函数在这门语言里不是值**——只有出现在调用位置（`resolveCallTarget`）才有意义。这条不用改，除非规范里真有"函数作值"的语义。

## 19. 2026-10-30 23:21 — 但是似乎规则里面允许了这种调用方式Type::method(receiver, …

**你**

但是似乎规则里面允许了这种调用方式Type::method(receiver, args)，`::` 确实可以调用方法，但它**不是 `.` 的另一种写法**；这两种形式在 Rx 规范中对应不同语法、不同语义：

- `receiver.method(args)` — **MethodCallExpression**，真正的方法调用，会做接收者查找（autoderef/autoref）。
- `Type::method(receiver, args)` — **CallExpression + 路径**，属于把方法当关联函数调用：接收者由你显式作为第一个实参传入，不走方法查找，也不自动加 `&`/`&mut`。
所以：日常方法调用用 `.`；`::` 是“显式指定类型、自己传接收者”的形式。两者不能随意互换。这个是解释，请你看看当前代码有没有问题

**Copilot**

你的解释是对的，而**当前代码在这里确实有一处与规范冲突**。

#### 问题在哪

`resolveCallTarget` 的多段分支：

```kotlin
is FunctionSymbol -> {
    val signature = member.signature ?: throw ...
    if (signature.selfParam != null) {
        throw SemanticError(callee.span, "method '$name' requires a receiver")   // ← 错
    }
    signature
}
```

按你给的规则，`Type::method(receiver, args)` 是**合法的**：用 `::` 走到方法时，self 变成调用者显式传入的第一个实参，只是**不自动加 `&`/`&mut`**。所以这里不该拒绝，而应该把 self 的类型**拼到形参表最前面**。

现有语料里就能找到证据：`Vec::<i32>::clone(&rows)` 就是这种形式（`clone` 是 `&self` 方法，调用者自己要写 `&`）。目前它能过是因为 `Vec` 分支走了 `return null` 的脚手架；一旦补上容器方法表、改成正常解析，同样的写法就会撞上这条错误的 `throw`。

还有一个隐含的反证：`methods-and-self` 里有一条 reject 叫 "explicit associated call does **not** autoref an owned argument"——正好说明 `S::m(owned)` 要被拒绝、`S::m(&owned)` 才通过。这条规则只有在"self 变成显式第一参数、并且只做普通可赋值性检查"时才会自然成立。

#### 修法

##### 1. 把 self 的类型映射抽成共享函数（放 `Symbols.kt`）

`BodyChecker` 里已经内联写过一遍，`ExprChecker` 马上要用第二遍，抽出来：

```kotlin
/**
 * `self` 的类型：`S` / `&S` / `&mut S`。
 * 方法体里的 `self` 绑定用它；`Type::method(receiver, args)` 的
 * 第一个显式实参类型也用它。
 */
internal fun selfValueType(selfParam: SelfParam, owner: StructSymbol): Type {
    val selfStruct = StructType(owner.name, owner.node)
    return if (selfParam.byReference) RefType(selfParam.mutable, selfStruct) else selfStruct
}
```

`BodyChecker` 那段就变成：

```kotlin
if (selfParam != null && owner != null) {
    ctx.currentScope.declareValue(
        LocalSymbol(
            name = "self",
            mutable = selfParam.mutable && !selfParam.byReference,
            type = selfValueType(selfParam, owner),
            span = null,
        )
    )
}
```

##### 2. 多段分支：不拒绝方法，改为前插 self

```kotlin
return when (member) {
    is FunctionSymbol -> {
        val signature = member.signature
            ?: throw SemanticError(callee.span, "'$name' has no signature")
        val selfParam = signature.selfParam
        if (selfParam == null) {
            signature                                   // 关联函数：原样
        } else {
            // Type::method(receiver, args)：self 变成显式第一实参，
            // 不做 autoref —— 引用必须由调用者自己写。
            FunctionSignature(
                paramTypes = listOf(selfValueType(selfParam, owner)) + signature.paramTypes,
                returnType = signature.returnType,
                selfParam = null,                       // 已经摊进 paramTypes
            )
        }
    }
    else -> throw SemanticError(callee.span, "'$name' is not callable")
}
```

要点：

- `signature.paramTypes` **本来就不含 self**（阶段 6 的约定），所以直接 `listOf(selfType) + paramTypes` 就是完整形参表；
- 返回类型不变；
- `selfParam` 置 `null` 只是表示"这个合成签名不再需要接收者"，`checkArguments` 只读 `paramTypes`，不受影响。

##### 3. 带来的两条正确行为（不用额外写代码）

| 写法 | 结果 | 原因 |
|---|---|---|
| `Pair::sum(&p)`（`sum(&self)`） | ✅ | `&S` → `&S` 可赋值 |
| `Pair::sum(p)`（拥有所有权的 `p`） | ❌ | `S` → `&S` 不可赋值，`assignable` 拒绝，**没有隐式 autoref** |
| `Pair::sum(&mut p)` 对 `&self` 方法 | ✅ | `&mut S` → `&S` 是可赋值性允许的弱化 |
| `Pair::set(&mut p)` 对 `&mut self` 方法 | ✅（`&mut` 对 `&mut`） |
| `Pair::set(&p)` 对 `&mut self` 方法 | ❌ | `&S` → `&mut S` 禁止加强 |

#### 顺便留个对称提醒

这条规则的另一半在即将写的 `checkMethodCall` 里：

- `.` 调用查到 `selfParam == null` 的成员 → 报"`f` is not a method"（对应 `rej-an-associated-function-is-not-a-dot-call-method`）；
- `.` 调用才做 autoref/autoderef；`::` 一律不做。

以及内建 `Vec`/`Box` 的方法表将来也要同时支持两种形式：点调用 `v.clone()` 和 UFCS `Vec::clone(&v)`——后者就是用同一个 `selfValueType` 的思路，把 `&Vec<T>` 前插到形参表。

## 20. 2026-10-30 23:37 — 这个修改直接在工作区里面改吧

**你**

这个修改直接在工作区里面改吧

**Copilot**

UFCS 改好了，但它把"调用检查"正式接通后，暴露了两类之前被 `?` 掩盖的问题。先看清楚再补。

已经在工作区改完了，并且顺手补了两个被"调用检查真正生效"暴露出来的问题。

#### 本次修改

**1. `Symbols.kt` 新增共享函数**

```kotlin
internal fun selfValueType(selfParam: SelfParam, owner: StructSymbol): Type
```

`S` / `&S` / `&mut S` 的映射现在只有一份，`BodyChecker` 的 `self` 绑定和 UFCS 调用都用它。

**2. `ExprChecker.resolveCallTarget`：不再拒绝方法，改为前插 self**

```kotlin
is FunctionSymbol -> withExplicitSelf(member.signature ?: ..., owner)

private fun withExplicitSelf(signature, owner) =
    if (signature.selfParam == null) signature
    else FunctionSignature(
        paramTypes = listOf(selfValueType(selfParam, owner)) + signature.paramTypes,
        returnType = signature.returnType,
        selfParam = null,
    )
```

`Pair::sum(&p)` 现在走普通可赋值性检查：`S → &S` 不可赋值，所以"owned 实参不 autoref"自然成立。

**3. 两处必须一并补的（否则 accept 掉到 65）**

| 回归 | 原因 | 修法 |
|---|---|---|
| `Node::clone(&root)`、`Pair::clone(&a)`、`Outer::clone(&a)` | derive 生成的 `clone` 不在 `associatedValues` 里 | `derivedMethodSignature`：`name == "clone"` 且结构体含 `Derive.CLONE`/`COPY` 时合成 `clone(&self) -> Self` |
| `read(&b)` 实参 `&Box<Box<i32>>` 对形参 `&i32` | 解引用收敛（deref coercion）还没实现 | `assignable` 里加 `boxDerefChainMatches`：沿 Box 逐层解引用，`&Box<...T>` → `&T` |

收敛规则刻意收窄，四个 reject 仍然成立：`Vec` 不参与、不在引用层之下的 `Box` 不参与、owned box 不会被隐式借用。

#### 结果

```text
accept: 69/69   （回归全部恢复）
reject: 127/167 （批次 C 结束时是 110，+17）
```

新拿下的目录：

| 目录 | reject |
|---|---|
| `calls-recursion-and-abi` | **3/3** |
| `builtin-io` | **4/4** |
| `box-and-moves` | **4/4** |
| `vec-operations` | 1/6 → **3/6** |
| `reference-coercions` | 0/4 → **3/4** |
| `nested-containers` | 0/2 → **1/2** |

#### 批次 D 还剩的部分

1. **`checkMethodCall`**：`resolveMethod`（autoderef 剥引用 → 结构体方法 / 容器方法表）+ `requireMutableReceiver`（`&mut self` 要可变接收者）+ 复用 `checkArguments`；点调用查到 `selfParam == null` 要报"不是方法"。
2. **容器方法表**：`Vec` 的 `len/push/remove/clone`、`Box` 的 `clone`，点调用与 UFCS 两种形式共用（UFCS 就是 `withExplicitSelf` 前插 `&Vec<T>` / `&mut Vec<T>`）。

这两步做完，`vec-operations`、`methods-and-self`、`nested-containers` 剩下的 reject 基本就清了。

## 21. 2026-10-31 01:48 — 所以现在是把self给放到所有函数参数前面，作为一个新函数吗？

**你**

所以现在是把self给放到所有函数参数前面，作为一个新函数吗？

**Copilot**

不是。这里没有"新造一个函数"，也没有动符号表里的任何东西，只是**为这一次调用临时拼了一张形参表**。

#### 它只做三件事

`withExplicitSelf` 是个纯函数：吃一个 `FunctionSignature`，吐一个新的 `FunctionSignature`（data class 是不可变的，原对象不受影响）。

```kotlin
private fun withExplicitSelf(signature, owner) =
    if (signature.selfParam == null) signature          // 普通函数 / 关联函数：原样返回
    else FunctionSignature(
        paramTypes = listOf(selfValueType(selfParam, owner)) + signature.paramTypes,  // 前插 self 类型
        returnType = signature.returnType,               // 返回类型不变
        selfParam = null,                                // self 已经变成普通参数
    )
```

所以：

- **不是"所有函数"**——只有 `selfParam != null` 的（也就是方法）才会前插；普通函数、关联函数、内建函数都是原样通过；
- **不是"新函数"**——`FunctionSymbol`、`associatedValues`、`signature` 字段全都没变，方法也没多出一个 `self` 参数；
- **生命周期只有一次调用**——`checkArguments` 用完这个临时签名就丢了。

#### 对比两个调用路径

假设：

```rx
impl Pair {
    fn sum(&self, extra: i32) -> i32 { ... }
}
```

符号表里存的签名**永远**是：

```text
FunctionSignature(paramTypes = [i32], returnType = i32, selfParam = &self)
```

##### `Pair::sum(&p, 3)` —— 走 `resolveCallTarget`

```text
签名 [i32], selfParam=&self
        ↓ withExplicitSelf（临时值）
     [&Pair, i32], selfParam=null
        ↓ checkArguments(args = [&p, 3])
     数量对了，&p 对 &Pair、3 对 i32
        ↓
     返回 i32
```

因为 `::` 语法本来就要求**把接收者写成第一个显式实参**，形参表不加它，数量检查就会错一位。

##### `p.sum(3)` —— 走（还没写的）`checkMethodCall`

```text
同一个签名 [i32], selfParam=&self
        ↓ checkArguments(args = [3])        ← 用原签名，self 不在实参里
     数量 1 对 1
        ↓ requireMutableReceiver(p, &self)  ← 接收者另行检查
     返回 i32
```

点调用里 `self` 是**隐式接收者**，不占实参位置；它是否满足 `&self`/`&mut self` 的要求，由接收者的 place 单独判定（autoref）。

#### 一句话

> `withExplicitSelf` 只服务于 `Type::method(receiver, args)` 这一种写法：把"隐式接收者"翻译成"显式第一参数"，让两种调用形式能共用同一个 `checkArguments`，仅此而已。

## 22. 2026-10-31 01:51 — 我的意思就是它生成了一个新的函数签名，返回的时候就返回新的这个函数签名？这个签名…

**你**

我的意思就是它生成了一个新的函数签名，返回的时候就返回新的这个函数签名？这个签名相比于普通的签名就是把self对应的类型放在前面了对吗？

**Copilot**

对，就是你说的意思。

#### 确认你的理解

**是的，`withExplicitSelf` 返回一个新的 `FunctionSignature` 对象**（`FunctionSignature` 是 data class，`FunctionSignature(...)` 就是在构造一个新值），`resolveCallTarget` 把它返回给 `checkCallExpr`，`checkCallExpr` 再用它调 `checkArguments`、并用它的 `returnType` 作为整个调用表达式的类型。

**是的，与存档的签名相比只改了两处**：

| 字段 | 符号表里存的 | 返回的临时签名 |
|---|---|---|
| `paramTypes` | `[extra: i32]` | `[selfType, extra: i32]`（self 类型前插） |
| `returnType` | `i32` | `i32`（不变） |
| `selfParam` | `&self` | `null`（已摊进 `paramTypes`） |

其中 `selfType` 由 `selfValueType` 决定：`self` → `S`，`&self` → `&S`，`&mut self` → `&mut S`。

#### 一个补充说明

准确说不是"生成了一个新函数"，而是"**生成了一个新签名值**"：

- 没有往 `associatedValues` 里加东西；
- 没有改 `member.signature`（它是 `var`，但这里没写它）；
- 这个新签名只活在这一次 `resolveCallTarget` 的返回值里，`checkArguments` 用完就没了，下次调用会重新拼。

而且 `selfParam == null` 时（普通函数、关联函数、内建函数），函数直接 `return signature`，返回的就是原来那个对象，连拷贝都没有。

所以整条链是：

```text
resolveCallTarget(Pair::sum)
  → 新签名 [&Pair, i32], selfParam=null
  → checkCallExpr 用它调 checkArguments(args = [&p, 3])
  → 再用它的 returnType（i32）作为 Pair::sum(&p, 3) 的类型
```

## 23. 2026-10-31 01:52 — 接下来写什么部分

**你**

接下来写什么部分

**Copilot**

下一步就是**点调用**：`checkMethodCall` + 方法查找 + 容器方法表。它要同时解决 `methods-and-self` 剩的 4 个 reject 和 `vec-operations` 剩的 3 个 reject，而且不能碰坏现有 accept（尤其是 `Box<Counter>` 上的方法调用）。

#### 一、要写的东西（4 个）

| 东西 | 放哪 |
|---|---|
| `resolveMethod(receiverType, name): FunctionSignature?` | `ExprChecker.kt` 私有 |
| `hasMutableAccess(receiverType, place): Boolean` | 同上 |
| `builtinMethodSignature(receiverType, name): FunctionSignature?` | `Builtins.kt`（语言知识） |
| `checkMethodCall` 重写 | `ExprChecker.kt` |

另外把 `withExplicitSelf` 从"收 `StructSymbol`"改成"收 `Type`"，因为 `Vec`/`Box` 没有 `StructSymbol`。

#### 二、方法查找：沿类型逐层走，每层先查自身再解引用

```kotlin
private fun resolveMethod(
    receiverType: Type,
    name: String,
    span: Span,
): FunctionSignature? {
    var current: Type = receiverType
    while (true) {
        when (current) {
            UnknownType -> return null                       // 脚手架：整体 ?
            is RefType -> current = current.inner            // autoref 的 deref 部分
            is StructType -> {
                val owner = ctx.globals.lookupType(current.name) as? StructSymbol
                    ?: return null
                val signature = structMethodSignature(owner, name)  // 见下
                    ?: throw SemanticError(span, "type '${current.name}' has no method '$name'")
                return signature
            }
            is VecType, is BoxType -> {
                builtinMethodSignature(current, name)?.let { return it }
                if (current is BoxType) {                    // Box 可以再解一层
                    current = current.element
                } else {
                    throw SemanticError(span, "type '$current' has no method '$name'")
                }
            }
            else -> throw SemanticError(span, "type '$current' has no method '$name'")
        }
    }
}
```

`structMethodSignature` 和已有代码共用"derive 生成"的识别：

```kotlin
private fun structMethodSignature(owner: StructSymbol, name: String): FunctionSignature? {
    val member = owner.associatedValues[name]
    val signature = when (member) {
        is FunctionSymbol -> member.signature ?: return null
        null -> derivedMethodSignature(owner, name) ?: return null   // 复用已有的
        else -> return null                                          // 常量不是方法
    }
    if (signature.selfParam == null) {
        throw SemanticError(..., "'$name' is not a method")          // 关联函数不能点调用
    }
    return signature
}
```

三个顺序细节：

1. **`RefType` 先解引用**：`&mut Counter`、`&&Counter` 都能找到 `Counter` 的方法。
2. **`Box` 先查自己的方法（`clone`），查不到再解到元素**。这对应 "dot clone precedes dereference"：`box.clone()` 得到的是 `Box<T>`，不是 `T`。
3. **`Vec` 不参与解引用**（`rej-vec-is-not-dereferenceable` 的语义），查不到就是错。

#### 三、接收者可写性：`&mut self` 的判定

`&self` / `self` 直接放行；只有 `&mut self` 需要检查：

```kotlin
private fun hasMutableAccess(type: Type, place: Place?): Boolean {
    var current = type
    var throughRef = false
    while (current is RefType) {
        if (!current.mutable) return false
        if (place?.underShared == true) return false
        throughRef = true
        current = current.inner
    }
    return if (throughRef) true else (place?.mutable ?: true)
}
```

对照：

| 表达式 | 类型 / place | 结果 |
|---|---|---|
| `let mut b: Box<Counter>; b.add(1)` | `Box<..>` / mutable=true | ✅ |
| `let b: Box<Counter>; b.add(1)` | place.mutable=false | ❌ |
| `let r = &mut b; r.add(1)` | `&mut Box<..>` | ✅（不要求 `r` 绑定 mut） |
| `let s = &b; s.add(1)` | `&Box<..>` | ❌ |
| `Counter::new().changed()` | rvalue，place=null | ✅（临时值可写） |

#### 四、容器方法表（`Builtins.kt`）

用 `FunctionSignature` 表达，`selfParam` 不能省——它决定接收者要求，也决定 UFCS 时前插什么：

```kotlin
internal fun builtinMethodSignature(receiver: Type, name: String): FunctionSignature? =
    when (receiver) {
        is VecType -> when (name) {
            "new"    -> FunctionSignature(listOf(receiver.element), receiver, null)  // 关联函数
            "len"    -> FunctionSignature(emptyList(), UsizeType, SHARED_SELF)
            "push"   -> FunctionSignature(listOf(receiver.element), UnitType, MUT_SELF)
            "remove" -> FunctionSignature(listOf(UsizeType), receiver.element, MUT_SELF)
            "clone"  -> FunctionSignature(emptyList(), receiver, SHARED_SELF)
            else -> null
        }
        is BoxType -> when (name) {
            "new"   -> FunctionSignature(listOf(receiver.element), receiver, null)
            "clone" -> FunctionSignature(emptyList(), receiver, SHARED_SELF)
            else -> null
        }
        else -> null
    }
```

这样 `resolveCallTarget` 里 `owner == null` 那段就能合并成一条路：

```kotlin
val selfType = when (kind) {
    BuiltinTypeKind.BOX -> BoxType(element)
    BuiltinTypeKind.VEC -> VecType(element)
    else -> throw SemanticError(callee.span, "primitive type '${first.name}' has no associated items")
}
return builtinMethodSignature(selfType, name)?.let { withExplicitSelf(it, selfType) }
```

`Vec::<i32>::clone(&v)` 的 `null` 脚手架也就此转正。

#### 五、`checkMethodCall` 本体

```kotlin
private fun checkMethodCall(node: MethodCallExprNode): Type {
    if (node.name.genericArgs.isNotEmpty()) throw ...

    // 一次遍历：类型 + place（&mut self 判定要用）
    val place = checkPlace(node.receiver)
    val receiverType = node.receiver.type!!

    val signature = resolveMethod(receiverType, node.name.name, node.name.span)
        ?: run {
            for (arg in node.args) check(arg)        // 脚手架：只检查实参
            return UnknownType
        }

    val selfParam = signature.selfParam!!
    if (selfParam.byReference && selfParam.mutable &&
        !hasMutableAccess(receiverType, place)
    ) {
        throw SemanticError(node.receiver.span, "method '${node.name.name}' requires mutable access")
    }

    checkArguments(node.args, signature, node.span, "method '${node.name.name}'")
    return signature.returnType
}
```

注意 `checkArguments` 用的是**原签名**（`paramTypes` 不含 self），这和 UFCS 路径刚好相反。

#### 六、验证

1. 先跑 `methods-and-self/ vec-operations/`：expect `4/5`、`6/6` 两个目录转绿，accept 不掉。
2. 再跑全量：期望 `accept 69/69`，reject 从 127 到 **~134**。
3. 特别盯两个 accept：`Box<Counter>` 上的 `b.add(1)` / `r.value()`，以及 `Counter::new().changed().value()` 这种 rvalue 链式调用。

一个已知的批次边界：`Box`/`Vec` 的 **索引** 仍是 `?`，所以 `vec-index-mutability` 不会被这批动到，留给批次 E。

## 24. 2026-10-31 10:55 — 现在的代码里面有一些编译错误，比如说这个SHARED_SELF是什么？

**你**

现在的代码里面有一些编译错误，比如说这个SHARED_SELF是什么？

**Copilot**

`SHARED_SELF` / `MUT_SELF` 是我在伪代码里用的**占位常量**，表示 `&self` 和 `&mut self` 这两种 `SelfParam`，代码里并没有定义它们——这是我的疏忽。同时那张表应该放 `Builtins.kt` 而不是 `ExprChecker`，另外还有三处占位符（`throw ...`、`node.name.span`、`SemanticError(...)`）也需要补实。我一起修掉。

两处小问题：`Vec::new` 的形参表我写成了 1 个（`Box::new` 才是 1 个），另外三个 methods-and-self 的 reject 都卡在**结构体字面量还返回 `?`**——`s.f()` 的接收者类型未知，方法查找直接跳过了。把字面量定型补上就能通。

再补三处：`Vec::is_empty`、数组的 `len`、以及 `clone` 的兜底（标量/数组是 Copy；共享引用本身可克隆，这正是那两个 accept 的语义）。

## 25. 2026-10-31 11:42 — 请你介绍一下下一个部分要解决什么问题，按照什么思路解决？请你在说解决思路的时候最…

**你**

请你介绍一下下一个部分要解决什么问题，按照什么思路解决？请你在说解决思路的时候最好能够和例子配合着说，用教学的方式告诉我其中的过程

**Copilot**

#### 先定位：批次 E 在整个阶段 8 里的位置

在批次 A–D 中，我们处理了控制流、运算、place（可赋值地点）和调用。批次 D 结束时：

```
accept 69/69, reject 138/167
```

剩下的 29 个未拒绝用例，按目录分布恰好对应两件尚未完成的工作：

| 目录 | 剩下 | 根因 |
|---|---|---|
| `vec-index-mutability` | 13 | 字段/下标的**真实类型**与**可变性归属** |
| `structs-and-fields` | 5 | 结构体字面量的 4 条规则 |
| `arrays` | 5 | 数组字面量类型 + 下标规则 + `Copy` 重复 |
| `aggregate-arguments-and-reference-fields` | 1 | 数组长度与形参类型比对 |
| 其余 4 个目录 | 5 | 批次 F（trait 派发、引用相等、引用强制转换） |

所以 **批次 E = 聚合类型的“构造 + 投影”**，是最后一个“纯类型”批次。它要回答 5 个问题：

1. `s.x` 的类型是什么？
2. `a[i]` 的类型是什么？谁能被下标？
3. `S { x: 1 }` 的字段怎么查？
4. `[1, 2, 3]` 的类型和长度是什么？
5. **通过一层投影能不能写？** ← 这是真正的难点，占 15 个用例

---

#### 一、最容易入手的问题：投影现在是 `?`

打开 `ExprChecker.kt` 第 49–50 行：

```kotlin
is FieldExprNode -> { check(expr.receiver); UnknownType }
is IndexExprNode -> { check(expr.receiver); check(expr.index); UnknownType }
```

而 `placeOfField` / `placeOfIndex` 里写着：

```kotlin
node.type = UnknownType      // 批次 E 换成真实字段/元素类型
```

这解释了为什么 `rej-unknown-accessed-field` 现在能通过：

```rust
struct S { x: i32 }
fn main() {
    let s = S { x: 1 };
    println_i32(s.y);          // 必须报错：S 没有字段 y
}
```

`s` 的类型是对的（批次 D 我们把 `checkStructLitExpr` 改成返回 `StructType` 了），但 `placeOfField` 没有去查 `StructSymbol.fields`，直接吐 `?`。而 `?` 在 `requireAssignable` 里是通配符，所以什么都拦不住。

修法很平：

```kotlin
// placeOfField 里
val structType = node.receiver.type as? StructType
    ?: throw SemanticError(node.span, "type ... has no fields")
val owner = ctx.globals.lookupType(structType.name) as? StructSymbol ?: ...
val fieldType = owner.fields[node.name]
    ?: throw SemanticError(node.span, "no field '${node.name}' in '${structType.name}'")
node.type = fieldType
```

`placeOfIndex` 同理：从 `ArrayType.element` / `VecType.element` 取元素类型，并顺便校验“谁能被下标”。

这里有一个设计味道很正的例子：

```rust
let a = [1, 2];
let i = 0i32;
println_i32(a[i]);        // rej-an-existing-i32-cannot-index-an-array
```

**数组长度可以是任意整数类型，但下标必须是 `usize`。** 这条规则在 Rust 里是 `SliceIndex` 的 trait 约束，在我们这里就是一个显式判断：`index.type` 必须是 `UsizeType`（或 `?`）。对应地：

```rust
let x = 1;
println_i32(x[0]);        // rej-integers-are-not-indexable
```

→ 接收者类型既不是 `ArrayType` 也不是 `VecType`（剥掉引用之后）就报 “type `i32` is not indexable”。

---

#### 二、核心难点：两种投影，可变性归属不同

这是批次 E 唯一需要“想明白”的地方，其余都是查表。先看两个**结构完全一样、结果相反**的例子：

```rust
// 接受：acc-immutable-array-struct-and-box-preserve-stored-mutable-access.rx
fn array(values: [&mut i32; 1]) {
    *values[0] = 2;          // values 不是 mut，却允许写
}
```

```rust
// 拒绝：rej-write-through-stored-reference.rx
fn update(values: Vec<&mut i32>) {
    *values[0] = 2;          // values 不是 mut，不允许写
}
```

同样的 `容器<&mut i32>`、同样的不可变绑定，为什么一个允许、一个必须拒绝？

关键在于**这一层投影是不是“借”过去的**：

- `array[0]` 是**内建下标**，它不经过任何借用，直接从你拥有的数组里取出那个位置 → 那个位置是**你的**，里面的 `&mut i32` 也是**你的**，你有完整权限。
- `Vec[0]` 是一次**借用型访问**（类比 Rust 的 `Index::index(&self)` / `IndexMut::index_mut(&mut self)`）→ 要拿到元素的可写权限，就必须先能可变地借 `Vec` 本身；`values` 不是 `mut`，借不到 → 元素位置是**只读的**，连它内部那个 `&mut` 也变成了假的。

用 manifest 里的原话更直白：

> "Writing through an indexed mutable reference requires **mutable access to the vector**."

所以我们需要给投影分两类：

| 投影种类 | 例子 | 得到的 place |
|---|---|---|
| **owned 投影**（拥有权直接下移） | `struct.field`、`array[i]`、`*box` | `mutable = base.mutable`，`underShared` **不变** |
| **borrowing 投影**（先要借容器） | `vec[i]` | 若 `base.mutable == false` → `mutable = false` **且 `underShared = true`** |

##### 为什么要 `underShared = true`，而不只是 `mutable = false`？

因为要拦的不是“写这个元素”，而是**“隔着这个只读位置去用里面的 `&mut`”**。看：

```rust
fn update(values: Vec<&mut i32>) {
    *values[0] = 2;
}
```

`values[0]` 的类型仍然是 `&mut i32`（类型没错！），错的是**这个 `&mut` 现在只是借来的**。字段 `mutable=false` 只能说明“这个槽位不能覆盖赋值”，拦不住 `*values[0] = 2`——因为那一步看的是**类型的** `mut`。

这正是我们在批次 C 埋下 `underShared` 的原因：它表示**“这个位置上的 `&mut` 是假的”**。于是链路是：

```
values                → Place(Vec<&mut i32>, mutable=false, underShared=false)
values[0]             → 借用型投影 → Place(&mut i32, mutable=false, underShared=true)
*values[0]            → 遇到 RefType(mutable=true)，但 inherited=underShared=true
                      → Place(i32, mutable = true && !true = false)  ← 拒绝写
```

**好消息是：`placeOfDeref` 里的这个逻辑批次 C 已经写好了。** 13 个 reject 目前全部失败，只是因为中间那一层 `node.type` 还是 `?`——`placeOfDeref` 看到 `?` 就直接返回“可写”，整条链白算了。所以这批工作的本质是：**把类型补上，让已经建好的可变性机制真正跑起来。**

##### 但要注意：现在必须同时修改 `projectionPlace`

看它现在的样子（批次 C 我们为了过 `let q = &p; **q = 2;` 写的）：

```kotlin
underShared = (base?.underShared ?: false) || !writable,
```

那个 `|| !writable` 是**对所有投影生效**的。如果只补类型、不改这里，上面那个接受用例立刻会回归：

```rust
fn array(values: [&mut i32; 1]) { *values[0] = 2; }
// values[0] → owned 投影，writable=false（values 不可变）
//            → underShared 被误置为 true → *values[0] 被拒 ✗
```

所以正确的改法是**把毒性的来源收窄**：

```kotlin
// owned 投影：字段 / 数组下标 / Box 解引用
private fun projectionPlace(base: Place?, baseType: Type): Place {
    val writable = when (baseType) {
        UnknownType -> true
        is RefType -> baseType.mutable && !(base?.underShared ?: false)   // 穿过共享引用
        else       -> base?.mutable ?: true                               // 拥有权下移
    }
    return Place(
        type = UnknownType,          // ← 各调用点补真实类型
        mutable = writable,
        // 只有"穿过共享引用"才毒化后面；拥有权下移不毒化
        underShared = (base?.underShared ?: false) || (baseType is RefType && !baseType.mutable),
    )
}

// borrowing 投影：只有 Vec 下标
private fun borrowingPlace(base: Place?): Place {
    val writable = base?.mutable ?: true
    return Place(UnknownType, writable, (base?.underShared ?: false) || !writable)
}
```

这样上面 6 个 accept / 13 个 reject 就能同时成立。我按这个模型把所有相关用例过了一遍，全部一致：

| 用例 | 关键一步 | 需要的判定 |
|---|---|---|
| `acc-...preserve-stored-mutable-access`（数组/字段/Box 三个函数） | `values[0]`、`holder.value`、`*value` | owned 投影不毒化 |
| `rej-write-through-stored-reference` | `*values[0]` | Vec 借用投影毒化 |
| `rej-shared-array/box/vector-reference-...` | `&T` 之后再投影 | 共享引用毒化（原来的 RefType 分支） |
| `acc-immutable-binding-of-mutable-vector-reference` | `values: &mut Vec<...>` | 类型里的 `&mut` 从**引用层**恢复可写 |
| `rej-immutable-box-owner-of-vector` | `Box<Vec<&mut i32>>` 再下标 | Box 解引用继承不可写 → Vec 借不到 |
| `rej-reference-field-does-not-bypass` | `values[0].value` | 毒化沿 owned 投影传播（`underShared` 继承） |

---

#### 三、还有一个“新”规则：只读位置不能把 `&mut` 搬走

下面两条用例，光有类型是拦不住的：

```rust
fn update(values: Vec<&mut i32>) {
    let target: &mut i32 = values[0];   // rej-mutable-reference-initializer
}
fn write(target: &mut i32) { *target = 2; }
fn update(values: Vec<&mut i32>) {
    write(values[0]);                   // rej-mutable-reference-argument
}
```

`values[0]` 的类型**确实**是 `&mut i32`，两边的赋值/传参在类型层面完全合法。错在**这不是一次“复制”，而是一次“搬迁”**：从只读位置里把 `&mut` 拿走，等于要求可变地访问那个位置。

所以需要一条独立于 `placeOfDeref` 的检查：**当表达式在 rvalue 位置求值、它是个只读 place、且结果的类型含 `&mut` 时，只允许“降级为共享引用”，不允许原样搬走。**

对照接受用例就能看清这条规则的边界：

```rust
fn inspect(values: Vec<&mut Counter>) {
    let shared: &Counter = values[0];   // 接受：&mut → & 的共享重借用
    println_i32(read(values[0]));       // 接受：&mut → & 形参，同上
    println_i32(values[0].read());      // 接受：&self 方法，只读接收者足够
}
```

实现上就是一小段，接在 `check(expr)` 算出类型之后：

```kotlin
// place 只读 + 类型是 &mut T 时，只有 expected 是 &T 才放行
if (!movable && type is RefType && type.mutable &&
    !(expected is RefType && !expected.mutable)) {
    throw SemanticError(expr.span, "cannot move a mutable reference out of a read-only place")
}
```

关键词：**只读 place 只能“借”，不能“搬”**。这条规则也是 Rust 里 `Cannot move out of ...` 的简化版，正好可以用它给学生讲清楚“place 的权限”和“值的类型”是两层独立的东西。

---

#### 四、结构体字面量：四条规则，一条已有

`rej-*.rx` 的四条（另外 `rej-same-shaped-structs-remain-nominally-distinct` 已经靠 `StructType` 按名字比较通过了）：

```rust
let s = S { x: 1, x: 2 };   // 重复字段
let s = S { x: 1 };         // 缺字段（S 还有 y）
let s = S { x: 1, z: 2 };   // 不存在的字段
let s = S { x: true };      // 字段类型不符
```

`StructSymbol.fields` 已经是 `LinkedHashMap<String, Type>`，所以 `checkStructLitExpr` 里加三个东西就够了：

```kotlin
val seen = HashSet<String>()
for (field in node.fields) {
    val declared = symbol.fields[field.name]
        ?: throw SemanticError(..., "no field '${field.name}' in '${symbol.name}'")
    if (!seen.add(field.name)) throw SemanticError(..., "duplicate field '${field.name}'")
    val actual = check(field.value, declared)
    requireAssignable(actual, declared, field.value.span, "field '${field.name}'")
}
val missing = symbol.fields.keys - seen
if (missing.isNotEmpty()) throw SemanticError(..., "missing field(s): ...")
```

注意顺序：**先查存在性，再查重复**——否则 `S { x:1, x:2 }` 里第一个 `x` 就把 `seen` 占了，报错信息会变成“重复”而不是“不存在”，语义都对但报错质量不同。

---

#### 五、数组字面量：类型、长度、`Copy`

`[1, 2, 3]` 现在也返回 `?`，顺手把三个用例解决：

```rust
let a: [i32; 2] = [1, 2, 3];          // rej-array-length-mismatch
fn f(a: [i32; 3]) {}
f([1, 2]);                             // rej-by-value-array-parameter-checks-its-fixed-length
let a = [Box::<i32>::new(1); 2];      // rej-repeating-more-than-once-requires-copy
```

思路是**用 `expected` 当提示词**（`check(expr, expected)` 本来就传进来了，只是数组分支没用）：

```
推导规则：
  有 expected = ArrayType(e, n)  → 元素按 e 检查，长度必须等于 n，结果类型 [e; n]
  无 expected                     → 统一所有元素类型（用现成的 join），长度 = 元素个数
  repeat 形式 [e; n]             → 类型 [typeOf(e); n]
                                → n > 1 时要求 typeOf(e) 是 Copy
```

第三条需要一个新的小谓词：

```kotlin
internal fun isCopy(type: Type): Boolean = when (type) {
    is IntegerType, BoolType, UnitType -> true
    is RefType -> true                       // &T 和 &mut T 都是"可复制"的句柄
    is ArrayType -> isCopy(type.element)
    is StructType -> // derive(Copy) 的结构体
    else -> false                            // Box / Vec 不是 Copy
}
```

`Box` 不是 `Copy`，所以 `[Box::new(1); 2]` 被拒；而 `[1, 2]`、`[[0,0]; 3]` 都通过。这也顺便解释了接受用例里的那句注释 “length one needs no copy”——**`[e; 1]` 只求值一次，不需要复制**，所以 `Box` 也允许。

---

#### 六、建议的实现顺序与验收

分成 4 步，每步都能独立验证（避免一次改太多、回归分不清是谁的锅）：

| 步 | 内容 | 目标目录 | 预期 |
|---|---|---|---|
| **E1** | `placeOfField`/`placeOfIndex` 补真实类型 + `projectionPlace` 收窄毒性 + 新增 `borrowingPlace`；`check()` 里 `Field`/`Index` 两个分支改为委托给 place 函数 | `vec-index-mutability/` `structs-and-fields/` | 13 + 1 |
| **E2** | 只读 place 禁止搬走 `&mut` | `vec-index-mutability/` | +2 |
| **E3** | 结构体字面量四条规则 | `structs-and-fields/` | +4 |
| **E4** | 数组字面量推导 + 下标规则 + `isCopy` | `arrays/` `aggregate-arguments-and-reference-fields/` | +5 |

每一步之后跑：

```bash
make compiler
python3 scripts/check_semantic.py vec-index-mutability/ structs-and-fields/ arrays/ -v
python3 scripts/check_semantic.py          # 全量，确认 accept 仍是 69/69
```

预期终点：`accept 69/69`，`reject 138 → ~161`（剩下 5 个留给批次 F）。

---

#### 七、三个一定会踩的坑

1. **`expr.type` 非 null 契约。** 批次 C 我们已经因为这条吃过 NPE 了。`placeOfField` / `placeOfIndex` 的**每一个** return 分支（包括出错前的正常路径）都必须写 `node.type`，因为 `placeOfDeref` 会直接 `node.operand.type!!`。
2. **`?: true` 不能删。** `make().x = ...`、`make_array(30)[0] = ...`、`&mut make_record(50).data[0]` 能通过，靠的是“base 为 null（rvalue）时视为可写”。补类型时千万别顺手改成 `base?.mutable ?: false`。
3. **E1 里改 `projectionPlace` 和补类型必须同一刀。** 只补类型不收紧毒性，`acc-immutable-array-struct-and-box-preserve-stored-mutable-access` 会从通过变失败；只收紧毒性不补类型，13 个 reject 一个都不动。改完先单独跑 `vec-index-mutability/`，它的 8 个 accept 全是这条规则的边界样本。

要开始的话，我建议先只做 E1 的两处改动（`projectionPlace` 收窄 + 两个 place 函数补类型），然后一起看 `-v` 输出——那一轮的错误信息会把 E2–E4 的边界也顺带确认一遍。

## 26. 2026-10-31 12:22 — // clone 兜底：标量 / 数组是 Copy；共享引用本身也可克隆 if …

**你**

// clone 兜底：标量 / 数组是 Copy；共享引用本身也可克隆
        if (name == "clone") {
            if (current is IntegerType || current == BoolType || current is ArrayType) {
                return FunctionSignature(emptyList(), current, SHARED_SELF)
            }
            sharedRef?.let { return FunctionSignature(emptyList(), it, SHARED_SELF) }
        }等一下i有个问题：这里的这个clone是derive里面的clone吗

**Copilot**

#### 不是。`resolveMethod` 里其实有 **5 个** `clone` 候选

你选中的这段是**最末位的兜底**，它跟 `derive` 生成的 `clone` 是两码事。把 `resolveMethod` 从头读到尾，优先级是这样一个序列：

| 顺位 | `clone` 的来源 | 命中位置 | 返回类型 | 用例 |
|---|---|---|---|---|
| 1 | 用户在 `impl` 里手写的 | `structMethodSignature` 的 `is FunctionSymbol` 分支 | 签名里写的 | `Inner::clone` |
| 2 | **`derive(Clone/Copy)` 生成** | `structMethodSignature` 的 `null -> derivedMethodSignature(...)` | `Self`（即 `StructType`） | `root.clone()` |
| 3 | `Vec` / `Box` 内建 | `Builtins.builtinMethodSignature` | 容器自身 | `view.clone() -> Box<i32>` |
| 4 | 标量 / 数组（Copy） | **兜底第一支** | 自身 | `5i32.clone()`、`a.clone()` |
| 5 | 走过的共享引用 | **兜底第二支** `sharedRef` | **引用本身** `&T` | `r.clone() -> &Opaque` |

关键点：**第 4、5 支永远轮不到 `struct`**。因为循环里一遇到 `is StructType` 就先调 `structMethodSignature`；只有它返回 `null`（既没 impl 也没 derive）才会 `break` 出来走到兜底，而这时 `current` 是 `StructType`，两支条件都不满足，直接掉到最后的 `throw`。这就是 `rej-clone-must-be-available` 的拦截路径：

```kotlin
throw SemanticError(span, "type '$receiverType' has no method '$name'")
```

#### 那这 4/5 支到底在补什么

因为**符号表里根本没有它们的条目**：

- `Builtins.builtinMethodSignature` 只登记了 `Vec` / `Box`；
- `structMethodSignature` 只能查 `StructSymbol.associatedValues`；
- 而 `i32`、`bool`、`[T; N]`、`&T` 是**内建类型**，没有 `StructSymbol`，也没有任何 impl 可查。

`acc-unique-shared-reference-clone-for-non-clone-target-array-clone-and-scalar-clone.rx` 就是专门测这三支的：

```rust
struct Opaque { x: i32 }        // 注意：没有 derive(Clone)！
fn main() {
    let x = Opaque { x: 8 };
    let r = &x;
    let copied = r.clone();     // ← 第 5 支：复制的是 &Opaque，不是 Opaque
    println_i32(copied.x);
    let a = [1, 2, 3];
    let mut b = a.clone();      // ← 第 4 支：数组是 Copy
    println_i32(5i32.clone());  // ← 第 4 支：标量是 Copy
}
```

用例名里的 *"unique shared reference clone for **non-clone target**"* 就是在说：`Opaque` 不可克隆，但 `&Opaque` 可以——**引用本身是 Copy，复制一个引用不需要被指向的类型可克隆**。这正是 `sharedRef?.let { FunctionSignature(..., it, SHARED_SELF) }` 那一行，注意它返回的是 `it`（`&Opaque`），不是 `it.inner`。

这也解释了 `println_i32(copied.x)` 为什么成立：`copied` 是 `&Opaque`，`copied.x` 是字段投影，直接穿过去。

#### 顺序不是随便排的：它决定语义

兜底里的顺序是 **先标量/数组，后共享引用**，而外层循环的顺序是 **先容器，后兜底**。两处顺序都影响结果：

```rust
fn f(view: &Box<i32>) {
    let mut via_reference: Box<i32> = view.clone();
    // 不是 &Box<i32>！循环先解掉 & → 命中 Box 的内建 clone（第 3 支），
    // 得到的是独立存储的 Box<i32>
}
```

如果先走兜底的 `sharedRef`，`view.clone()` 会变成 `&Box<i32>`，类型检查就过不了。这就是那个测试名 *"container dot clone **precedes dereference** and keeps storage independent"* 的含义。

同理 `&i32`：

```rust
// current 一路解到 i32，第 4 支先命中 → 返回 i32（而不是 &i32）
```

这也符合 Rust 的解析习惯（`x.clone()` 对 `&i32` 走 autoref 解析到 `<i32 as Clone>::clone`）。

#### 一个遗留的不一致

`derivedMethodSignature` 里写的是：

```kotlin
selfParam = SelfParam(byReference = true, mutable = false),
```

而兜底用的是 `SHARED_SELF`——两者值相同（`SelfParam` 是 data class，相等性一致），只是同一件事有两种写法。等批次 F 收拾 `derive` 语义时可以统一成 `SHARED_SELF`。

另外提醒一下批次 F 的一个已知缺口：现在 `Vec<T>::clone`、`Box<T>::clone` 在 `Builtins` 里是无条件返回的，没有检查 `T: Clone`，所以 `rej-even-empty-container-clone-requires-element-clone` 还没被拒。兜底这条 `current is ArrayType` 也有同样的问题——`[Box<i32>; 2].clone()` 现在会放行，严格说应该要求元素可克隆。

## 27. 2026-10-31 12:49 — 我们先来看place

**你**

我们先来看place

## 28. 2026-10-31 12:53 — 我们先来看place，似乎在我的理解里，这个checkPlace只是用来生成其是…

**你**

我们先来看place，似乎在我的理解里，这个checkPlace只是用来生成其是否可变的功能，通过记录其中的type、mut、是否在&下，所以这里具体到field和index下？这两个具体用在什么场景下呢？

**Copilot**

#### 先纠正一个前提：`checkPlace` 不只产出“能不能改”

`Place` 有三个字段，其中 `type` 不是副产物，它和 `mutable` 一样是**主产物**。证据就在 `checkAssign` 里：

```kotlin
val place = checkPlace(node.target) ?: throw ...
if (!place.mutable) throw ...

if (node.op == AssignOp.ASSIGN) {
    val valueType = check(node.value, place.type)   // ← 用 place.type 当 expected
    requireAssignable(valueType, place.type, ...)
}
```

`place.type` 是赋值右边的**期望类型**——`let mut x: usize = 1; x = 0;` 里的 `0` 能定成 `usize` 而不是默认 `i32`，靠的就是这一行。

所以 `checkPlace(e)` 的语义应该这样理解：

> **把 `e` 当成“一个位置”来求值**，同时回答两个问题：
> ① 这个位置装的是什么类型？② 现在这个位置我能写吗？

---

#### `checkPlace` 的四类调用者

它自己不会跑，是被这四处拉起来的：

| 调用者 | 触发场景 | 用到 `Place` 的什么 |
|---|---|---|
| `checkAssign` | `target = v`、`target += v` | `mutable` + `type`（当 expected） |
| `checkReference` | `&e`、`&mut e` | `mutable`（仅 `&mut`） |
| `checkMethodCall` | `recv.method()` | `mutable` + `underShared`（判断能不能 `&mut self`） |
| `checkUnary` / 递归 | `*p`、`s.f`、`a[i]` | 逐层传递 `mutable` / `underShared` |

四个场景有一个共同点：**它们操作的都不是一个“值”，而是一个“位置”。** 你不可能对 `1 + 2` 赋值、不能 `&mut f()`、不能 `3.increment()` —— 这些在语法上可以写，但语义上不是 place。

---

#### 为什么必须下探到 field 和 index

因为 place 的语法形式**不止一个变量名**。用文法写出来就是：

```
place ::= name                    ← placeOfPath
        | place . field           ← placeOfField
        | place [ index ]         ← placeOfIndex
        | * place                 ← placeOfDeref
```

`checkPlace` 的这一段 `when`，就是上面这个递归文法的直接实现：

```kotlin
private fun checkPlace(expr: ExprNode): Place? = when (expr) {
    is PathExprNode   -> placeOfPath(expr)
    is UnaryExprNode  -> if (expr.op == UnOp.DEREF) placeOfDeref(expr) else ...
    is FieldExprNode  -> placeOfField(expr)
    is IndexExprNode  -> placeOfIndex(expr)
    else              -> { check(expr); null }
}
```

少掉 `FieldExprNode` 和 `IndexExprNode` 这两支，`s.x = 2` 和 `a[0] = 3` 会掉进 `else` 返回 `null`，`checkAssign` 立刻报 **“assignment target is not a place”**——也就是说**连合法的字段/下标赋值都写不了**。

---

#### 三个具体场景，把这条链走一遍

##### 场景 A：`s.x = 2`

```rust
let mut s = S { x: 1 };
s.x = 2;                 // 允许
let t = S { x: 1 };
t.x = 2;                 // rej-immutable-struct-field-assignment
```

调用链：

```
checkAssign
└─ checkPlace(s.x)                 → placeOfField
   ├─ checkPlace(s)                → placeOfPath → Place(S, mutable = <绑定是否 mut>)
   └─ projectionPlace(base, S)     → Place(x 的类型, mutable = base.mutable)
```

`let mut s` → `mutable=true` → 放行；`let s` → `mutable=false` → “cannot assign to an immutable place”。

**这条链上唯一的权限来源是 `placeOfPath`，`field` 只负责传递。** 这正是“投影”的含义：字段本身没有权限，它继承你到达它时手里的权限。

##### 场景 B：`&mut values[0].value`

```rust
fn update(values: &mut Vec<&mut Counter>) {   // 注意：绑定不是 mut
    *values[0].value = 2;                     // 允许
}
```

```
checkReference(&mut) → checkPlace(values[0].value)
└─ placeOfField(.value)
   └─ checkPlace(values[0])
      └─ placeOfIndex([0])
         └─ checkPlace(values)  → placeOfPath → Place(&mut Vec<...>, mutable=false)
```

看起来 root 的 `mutable=false`（绑定没写 `mut`），但 `projectionPlace` 里有一段专门处理**类型层面的引用**：

```kotlin
baseType is RefType -> baseType.mutable && !(base?.underShared ?: false)
```

`&mut Vec<...>` 的 `mutable=true` → 即使绑定不可变，位置也可写（`let r: &mut T` 之后 `*r = ...` 合法，正是这条）。然后 index、field 逐层继承 `true`，最后 `&mut` 通过。

把类型换成 `Vec<&mut Counter>`（没有外层 `&mut`）→ 链上立刻变 `false` → `&mut` 被拒。**同一份 `checkPlace` 代码，两个相反结果，差别全在沿途的 `Place`。**

##### 场景 C：`values[0].increment()`

```rust
impl Counter { fn increment(&mut self) { self.value += 1; } }
fn update(values: Vec<&mut Counter>) {
    values[0].increment();     // rej-mutable-method-through-stored-reference
}
```

`checkMethodCall` 的第一件事就是：

```kotlin
val place = checkPlace(node.receiver)      // receiver = values[0] → 走 placeOfIndex
...
if (selfParam.mutable && !hasMutableAccess(receiverType, place)) throw ...
```

而 `hasMutableAccess` 的最后一行是：

```kotlin
return if (throughRef) true else (place?.mutable ?: true)
```

**注意 `?: true`。** 如果 `checkPlace` 因为少了 index 分支而返回 `null`，这里的默认值是“可写”→ 这个方法调用会被**静默放行**。这正是 `vec-index-mutability` 那 13 个 reject 现在一个都拦不住的原因之一：不是 `hasMutableAccess` 写错了，而是它拿到的 `place` 是在 `UnknownType`/`null` 上瞎猜的。

---

#### 一个容易忽略的设计点：place 的求值必须携带类型

`placeOfPath` 里第一行是 `val type = check(node)`——为什么不能只看形状、不查类型？

因为**可写性本身依赖类型**。批次 E 要落地的规则就是这个：

| 基座类型 | 投影种类 | 可写性 |
|---|---|---|
| 结构体（owned） | 拥有权下移 | 继承 `base.mutable` |
| 数组（owned） | 拥有权下移 | 继承 `base.mutable` |
| `Box<T>` | 拥有权下移 | 继承 `base.mutable` |
| `Vec<T>` | **借用型**（要先可变借容器） | `base.mutable` 且不产生“假 `&mut`” |
| `&T` | 共享引用 | 一律不可写，并且毒化后续 |

同一句 `x[0]`，`x` 是数组还是 `Vec`，结果完全不同：

```rust
fn array(values: [&mut i32; 1]) { *values[0] = 2; }   // 接受（owned 投影）
fn update(values: Vec<&mut i32>) { *values[0] = 2; }  // 拒绝（借用投影）
```

所以 `placeOfField` / `placeOfIndex` 在批次 E 里要做的正是：**把 `node.receiver.type` 从 `?` 换成真实类型，然后按类型选投影规则**——这两个函数就是那条规则唯一的落点。

---

#### 小结

- `checkPlace` 是 **place 文法的递归求值**，产物是 `(类型, 可写性, 是否被共享引用污染)` 三元组。
- `field` / `index` 两支不是“特例”，而是**投影链的传递节点**：权限从 `placeOfPath` 出发，沿 `field`/`index`/`*` 一路传播，每一步都可能降级。
- 三个使用方（赋值、`&mut`、`&mut self` 方法）消费的是同一条链的结果。
- 现在它们返回 `?`，等于把链打断在第一步；批次 E 的第一件事就是把类型补齐，让这条早就写好的链真正开始工作。

## 29. 2026-10-31 13:15 — 请你写一下placeofField 和placeofIndex，然后解释一下另一…

**你**

请你写一下placeofField 和placeofIndex，然后解释一下另一个问题：权限从 `placeOfPath` 出发，但是似乎field和index的检查和path是完全独立的？

**Copilot**

两个函数已经写进工作区了，先报结果，再回答你关于“独立性”的疑问。

#### 一、写好的部分

核心是先抽出**所有投影共用的第一步**——`containerPlace`：

```kotlin
private fun containerPlace(base: Place?, baseType: Type): Pair<Type, Place?>
```

它剥掉接收者类型外层的**引用**和 **Box**，返回“容器本身”和“到达容器时手里的权限”：

| 剥掉的一层 | 权限怎么变 |
|---|---|
| `&T` | 只读，并且毒化后续（`underShared = true`） |
| `&mut T` | 可写（与绑定是否 `mut` 无关） |
| `Box<T>` | 拥有权下移：权限照抄盒子本身，**不**毒化 |

然后两种投影各一个构造函数：

```kotlin
// 字段 / 数组元素：拥有权直接下移，权限原样继承
private fun ownedProjection(type, container) =
    Place(type, container?.mutable ?: true, container?.underShared ?: false)

// Vec 元素：借用型，容器只读则元素只读且毒化
private fun borrowingProjection(type, container) {
    val writable = container?.mutable ?: true
    return Place(type, writable, (container?.underShared ?: false) || !writable)
}
```

`placeOfField` / `placeOfIndex` 就变成“找成员类型 + 选一种投影”：

```kotlin
private fun placeOfField(node: FieldExprNode): Place {
    val base = checkPlace(node.receiver)
    val (containerType, container) = containerPlace(base, node.receiver.type!!)
    val fieldType = when (containerType) {
        UnknownType -> UnknownType
        is StructType -> /* owner.fields[name] ?: throw "no field" */
        else -> throw SemanticError(node.span, "type '$containerType' has no fields")
    }
    node.type = fieldType
    if (containerType == UnknownType) return unknownPlace()
    return ownedProjection(fieldType, container)
}
```

`placeOfIndex` 只多两件事：**下标的期望类型是 `usize`**，以及按容器种类选投影：

```kotlin
val indexType = check(node.index, UsizeType)      // a[0] 的 0 直接定成 usize
if (indexType != UnknownType && indexType != UsizeType) throw ...

return when (containerType) {
    UnknownType  -> unknownPlace()
    is ArrayType -> ownedProjection(elementType, container)      // 数组
    else         -> borrowingProjection(elementType, container)  // Vec
}
```

那个 `check(node.index, UsizeType)` 是一行很关键的细节：`a[0]` 因为没有后缀，默认会定成 `i32`，本来会被规则误杀；把 `usize` 当 `expected` 传进去，`a[0]` 合法、`a[i32变量]` 报错，两条用例同时成立。

`check()` 里的两个脚手架分支也顺势改成委托，这样右值位置的 `s.y`、`values[0]` 也有了真实类型：

```kotlin
is FieldExprNode -> placeOfField(expr).type
is IndexExprNode -> placeOfIndex(expr).type
```

**验证结果：** `accept 69/69`，`reject 138 → 152`。`vec-index-mutability` 的 8 个 accept 全过、14 个 reject 拒了 12 个；`arrays` 2/5、`structs-and-fields` 2/6。

过程中冒出一个必须处理的细节：`acc-moved-vectors-use-new-place-mutability` 要求 `Box<Vec<&mut i32>>` **可以**下标（`*values[0] = 4`，且 `values` 是 `mut`），而 `rej-immutable-box-owner-of-vector` 要求**同一个类型在不可变绑定下必须拒**。唯一的自洽解释就是：`*Box` 是拥有权下移，权限照抄盒子本身——这也是 `containerPlace` 里那个 `is BoxType` 分支的由来。

---

#### 二、`field` / `index` “独立”恰恰是有意的设计

你的观察是对的：这两个函数里**一个字符都没提到 `PathExprNode`**。但这不表示它们和 `placeOfPath` 没关系——关系不在“谁调用谁”，而在**参数传递**。

把三个函数的本质签名写出来就清楚了：

```
placeOfPath   : ExprNode            → Place?     -- 递归的 base case（权限的源头）
placeOfField  : Place? × Type       → Place      -- 状态转移函数
placeOfIndex  : Place? × Type       → Place      -- 状态转移函数
```

`placeOfField` 不关心“这个 `Place` 是怎么来的”，它只做 `Place → Place` 的变换。而链路是这样接上的：

```
checkPlace(values[0].value)
└─ placeOfField(.value)
   ├─ checkPlace(values[0])        ← 唯一的连接点
   │  └─ placeOfIndex([0])
   │     └─ checkPlace(values)
   │        └─ placeOfPath ──→ Place(Vec<&mut Counter>, mutable, underShared)   ← 权限诞生
   └─ containerPlace(base, ...) ⊕ ownedProjection(...)                          ← 权限被变换
```

用函数式的话说：**整条 place 求值是一个左折叠（fold）**，`placeOfPath` 是初始累加值，`field`/`index`/`*` 是每一步的步进函数，`Place` 就是那个被折叠的状态。

这种“独立”带来三个实际好处：

1. **多态**：同一个 `placeOfField` 服务所有 base——

   ```rust
   s.x              // base = path
   values[0].x      // base = index
   (*p).x           // base = deref
   make().x         // base = null（临时值）
   holder.value     // 方法体里的 self.field
   ```
   如果它内部去特判 `PathExprNode`，这五种就得写五份。

2. **规则集中**：判断“可写吗”的逻辑只在 `ownedProjection` / `borrowingProjection` 两个函数里，不在 AST 里、也不在 `checkAssign` 里。

3. **一个函数两个消费者**：右值位置（`println_i32(s.y)`）只想要类型，左值位置（`s.y = 1`）还想要权限。因为 `placeOfField` 返回的是 `Place`，`check()` 里取 `.type`、`checkAssign` 里取 `.mutable`，**不需要写两份遍历**。

---

#### 三、但“独立”不等于“无关”：三条必须守住的契约

这三个函数之所以能拼起来，全靠三条隐式约定：

**契约 1：必须调 `checkPlace(receiver)`，不能调 `check(receiver)`。**
写成 `check` 的话 `base` 永远是 `null`，于是 `container?.mutable ?: true` 默认成“可写”——所有字段赋值都会通过，`rej-immutable-struct-field-assignment` 立刻失败。`base == null` 这个默认值是为**临时值**（`make().x = 1`）准备的，不是为“懒得取权限”准备的。

**契约 2：`node.receiver.type!!`。**
`checkPlace` 的契约是“返回后 `type` 非 null”（批次 C 的 NPE 就是破坏它导致的）。`containerPlace` 依赖这一点才能区分“接收者是 `?`”和“接收者是 `&T`”。

**契约 3：`underShared` 要跟着 `Place` 一路传下去。**
字段是拥有权下移，所以它**继承但不新增**毒化；Vec 下标是借用，所以它**可能新增**毒化。漏掉前者会误杀合法代码（`[&mut i32;1]` 那个 accept），漏掉后者会放过非法代码（`Vec<&mut i32>` 的 13 个 reject）——这两个错误方向刚好相反，这也是为什么规则要写在两个不同的函数里，而不是一个 `projectionPlace` 里。

---

#### 四、还剩什么

| 剩余 | 数量 | 属于 |
|---|---|---|
| `Vec<&mut T>` 只读位置搬走 `&mut`（`rej-mutable-reference-initializer/argument`） | 2 | E2，需要新增一条“只读 place 不能 move”规则 |
| 结构体字面量（重复/缺失/未知/类型不符） | 4 | E3 |
| 数组字面量类型、长度、`Copy` 重复、不可变数组元素赋值 | 3 | E4 |
| `rej-by-value-array-parameter-checks-its-fixed-length` | 1 | E4（数组长度和形参比对） |
| 批次 F | 5 | trait 派发、引用相等、容器 clone 元素约束等 |

要不要接着做 E2？它只有两条用例，但规则本身（“只读 place 只能借、不能搬”）值得单独讲一遍。

## 30. 2026-10-31 14:05 — 写一下E2

**你**

写一下E2

**Copilot**

现在写 E2：给“只读位置不能搬走 `&mut`”落地。

E2 已完成，先看结果：

```
vec-index-mutability   accept 8/8   reject 14/14   ← 全绿
references-and-mutability / methods-and-self        ← 无回归
全量：accept 69/69, reject 152 → 154
```

#### 一、这条规则到底在说什么

先看两个对比用例：

```rust
fn write(target: &mut i32) { *target = 2; }

fn array(values: [&mut i32; 1]) {
    write(values[0]);            // 接受
}
fn update(values: Vec<&mut i32>) {
    write(values[0]);            // 拒绝：rej-mutable-reference-argument
}
```

两边的 `values[0]` **类型都是 `&mut i32`**，类型层面完全合法。差别在两处：

- 数组元素：拥有权直接下移，那个 `&mut i32` 是**你的**，可以随便搬。
- Vec 元素：是从只读容器里**借**出来的，那个 `&mut i32` 不是你的，你只能拿它**重借用**成 `&T` 借出去，不能整个搬走。

`let target: &mut i32 = values[0];`（`rej-mutable-reference-initializer`）是同一件事：初始化 = 把值搬进一个新变量。

#### 二、关键概念：`mutable` 管“槽位”，`underShared` 管“东西是谁的”

这是 E2 最重要的一点——`Place` 的两个布尔量回答的是**两个不同的问题**：

| | 问题 | 用来拦什么 |
|---|---|---|
| `mutable` | 这个**槽位**能不能被覆盖？ | `s.x = 1`、`a[0] = 1`、`*p = 1` |
| `underShared` | 槽位里的**东西**是不是我自己的？ | 透过里面的 `&mut` 写；把里面的 `&mut` 整体搬走 |

所以四种位置组合都能出现：

| 位置 | `mutable` | `underShared` | 能覆盖槽位 | 能搬走 `&mut` |
|---|---|---|---|---|
| `mut v: Vec<&mut i32>` 的 `v[0]` | ✓ | ✗ | ✓ | ✓ |
| `v: Vec<&mut i32>` 的 `v[0]` | ✗ | **✓** | ✗ | **✗** ← E2 |
| `v: [&mut i32; 1]` 的 `v[0]` | ✗ | ✗ | ✗ | **✓** |
| `v: &Vec<&mut i32>` 的 `v[0]` | ✗ | ✓ | ✗ | ✗ |

第 2、3 行是这次的核心：**“不能写这个槽位”和“不能搬走槽位里的东西”是两回事**。

#### 三、实现

新增一个检查入口：

```kotlin
fun requireMovable(expr: ExprNode, expected: Type?, type: Type, what: String) {
    if (type !is RefType || !type.mutable) return          // 只有 `&mut` 会被搬走
    if (expected is RefType && !expected.mutable) return   // `&mut` → `&`：重借用，合法
    if (!isPlaceShaped(expr)) return                       // 普通右值：没有位置权限问题

    val place = checkPlace(expr) ?: return
    if (place.underShared) {
        throw SemanticError(
            expr.span,
            "$what cannot move a mutable reference out of a read-only place",
        )
    }
}
```

三个提前返回各自挡掉一类误杀：

1. **只有 `&mut T` 才会“搬走”**——`i32`、`Vec<T>`、`&T` 的读取是复制或借用，不受限制。
2. **`&mut → &` 是重借用**。这就是 `acc-shared-coercions-and-receivers-on-immutable-vector` 里这行能过、而 `&mut → &mut` 不能过的原因：

   ```rust
   fn inspect(values: Vec<&mut Counter>) {
       let shared: &Counter = values[0];   // 允许：只读位置可以借出共享引用
       write(values[0]);                   // 拒绝：这是搬迁
   }
   ```
3. **非 place 形状的表达式**跳过。`write(&mut x)` 的实参是 `ReferenceExprNode`，右值就是新造的引用，不存在“从只读位置搬东西”。

`isPlaceShaped` 这个小函数还有个实际作用：避免对任意表达式再跑一遍 `checkPlace`。`checkPlace` 对非 place 节点会 `check(expr)`，那样块表达式会被二次检查、白跑一遍作用域。

#### 四、两个调用点

规则只在“值被绑定/传递出去”的地方生效：

```kotlin
// 1) 实参
requireAssignable(actual, expected, arg.span, "argument ${index + 1} of $what")
requireMovable(arg, expected, actual, "argument ${index + 1} of $what")

// 2) let 初始化（在 BodyChecker 里）
exprs.requireMovable(stmt.initializer, declared, initType, "let initializer")
```

注意第 2 处放在 `if (declared != null)` **外面**——没有类型标注的 `let x = values[0];` 同样是搬迁，也该拒；`expected` 为 `null` 时不会命中“重借用”豁免，正好落到检查里。

普通赋值 `*p = values[0];` 这次没有接。语义上同样应该拦，但没有对应用例，先不加，避免动到批次 C 已经稳定的赋值路径——等有测试覆盖或你确认语义时再补一行就行。

#### 五、剩下的

| 剩余 | 数量 | 属于 |
|---|---|---|
| 结构体字面量（重复/缺失/未知/类型不符） | 4 | **E3** |
| 数组字面量类型、长度、`Copy` 重复、不可变数组元素赋值 | 3 | **E4** |
| `rej-by-value-array-parameter-checks-its-fixed-length` | 1 | E4 |
| trait 派发 / 引用相等 / 容器 clone 约束 / 引用强制转换 | 5 | 批次 F |

E3 和 E4 都是纯查表式的收尾，可以合并成一轮做完；要现在继续吗？

## 31. 2026-10-31 14:18 — 把E3 E4也做了吧，向我解释其中改了什么

**你**

把E3 E4也做了吧，向我解释其中改了什么

**Copilot**

E3、E4 都做完了。全量结果：

```
accept 69/69, reject 154 → 163/167
```

剩下 4 个都在批次 F（容器相等/克隆的元素约束、引用强制转换），E 批次的 27 个目标全部拿下。

#### 一、E3：结构体字面量的四条规则

改动集中在 `checkStructLitExpr` 的循环里：

```kotlin
val seen = HashSet<String>()
for (field in node.fields) {
    // ① 字段必须存在
    val declared = symbol.fields[field.name]
        ?: throw SemanticError(field.value.span,
            "struct '${symbol.name}' has no field '${field.name}'")
    // ② 同名字段不能写两次
    if (!seen.add(field.name)) {
        throw SemanticError(field.value.span,
            "duplicate field '${field.name}' in '${symbol.name}'")
    }
    // ③ 字段类型必须匹配（declared 当 expected，正好给字面量定型）
    val actual = check(field.value, declared)
    requireAssignable(actual, declared, field.value.span, "field '${field.name}'")
}

// ④ 声明了但没写的字段
val missing = symbol.fields.keys - seen
if (missing.isNotEmpty()) {
    throw SemanticError(node.span,
        "missing field(s) ${missing.joinToString { "'$it'" }} in '${symbol.name}'")
}
```

对应四个用例：

| 用例 | 命中哪条 |
|---|---|
| `S { x: 1, z: 2 }` | ① `z` 不存在 |
| `S { x: 1, x: 2 }` | ② 重复 |
| `S { x: 1 }`（`S` 还有 `y`） | ④ 缺 `y` |
| `S { x: true }` | ③ 期望 `i32`，实得 `bool` |

三个值得说明的决定：

**1. 顺序是“先存在性、再重复”。** 如果反过来，`S { x: 1, x: 2 }` 里第一个 `x` 就占了 `seen`，第二个会先报“重复”——语义没错；但 `S { z: 1, z: 2 }` 会报“重复 `z`”而不是“没有 `z`”，报错信息会把人带偏。

**2. `check(field.value, declared)` 把声明类型当 expected 传进去**，这样 `struct S { n: usize }` 里的 `S { n: 0 }` 中 `0` 才会定成 `usize`——和数组长度、函数实参是同一套“无后缀字面量按期望定型”的机制。

**3. 返回类型没动。** 名义区分（`let a: A = B { x: 1 }` 必须拒）靠的是 `StructType` 用**名字**比较：

```kotlin
override fun equals(other: Any?): Boolean = other is StructType && other.name == name
```

所以 A 和 B 即使字段一模一样，`assignable` 也不成立。这条本来就通过，E3 没碰它。

#### 二、E4：数组字面量的类型推导

`check()` 里的分支从“检查完返回 `?`”换成一次真正的推导：

```kotlin
is ArrayLitNode -> checkArrayLit(expr, expected)
```

新函数处理两种形式：

##### 形式一：`[a, b, c]`

```kotlin
var elementType: Type? = null
for (element in node.elements) {
    val type = check(element, expectedArray?.element)   // 有 [T; N] 期望时给元素定型
    elementType = elementType?.let { join(it, type, element.span, "array elements") } ?: type
}

val length = node.elements.size.toLong()
if (expectedArray != null && expectedArray.length != length) {
    throw SemanticError(node.span, "array literal has $length element(s), expected ${expectedArray.length}")
}
return ArrayType(elementType ?: UnknownType, length)
```

- 元素类型用 `join` 求公共类型（`[[1,2],[3,4]]` 因此得到 `[[i32; 2]; 2]`）；
- 有 `[T; N]` 期望时，**元素按 `T` 检查、个数按 `N` 检查**——直接解决两条：

  ```rust
  let a: [i32; 2] = [1, 2, 3];   // 个数 3 ≠ 2        → rej-array-length-mismatch
  f([1, 2]);                     // 形参是 [i32; 3]   → rej-by-value-array-parameter-...
  ```
  第二条不用写任何新代码：`checkArguments` 本来就会 `check(arg, expected)`，只是以前数组分支把这个 `expected` 扔掉了。

- 顺带修好了一条没在清单里的用例：`expected-types/rej-a-fixed-array-target-rejects-a-mismatched-element`——因为元素现在真的按期望类型检查了。也正因为如此，**先查元素、后查个数**：这样 `[true, true]` 对 `[i32; 2]` 报的是元素类型错，而不是个数错。

##### 形式二：`[e; n]`

```kotlin
val length = ctx.constEval.requireUsize(repeat.length)      // 原本就有
val hint = expectedArray?.takeIf { it.length == length }?.element
val elementType = check(repeat.element, hint)

if (length > 1 && !isCopy(elementType)) {
    throw SemanticError(node.span,
        "repeating an array more than once requires a Copy element type, found $elementType")
}
return ArrayType(elementType, length)
```

关键在于 `length > 1` 这个条件，它正好对应接受用例里的两类写法：

```rust
let a = [element(); 3];          // i32 是 Copy           → 允许
let lone = [Box::<i32>::new(5); 1];   // 只有 1 份 = 移动，不需要复制 → 允许
let a = [Box::<i32>::new(1); 2];      // 2 份 Box，Box 不 Copy       → 拒绝
```

长度不匹配（比如 `let a: [i32; 3] = [1; 2];`）这里不单独报，交给外层的 `requireAssignable`——`ArrayType` 是 data class，长度参与相等性，`[i32; 2]` 对 `[i32; 3]` 自然不成立。

##### 配套的 `isCopy`（放在 `Coercion.kt`）

```kotlin
internal fun isCopy(type: Type): Boolean = when (type) {
    is IntegerType, BoolType, UnitType -> true
    UnknownType -> true                       // 脚手架通配，不误伤
    is RefType -> !type.mutable               // &mut T 复制 = 两条可变路径，不允许
    is ArrayType -> isCopy(type.element)
    is StructType -> type.decl?.derives?.any { it == Derive.COPY } ?: false
    else -> false                             // Box / Vec / Never
}
```

放在 `Coercion.kt` 是因为它是**类型能力**的判定，和 `assignable` / `join` 同一层；`&mut T` 特意返回 `false`——复制 `&mut` 会凭空造出第二条可变路径，和整个 E 批次的可变性模型冲突。

##### 一个连锁收益

`rej-immutable-array-element-cannot-be-assigned` 其实一行代码都没为它写：

```rust
let a = [1, 2];   // 以前是 ?，现在终于变成 [i32; 2]
a[0] = 3;         // placeOfIndex → ownedProjection → 容器 mutable=false → 拒绝
```

**批次 E 的规律就是：类型一补齐，批次 C 写好的权限机制自己就生效了。**

#### 三、改动清单

| 文件 | 改动 |
|---|---|
| `ExprChecker.kt` | `is ArrayLitNode` 分支改为调用 `checkArrayLit`；新增 `checkArrayLit`；`checkStructLitExpr` 补全四条字段规则 |
| `Coercion.kt` | 新增 `isCopy` |

#### 四、下一步

剩下 4 个都在批次 F，而且都是“能力约束”类的规则：

| 用例 | 需要的规则 |
|---|---|
| `rej-container-equality-requires-element-partialeq` | `Vec<T>/Box<T>` 的 `==` 要求 `T: PartialEq` |
| `rej-even-empty-container-clone-requires-element-clone` | `Vec<T>/Box<T>` 的 `clone` 要求 `T: Clone`（空容器也一样） |
| `rej-same-type-equality-requires-partialeq` | 结构体 `==` 要求 `derive(PartialEq)` |
| `rej-inner-reference-mutability-is-not-rewritten` | 引用强制转换不能改写内层可变性 |

这 4 条都要先建一张“类型 → 实现了哪些 derive”的查询表（`Vec`/`Box` 的内建表 + 结构体的 `derives`），正好可以复用批次 D 的 `Builtins.builtinMethodSignature` 那套结构。

另外提醒一句：批次 C、D、E 全都还没提交，现在是一个很自然的检查点（`accept 69/69 / reject 163/167`，且 `vec-index-mutability`、`arrays`、`structs-and-fields` 全绿）。建议先 `git commit` 再开 F。

## 32. 2026-10-31 14:43 — ok，现在我已经提交，然后，请你先给我看批次F要解决问题的例子

**你**

ok，现在我已经提交，然后，请你先给我看批次F要解决问题的例子

**Copilot**

批次 F 只剩 4 个用例，但它们代表**两类全新的规则**——前面 A–E 一直在做“类型算得对不对”，F 要做“**这个类型有没有这个能力**”。先看程序。

---

#### 第一类：`==` 和 `.clone()` 需要“能力”

##### F-1 结构体相等要求 `PartialEq`

```rust
// copy-clone-and-equality/rej-same-type-equality-requires-partialeq.rx
struct S {          // 没有 derive
    x: i32,
}
fn main() {
    let a = S { x: 1 };
    let b = S { x: 1 };
    let x = a == b;      // ← 必须报错
}
```

**现在**：`checkEquality` 只比较两侧类型是否相同，`S == S` 类型一致 → 直接返回 `bool`，静默通过。

**规则**：结构体能比较，前提是显式写了 `#[derive(PartialEq)]`。

**边界用例**（决定规则能写多宽）：

```rust
// copy-clone-and-equality/acc-copy-stays-independent-...rx
#[derive(Clone, Copy, PartialEq, Eq)]
struct Pair { flag: bool, x: i32 }

#[derive(Clone, PartialEq, Eq)]
struct Owned { p: Box<Pair>, values: Vec<i32> }

let a = Pair { flag: true, x: 4 };
let b = a;
println_i32((a != b) as i32);          // ✓ Pair 有 PartialEq
println_i32((original == cloned) as i32);   // ✓ Owned 有 PartialEq
println_i32(([a, c] == [c, a]) as i32);     // ✓ 数组，元素 Pair 有 PartialEq
```

这里同时透露了：**数组的相等要看元素**。

##### F-2 容器相等要求元素 `PartialEq`

```rust
// trait-dispatch-and-reference-equality/rej-container-equality-requires-element-partialeq.rx
struct Opaque { x: i32 }                 // 没有 derive
fn main() {
    let a = Box::<Opaque>::new(Opaque { x: 1 });
    let b = Box::<Opaque>::new(Opaque { x: 1 });
    let result = a == b;                 // ← 必须报错：Opaque 没有 PartialEq
}
```

**现在**：`Box<Opaque> == Box<Opaque>` 类型一致 → 通过。

**规则**：`Box<T>`、`Vec<T>`、`[T; N]` 能比较，前提是**元素**能比较——这是一条递归定义，不是一层判断。

##### F-3 容器 `clone` 要求元素 `Clone`（空容器也一样）

```rust
// trait-dispatch-and-reference-equality/rej-even-empty-container-clone-requires-element-clone.rx
struct Opaque { x: i32 }
fn main() {
    let v = Vec::<Opaque>::new();        // 一个元素都没有
    let b = Vec::<Opaque>::clone(&v);    // ← 必须报错
}
```

这条用例的**名字**就是重点：*even empty*。能力检查是**静态**的，和运行时有没有元素无关——`Vec<Opaque>` 这个类型本身就不可克隆。

**现在**：`Builtins.builtinMethodSignature` 里 `Vec::clone` 无条件是 `FunctionSignature(emptyList(), receiver, SHARED_SELF)`，谁来都放行。

**边界用例**：

```rust
// copy-clone-and-equality/acc-container-dot-clone-...rx
let original = Box::<i32>::new(7);
let mut copied: Box<i32> = original.clone();     // ✓ i32: Clone
let mut copy: Vec<Box<i32>> = values.clone();    // ✓ Box<i32>: Clone（元素 Box 又看 i32）
let mut nested_copy: Box<Box<i32>> = nested.clone();  // ✓ 两层都要过
```

以及**必须保持不变**的特殊通道：

```rust
// copy-clone-and-equality/acc-unique-shared-reference-clone-...rx
struct Opaque { x: i32 }        // 没有 Clone
let r = &x;
let copied = r.clone();         // ✓ 复制的是 &Opaque（引用本身 Copy），不是 Opaque
let a = [1, 2, 3];
let mut b = a.clone();          // ✓ [i32; 3] 的元素 i32: Clone
println_i32(5i32.clone());      // ✓ 标量
```

也就是说 `resolveMethod` 末尾那条 `sharedRef` 兜底**不属于**“容器 clone”，不能拿元素能力去卡它。

---

#### 第二类：引用强制转换只能改**最外层**

##### F-4 内层可变性不能被改写

```rust
// reference-coercions/rej-inner-reference-mutability-is-not-rewritten.rx
fn main() {
    let mut x = 1;
    let mut p = &mut x;         // p : &mut i32
    let q: &mut &i32 = &mut p;  // ← 必须报错
}
```

`&mut p` 的类型是 `&mut (&mut i32)`，目标类型是 `&mut (&i32)`。要让它通过，编译器必须把**内层**的 `&mut i32` 悄悄改成 `&i32`——这就是“改写内层可变性”。

**现在**：`Coercion.assignable` 的引用分支是递归的：

```kotlin
from is RefType && to is RefType ->
    (!to.mutable || from.mutable) &&
        (assignable(from.inner, to.inner) || boxDerefChainMatches(...))
```

`assignable(&mut i32, &i32)` 又命中同一条规则、`to.mutable=false` 放行 → 整体通过。

**边界用例**（合法的强转长什么样）：

```rust
// reference-coercions/acc-repeated-deref-coercion-and-mutable-to-shared-coercion.rx
fn read(x: &i32) -> i32 { *x }
fn write(x: &mut i32) { *x += 5; }

let mut b = Box::<Box<i32>>::new(Box::<i32>::new(7));
println_i32(read(&b));      // &Box<Box<i32>> → &i32    ：Box 链逐层解引用（合法）
write(&mut b);              // &mut Box<Box<i32>> → &mut i32：同上（合法）
let r: &i32 = &b;           // &Box<Box<i32>> → &i32    （合法）

let mut x = 3;
let p = &mut x;
println_i32(read(p));       // &mut i32 → &i32          ：外层可变性放宽（合法）
write(p);                   // &mut i32 → &mut i32      ：原样（合法）
```

所以 F-4 的规则可以精确表述为：

> 引用转换只允许发生在**最外层**：外层 `&mut` 可以降级为 `&`，`Box` 链可以在最外层逐层解引用；但**一旦进入被指向的类型内部，就必须完全相同**，不允许再做任何降级。

注意 `write(&mut b)` 说明“Box 链”和“外层降级”这两件事本身都合法——被判死的只有“内层降级”。

---

#### 四条规则合成一张表

| 判定 | 规则（递归） |
|---|---|
| `T` 能 `==` | 整数/布尔/`()` → 是；引用 → **是**（比地址）；`[T;N]`/`Vec<T>`/`Box<T>` → 看 `T`；结构体 → `derive(PartialEq)`；`?` → 是 |
| `T` 能 `clone()` | 整数/布尔/`()` → 是；`&T` → 是（复制引用）；`[T;N]`/`Vec<T>`/`Box<T>` → 看 `T`；结构体 → `derive(Clone)`（`Copy` 单独出现已在声明期被拒）；`?` → 是 |
| 相等运算 | 两侧都是引用 → 豁免能力检查（`p == q` 比地址） |
| 引用强转 | 外层：允许 `&mut→&`、允许 Box 链；内层：必须相等 |

两个“看结构化信息”的判定，正好分别卡在：

- `checkEquality` —— 在返回 `Bool` 之前加一次能力检查；
- `Builtins.builtinMethodSignature` 的两条调用路径（点调用 `v.clone()` 走 `checkMethodCall`/`resolveMethod`，UFCS `Vec::<Opaque>::clone(&v)` 走 `resolveCallTarget`）—— **两条都要卡**，否则 F-3 那条用例会从另一条路溜过去；
- `Coercion.assignable` 的 `RefType` 分支 —— 把内层的 `assignable(...)` 换成相等判断。

#### 需要你确认的两点语义

1. **`Eq` 是否隐含 `PartialEq`？** 从 `rej-eq-must-explicitly-request-partialeq.rx`（`#[derive(Eq)]` 单独写、声明期就报错）看，答案是**不隐含**，所以相等只看 `derive(PartialEq)`。
2. **`&mut T` 是否 `Clone`？** `acc-unique-shared-reference-clone` 只测了 `&T`。按“复制 `&mut` 会造出第二条可变路径”的一贯立场，我倾向判**不是**；但这不影响现有用例，可以先按宽松（引用都算）实现，等有测试再收紧。

要看实现的话我可以直接按这张表动手，预计 `reject 163 → 167`、`accept` 保持 69/69。

## 33. 2026-10-31 14:53 — 所以相等只看 `derive(PartialEq)`，`&mut T` 不是 `…

**你**

所以相等只看 `derive(PartialEq)`，`&mut T` 不是 `Clone`（也不是 `Copy`），完成一下F

**Copilot**

批次 F 已完成，测试结果全部通过：

```
accept: 69/69 passed
reject: 167/167 properly rejected
SUMMARY: OK
```

#### 一、新增 `Capabilities.kt`：三条递归能力判定

前面批次都在计算类型，这里开始判断类型是否具备某种能力。三条判定的结构一致：基本类型直接通过，容器继续检查元素，结构体检查 `derive`，引用单独处理。

```kotlin
internal fun supportsPartialEq(type: Type): Boolean = when (type) {
    is IntegerType, BoolType, UnitType -> true
    UnknownType -> true
    is RefType -> true                        // 引用比的是地址
    is ArrayType -> supportsPartialEq(type.element)
    is VecType -> supportsPartialEq(type.element)
    is BoxType -> supportsPartialEq(type.element)
    is StructType -> type.decl?.derives?.any { it == Derive.PARTIAL_EQ } ?: false
    else -> false
}
```

`supportsClone` 形状相同，但有两处刻意不同：

```kotlin
is RefType -> !type.mutable                   // 复制 &mut 会造出第二条可变路径
is StructType -> derives(CLONE) || derives(COPY)
```

`isCopy` 也从 `Coercion.kt` 搬到了这里——三个能力判定放在一起，`Coercion.kt` 就回到“只处理类型与类型之间的可赋值性”。

**为什么引用豁免**：`&Opaque` 能 `clone`，不需要 `Opaque` 可 clone——复制的是引用。这正是 `acc-unique-shared-reference-clone-for-non-clone-target` 那条用例的意义，也是能力表里最容易被写成“一路递归到底”而误杀的地方。

#### 二、相等：`checkEquality` 里加一道能力检查

```kotlin
if (!assignable(right, left) && !assignable(left, right)) {
    throw SemanticError(node.span, "equality operands must have the same type: ...")
}
requireEquatable(left, node.span)
return BoolType
```

顺序很重要：**先报类型不一致，再报能力不足**。`a == 1` 里 `a: S`，用户需要知道的是“两侧类型不同”，而不是“S 没有 PartialEq”。

`requireEquatable` 只是一层带 `Span` 的包装：

```kotlin
private fun requireEquatable(type: Type, span: Span) {
    if (!supportsPartialEq(type)) {
        throw SemanticError(span, "equality requires PartialEq, but '$type' does not implement it")
    }
}
```

这一处同时解决了两个用例：

- `S == S`（无 derive）→ `supportsPartialEq(StructType)` = false → 拒绝
- `Box<Opaque> == Box<Opaque>` → 递归到 `Opaque` → false → 拒绝

而 `p == q`（`&mut i32`）依旧通过，因为引用层直接返回 true。

#### 三、容器 clone：**两条路径都要拦截**

这是这批唯一容易出错的地方。`Vec::<Opaque>::clone(&v)` 走的是 **UFCS 路径**：

```
checkCallExpr → resolveCallTarget（多段路径）
              → owner == null（内建类型）→ Builtins.builtinMethodSignature
```

而 `v.clone()` 走的是 `checkMethodCall → resolveMethod`。如果只在点调用路径加检查，那么测试用的 UFCS 写法仍会被放行。因此两处都加了检查：

```kotlin
// resolveCallTarget 里
val signature = Builtins.builtinMethodSignature(selfType, name) ?: return null
if (name == "clone") requireCloneable(selfType, callee.span)
return withExplicitSelf(signature, selfType)

// resolveMethod 的 Vec/Box 分支里
if (name == "clone") requireCloneable(current, span)
```

数组的 clone 在兜底分支中也一并覆盖：

```kotlin
if (current is IntegerType || current == BoolType || current is ArrayType) {
    requireCloneable(current, span)          // 数组要求元素 Clone
    return FunctionSignature(emptyList(), current, SHARED_SELF)
}
// 引用复制：被指向的类型可不可 clone 与此无关，直接豁免能力检查
sharedRef?.let { return FunctionSignature(emptyList(), it, SHARED_SELF) }
```

最后那行注释是有意保留下来的“例外登记”：`r.clone()`（`r: &Opaque`）不能因为 `Opaque` 没有 derive 而被卡住。

#### 四、引用强转：内层必须完全相同

`Coercion.assignable` 的引用分支改了一行核心判断：

```kotlin
from is RefType && to is RefType ->
    (!to.mutable || from.mutable) &&
        (from.inner == to.inner ||                 // ← 原来是 assignable(from.inner, to.inner)
            from.inner == UnknownType || to.inner == UnknownType ||
            boxDerefChainMatches(from.inner, to.inner))
```

对照两个用例看效果：

| 表达式 | 判断过程 | 结果 |
|---|---|---|
| `&mut p`（`&mut &mut i32`）→ `&mut &i32` | 外层可变性 ✓，但内层 `&mut i32 != &i32`，Box 链不适用 | **拒绝** |
| `&mut b`（`&mut Box<Box<i32>>`）→ `&mut i32` | 内层不等，但 `boxDerefChainMatches` 沿 Box 解到 `i32` | 通过 |
| `p`（`&mut i32`）→ `&i32` | 外层降级 ✓，内层 `i32 == i32` | 通过 |
| `&b`（`&Box<Box<i32>>`）→ `&i32` | 外层降级 ✓，Box 链 ✓ | 通过 |

一句话：**可变性只能在最外层放宽，Box 链也只能在最外层解**；进入被指类型内部后，不再做任何改写。`rej-inner-reference-mutability-is-not-rewritten` 通过，而“重复解引用 / 可变转共享”那个 accept 不受影响。

#### 五、改动清单

| 文件 | 改动 |
|---|---|
| `Capabilities.kt` | **新增**：`supportsPartialEq` / `supportsClone` / `isCopy` |
| `Coercion.kt` | 引用分支内层改为相等判断；`isCopy` 移出 |
| `ExprChecker.kt` | `checkEquality` 调 `requireEquatable`；`resolveCallTarget` 与 `resolveMethod` 的内建 clone 调 `requireCloneable`；数组 clone 兜底同样受约束，引用兜底豁免 |

#### 六、接下来

阶段 8 的 167 个 reject 全部拒掉、69 个 accept 全部通过，语义分析这章可以说收尾了。建议：

1. **先提交这一批**（`accept 69/69, reject 167/167` 是个很干净的提交点）。
2. **接 WP6，把测试接起来**：`config.mk` 里的 `SEMANTIC` 现在还是用参考实现的 `rustc` 命令，换成我们自己的编译器：

   ```
   SEMANTIC = ./scripts/rxc --stage semantic {source}
   ```
   然后 `make test STAGE=semantic`，就能用官方 runner 跑这 236 个用例，而不是只靠 `scripts/check_semantic.py`。

需要我先把 `config.mk` 改掉并跑一遍 `make test STAGE=semantic` 吗？

## 附录：导出对话（2026-11-03）

**你**

导出一下上次导出之后所有的对话记录，叫做 AI_CHAT_7

**Copilot**

已将 `docs/AI_CHAT6.md` 之后的会话导出为 `docs/AI_CHAT_7.md`（即本文件）。
