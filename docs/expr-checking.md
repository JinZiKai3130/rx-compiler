# 阶段 8：表达式类型检查清单

> 状态（2026-10-29）：阶段 0–7 已完成；批次 A、B 已完成。
> 基线：`accept 69/69`、`reject 96/167`。**剩余 71 个 reject。**
> 本文件是阶段 8 的施工图：机制、逐节点规则、实施顺序、验收数字、坑清单。

---

## 0. 现状

`BodyChecker` 目前只做名字解析（作用域进出、未定义名、`self` 可用性、路径泛型实参）。
`checkExpr(expr)` 只递归子节点、恒返回 `Unit`，**尚未做任何类型判断**。

顶层函数与 impl 方法体走同一条路径：`checkFunctionBody(node, owner)`，`owner != null` 时
`currentHasSelfParam` 与 `currentSelfType` 会被设置，因此方法体里的 `self` / `Self::x` / `Self { }`
可用（2026-10-27 补上；此前 impl 方法体完全没有被遍历）。

### 表达式根：全 AST 审计

`ExprNode` 在整棵 AST 里只有 **5 个根**，其余都是表达式内部的嵌套子节点：

| 根 | 出现位置 | 谁负责 | 机制 |
| --- | --- | --- | --- |
| `FnNode.body` | 顶层函数 / impl 方法 | `BodyChecker` | `checkBlock` → `check(expr)` |
| `LetNode.initializer` | 块内 | `BodyChecker` | `check(expr, declaredType)` |
| `ExprStmtNode.expr` | 块内 | `BodyChecker` | `check(expr, null)` |
| `ConstNode.value` | 顶层 const / impl 关联常量 | `ConstEval`（阶段 4） | 受限常量求值，**不用** `check(expr)` |
| `ArrayTypeNode.length` | 任意类型位置（字段 / 签名 / let / 强转） | `resolveType` → `requireUsize` | 受限常量求值 |

两条容易误解的结论：

- **struct 本身没有表达式**。`StructNode` 只有字段*类型*，字段类型在阶段 5 全部 `resolveType`；
  其中的 `[T; N]` 长度由 `requireUsize` 检查（实测 `struct S { a: [i32; 0i32] }` 会被拒）。
- **const 不走表达式检查器，也不该走**。文法上 `constValue` 只允许字面量 / 路径 / 负号
  （`const A: i32 = 1 + 2;` 在 **parse** 阶段就被拒），所以常量的正确机制是
  "受限求值 + 与声明类型比对"，而不是通用的 `check(expr, expected)`。

阶段 8 要把 `checkExpr` 升级成

```kotlin
fun check(expr: ExprNode, expected: Type? = null): Type
```

并在返回前把结果写回 `expr.type`（`AstNode.kt:34` 预留的槽位，供 IR 阶段直接读，避免重复推导）。

---

## 1. 三个前置机制

先立这三根柱子，再逐个节点填规则。否则写到第 20 个用例时会互相打架。

### 1.1 双向检查签名

```kotlin
fun check(expr: ExprNode, expected: Type? = null): Type
```

`expected` 只有三个来源：

| 来源 | 例子 |
| --- | --- |
| 显式类型标注 | `let x: &i32 = ...`、`fn f() -> i32`、结构体字段声明类型 |
| 上下文已确定的目标 | `if` 的一支先定型 → 另一支以它为 expected；数组首元素 → 其余元素；`break` 值 → loop 的 expected；`return` 值 → 函数返回类型 |
| 形参/字段类型 | 调用实参、结构体字面量字段值 |

**`expected` 只影响两类表达式**：整数字面量的选型、引用层级的弱化（`&mut T → &T`）。
它**不是**隐式转换：`let x: u32 = 1i32;` 必须拒绝。

### 1.2 Place（左值）+ 可变性链

可变性**不是类型属性**，而是"这条访问路径上的每一层是否都允许写"。
这是 `vec-index-mutability`(14)、`references-and-mutability`(6)、`compound-assignment`(4)、`aggregate-arguments`(2) 等约 30 个 reject 的总开关。

```kotlin
class Place(val type: Type, val mutable: Boolean)
fun checkPlace(expr: ExprNode): Place?      // null = 不是左值
```

| 形式 | 可变性怎么算 |
| --- | --- |
| `x`（局部/参数/`self`） | 看绑定是否 `mut`；`self` 看 `&mut self` |
| `*p` | `checkPlace(p).mutable && p.type is RefType && p.type.mutable`；`&T` → 不可写；非引用 → 报错 |
| `e.f` | `checkPlace(e).mutable`（字段不额外授权） |
| `e[i]` | `checkPlace(e).mutable`（数组 / Vec 同规则） |

三条推论：

- `let p = &mut x;` 里可以 `*p = 1`，但**不能** `p = &mut y`（重绑定要绑定本身 `mut`）→ `rej-rebinding-an-immutable-reference-binding-is-invalid`。
- `&&mut T` 不能 `**q = 2`：外层 `&` 一出现，整条链降级为只读 → `rej-a-shared-outer-layer-prevents-mutable-access`。
- `&mut expr` 要求 `checkPlace(expr)` 非空且 `mutable` → `rej-a-named-immutable-local-cannot-be-mutably-borrowed`。

### 1.3 类型调整（coercion）

```kotlin
fun assignable(from: Type, to: Type): Boolean
fun join(a: Type, b: Type): Type?      // LUB，用于 if 两支 / 数组元素 / break 载荷
```

| from → to | 允许 |
| --- | --- |
| 无后缀整数字面量 → 任意整数类型 | 是（按 expected 定型） |
| 带后缀 / 已有类型的整数 → 另一种整数 | 否 |
| `&mut T` → `&T` | 是（单向弱化） |
| `&T` → `&mut T` | 否 → `rej-shared-reference-cannot-become-mutable` |
| `&mut T` → `&mut U`（`T` 可弱化为 `U`） | 否（内层 mut 不改写）→ `rej-inner-reference-mutability-is-not-rewritten` |
| `Box<T>` → `&T` / `T`；`Vec<T>` → `[T]` | 否 → `reference-coercions` 4 个 |
| 同名结构体 | 是；同形状不同名 → 否（名义类型） |

---

## 2. 逐节点规则

| # | 节点 | 判定要点 | 主要 reject |
| --- | --- | --- | --- |
| 1 | `IntLitNode` | 有 suffix 锁定该类型 + 范围检查；无 suffix 用 expected，否则默认 `i32` | `casts-and-literals` |
| 2 | `BoolLitNode` | 恒 `bool`；不参与算术、不能当真值 | `rej-no-bool-to-integer-implicit-conversion` |
| 3 | `UnitLitNode` | 恒 `()`；与任何值不兼容 | `blocks-if-and-never` |
| 4 | `PathExprNode` | 1 段：局部 → 参数 → `self` → 全局函数/常量；多段：`S::x` / `Self::x`；关联值名字空间跨 impl 共享；函数作值无意义 | `rej-an-associated-function-is-not-a-dot-call-method`、`rej-associated-values-share-a-namespace-across-impl-blocks` |
| 5 | `BlockExprNode` | 尾表达式为值；**非尾语句/表达式语句必须 `()`** | `rej-a-non-final-block-statement-without-semicolon-must-be-unit`、`rej-a-semicolon-removes-the-function-tail-value` |
| 6 | `IfExprNode` | 条件必须 `bool`；两支 `join`；**无 else 则 then 必须 `()`**；任一支 `!` 时取另一支 | `rej-no-integer-truthiness`、`rej-if-without-else-requires-a-unit-consequent` |
| 7 | `LoopExprNode` | 类型由 `break` 载荷决定：无 break → `!`；多个 break → `join`；**body 必须 `()`** | `rej-a-fixed-loop-target-rejects-a-mismatched-break` |
| 8 | `WhileExprNode` | 条件 `bool`；**结果恒 `()`**；body 必须 `()`；不接受 `break value` | `rej-while-remains-unit-even-when-its-condition-is-true`、`rej-a-while-cannot-accept-a-break-value` |
| 9 | `Break` / `Continue` / `Return` | 上下文合法性；`break` 载荷参与 loop 定型；`return` 载荷对函数返回类型；裸 `return` 是 `()` | `rej-bare-return-is-unit`、`rej-break-outside-a-loop`、`rej-continue-outside-a-loop` |
| 10 | `UnaryExprNode` | `-` 仅有符号整数；`!` 整数或 bool；`*` 只能对引用、结果去掉一层；三者都允许**一层共享 `&`** 包裹 | `rej-unary-minus-is-signed-only`、`rej-scalar-values-cannot-be-dereferenced`、`rej-mutable-reference-unary-arithmetic-unsupported` |
| 11 | `ReferenceExprNode` | 需要 place；`&mut` 需要可变 place；结果 `&T` / `&mut T` | `references-and-mutability` 6 个 |
| 12 | `BinaryExprNode` | 10 组运算符各不相同，见 §3 | 4 个目录 15 个 |
| 13 | `AssignExprNode` | 目标必须 place 且 mutable；`=` 不改绑定类型；复合赋值左右**同型无转换**；引用目标不自动解引用；值类型恒 `()` | `compound-assignment` 4、`evaluation-order` 2 |
| 14 | `CallExprNode` | 被调者必须是函数/关联函数（局部变量不可调用）；实参个数 + 逐个 `assignable`；`Box::new` 恰 1 个、`Vec::new` 恰 0 个；内建 `get_i32`/`print_i32` | `calls-recursion-and-abi` 3、`builtin-io` 4、`box-and-moves` 4、`vec-operations` 6 |
| 15 | `MethodCallExprNode` | 按 receiver 类型查 `associatedValues`；`&self` / `&mut self` 需要对应可变性（autoref）；内建方法 `len/push/remove/clone`；方法段泛型实参个数 | `methods-and-self` 4、`rej-push-requires-mutable-access` |
| 16 | `FieldExprNode` | 字段必须存在于结构体；`Vec` 无 `.len` 字段 | `rej-unknown-accessed-field` |
| 17 | `IndexExprNode` | 只能索引 `[T; N]` 和 `Vec<T>`；**下标定型为 `usize`**（`i32` 变量不行）；结果元素类型；可变性继承 | `rej-an-existing-i32-cannot-index-an-array`、`rej-integers-are-not-indexable` |
| 18 | `CastExprNode` | **只允许整数 ↔ 整数**；`i32 as bool`、`&i32 as i32` 均错 | `casts-and-literals` 4 |
| 19 | `ArrayLitNode` | 元素逐个 `join`；`[x; N]` 的 `N` 是 `usize`，`N > 1` 时 `x` 必须 **Copy**；固定目标长度必须相等 | `rej-repeating-more-than-once-requires-copy`、`rej-array-length-mismatch`、`rej-by-value-array-parameter-checks-its-fixed-length` |
| 20 | `StructLitNode` | 字段存在、不重复、不缺、类型匹配；路径解析到 struct | `structs-and-fields` 6 |

### 2.1 `BinaryExprNode` 展开

| 组 | 规则 | 备注 |
| --- | --- | --- |
| `+ - * / % & \| ^ << >>` | 两侧整数且**同型**；每侧最多**一层共享 `&`**；结果回到**值**类型 | `&&a + &b` 错（两层）；`-&mut a` 错；`&a * b` 对（一值一引用可以）→ `rej-arithmetic-supports-only-one-shared-reference-layer` |
| `&& \|\|` | 两侧必须 `bool`；无引用变体 | `rej-lazy-operators-require-bool`、`rej-lazy-operators-have-no-reference-variants` |
| `== !=` | 两侧同型；标量/引用直接比；容器与结构体要求 `PartialEq` 能力 | 复用阶段 7 的 `supports` |
| `< <= > >=` | 两侧同型；允许引用，但**左操作数先定型，右操作数向它调整**（不对称） | `&mut a < &b` 错、`&a < &mut b` 对 → `rej-ordering-cannot-coerce-a-mutable-left-operand-to-match-a-shared-right-operand` |

> 注意：`&&a < &&b` 合法，但 `&&a + &b` 不合法。"一层共享引用"的限制只写在算术/位运算上；比较走引用 LUB 通道。不要合并成同一个 helper。

### 2.2 位置与类型无关

| 用例 | 规则 |
| --- | --- |
| `rej-ordinary-assignment-requires-a-place` | 赋值左侧必须是 place |
| `rej-an-arithmetic-result-is-not-a-place` | `a + b = c` 错 |
| `rej-a-literal-is-not-an-assignment-place` | `1 = 2` 错 |
| `rej-reference-destinations-are-not-implicitly-dereferenced` | `p += 1`（`p: &mut i32`）错，要写 `*p += 1` |
| `rej-assignment-cannot-change-a-binding-s-type` | `let x = 1; x = true;` 错 |

---

## 3. 横切规则

1. **整数没有隐式转换**，只有"无后缀字面量按 expected 定型"。`1u32 + 1i32` 永远错。
2. **unit 是强制的**：非尾语句、`while` / `loop` 的 body、`if` 无 else 的 then 分支，全部必须 `()`。
3. **`!` 的吸收律**：`if c { return 1 } else { 2 }` 类型是 `i32`；但 `!` 不能当值赋给别人。
4. **不可达代码照样检查**：`return 1; undefined_name;` 要报错；`if true {} else { bad }` 也要报错。**不剪枝**。
5. **`while` 的条件在循环外**：检查 condition 时循环尚未入栈，condition 里的 `break` 不能 targeting 这个 while。
6. **短路的只是求值语义**，静态检查两支都做。
7. **无分号的 block-like 语句 = 块尾**：文法上 `statement : expressionWithBlock SEMI?` 与尾表达式重叠，ANTLR 会把
   `{ { x + 1 } }` 的内层块归成 statement。`AstBuilder.visitBlockExpression` 因此在“没有显式尾表达式”时，
   把最后一条无分号的 `ExprStmtNode` 提升为 `tail`（块的值就是它）。带分号的 `1;` 不会提升，
   所以“分号吞掉尾值”的语义不变。
8. **块类型的优先级**：有尾表达式 → 块类型就是尾表达式类型；没有尾表达式 → `()`；
   **没有尾且块内已经发散** → `!`。前面的语句发散**不会**吞掉尾表达式的类型——
   不可达的尾同样参与类型检查（`return 7; true` 在 `-> i32` 函数里要被拒绝），
   但 `{ return 90; }` 这种“没有尾”的发散块仍然是 `!`，否则 `if/else` 的 `join` 会失败。

---

## 4. 实施批次

每批结束都要跑本批目录 + 全量，确认 **accept 不掉**（69/69 是底线）。

| 批次 | 内容 | 目标目录 | reject 数 | 累计 reject |
| --- | --- | --- | --- | --- |
| **A ✅** | `check(expr, expected)` 骨架 + 字面量/路径/块/if/unit/never + 三种跳转 | `blocks-if-and-never` 5、`unreachable-checks` 3、`loops-and-jumps` 7 | 15 | 73（实测 **80**，顺带带上 boolean/integer/casts/lifetimes 共 5 个） |
| **B ✅** | 全部二元/一元运算符 + 一层共享引用规则 + 强转 | `integer-arithmetic` 4、`boolean-and-short-circuit` 4、`shifts` 2、`scalar-reference-operators` 4 | 14 | 96（顺带带上 casts 4/4） |
| **C** | `Place` + `AssignExpr` + `ReferenceExpr` + 解引用 | `references-and-mutability` 6、`compound-assignment` 4、`evaluation-order` 2 | 12 | 99 |
| **D** | `Call` + 内建函数/构造函数 + 方法查找与 autoref | `calls-recursion-and-abi` 3、`builtin-io` 4、`box-and-moves` 4、`vec-operations` 6、`nested-containers` 2、`lifetimes-and-use` 1 | 20 | 119 |
| **E** | 聚合：结构体字面量/字段/下标/数组 + 可变性穿透 | `structs-and-fields` 6、`arrays` 5、`vec-index-mutability` 14、`aggregate-arguments-and-reference-fields` 2 | 27 | 146 |
| **F** | `Cast` + 引用 coercion/LUB + derive 运行时语义 | `casts-and-literals` 4、`methods-and-self` 4、`reference-coercions` 4、`reference-lub` 1、`trait-dispatch-and-reference-equality` 2、`copy-clone-and-equality` 2 | 17 | 163 |
| — | `expected-types` 4 条随 A/B/C 顺带完成 | `expected-types` | 4 | **167** |

### 剩余 reject 分布（当前实测）

```
arrays 5            blocks-if-and-never 5      boolean-and-short-circuit 4
box-and-moves 4     builtin-io 4               calls-recursion-and-abi 3
casts-and-literals 4  compound-assignment 4    copy-clone-and-equality 2
evaluation-order-and-temporaries 2             expected-types 4
integer-arithmetic 4  loops-and-jumps 7        methods-and-self 4
nested-containers 2   reference-coercions 4    reference-lub 1
references-and-mutability 6                    scalar-reference-operators 4
shifts 2              structs-and-fields 6      trait-dispatch-and-reference-equality 2
unreachable-checks 3  vec-index-mutability 14  vec-operations 6
aggregate-arguments-and-reference-fields 2     lifetimes-and-use 1
```

### 验收命令

```bash
make compiler
python3 scripts/check_semantic.py blocks-if-and-never loops-and-jumps -v   # 本批
python3 scripts/check_semantic.py                                          # 全量
```

期望：全量 `accept 69/69`，`reject 167/167`。

---

## 5. 坑清单

1. **把 `expected` 当强制转换。** `let x: u32 = 1i32;` 必须错。
2. **把可变性写成类型属性。** `&mut T` 的字段在 `let p = &mut s;` 下可写，在 `let p = &s;` 下不可写——同一个类型，两种可变性。
3. **比较运算符写成对称 LUB。** 必须先定型左边，右边向左边调整。
4. **`while` 当成 `!` 或循环结果。** `while` 恒 `()`；`loop` 才是"由 break 决定类型"。
5. **derive 生成的方法不参与点号查找。** `acc-generated-clones-bypass-dot-lookup`：`Inner` 同时有 `#[derive(Clone)]` 和手写 `fn clone(&self) -> i32`，`Inner::clone(&x)` 取到手写版本（返回 `i32`），而 `Outer::clone(&a)` 走 derive 版本（返回 `Outer`）。最后一批再处理。
6. **`Vec`/`Box` 的 `Clone`/`PartialEq` 要元素能力。** `Vec::<Opaque>::clone(&v)` 即使运行时为空也要求 `Opaque: Clone`；`Box<Opaque> == Box<Opaque>` 要求 `Opaque: PartialEq`。直接复用阶段 7 的 `supports`。
7. **`ExprNode` 不是 sealed。** `check` 的 `when` 漏一个节点不会编译报错，只会静默走 `else`。想被编译器强制，可把 `AstNode` 家族改 `sealed`。

---

## 6. 结构建议

**第一刀已完成（2026-10-27）**：`BodyChecker.kt` 只留语句 / 块 / 作用域，表达式全部搬到 `ExprChecker.kt`（`BodyChecker` 持有 `private val exprs`，`ExprChecker` 回调 `body.checkBlock`）。行为不变，验收仍是 `accept 69/69`、`reject 58/167`。

```
src/main/kotlin/rx/semantic/
  BodyChecker.kt     // ✅ 语句、块、作用域、let
  ExprChecker.kt     // ✅ 20 个表达式的分派 + 名字解析
  Place.kt           // ⏳ 批次 C 建：checkPlace + 可变性传播
  Coercion.kt        // ⏳ 批次 B 建：assignable / join / 剥一层共享引用
```

`BodyChecker` 将持有 `currentReturnType`、`loopStack: List<LoopContext>`（`LoopContext { kind, breakTypes }`），`ExprChecker` 通过构造参数取用。

**不要**为了"用上 `rx.ast.AstVisitor`"把类型检查改成 visitor：
`visit(n)` 返回 `Unit`，装不下表达式类型；而且每个阶段需要的子树、顺序、重入次数都不同。
visitor 留给同构、只读的消费者（`AstPrinter` 已是先例，将来 IR 生成器可以再实现一个）。
