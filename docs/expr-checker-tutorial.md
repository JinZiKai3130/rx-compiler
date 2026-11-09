# ExprChecker 详解（阶段 8 表达式检查）

> 代码：`src/main/kotlin/rx/semantic/ExprChecker.kt`
> 配套：`BodyChecker.kt`（语句/作用域）、`Coercion.kt`（类型关系）、
> `Place.kt`、`Capabilities.kt`、`Builtins.kt`
> 运行：`./scripts/rxc --stage semantic <file.rx>`；回归：`python3 scripts/check_semantic.py`
>
> 本文以**当前实现**为准。`docs/expr-checking.md` 是当时的施工计划，
> 里面“批次 A/B/C…”的完成状态已经过时；规则细节以本文和源码为准。
> 示例默认省略 `use rx::core::*;` 和 `fn main() {}`，单独运行时请补上。

---

## 0. ExprChecker 的位置与调用关系

### 0.1 从 `SemanticAnalyzer` 到 `ExprChecker`

```text
SemanticAnalyzer.analyze
  └─ BodyChecker.check(crate.items)                  // 阶段 8
       ├─ 顶层 FnNode → checkFunctionBody(fn, owner = null)
       └─ ImplNode   → 每个成员 FnNode → checkFunctionBody(fn, owner = struct)
              ├─ 建函数作用域，绑定参数 / self
              ├─ checkBlock(body, currentReturnType)
              │    ├─ checkStatement(LetNode)
              │    │    ├─ resolveType(declaredType)
              │    │    ├─ exprs.check(initializer, declaredType)
              │    │    └─ requireMovable(...)
              │    ├─ checkStatement(ExprStmtNode)
              │    │    └─ exprs.check(expr, expected)
              │    └─ exprs.check(tail, expected)
              └─ requireAssignable(bodyType, currentReturnType, ...)
```

`BodyChecker` 负责"结构和作用域"，`ExprChecker` 负责"值、类型、place"。两者互相持有引用：

- `BodyChecker.checkBlock` 遇到块尾表达式时调用 `exprs.check`；
- `ExprChecker.check` 遇到 `BlockExprNode` 时回调 `body.checkBlock`；
- 这是刻意的分工，不要试图把两者合成一个类。

### 0.2 表达式在整棵 AST 里的根

| 根 | 谁负责 | 传入的 `expected` |
| --- | --- | --- |
| `FnNode.body` | `BodyChecker.checkBlock` | 函数返回类型 |
| `LetNode.initializer` | `BodyChecker.checkStatement` | 声明的类型（没写则 `null`） |
| `ExprStmtNode.expr` | `BodyChecker.checkStatement` | 带分号 `null`；不带分号 `UnitType` |
| `BlockExprNode.tail` | `BodyChecker.checkBlock` | 块的 `expected` |
| `ConstNode.value` | `ConstEval`（阶段 4） | 不走 `ExprChecker` |
| `ArrayTypeNode.length` | `ConstEval.requireUsize` | 不走 `ExprChecker` |

两个容易误解的点：

1. **`const` 初值不经过 `ExprChecker`**。文法上 `constValue` 只允许字面量/路径/负号，
   所以它走受限的 `ConstEval`，不走通用表达式类型检查。
2. **`struct` 本身没有表达式**。字段类型在阶段 5 解析；字段的初始值只在
   `StructLitNode` 这种表达式里出现，才由 `ExprChecker` 检查。

### 0.3 为什么不用 `AstVisitor`

`AstVisitor.visit(node)` 返回 `Unit`，装不下"表达式 → 类型"的结果；
而且 `ExprChecker` 对同一棵子树有明确的检查顺序和 `expected` 传播，
不是简单的同构遍历。visitor 留给 `AstPrinter` 这类只读消费者。

---

## 1. 核心契约

### 1.1 `check(expr, expected) -> Type`

```kotlin
fun check(expr: ExprNode, expected: Type? = null): Type {
    val type = when (expr) {
        is IntLitNode -> intLiteralType(expr, expected)
        is BoolLitNode -> BoolType
        is UnitLitNode -> UnitType
        // ... 其余节点
        else -> UnknownType
    }
    expr.type = type       // ← 算完写回 AST，供 codegen 直接读
    return type
}
```

三条约定：

1. **每个分支算出一个 `Type`，最后统一写回 `expr.type`。**
   中途抛 `SemanticError` 时不会写回；语义阶段是 fail-fast。
2. **`checkPlace(expr)` 也会给表达式定型。**
   对不是 place 的表达式，`checkPlace` 内部调用 `check(expr)` 后返回 `null`；
   对 place 表达式，它返回 `Place`，并保证相关 `expr.type` 已写回。
3. **`checkPlace` 之后 `receiver.type` 一定非 null。**
   `placeOfField` / `placeOfIndex` / `placeOfDeref` 都依赖这个契约，
   所以它们可以安全使用 `node.receiver.type!!`。

### 1.2 上下文状态

`ExprChecker` 持有 `SemanticContext` 和 `BodyChecker`：

| 状态 | 含义 | 谁维护 |
| --- | --- | --- |
| `globals` | 全局作用域：内建 + 顶层 `fn`/`const` + 用户 `struct` | 阶段 1 建立，之后只读 |
| `currentScope` | 当前局部作用域（函数/块） | `BodyChecker` 进入/退出时 save/restore |
| `currentReturnType` | 当前函数返回类型 | `BodyChecker.checkFunctionBody` |
| `currentHasSelfParam` | 当前函数是否有 `self` | `BodyChecker.checkFunctionBody` |
| `currentSelfType` | 当前 `Self` 指向的 `StructSymbol` | `BodyChecker`（方法体）/ `StructResolver`（字段）/ `FunctionResolver`（签名） |
| `loopStack` | 活跃循环栈，末尾是最内层 | `ExprChecker` 自己 push/pop |
| `constEval` | 常量求值器，数组长度 / repeat 长度用 | 阶段 4 创建 |
| `body` | 回调 `checkBlock` | 构造 `ExprChecker` 时注入 |

### 1.3 内部类型

| 类型 | 含义 | 备注 |
| --- | --- | --- |
| `I32Type` / `U32Type` / `IsizeType` / `UsizeType` | 四种整数 | 都实现 `IntegerType(signed, bits)`；RV32 下 32 位 |
| `BoolType` / `UnitType` / `NeverType` | `bool` / `()` / `!` | `NeverType` 表示永不正常返回 |
| `StructType(name, decl)` | 用户结构体 | **名义类型**：只按名字相等，同形状不同名仍是不同类型 |
| `RefType(mutable, inner)` | `&T` / `&mut T` | |
| `ArrayType(element, length)` | `[T; N]` | 长度由常量求值得到，参与类型相等 |
| `BoxType(element)` | `Box<T>` | 内建容器 |
| `VecType(element)` | `Vec<T>` | 内建容器 |
| `UnknownType` | 脚手架 `?` | 不是源码类型；见 §15 |

### 1.4 类型关系：assignable / join / stripSharedRef

#### `assignable(from, to)`

定义在 `Coercion.kt`，只回答"`from` 能否直接当 `to` 用"：

```text
from == to                                  → ✓
from == !                                   → ✓（发散表达式可出现在任何位置）
from == ? 或 to == ?                        → ✓（脚手架）
from = &mut T, to = &T                      → ✓（只允许最外层弱化）
from = &T,    to = &mut T                   → ✗
from = &T1,   to = &T2（T1 != T2）           → ✗，例外是 &Box<...Box<T>> → &T
其他                                        → ✗
```

关键点：

- **没有隐式整数转换**：`i32` 不能赋给 `u32`。
- **没有自动借用**：`T` 不能当 `&T` 用（`f(b)` 不会自动变成 `f(&b)`）。
- **没有 Box/Vec 解引用 coercion**：值 `Box<T>` 不能当 `T`/`&T`；
  值 `Vec<T>` 不能当 `&T` 或数组。例外是 `&Box<T>` 可以收敛成 `&T`（沿 Box 链解引用）。
- 引用内层可变性不会被改写：
  `&mut &mut i32` 不能当 `&mut &i32` 用（`rej-inner-reference-mutability-is-not-rewritten`）。
- `boxDerefChainMatches` 只沿 `Box` 解，不沿 `Vec` 解。

#### `join(a, b, span, what)`

用于 if 两支、数组元素、loop 的 break 载荷等"找公共类型"：

```text
! 与任意 T        → T
? 与任意 T        → T
相同类型          → 该类型
&mut T 与 &T      → &T（外层 mut 取交）
&T1 与 &T2        → &join(T1, T2)
其他              → SemanticError("$what: incompatible types ...")
```

注意：数组/结构体/Box/Vec 没有协变或 LUB；只有完全相同才会通过 `a == b`。

#### `requireAssignable(from, to, span, what)`

不满足就报 `"$what must have type $to, found $from"`。

#### `stripSharedRef(type, span, what)`

一元 `-`/`!`、算术、位运算、移位会先剥掉**一层共享引用**：

```text
?          → ?
&T         → T
&mut T     → 报错："$what does not accept a mutable reference"
其他 T     → T
```

所以：

```rust
let a = 1;
let x = -&a;        // ✓ 剥一层 &i32 → i32
let y = -&mut a;    // ✗ 可变引用不参与标量运算
let z = &&a + &a;   // ✗ 左边剥完还是 &i32，不是整数
```

### 1.5 使用期能力与 Place

`Capabilities.kt` 提供三个"类型有没有这个能力"的函数：

| 函数 | 用于 | 主要规则 |
| --- | --- | --- |
| `supportsPartialEq(T)` | `==` / `!=` | 标量 true；引用 true；容器递归元素；struct 看 derive `PartialEq` |
| `supportsClone(T)` | `.clone()` | 标量 true；`&T` true；`&mut T` false；容器递归；struct 看 `Clone`/`Copy` |
| `isCopy(T)` | `[e; n]`（n > 1） | 标量 true；`&T` true；`&mut T` false；数组递归；struct 看 `Copy`；Box/Vec false |

`Place` 在 §9 详细展开；先记住它可以回答：

```text
这个表达式能不能当左值？       → checkPlace(expr) != null
当前位置能不能写？             → place.mutable
这个位置是不是被共享引用罩住？ → place.underShared
```

---

## 2. 分派总表

`check` 的 `when` 覆盖全部表达式节点：

| 节点 | 处理函数 | 结果类型 |
| --- | --- | --- |
| `IntLitNode` | `intLiteralType` | 后缀 > expected > `i32` |
| `BoolLitNode` / `UnitLitNode` | 直接返回 | `bool` / `()` |
| `PathExprNode` | `checkPathExpr` | 局部/常量/关联常量；其余 `?` |
| `BlockExprNode` | `body.checkBlock` | 尾表达式类型 / `()` / `!` |
| `IfExprNode` | `checkIf` | 两支 `join`；无 else 为 `()` |
| `WhileExprNode` | `checkWhile` | 恒 `()` |
| `LoopExprNode` | `checkLoop` | break 载荷 `join`；无 break 为 `!` |
| `BreakExprNode` / `ContinueExprNode` / `ReturnExprNode` | 对应检查 | 恒 `!` |
| `AssignExprNode` | `checkAssign` | 恒 `()` |
| `CallExprNode` | `checkCallExpr` | 被调函数返回类型 |
| `StructLitNode` | `checkStructLitExpr` | `StructType` |
| `UnaryExprNode` | `checkUnary` | 操作数类型（`*` 为内层类型） |
| `ReferenceExprNode` | `checkReference` | `&T` / `&mut T` |
| `BinaryExprNode` | `checkBinary` | 按运算符分组 |
| `FieldExprNode` | `placeOfField(...).type` | 字段类型 |
| `IndexExprNode` | `placeOfIndex(...).type` | 元素类型 |
| `MethodCallExprNode` | `checkMethodCall` | 方法返回类型 |
| `CastExprNode` | `checkCast` | 目标整数类型 |
| `ArrayLitNode` | `checkArrayLit` | `ArrayType` |
| 其他（`ExprNode` 不是 sealed） | `else` | `UnknownType` |

> `ExprNode` 家族没有 `sealed`，所以新增节点时编译器不会强制你更新这里。
> 改 AST 时一定要回来检查 `check` 的分派。

---

## 3. 字面量

### 3.1 整数字面量

```kotlin
private fun intLiteralType(node: IntLitNode, expected: Type?): Type = when (node.suffix) {
    "i32" -> I32Type
    "u32" -> U32Type
    "isize" -> IsizeType
    "usize" -> UsizeType
    null -> expected as? IntegerType ?: I32Type
    else -> throw SemanticError(node.span, "invalid integer suffix '${node.suffix}'")
}
```

选型顺序：**后缀 > 期望类型 > 默认 `i32`**。

```rust
let a: u32 = 1;      // ✓ 1 定成 u32
let b = 1;           // i32（默认）
let c = 1usize;      // usize（后缀）
let d: u32 = 1i32;   // ✗ 后缀优先，i32 不能赋给 u32
```

边界：

- `IntLitNode.value` 是 `Long`；AstBuilder 已经处理 `0b`/`0o`/`0x`、下划线、后缀剥离。
- `ExprChecker` **不做数值范围检查**（超出 `i32`/`u32` 属于规范里的 UB）。
- 只有"无后缀"字面量会使用 `expected`；有后缀时 `expected` 不参与选型。

### 3.2 布尔与单元

```rust
true          // bool
false         // bool
()            // ()
```

`UnitLitNode` 恒为 `UnitType`；它不能当别的类型使用（例如 `let x: i32 = ();` 会报
`let initializer must have type i32, found ()`）。

### 3.3 `expected` 到底能传多远

`expected` 不是"往下无脑传播"的：

| 场景 | 是否传播 |
| --- | --- |
| `let` 初始化器 | ✓ 声明类型 |
| 赋值右侧 | ✓ 左值类型 |
| 调用实参 / 结构体字段 | ✓ 形参 / 字段类型 |
| if 两支 | ✓ 外层 `expected` |
| 数组元素 | ✓ `[T; N]` 的元素类型 |
| `loop` 的 break 载荷 | ✓ 外层 `expected` |
| `return` 载荷 | ✓ 函数返回类型 |
| `&expr` / `&mut expr` 的操作数 | ✗（不向引用内部传播） |
| 一元 `-` / `!` 的操作数 | ✗ |
| 算术左操作数 | ✓ 外层 `expected`；右操作数用左类型当 `expected` |
| 比较左操作数 | ✓ 外层 `expected`；右操作数用左类型 |

因此：

```rust
let a: &u32 = &1;      // ✗ &i32 vs &u32：expected 没有进入 &
let b: u32 = 1;        // ✓ expected 进入 let
let c: u32 = if true { 1 } else { 2 };  // ✓ 两支都收到 u32
```

---

## 4. 路径表达式

### 4.1 单段路径

`checkSingleSegmentValuePath` 的顺序：

1. 如果是 `self`：
   - 不在有 `self` 参数的函数里 → `` `self` is only allowed inside a method ``；
   - `self` 带泛型实参 → `` `self` takes no type arguments ``；
   - 否则继续按普通局部名查找（BodyChecker 已经把 `self` 声明成 `LocalSymbol`）。
2. `currentScope.lookupValue(name)`：
   - 找不到 → `unresolved value 'x'`；
3. 路径段带泛型实参 → `functions and values have no type parameters`；
4. 按符号类型返回：
   - `LocalSymbol` → 它的类型；
   - `ConstantSymbol` → `resolveType(node.type)`；
   - 其他（`FunctionSymbol` / `BuiltinFunctionSymbol`）→ `UnknownType`（函数不是一等值，属于 UB）。

```rust
fn main() {
    let x = 1;
    let y = x;          // LocalSymbol → i32
}
const C: i32 = 1;
fn main() {
    let y = C;          // ConstantSymbol → i32
}
```

### 4.2 多段路径与 `resolvePathOwner`

`checkMultiSegmentPath` 先调用 `resolvePathOwner`：

- 第一段是 `Self`：
  - `currentSelfType` 为空 → `` `Self` is not allowed here ``；
  - 带泛型实参 → `` `Self` takes no type arguments ``；
  - 否则 owner = 当前 struct。
- 第一段查类型名字空间：
  - `Box` / `Vec`：允许 0 或 1 个泛型实参；**多于 1 个**报
    `'Box' takes at most one type argument here`；owner = `null`；
  - 其他内建类型（`i32` 等）：不能带实参；owner = `null`；
  - 用户 `StructSymbol`：不能带实参（`struct 'S' has no type parameters`）；owner = struct；
  - 找不到 → `unresolved path 'X'`。
- 第二段及以后：任何泛型实参都报 `associated items have no type parameters`。
- 所有段的泛型实参都会用 `ctx.checkTypeRef(arg)` 递归检查类型名。

然后 `checkMultiSegmentPath` 只看最后一段：

```text
owner?.associatedValues[last]
  ├─ ConstantSymbol → resolveType(常量类型)
  └─ 其他 / 缺失  → UnknownType（函数作值 / 缺失项属于 UB；调用位置另行检查）
```

### 4.3 典型例子

```rust
struct S { x: i32 }
impl S {
    const N: i32 = 3;
}

fn main() {
    let a: i32 = S::N;        // ✓ 关联常量
    let b = S::missing;       // 当前实现：UnknownType（不报错）
    // let c = S::missing();  // ✗ no associated item 'missing' in 'S'
}
```

```rust
impl S {
    fn f(&self) -> i32 {
        Self::N                // ✓ Self = S，取关联常量
    }
}

fn g() {
    // let x = Self::N;        // ✗ `Self` is not allowed here
}
```

### 4.4 泛型实参规则速查

| 位置 | 允许的实参 |
| --- | --- |
| 单段值路径（局部/常量/函数） | 无 |
| 表达式位置 `Box::<T>` / `Vec::<T>` | 0 或 1 个；0 个是 UB 但暂不报错 |
| 其他内建类型 `i32<bool>` | 无 |
| 用户 struct `S<i32>` | 无（Rx 没有类型参数） |
| 关联段 `S::<T>::f` / `v.len::<T>()` | 无 |

对应官方测试：`invalid-impls-and-generics` 里的
`rej-functions-have-no-type-parameters`、`rej-structs-have-no-type-parameters`、
`rej-primitives-take-no-type-arguments`、`rej-box-requires-exactly-one-concrete-type-argument`、
`rej-vec-requires-exactly-one-concrete-type-argument`、`rej-method-segments-have-no-type-parameters`。

---

## 5. 控制流

### 5.1 块与语句（与 BodyChecker 的交界）

`checkBlock` 在 `BodyChecker` 里：

```kotlin
internal fun checkBlock(block: BlockExprNode, expected: Type? = null): Type {
    val savedScope = ctx.currentScope
    ctx.currentScope = savedScope.child()
    try {
        var diverges = false
        for (stmt in block.statements) {
            if (checkStatement(stmt)) diverges = true
        }
        val tail = block.tail
        if (tail != null) return exprs.check(tail, expected)
        return if (diverges) NeverType else UnitType
    } finally {
        ctx.currentScope = savedScope
    }
}
```

语句规则：

| 语句 | 检查 |
| --- | --- |
| `let` | 先 `resolveType(declaredType)`；初始化器以声明类型为 `expected`；`requireAssignable`；`requireMovable`；**检查完才** `defineLocal` |
| 带分号表达式语句 | 值被丢弃，任意类型都可以 |
| 不带分号、非尾表达式语句 | 必须是 `()`，否则 `block statement must have type ()` |

`let x = x;` 看到的是外层 `x`（或报未定义），因为 `defineLocal` 在初始化器之后。
同一块里 `let x = 1; let x = true;` 合法（局部遮蔽）；块内绑定不会逃出块。

块的结果类型：

```text
有 tail            → tail 的类型（即使前面的语句已经发散）
无 tail + 发散     → !
无 tail + 未发散   → ()
```

“发散”只由类型为 `!` 的**表达式语句**标记；`let` 初始化器发散不会设置 `diverges`。
不可达的尾表达式仍然参与类型检查：

```rust
fn f() -> i32 {
    return 7;
    9                  // ✓ 仍然检查，类型 i32
}

fn g() -> i32 {
    return 7;
    true               // ✗ true 与 i32 不匹配
}
```

### 5.2 `if`

```kotlin
private fun checkIf(node: IfExprNode, expected: Type?): Type {
    val condType = check(node.condition, BoolType)
    requireAssignable(condType, BoolType, node.condition.span, "if condition")

    val thenType = body.checkBlock(node.thenblock, expected)
    val elseBranch = node.elseBranch
    if (elseBranch == null) {
        requireAssignable(thenType, UnitType, node.thenblock.span, "if without else")
        return UnitType
    }

    val elseType = check(elseBranch, expected)
    return join(thenType, elseType, node.span, "if branches")
}
```

要点：

- 条件必须是 `bool`；`if 1 {}` 会报 `if condition must have type bool, found i32`。
- 无 else：then 分支必须 `()`，整个 if 类型也是 `()`。
- 有 else：两支都接收外层 `expected`，最后 `join`。
- `!` 在 `join` 中让位给另一支：
  `if true { return 1; } else { 2 }` 的类型是 `i32`。
- 不可达/未走到的分支照样检查：`if true { 1 } else { true }` 会报分支类型不兼容。

```rust
let a: u32 = if true { 1 } else { 2 };  // ✓ 两支都被 expected 定成 u32
let b = if true { 1 };                  // ✗ then 是 i32，不是 ()
```

### 5.3 `while`

```kotlin
private fun checkWhile(node: WhileExprNode, expected: Type?): Type {
    val savedLoops = ctx.loopStack.toMutableList()
    ctx.loopStack.clear()
    val condType = try {
        check(node.condition, BoolType)
    } finally {
        ctx.loopStack.addAll(savedLoops)
    }
    requireAssignable(condType, BoolType, node.condition.span, "while condition")

    ctx.loopStack.addLast(
        LoopContext(acceptsValue = false, expected = expected, span = node.span)
    )
    try {
        val bodyType = body.checkBlock(node.block, UnitType)
        requireAssignable(bodyType, UnitType, node.block.span, "while body")
    } finally {
        ctx.loopStack.removeLast()
    }
    return UnitType
}
```

两个关键的顺序细节：

1. **条件在任何循环之外求值**：先把 `loopStack` 清空，再检查条件。
   所以 `while break {}` 里的 `break` 没有可归属的循环；
   `while continue {}` 也一样。
2. **进入循环体才 push `LoopContext`**，所以 body 里的 `break` / `continue` 属于这个 `while`。
   `while` 的 `LoopContext.acceptsValue = false`，因此 `break 1` 会报
   `` `while` does not accept a break value ``。

`while` 的结果恒为 `()`，即使条件恒真：

```rust
fn f() -> i32 {
    while true {}       // ✗ body 是 ()，不能当 i32 返回
}
```

`while` 的 body 必须 `()`；`while body must have type (), found X`。

### 5.4 `loop`

```kotlin
private fun checkLoop(node: LoopExprNode, expected: Type?): Type {
    val loop = LoopContext(acceptsValue = true, expected = expected, span = node.span)
    ctx.loopStack.addLast(loop)
    try {
        val bodyType = body.checkBlock(node.block, UnitType)
        requireAssignable(bodyType, UnitType, node.block.span, "loop body")
    } finally {
        ctx.loopStack.removeLast()
    }

    var result: Type = NeverType
    for (breakType in loop.breakTypes) {
        result = join(result, breakType, node.span, "break values")
    }
    return result
}
```

- body 必须 `()`（循环体的尾值不是 loop 的结果，`break` 才是）。
- 类型由所有 `break` 的载荷 `join` 得到：
  - 没有带载荷的 break → `!`（裸 `break` 也不加入 `breakTypes`）；
  - 一个带载荷的 break → 载荷类型；
  - 多个 break → 公共类型，不兼容则报 `break values: incompatible types ...`。
- 外层 `expected` 会传给 `break` 载荷：`let x: u32 = loop { break 1; }` 里的 `1` 定成 `u32`。
- 不可达的 break 也参与定型（`unreachable-checks`）：
  `loop { if false { break 1i32; } }` 仍然是 `i32` 的 loop。

```rust
let a: i32 = loop { break 1; };          // ✓
let b: i32 = loop { if false { break 1i32; } }; // ✓ 不可达 break 也定型
let c = loop { break 1i32; break true; }; // ✗ break values 不兼容
let d = loop {};                          // 类型 !（永不正常返回）
```

### 5.5 `break` / `continue` / `return`

| 表达式 | 规则 | 结果 |
| --- | --- | --- |
| `break` | `loopStack` 非空；有值时可归属的循环必须 `acceptsValue = true`；值按 `loop.expected` 检查；加入 `breakTypes` | `!` |
| `continue` | `loopStack` 非空 | `!` |
| `return expr` | 载荷按 `currentReturnType` 检查；裸 `return` 视为 `()` | `!` |

细节：

- `break` 只认**最内层**循环（`loopStack.last()`）。
- `while` 里的 `break` 可以不带值，但不能带值。
- `continue` 不携带载荷。
- `return` 可以出现在任何表达式位置，类型是 `!`；`!` 可以赋给任何期望类型。

```rust
fn f() -> i32 {
    return 1;         // ✓
}

fn g() {
    return 1;         // ✗ return value must have type (), found i32
}

fn h() {
    break;            // ✗ `break` outside a loop
}
```

> **实现细节**：裸 `break` 不会向 `breakTypes` 添加 `()`。
> 所以 `loop { break; }` 当前会得到 `!`，而不是 `()`；
> 现有官方语料利用的是 `!` 可以赋给任何类型的性质，没有覆盖这个边界。

### 5.6 `Never` 与 `join`

`!` 的规则：

- 可以出现在任何期望类型的位置（`assignable(Never, T) = true`）；
- 在 `join` 中让位给另一支；
- 不能当普通值保存（它本身没有可用的运行时值）。

```rust
let a: i32 = if true { return 1; } else { 2 };  // i32
let b: u32 = loop { break 1; };                 // u32（expected 传播）
let c = { return 1; };                          // !（没有尾表达式且发散）
let d = { return 1; 2 };                        // i32（尾表达式优先）
```

---

## 6. 一元运算与引用

### 6.1 一元 `-`

```kotlin
UnOp.NEG -> {
    val operand = stripSharedRef(check(node.operand), node.operand.span, "unary '-' operand")
    if (operand != UnknownType) {
        val integer = operand as? IntegerType
            ?: throw SemanticError(..., "unary '-' requires an integer, found $operand")
        if (!integer.signed) {
            throw SemanticError(..., "unary '-' requires a signed integer, found $operand")
        }
    }
    operand
}
```

- 允许一层共享 `&`：`-&a` 合法，结果是 `i32`。
- `&mut T` 直接报错：`unary '-' operand does not accept a mutable reference`。
- 只允许**有符号**整数：`-1u32` 报 `unary '-' requires a signed integer, found u32`。
- 不做范围检查（`-2147483648` 之类属于 UB）。

### 6.2 一元 `!`

- 同样允许一层共享 `&`；
- 只允许整数或 `bool`：
  - `!true` → `bool`；
  - `!1i32` → `i32`（按位取反）；
  - `!&true` → `bool`；
  - `!&mut true` → 报可变引用；
  - 其他类型 → `unary '!' requires an integer or bool, found X`。

### 6.3 解引用 `*`

`UnOp.DEREF` 走 `placeOfDeref(node).type`：

| 操作数类型 | 结果 place |
| --- | --- |
| `&T` | `T`，`mutable = false`，`underShared = true` |
| `&mut T` | `T`，`mutable = true`（除非继承的 `underShared` 已经是 true） |
| `Box<T>` | `T`，`mutable` 继承盒子本身 |
| `UnknownType` | `?`，`mutable = true`（脚手架） |
| 其他 | 报错 `cannot dereference a value of type X` |

```rust
let mut x = 1;
let p = &mut x;
*p = 2;          // ✓
let q = &x;
*q = 2;          // ✗ cannot assign to an immutable place
let r = &mut p;  // &mut &mut i32
**r = 2;         // ✓ 外层可变，内层也可变
let s = &p;      // &&mut i32：外层共享
**s = 3;         // ✗ 外层共享引用让内层也失效
```

### 6.4 `&` / `&mut`

```kotlin
private fun checkReference(node: ReferenceExprNode): Type {
    val place = checkPlace(node.operand)
    val inner = node.operand.type!!

    if (!node.mutable) return RefType(false, inner)   // &e：只要求 e 合法

    if (place != null && !place.mutable) {
        throw SemanticError(node.operand.span, "cannot mutably borrow an immutable place")
    }
    return RefType(true, inner)
}
```

要点：

- `&e` **不要求** `e` 是可变 place；甚至可以借字面量/临时值。
- `&mut e` 要求：
  - `e` 是 place 时必须 `place.mutable = true`；
  - `e` 不是 place（临时值，`place == null`）时当前实现**不额外拒绝**。
- `&&e` / `&mut &mut e` 由 AST 的嵌套 `ReferenceExprNode` 表示；
  外层 `&mut` 对内层引用表达式调用 `checkPlace`，得到 `null`，因此按临时值处理。
- `&` 不会传播 `expected`：
  ```rust
  let x: &u32 = &1;    // ✗ &i32，不是 &u32
  let y: &u32 = &1u32; // ✓
  ```

---

## 7. 二元运算

### 7.1 分组总表

| 组 | 运算符 | 类型要求 | 引用处理 | 结果 |
| --- | --- | --- | --- | --- |
| 算术 | `+ - * / %` | 两侧整数且**同型** | 每侧最多剥一层共享 `&` | 整数类型 |
| 位运算 | `& \| ^` | 两侧整数或 `bool`，**同型** | 每侧最多剥一层共享 `&` | 同类型 |
| 移位 | `<< >>` | 左侧整数；计数是任意整数（不要求同型） | 两侧最多剥一层共享 `&` | 左侧类型 |
| 逻辑 | `&& \|\|` | 两侧 `bool` | **不剥引用** | `bool` |
| 相等 | `== !=` | 两侧同型（可赋值） | 引用比地址 | `bool` |
| 比较 | `< <= > >=` | 左侧先定型，右侧可赋给左侧 | 引用合法；左侧需可比 | `bool` |

### 7.2 算术：`+ - * / %`

```kotlin
val left = stripSharedRef(check(node.left, expected), ...)
val right = stripSharedRef(
    check(node.right, left.takeIf { it is IntegerType }),
    ...,
)
val type = mergeOperandTypes(left, right, node.span)
if (type != UnknownType && type !is IntegerType) {
    throw SemanticError(node.span, "arithmetic requires integers, found $type")
}
```

- 左操作数接收外层 `expected`；右操作数用"剥完引用的左类型（如果它是整数）"当 `expected`。
- 两侧必须**完全同型**：`1u32 + 1i32` → `operands must have the same type: u32 vs i32`。
- `bool` 不能参与算术：`true + false` → `arithmetic requires integers, found bool`。
- `&mut` 不能参与：`&mut a + b` → `left operand does not accept a mutable reference`。
- 一层共享引用可以：`&a + &b`、`&a + b`、`a + &b` 都合法，结果回到值类型。

```rust
let a = 1;
let b = 2;
let x = &a + &b;     // ✓ i32
let y = &&a + &b;    // ✗ 左边剥完还是 &i32
let z = -&a * b;     // ✓ i32（-&a 已经是值）
```

### 7.3 位运算：`& | ^`

与算术基本一致，但允许 `bool`：

```rust
true & false        // ✓ bool
1i32 | 2i32         // ✓ i32
1i32 & true         // ✗ operands must have the same type: i32 vs bool
```

注意：`&` 在表达式里既可能是位运算，也可能是引用；AST 已经区分
`BinaryExprNode` 和 `ReferenceExprNode`，这里只看二元节点。

### 7.4 移位：`<< >>`

```kotlin
val left = stripSharedRef(check(node.left, expected), ...)
val count = stripSharedRef(check(node.right), ...)  // 不传 expected
```

- 左侧必须是整数；
- 计数必须是整数，但**不要求和左侧同型**：
  `3i32 << 33u32` 合法；
- 结果类型 = 左侧类型；
- 计数无后缀字面量默认 `i32`（因为没有传 `expected`）。

```rust
let a = 1i32 << 2;       // ✓ i32
let b = 1i32 << 2u32;    // ✓ i32
let c = 1i32 << true;    // ✗ shift count must be an integer, found bool
```

### 7.5 逻辑：`&& ||`

```kotlin
val left = check(node.left, BoolType)
val right = check(node.right, BoolType)
requireAssignable(left, BoolType, ...)
requireAssignable(right, BoolType, ...)
return BoolType
```

- 不剥引用：`&true && true` 报 `left operand of '&&' must have type bool, found &bool`；
- 两侧都检查，短路只影响运行时不改变静态检查；
- 结果恒 `bool`。

### 7.6 相等：`== !=`

```kotlin
val left = check(node.left, expected)
val right = check(node.right, left)
if (!assignable(right, left) && !assignable(left, right)) {
    throw SemanticError(node.span, "equality operands must have the same type: $left vs $right")
}
requireEquatable(left, node.span)
return BoolType
```

- 两侧必须能互相赋值（通常就是同型）；
- 能力检查只看左侧 `supportsPartialEq(left)`：
  - 标量/引用直接可以；
  - `Box<T>` / `Vec<T>` 递归要求元素 `PartialEq`；
  - struct 必须 derive `PartialEq`（`Eq` 需要在阶段 7 显式带 `PartialEq`）；
  - 空的 `Vec<Opaque>` 也一样要求 `Opaque: PartialEq`。
- 引用比较按地址，不要求被指类型有能力：
  `&opaque1 == &opaque2` 合法。

```rust
struct Opaque { x: i32 }
let a = Opaque { x: 1 };
let b = Opaque { x: 1 };
// a == b                       // ✗ Opaque 没有 PartialEq
// &a == &b                     // ✓ 引用比地址
// Box::<Opaque>::new(a) == Box::<Opaque>::new(b)  // ✗ Box 递归要求 Opaque: PartialEq
```

### 7.7 比较：`< <= > >=`

比较是**不对称**的：

```kotlin
val left = check(node.left, expected)
val right = check(node.right, left)
requireAssignable(right, left, node.right.span, "right operand")
requireComparable(left, node.left.span, "left operand")
return BoolType
```

- 左操作数先定型，右操作数向左侧调整；
- `requireComparable` 会剥掉**全部**引用层，底层必须是整数或 `bool`；
- 不允许"值 vs 引用"混用：`a < &a` 报 `right operand must have type i32, found &i32`；
- 引用之间允许，但可变性方向仍受 `assignable` 限制：
  - `&a < &mut b` ✓（右侧 `&mut` 可赋给左侧 `&`）
  - `&mut a < &b` ✗（右侧 `&` 不能赋给左侧 `&mut`）

```rust
let mut a = 1;
let b = 2;
let x = &a < &b;       // ✓
let y = &a < &mut b;   // ✓
let z = &mut a < &b;   // ✗
```

### 7.8 `mergeOperandTypes`

```kotlin
left == UnknownType || right == UnknownType -> UnknownType
left == right                                -> left
else -> throw SemanticError("operands must have the same type: ...")
```

`UnknownType` 会让整条二元表达式也变成 `?`，从而暂时跳过检查；
这是脚手架行为，不是语言规则。

---

## 8. 强转 `as`

```kotlin
val target = ctx.resolveType(node.targetType)
if (target !is IntegerType) {
    throw SemanticError(..., "casts must target an integer type, found $target")
}
val source = check(node.expr)
if (source != UnknownType && source !is IntegerType && source != BoolType) {
    throw SemanticError(..., "only integers and bool can be cast, found $source")
}
return target
```

规则：

- **目标**必须是整数类型（`i32/u32/isize/usize`）；
- **源**可以是整数或 `bool`：
  - `true as i32` ✓
  - `1i32 as u32` ✓
  - `1 as bool` ✗ `casts must target an integer type, found bool`
  - `&x as i32` ✗ `only integers and bool can be cast, found &i32`
- 不做范围检查，保留 32 位模式（官方 `acc-every-integer-cast-preserves-all-32-bits`）；
- `as` 的优先级在 AST 里已经定型（`casts-and-literals` 的 precedence 用例）。

---

## 9. Place 与可变性（重点）

### 9.1 `Place` 三个字段

```kotlin
internal class Place(
    val type: Type,
    val mutable: Boolean,      // 当前这个位置能不能写
    val underShared: Boolean,  // 是否处在共享引用之下（"毒化"标记）
)
```

- `mutable` 是"这条访问路径当前能不能写"，不是类型属性；
- `underShared` 用来处理 `&&mut T`：外层一旦是共享引用，
  里面的 `&mut` 也不能再被当作可变权限使用；
- `checkPlace(expr)` 返回 `null` 表示"这个表达式不是左值"；
  返回 `Place` 表示它是左值，并已把 `expr.type` 写好。

### 9.2 `checkPlace` 分派

| 表达式 | 处理 | 备注 |
| --- | --- | --- |
| `PathExprNode` | `placeOfPath` | 只有单段局部变量是 place |
| `UnaryExprNode(*p)` | `placeOfDeref` | 引用或 `Box` 的解引用 |
| `FieldExprNode` | `placeOfField` | 结构体字段投影 |
| `IndexExprNode` | `placeOfIndex` | 数组 / Vec 下标 |
| 其他 | `check(expr); null` | 字面量、二元、调用、块、强转等 |

### 9.3 路径 place

```kotlin
private fun placeOfPath(node: PathExprNode): Place? {
    val type = check(node)
    val segment = node.segments.singleOrNull() ?: return null
    val symbol = ctx.currentScope.lookupValue(segment.name) as? LocalSymbol ?: return null
    return Place(type, symbol.mutable, underShared = false)
}
```

- 只有**单段局部变量/参数/self** 是 place；
- 常量、函数、多段关联路径不是 place（`check` 之后返回 `null`）；
- 可写性看 `LocalSymbol.mutable`：
  - `let mut x` → true；
  - `let x` / 普通参数 → false；
  - `self`：`&mut self` 的字段投影可写，但 `self` 绑定本身不可重新赋值
    （BodyChecker 设置 `mutable = selfParam.mutable && !selfParam.byReference`）。

### 9.4 `*p` place

```kotlin
private fun placeOfDeref(node: UnaryExprNode): Place {
    val base = checkPlace(node.operand)
    val operandType = node.operand.type!!

    if (operandType == UnknownType) {
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
        is BoxType -> Place(
            type = operandType.element,
            mutable = base?.mutable ?: true,
            underShared = base?.underShared ?: false,
        )
        else -> throw SemanticError(..., "cannot dereference a value of type $operandType")
    }
    node.type = place.type
    return place
}
```

要点：

- `*&T` → 只读 place（`mutable = false`, `underShared = true`）；
- `*&mut T` → 可写（除非外面已经套了共享引用）；
- `*Box<T>` → 拥有权投影：可写性跟盒子本身走，不毒化；
- `*` 只能作用于引用/Box；`*1` 报 `cannot dereference a value of type i32`；
- `UnknownType` 解引用是脚手架，返回 `?` 可写 place。

### 9.5 `containerPlace`：投影前的权限准备

`placeOfField` / `placeOfIndex` 都先：

```kotlin
val base = checkPlace(node.receiver)
val (containerType, container) = containerPlace(base, node.receiver.type!!)
```

`containerPlace` 逐层剥掉接收者外层的引用和 Box：

```text
while (true) {
  RefType:
    inherited = place?.underShared ?: false
    place = Place(inner,
                  mutable = ref.mutable && !inherited,
                  underShared = inherited || !ref.mutable)
    type = inner
  BoxType:
    place = Place(element,
                  mutable = place?.mutable ?: true,
                  underShared = place?.underShared ?: false)
    type = element
  else: return (type, place)
}
```

这里有两条重要规则：

1. **引用层覆盖继承权限**：
   - `&T` → 只读，并且 `underShared = true`（毒化后续）；
   - `&mut T` → 可写（绑定是不是 `mut` 不影响：`let r: &mut T` 也能写）。
2. **`Box` 是拥有权下移**：权限照抄盒子本身，不毒化。
   例如 `values: Box<Vec<T>>`，`values[0]` 等价于对盒子内容取下标。

### 9.6 owned projection vs borrowing projection

投影有两类：

| 投影 | 用于 | `mutable` | `underShared` |
| --- | --- | --- | --- |
| **拥有权投影** `ownedProjection` | struct 字段、数组元素、`*Box` | 继承容器 | 继承容器 |
| **借用投影** `borrowingProjection` | Vec 元素 | 容器可写才可写 | 继承容器**或**容器不可写 |

```kotlin
private fun ownedProjection(type: Type, container: Place?) = Place(
    type = type,
    mutable = container?.mutable ?: true,       // 临时值归我们所有
    underShared = container?.underShared ?: false,
)

private fun borrowingProjection(type: Type, container: Place?) {
    val writable = container?.mutable ?: true
    return Place(
        type = type,
        mutable = writable,
        underShared = (container?.underShared ?: false) || !writable,
    )
}
```

这就是下面两组行为差异的根源：

```rust
fn write(p: &mut i32) { *p = 1; }

fn f(xs: [&mut i32; 1]) { write(xs[0]); }   // ✓ 数组元素是拥有权投影
fn g(xs: Vec<&mut i32>) { write(xs[0]); }   // ✗ Vec 元素是借出来的
```

再记几个组合：

| 场景 | 结果 |
| --- | --- |
| `values: [&mut i32; 1]`，`*values[0] = 2` | ✓ 数组元素拥有权下移 |
| `values: Vec<&mut i32>`，`write(values[0])` | ✗ 只读位置不能搬走 `&mut` |
| `values: &[&mut i32; 1]`，`*values[0] = 2` | ✗ 外层共享引用毒化 |
| `values: Vec<[&mut i32; 1]>`，`*values[0][0] = 2` | ✗ Vec 元素先变成只读 |
| `values: Vec<Box<&mut i32>>`，`**values[0] = 2` | ✗ Box 也救不了只读的 Vec 元素 |
| `values: Vec<Holder<'_>>`，`*values[0].value = 2` | ✗ 字段继承只读的 Vec 元素 |

对应官方目录：`vec-index-mutability` 的 14 个 reject 基本都在考这张表。

### 9.7 字段 place

```kotlin
private fun placeOfField(node: FieldExprNode): Place {
    val base = checkPlace(node.receiver)
    val (containerType, container) = containerPlace(base, node.receiver.type!!)

    val fieldType = when (containerType) {
        UnknownType -> UnknownType
        is StructType -> {
            val owner = ctx.globals.lookupType(containerType.name) as? StructSymbol
                ?: throw ...
            owner.fields[node.name]
                ?: throw SemanticError(node.span, "struct '${containerType.name}' has no field '${node.name}'")
        }
        else -> throw SemanticError(node.span, "type '$containerType' has no fields")
    }

    node.type = fieldType
    if (containerType == UnknownType) return unknownPlace()
    return ownedProjection(fieldType, container)
}
```

- 字段必须在 `StructSymbol.fields` 里；
- 引用 / `Box` 会先被 `containerPlace` 剥掉，所以 `Box<S>.field` 可以访问 `S` 的字段；
- 非结构体（包括 `Vec`）没有字段：
  `type 'Vec<i32>' has no fields`；
- 字段是**拥有权投影**：可写性/毒化状态继承容器；
- 临时值（`make().field`）没有容器 place，默认可写。

### 9.8 下标 place

```kotlin
private fun placeOfIndex(node: IndexExprNode): Place {
    val base = checkPlace(node.receiver)
    val (containerType, container) = containerPlace(base, node.receiver.type!!)

    val indexType = check(node.index, UsizeType)
    if (indexType != UnknownType && indexType != UsizeType) {
        throw SemanticError(node.index.span, "index must be usize, found $indexType")
    }

    val elementType = when (containerType) {
        UnknownType -> UnknownType
        is ArrayType -> containerType.element
        is VecType -> containerType.element
        else -> throw SemanticError(node.span, "type '$containerType' is not indexable")
    }
    node.type = elementType

    return when (containerType) {
        UnknownType -> unknownPlace()
        is ArrayType -> ownedProjection(elementType, container)
        else -> borrowingProjection(elementType, container)  // Vec
    }
}
```

- 下标恒为 `usize`；`i32` 变量下标会报 `index must be usize, found i32`；
- 无后缀字面量下标会被 `expected = usize` 定成 `usize`；
- 只有数组和 `Vec` 可索引（引用 / `Box` 会先被 `containerPlace` 剥掉）；
  容器类型不对报 `type 'i32' is not indexable`，下标类型不对报 `index must be usize, found i32`；
- 数组是拥有权投影；Vec 是借用投影。

```rust
let mut a = [1, 2];
a[0] = 9;              // ✓
let i = 0i32;
// a[i]                // ✗ i32 不是 usize

let mut v = Vec::<i32>::new();
v.push(1);
v[0] = 9;              // ✓

let w = Vec::<i32>::new();
// w[0] = 9;           // ✗ w 不可变
```

### 9.9 `requireMovable`

`requireMovable` 只在两个地方调用：

- `let` 初始化器；
- 调用实参。

它只关心"只读位置里的 `&mut` 被整体搬走"：

```kotlin
fun requireMovable(expr: ExprNode, expected: Type?, type: Type, what: String) {
    if (type !is RefType || !type.mutable) return          // 只检查 &mut
    if (expected is RefType && !expected.mutable) return   // &mut → &：重借用，允许
    if (!isPlaceShaped(expr)) return                       // 普通右值，没有位置问题

    val place = checkPlace(expr) ?: return
    if (place.underShared) {
        throw SemanticError(..., "$what cannot move a mutable reference out of a read-only place")
    }
}
```

注意：

- 它只检查 `underShared`，**不检查绑定是否 `mut`**。
  `let p = &mut x; let q = &mut i32 = p;` 这类"搬走引用"是允许的；
- `place == null` / 非 place 形状 → 直接放行；
- 目标如果是 `&T`（共享引用），视为重借用，直接放行。

```rust
fn write(p: &mut i32) { *p = 1; }

fn f(xs: [&mut i32; 1]) { write(xs[0]); }  // ✓
fn g(xs: Vec<&mut i32>) { write(xs[0]); }  // ✗
fn h(v: Vec<&mut i32>) {
    let s: &i32 = v[0];                     // ✓ 重借用成共享
    // let m: &mut i32 = v[0];              // ✗ 搬走 &mut
}
```

### 9.10 `hasMutableAccess`（可写接收者）

用于 `&mut self` 方法：

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

规则：

- 接收者类型中只要有一层共享引用 → 不可写；
- 有引用层且全部是 `&mut` → 可写（绑定是否 `mut` 无关）；
- 没有引用层 → 看 place.mutable；临时值（place == null）默认可写；
- 它不专门看 `Box`；`Box<T>` 走"没有引用层"分支，权限看盒子绑定。

例子：

```rust
impl S { fn m(&mut self) {} }

let mut s = S { x: 1 };
s.m();               // ✓

let s2 = S { x: 1 };
// s2.m();           // ✗ method 'm' requires mutable access

let mut s3 = S { x: 1 };
let p = &mut s3;
p.m();               // ✓ p 绑定不可变，但类型是 &mut S

let q = &p;
// q.m();            // ✗ 外层 & 让内层 &mut 失效
```

---

## 10. 赋值与复合赋值

### 10.1 简单赋值 `=`

```kotlin
private fun checkAssign(node: AssignExprNode): Type {
    val place = checkPlace(node.target)
        ?: throw SemanticError(node.target.span, "assignment target is not a place")
    if (!place.mutable) {
        throw SemanticError(node.target.span, "cannot assign to an immutable place")
    }

    if (node.op == AssignOp.ASSIGN) {
        val valueType = check(node.value, place.type)
        requireAssignable(valueType, place.type, node.value.span, "assignment")
        return UnitType
    }
    checkCompoundAssignment(node, place.type)
    return UnitType
}
```

顺序：

1. 目标必须是 place；
2. 目标必须可写；
3. `=` 右侧以目标类型为 `expected`，然后检查可赋值性；
4. 赋值表达式的值恒为 `()`。

```rust
let mut x = 1;
x = 2;          // ✓
// x = true;    // ✗ assignment must have type i32, found bool

let y = 1;
// y = 2;       // ✗ cannot assign to an immutable place
// 1 = 2;       // ✗ assignment target is not a place
// (x + 1) = 2; // ✗ 算术结果不是 place
```

### 10.2 复合赋值

支持十种：`+= -= *= /= %= &= |= ^= <<= >>=`。

流程：

1. 目标 place 必须可写；
2. 右侧 `check(value, targetType if int/bool)`，然后 `stripSharedRef`；
3. 按运算符分组检查目标类型和右侧类型。

| 组 | 目标类型 | 右侧要求 |
| --- | --- | --- |
| `+= -= *= /= %=` | 整数 | 与目标**同型** |
| `&= \|= ^=` | 整数或 `bool` | 与目标**同型** |
| `<<= >>=` | 整数 | 任意整数（不要求同型） |

```rust
let mut x = 1u32;
x += 1;          // ✓ 1 按 expected 定成 u32
// x += 1i32;    // ✗ compound assignment operands must have the same type: u32 vs i32

let mut b = true;
b &= false;      // ✓ bool 支持位运算复合赋值

let mut s = 1i32;
s <<= 33u32;     // ✓ 计数不要求同型
```

注意：**不自动解引用**。

```rust
let mut x = 1;
let mut p = &mut x;
// p += 1;       // ✗ arithmetic compound assignment requires an integer destination, found &mut i32
*p += 1;         // ✓
```

---

## 11. 调用与方法

### 11.1 `checkCallExpr` 总流程

```kotlin
private fun checkCallExpr(node: CallExprNode): Type {
    val callee = node.callee
    if (callee !is PathExprNode) {
        check(callee)
        throw SemanticError(callee.span, "expression is not callable")
    }
    if (callee.segments.isEmpty()) {
        throw SemanticError(callee.span, "empty path")
    }

    val signature = resolveCallTarget(callee)
    if (signature == null) {          // 尚未支持的目标：保持 ?
        for (arg in node.args) check(arg)
        return UnknownType
    }
    checkArguments(node.args, signature, callee.span, describePath(callee))
    return signature.returnType
}
```

- callee 必须是路径；`obj.method()()` 这类“调用表达式再调用”会报 `expression is not callable`（先检查子表达式自身的错误）；
- 函数在这门语言里不是一等值，所以调用位置**不**把 callee 当普通值检查。

### 11.2 `resolveCallTarget`：单段

| 符号 | 结果 |
| --- | --- |
| `FunctionSymbol` | 使用 `signature`；没有签名 → `function 'f' has no signature` |
| `BuiltinFunctionSymbol` | 直接用内建签名 |
| `LocalSymbol` / `ConstantSymbol` | `'x' is not callable` |
| 找不到 | `unresolved value 'f'` |
| 带泛型实参 | `functions and values have no type parameters` |

```rust
fn f(x: i32) -> i32 { x }
fn main() {
    f(1);          // ✓
    // f();        // ✗ expects 1 argument(s), found 0
    // f(true);    // ✗ argument 1 of f must have type i32, found bool
    // f(1, 2);    // ✗ expects 1 argument(s), found 2
}
```

内建函数（`Builtins.kt`）：

| 函数 | 签名 |
| --- | --- |
| `get_i32` | `() -> i32` |
| `print_i32` | `(i32) -> ()` |
| `println_i32` | `(i32) -> ()` |

所以 `let x: i32 = println_i32(1);` 会报类型不匹配（`builtin-io` 的
`rej-unit-result-cannot-be-used-as-i32`）。

### 11.3 `resolveCallTarget`：多段

多段路径只支持：

1. 用户 struct 的关联函数/方法（`S::f(...)`；`Self::f(...)` 由 `resolvePathOwner` 解析到当前 struct）；
2. `Box` / `Vec` 的内建关联项（`Box::<T>::new`、`Vec::<T>::new` 等）。

```text
S::f(...)
  ├─ associatedValues[f] 是 FunctionSymbol → 用它的签名
  ├─ 不存在但 derive 生成 clone → 用派生 clone 签名
  └─ 不存在且不是 clone → no associated item 'f' in 'S'

Box/Vec::method(...)
  ├─ 从第一段的泛型实参得到元素类型（缺省 → ?）
  ├─ 查 Builtins.builtinMethodSignature
  ├─ 找不到 → null（调用检查参数后返回 ?）
  └─ 找到 → withExplicitSelf(...)
```

内建表（`Builtins.kt`）：

| 容器 | 关联项 | 签名 |
| --- | --- | --- |
| `Vec<T>` | `new` | `() -> Vec<T>` |
| `Vec<T>` | `len` | `(&self) -> usize` |
| `Vec<T>` | `is_empty` | `(&self) -> bool` |
| `Vec<T>` | `push` | `(&mut self, T) -> ()` |
| `Vec<T>` | `remove` | `(&mut self, usize) -> T` |
| `Vec<T>` | `clone` | `(&self) -> Vec<T>`，要求 `T: Clone` |
| `Box<T>` | `new` | `(T) -> Box<T>` |
| `Box<T>` | `clone` | `(&self) -> Box<T>`，要求 `T: Clone` |

### 11.4 UFCS：`Type::method(receiver, args)`

```kotlin
private fun withExplicitSelf(signature: FunctionSignature, selfType: Type): FunctionSignature {
    val selfParam = signature.selfParam ?: return signature
    val explicit = if (selfParam.byReference) {
        RefType(selfParam.mutable, selfType)
    } else {
        selfType
    }
    return FunctionSignature(
        paramTypes = listOf(explicit) + signature.paramTypes,
        returnType = signature.returnType,
        selfParam = null,
    )
}
```

- `S::f(&s, ...)` / `S::f(&mut s, ...)`：引用由调用者自己写；
- **不做 autoref/autoderef**：
  `S::f(s)` 对 `fn f(&self)` 会报参数类型不匹配
  （`rej-explicit-associated-call-does-not-autoref-an-owned-argument`）；
- `new` 这种 `selfParam == null` 的关联函数不会插入额外参数。

```rust
impl S { fn f(&self) {} }
// S::f(s);      // ✗ argument 1 需要 &S
S::f(&s);        // ✓
```

### 11.5 实参检查

`checkArguments`：

1. 先比个数：`$what expects N argument(s), found M`；
2. 再逐个：
   - `check(arg, expected)`；
   - `requireAssignable(actual, expected, ...)`；
   - `requireMovable(arg, expected, actual, ...)`。

`requireMovable` 的细节见 §9.9；它专门拦：

```rust
fn write(p: &mut i32) { *p = 1; }
fn update(values: Vec<&mut i32>) {
    write(values[0]);   // ✗ cannot move a mutable reference out of a read-only place
}
```

### 11.6 派生 `clone`

```kotlin
private fun derivedMethodSignature(owner: StructSymbol, name: String): FunctionSignature? {
    if (name != "clone") return null
    val derives = owner.node.derives
    if (derives.none { it == Derive.CLONE || it == Derive.COPY }) return null
    return FunctionSignature(
        paramTypes = emptyList(),
        returnType = StructType(owner.name, owner.node),
        selfParam = SelfParam(byReference = true, mutable = false),
    )
}
```

- 只有 `clone` 是自动生成的；
- `derive(Clone)` 或 `derive(Copy)` 都提供（`Copy` 在阶段 7 已被要求显式带 `Clone`）；
- 返回 `Self`，接收者是 `&self`；
- 查找顺序：显式 `associatedValues` 优先，派生 `clone` 只作为兜底。
  因此手写 `fn clone(&self) -> i32` 会覆盖派生版本。

```rust
#[derive(Clone)]
struct S { x: i32 }
let s = S { x: 1 };
let t = s.clone();      // ✓ S
let u = S::clone(&s);   // ✓ S（UFCS，需显式 &）
```

### 11.7 `checkMethodCall` 总流程

```kotlin
private fun checkMethodCall(node: MethodCallExprNode): Type {
    if (node.name.genericArgs.isNotEmpty()) {
        throw SemanticError(node.span, "method segments have no type parameters")
    }

    val place = checkPlace(node.receiver)
    val receiverType = node.receiver.type!!

    val signature = resolveMethod(receiverType, node.name.name, node.span)
        ?: run {
            for (arg in node.args) check(arg)
            return UnknownType
        }

    val selfParam = signature.selfParam
        ?: throw SemanticError(node.span, "'${node.name.name}' is not a method")

    if (selfParam.byReference && selfParam.mutable &&
        !hasMutableAccess(receiverType, place)) {
        throw SemanticError(node.receiver.span, "method '${node.name.name}' requires mutable access")
    }

    checkArguments(node.args, signature, node.span, "method '${node.name.name}'")
    return signature.returnType
}
```

注意：

- 先 `checkPlace(receiver)`，既定型也拿到 place；
- 方法段不能带泛型实参：`v.len::<i32>()` 直接报错；
- 如果 `resolveMethod` 返回 `null`（脚手架），只检查实参并返回 `?`；
- 找到的签名如果 `selfParam == null`，说明是关联函数，不能用点号调用：
  `s.f()` 对 `fn f()` 报 `'f' is not a method`；
- `&mut self` 还要 `hasMutableAccess` 通过。

### 11.8 `resolveMethod` 的查找算法

按接收者类型逐层走：

```text
while (true) {
  UnknownType        → null
  RefType            → 记录共享引用（clone 兜底用），解引用，继续
  StructType         → 查 associatedValues
                       找到方法 → 返回
                       没找到   → break（走 clone 兜底）
  VecType / BoxType  → 查内建方法表
                       找到 → 检查 selfParam / clone 能力 → 返回
                       Box 没找到 → 解到元素，继续
                       Vec 没找到 → break
  ArrayType          → 直接方法只有 len；否则 break
  其他               → break
}

clone 兜底：
  标量 / bool / 数组 → 检查 Clone 能力后返回自身类型
  共享引用           → 返回该引用类型（不要求被指类型 Clone）
  否则               → type 'X' has no method 'clone'
```

几个重要结果：

- `Box<T>` 可以继续解到 `T`，所以 `b.method()` 可以调用 `T` 的方法；
- `Vec<T>` **不会**解到 `T`，所以 `v.push(...)` 是 Vec 自己的方法；
- 数组的直接方法只有 `len()`；`clone` 由兜底提供（要求元素 `Clone`）；
- 关联函数（如 `new`）不能用点号调用：`v.new()` 报 `'new' is not a method`；
- `&mut self` 方法的可写性由 `hasMutableAccess` 判断（§9.10）；
- `Vec<T>`/`Box<T>` 的 `clone` 要求元素 `Clone`（空容器也一样）。

```rust
struct Opaque { x: i32 }

let v = Vec::<Opaque>::new();
// let w = v.clone();    // ✗ clone requires Clone, but 'Vec<Opaque>' does not implement it

let o = Opaque { x: 1 };
// let p = o.missing();  // ✗ type 'Opaque' has no method 'missing'
```

---

## 12. 结构体字面量

```kotlin
private fun checkStructLitExpr(node: StructLitNode): Type {
    val name = node.path.lastOrNull()
        ?: throw SemanticError(node.span, "empty struct path")

    val symbol: StructSymbol = if (name == "Self") {
        ctx.currentSelfType ?: throw SemanticError(node.span, "`Self` is not allowed here")
    } else {
        val looked = ctx.currentScope.lookupType(name)
        if (looked !is StructSymbol) {
            throw SemanticError(node.span, "unresolved struct type '$name'")
        }
        looked
    }

    val seen = HashSet<String>()
    for (field in node.fields) {
        val declared = symbol.fields[field.name]
            ?: throw SemanticError(field.value.span, "struct '${symbol.name}' has no field '${field.name}'")
        if (!seen.add(field.name)) {
            throw SemanticError(field.value.span, "duplicate field '${field.name}' in '${symbol.name}'")
        }
        val actual = check(field.value, declared)
        requireAssignable(actual, declared, field.value.span, "field '${field.name}'")
    }

    val missing = symbol.fields.keys - seen
    if (missing.isNotEmpty()) {
        throw SemanticError(node.span, "missing field(s) ${missing.joinToString { "'$it'" }} in '${symbol.name}'")
    }
    return StructType(symbol.name, symbol.node)
}
```

规则：

1. 名字取 `node.path` 最后一段；`Self` 用当前 `currentSelfType`；
2. 字段必须存在；
3. 同名字段不能重复；
4. 字段值以声明类型为 `expected`，然后检查可赋值性；
5. 缺字段直接报错（本语言没有 `..Default`）；
6. 返回名义 `StructType`。

结构体只能用 `S { ... }` 构造；`S(...)` 会走调用解析（值名字空间里没有 `S`），
对应官方用例 `rej-named-field-structs-are-not-callable-constructors`。

```rust
struct A { x: i32 }
struct B { x: i32 }

let a: A = B { x: 1 };      // ✗ A vs B：形状相同但名义不同
let b = A { x: 1, z: 2 };   // ✗ A has no field 'z'
let c = A { x: 1, x: 2 };   // ✗ duplicate field 'x'
let d = A { x: true };      // ✗ field 'x' must have type i32, found bool
let e = A { };              // ✗ missing field 'x'
```

在 impl 内：

```rust
impl S {
    fn new() -> Self {
        Self { x: 1 }       // ✓ Self 由 currentSelfType 解析
    }
}
```

---

## 13. 数组字面量

### 13.1 列表形式 `[a, b, c]`

```text
每个元素 check(element, expectedArray?.element)
第一个元素定初值，后续元素 join
长度 = 元素个数
有固定目标 [T; N] 时，长度必须相等
```

```rust
let a = [1, 2, 3];              // [i32; 3]
let b: [u32; 3] = [1, 2, 3];    // ✓ expected 把三个字面量都定成 u32
let c = [1i32, 2u32];           // ✗ array elements: incompatible types i32 vs u32
let d: [i32; 2] = [1, 2, 3];    // ✗ array literal has 3 element(s), expected 2
```

引用元素会做 LUB：

```rust
let mut a = 1;
let b = 2;
let refs = [&mut a, &b];        // [&i32; 2]：&mut 与 & 的 join 是 &
```

### 13.2 重复形式 `[e; n]`

```kotlin
val length = ctx.constEval.requireUsize(repeat.length)
val hint = expectedArray?.takeIf { it.length == length }?.element
val elementType = check(repeat.element, hint)

if (length > 1 && !isCopy(elementType)) {
    throw SemanticError(..., "repeating an array more than once requires a Copy element type, found $elementType")
}
return ArrayType(elementType, length)
```

- `n` 走常量求值，必须是 `usize`；
- `n > 1` 要求元素 `Copy`：
  - `[Box::<i32>::new(1); 2]` ✗
  - `[Box::<i32>::new(1); 1]` ✓（长度 1 不要求 Copy）
  - `[&mut x; 2]` ✗
  - `[&x; 2]` ✓（`&T` 是 Copy）
- 只有目标数组长度与 `n` 相等时，才把目标元素类型作为 `expected` 传给元素；
- 长度不一致由外层 `assignable` 报（可能不是专门的 length mismatch 消息）。

### 13.3 空数组的现状

空列表 `[]` 没有元素可以定型，当前返回 `ArrayType(UnknownType, 0)`，即 `[?; 0]`。
因此：

```rust
let a = [];                 // 类型 [?; 0]
let b: [i32; 0] = [];       // ✗ [i32; 0] vs [?; 0]：数组要求元素完全相等
```

现有官方语义语料没有覆盖这个边界。写文档/调试时不要把 `[?; 0]` 当成合法源码类型。

---

## 14. 使用期能力检查

三处使用期能力：

| 使用 | 调用 | 规则 |
| --- | --- | --- |
| `a == b` / `a != b` | `requireEquatable(left)` | `supportsPartialEq(left)` |
| `.clone()` / `Type::clone(...)` | `requireCloneable(...)` | `supportsClone(...)` |
| `[e; n]`，`n > 1` | `isCopy(elementType)` | `isCopy(...)` |

递归规则（`Capabilities.kt`）：

| 类型 | PartialEq | Clone | Copy |
| --- | --- | --- | --- |
| 整数 / `bool` / `()` | ✓ | ✓ | ✓ |
| `&T` | ✓（引用比地址） | ✓（复制引用） | ✓ |
| `&mut T` | ✓ | ✗ | ✗ |
| `[T; N]` | 看 `T` | 看 `T` | 看 `T` |
| `Box<T>` / `Vec<T>` | 看 `T` | 看 `T` | ✗ |
| `struct S` | 必须有 `derive(PartialEq)` | `Clone` 或 `Copy` | `Copy` |
| `!` | ✗ | ✗ | ✗ |
| `?` | ✓ | ✓ | ✓ |

重点：

- 空的容器也按**静态元素类型**检查：`Vec::<Opaque>::clone(&v)` 即使 `v` 为空也会拒绝；
- `Box<Opaque> == Box<Opaque>` 要求 `Opaque: PartialEq`；
- `&Opaque` 可以 clone / 比较，不要求 `Opaque` 有能力；
- `&mut Opaque` 不能 clone，也不能按位复制；
- 声明期（阶段 7）的 derive 能力检查比使用期更严格，
  两套规则不要混用（详见 `semantic-check-tutorial.md` §7）。

---

## 15. `UnknownType` 脚手架

`UnknownType`（打印为 `?`）不是源码类型，而是"这里暂时不检查"的占位。

出现位置：

| 来源 | 例子 |
| --- | --- |
| 函数/内建函数作值 | `let x = f;` |
| 多段路径中非常量关联项作值 | `let x = S::f;` / `let x = S::missing;` |
| 未实现的内建关联项 | `resolveCallTarget` 返回 null |
| 未找到的方法（脚手架路径） | `resolveMethod` 返回 null |
| 解引用未知类型 | `*?` |
| 空数组字面量 | `[]` → `[?; 0]` |
| 缺省 `Box`/`Vec` 类型实参 | `Box::new(1)` |
| `check` 的 `else` 分支 | 新增 ExprNode 未接分派时 |

传播规则：

- `assignable(?, T)` 和 `assignable(T, ?)` 都为 true；
- `join(?, T)` 得到 `T`；
- `mergeOperandTypes(?, T)` 得到 `?`；
- `supportsPartialEq(?)` / `supportsClone(?)` / `isCopy(?)` 都为 true。

这解释了为什么下面这些"看起来有问题"的程序当前会通过：

```rust
fn f() -> i32 { 1 }

fn main() {
    let x = f;          // x : ?（函数作值是 UB，不要求报错）
    let y = x + 1;      // ?（算术遇到 ? 直接放行）
    let b = Box::new(1); // Box<?>
    let s = S::missing;  // ?（缺失关联项作值）
}
```

它们属于规范里的 UB 区域；**不要**把 `UnknownType` 的宽松当成正常类型规则。
真正调用 `S::missing()` 时，`resolveCallTarget` 会报 `no associated item 'missing' in 'S'`。

---

## 16. 错误信息速查

### 16.1 字面量与路径

- `invalid integer suffix 'x'` — 整数后缀不是四种之一。
- `self is only allowed inside a method` — 在无 `self` 参数的函数里写 `self`。
- `self takes no type arguments` — `self::<T>`。
- `unresolved value 'x'` — 值名字空间找不到。
- `functions and values have no type parameters` — 对普通值/函数写泛型实参。
- `unresolved path 'X'` — 多段路径第一段不是类型。
- `Self is not allowed here` — 顶层函数/无 owner 上下文使用 `Self`。
- `Self takes no type arguments` — `Self::<T>`。
- `'Box' takes at most one type argument here` — 表达式位置 `Box::<A, B>`。
- `primitive type 'i32' takes no type arguments` — `i32<bool>`。
- `struct 'S' has no type parameters` — `S<i32>`。
- `associated items have no type parameters` — `S::<T>::f`。

### 16.2 控制流

- `if condition must have type bool, found X`
- `if without else must have type (), found X`
- `if branches: incompatible types X vs Y`
- `break outside a loop`
- `while does not accept a break value`
- `break value must have type X, found Y`
- `break values: incompatible types X vs Y`
- `continue outside a loop`
- `while condition must have type bool, found X`
- `while body must have type (), found X`
- `loop body must have type (), found X`
- `return value must have type X, found Y`

### 16.3 一元、二元、强转

- `unary '-' requires an integer, found X`
- `unary '-' requires a signed integer, found X`
- `unary '!' requires an integer or bool, found X`
- `unary '-' operand does not accept a mutable reference`
- `unary '!' operand does not accept a mutable reference`
- `arithmetic requires integers, found X`
- `bitwise operators require integers or bool, found X`
- `shift left operand must be an integer, found X`
- `shift count must be an integer, found X`
- `left operand of '&&' must have type bool, found X`
- `right operand of '&&' must have type bool, found X`
- `left operand of '||' must have type bool, found X`
- `right operand of '||' must have type bool, found X`
- `equality operands must have the same type: X vs Y`
- `equality requires PartialEq, but 'X' does not implement it`
- `right operand must have type X, found Y`
- `left operand cannot be ordered: X`
- `operands must have the same type: X vs Y`
- `casts must target an integer type, found X`
- `only integers and bool can be cast, found X`

### 16.4 赋值与 place

- `assignment target is not a place`
- `cannot assign to an immutable place`
- `assignment must have type X, found Y`
- `arithmetic compound assignment requires an integer destination, found X`
- `bitwise compound assignment requires an integer or bool destination, found X`
- `shift compound assignment requires an integer destination, found X`
- `compound assignment operands must have the same type: X vs Y`
- `cannot dereference a value of type X`
- `cannot mutably borrow an immutable place`
- `struct 'S' has no field 'f'`
- `type 'X' has no fields`
- `index must be usize, found X`
- `type 'X' is not indexable`
- `cannot move a mutable reference out of a read-only place`

### 16.5 调用、方法与聚合

- `expression is not callable`
- `'x' is not callable`
- `function 'f' has no signature`
- `no associated item 'f' in 'S'`
- `primitive type 'i32' has no associated items`
- `expects N argument(s), found M`
- `argument N of f must have type X, found Y`
- `method segments have no type parameters`
- `method 'f' requires mutable access`
- `'f' is not a method`
- `type 'X' has no method 'f'`
- `clone requires Clone, but 'X' does not implement it`
- `empty struct path`
- `unresolved struct type 'S'`
- `duplicate field 'f' in 'S'`
- `field 'f' must have type X, found Y`
- `missing field(s) 'x', 'y' in 'S'`
- `repeating an array more than once requires a Copy element type, found X`
- `array literal has N element(s), expected M`
- `array elements: incompatible types X vs Y`

---

## 17. 官方语料对照

| 规则区域 | 官方目录 |
| --- | --- |
| expected 传播 / 字面量定型 | `expected-types`、`casts-and-literals` |
| 算术 / 位运算 / 比较 | `integer-arithmetic`、`scalar-reference-operators` |
| 逻辑运算 | `boolean-and-short-circuit` |
| 移位 | `shifts` |
| 引用运算 | `scalar-reference-operators`、`reference-coercions`、`reference-lub` |
| place / 可变性 | `references-and-mutability`、`vec-index-mutability` |
| 赋值 / 复合赋值 | `compound-assignment`、`evaluation-order-and-temporaries` |
| if / 块 / Never | `blocks-if-and-never`、`unreachable-checks` |
| loop / break / continue | `loops-and-jumps` |
| 调用 / 内建 I/O | `calls-recursion-and-abi`、`builtin-io` |
| 方法 / self | `methods-and-self` |
| 结构体字面量 / 字段 | `structs-and-fields` |
| 数组 | `arrays` |
| Box / Vec / 嵌套容器 | `box-and-moves`、`vec-operations`、`nested-containers` |
| clone / equality / derive | `copy-clone-and-equality`、`trait-dispatch-and-reference-equality` |
| 常量 / 数组长度 | `constant-errors`、`constants-and-paths` |

调试单个用例：

```bash
make compiler
./scripts/rxc --stage semantic tests/official/semantic/arrays/rej-array-length-mismatch.rx
python3 scripts/check_semantic.py arrays -v
python3 scripts/check_semantic.py vec-index-mutability -v
```

注意：语义阶段的错误只保证 exit 1，诊断措辞不参与官方判定；
本文列出的消息用于定位问题。

---

## 18. 坑与记忆卡

1. **`expected` 不是强制转换。**
   `let x: u32 = 1i32;` 必须拒绝；只有无后缀字面量按 `expected` 定型。
2. **可变性不是类型属性。**
   同一个 `&mut T`，在 `let p = &mut s;` 下能写，在 `let p = &s;` 下不能写；
   要看 place 链，不要只看类型。
3. **比较不对称。**
   左操作数先定型，右操作数向它调整；`&mut a < &b` 错，`&a < &mut b` 对。
4. **`while` 恒 `()`，`loop` 才由 break 定型。**
   不要把 `while` 的 body 尾值当结果。
5. **`while` 条件在循环外。**
   条件里的 `break` / `continue` 不能指向外层循环，也不能指向这个 `while`。
6. **不可达不剪枝。**
   `return 1; bad_name` 仍然检查 `bad_name`；
   `if false { bad }` 仍然检查 `bad`。
7. **分号吞尾值。**
   `fn f() -> i32 { 1; }` 的 body 是 `()`；
   非尾位置的无分号表达式必须是 `()`。
8. **`&mut` 的移动检查只看 `underShared`，不看绑定 `mut`。**
   `let p = &mut x; let q: &mut i32 = p;` 是搬引用，允许；
   `Vec<&mut T>` 的元素在只读位置里，不能搬。
9. **数组是拥有权投影，Vec 是借用投影。**
   这决定了 `[&mut i32; 1]` 与 `Vec<&mut i32>` 的行为差异。
10. **Box 是拥有权下移，不是引用。**
    `*Box<T>` 的权限跟盒子绑定；`&Box<T>` 则会让内容只读。
11. **赋值不自动解引用。**
    `p += 1`（`p: &mut i32`）错；要写 `*p += 1`。
12. **引用没有隐式 coercion。**
    值 `Box<T>` 不会自动变 `&T`/`T`；`Vec<T>` 不会自动变 `&T`/数组；
    `T` 不会自动变 `&T`。例外：`&Box<T>` 可以由 `assignable` 沿 Box 链收敛成 `&T`。
13. **Vec/Box 的 clone/equality 看元素能力。**
    空容器也一样；引用例外（`&T` 可以直接 clone/比较）。
14. **`ExprNode` 不是 sealed。**
    新增节点若忘记加 `check` 分支，会静默落到 `else -> UnknownType`。
15. **`checkPlace` 的副作用是契约。**
    field/index/deref 依赖操作数 `.type` 已经写回；不要绕过 `checkPlace` 直接读 `type!!`。
16. **`UnknownType` 很宽松。**
    函数作值、缺失关联项、空数组、缺省 Box 类型实参都可能变成 `?`；
    这是 UB 区域的脚手架，不要拿它当正常规则。
17. **派生 clone 是兜底。**
    显式 `associatedValues["clone"]` 先查；派生 clone 只在没有显式成员时使用。
18. **`self` 是普通 LocalSymbol，`Self` 是 currentSelfType。**
    两者不要混：`self` 是值，`Self` 是类型/宿主。

### 一句话算法

```text
check(expr, expected):
  按节点分派
  字面量看后缀/expected
  路径查值/类型名字空间
  块/if/while/loop 管控制流和 Never
  一元/二元/强转做类型与引用规则
  赋值和 &mut 先要 Place
  调用查签名，参数逐个 assignable + movable
  聚合检查字段/元素/长度/Copy
  最后 expr.type = 结果
```

---

## 19. 完整走查

```rust
use rx::core::*;

#[derive(Clone, Copy)]
struct Counter { value: i32 }

impl Counter {
    const START: i32 = 1;

    fn new() -> Self {
        Self { value: Self::START }
    }

    fn add(&mut self, x: i32) -> i32 {
        self.value += x;
        self.value
    }

    fn get(&self) -> i32 {
        self.value
    }
}

fn main() {
    let mut c = Counter::new();
    let mut v = Vec::<i32>::new();
    v.push(c.get());
    let n: usize = v.len();
    let a: [i32; 2] = [v[0], n as i32];
    c.add(a[0]);
    println_i32(c.get());
    println_i32(a[1]);
}
```

逐表达式：

| 表达式 | 关键步骤 | 类型 |
| --- | --- | --- |
| `Counter::new()` | 多段路径；`Counter` 是 struct；`new` 是关联函数；无 self 插入；无参数 | `Counter` |
| `let mut c = ...` | 初始化器类型 `Counter`；局部绑定 `c` 可变 | `Counter` |
| `Vec::<i32>::new()` | 第一段 `Vec` 带 1 个类型实参；`new` 是关联函数；返回 `Vec<i32>` | `Vec<i32>` |
| `v.push(c.get())` | 先检查 receiver `v` 的 place（可变）；`push` 的 `&mut self` 通过；实参 `c.get()`：方法 `get` 是 `&self -> i32`，返回 `i32`；`push` 参数 `i32` | `()` |
| `v.len()` | `Vec` 内建 `len`，`&self -> usize` | `usize` |
| `v[0]` | `placeOfIndex`：下标 `0` 按 `usize` 定型；Vec 元素是借用投影；只读读取 | `i32` |
| `n as i32` | 目标是整数；源 `usize` 是整数 | `i32` |
| `[v[0], n as i32]` | expected `[i32; 2]`；两个元素都是 `i32`；长度 2 匹配 | `[i32; 2]` |
| `c.add(a[0])` | receiver `c` 可变；`add` 是 `&mut self, i32 -> i32`；实参 `a[0]` 是数组元素（拥有权投影），类型 `i32` | `i32` |
| `self.value += x`（在 `add` 内） | `self` 类型 `&mut Counter`；`containerPlace` 剥引用得到可写 `Counter`；字段 `value` 是拥有权投影；复合赋值 `i32 += i32` | `()` |
| `Self { value: Self::START }`（在 `new` 内） | `Self` 用 `currentSelfType = Counter`；`Self::START` 是关联常量 `i32`；字段类型匹配 | `Counter` |
| `c.get()` | `get` 的 `&self`；返回 `i32` | `i32` |
| `a[1]` | 数组下标；元素 `i32` | `i32` |
| `println_i32(...)` | 内建函数签名 `(i32) -> ()`；实参 `i32` | `()` |

这个例子把路径、关联常量、方法、`&mut self`、Vec、数组、下标、强转、`expected` 传播
和 place 投影都串了一遍。

---

## 20. 源码地图

| 文件 | 作用 |
| --- | --- |
| `ExprChecker.kt` | 表达式分派、路径、控制流、运算符、调用、聚合 |
| `BodyChecker.kt` | 函数/块作用域、`let`、语句、块类型 |
| `Coercion.kt` | `assignable` / `join` / `requireAssignable` / `stripSharedRef` |
| `Place.kt` | `Place(type, mutable, underShared)` |
| `Capabilities.kt` | `supportsPartialEq` / `supportsClone` / `isCopy` |
| `Builtins.kt` | `Box`/`Vec` 方法和 I/O 函数签名 |
| `Symbols.kt` | `FunctionSymbol.signature`、`StructSymbol.fields/associatedValues`、`LocalSymbol` |
| `SemanticContext.kt` | `resolveType`、`currentSelfType`、`currentReturnType`、`loopStack` |
| `SemanticError.kt` | 统一诊断与 `file:line:col` 渲染 |

相关文档：

- `docs/semantic-check-tutorial.md`：语义检查八阶段总览；
- `docs/expr-checking.md`：阶段 8 的早期施工计划（部分批次状态已过时）；
- `docs/semantic_design.md`：整体设计与测试合同。
