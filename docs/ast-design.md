# Rx 编译器 AST 设计（阶段 2.4.1）

> 状态：**已定稿**（2026-10-02）。
> 已确认的三点：① if/loop/while/block 归 **Expr 族**；② 位置存 **起点 + 范围（span）**；③ `ExprNode` **现在就带 Type 槽**（与手册 §2.4.1 一致）。
> 依据：手册 §2.4；文法 `grammar/Lexer.g4`、`grammar/Parser.g4`；官方用例 `tests/official/**`。

---

## 0. 为什么要有 AST

1. `fn foo() {}` 的 CST：
   `(crate (item (functionDefinition fn (identifier foo) ( ) (blockExpression { }))) <EOF>)`
   —— 一半内容是括号、分号这类标点。
2. `if true {}` 在 CST 里套了十几层 `conditionXxxExpression`（表达优先级留下的"管道"）。
3. 同一个"加法"在文法里有 **4 套语法变体**（普通 / statement / condition / conditionBreak 家族）：写法位置不同、CST 形状不同，但**意思完全一样**。

CST 忠实但"没有观点"。类型检查和代码生成需要一棵**只保留意思**的树——AST。
设计的全部功夫就是回答：**哪些信息必须留，哪些可以丢？**

## 1. 谁在用这棵树（决定它长什么样）

| 使用方 | 何时用 | 对树的要求 |
| --- | --- | --- |
| AstBuilder（建造方） | 2.4.2 | CST 里一切**有语义的信息**都要能装进去（`mut`、`&mut`、`#[derive(Copy)]`……） |
| 语义检查 | 2.5 | 每个节点知道：**位置**（报错）、**名字**（符号表）、**可变性**等标记 |
| 代码生成/优化 | 第三章 | 结构**统一**（运算符别碎成几十种）、控制流边界清楚 |

原则：**不漏信息、不装垃圾、位置齐全、结构统一。**

## 2. 总骨架：四族 + 一个根

```
CrateNode（根，= 整个文件）
 ├─ ItemNode      声明族：use / fn / struct / const / impl（文法顶层就这 5 种）
 ├─ StmtNode      语句族：let、表达式语句
 ├─ ExprNode      表达式族：出值的构造（含 if/loop/while/block，见决策 B）
 └─ TypeRefNode   类型族：源码里"写出来的类型"
```

为什么分族：它们在语言里的**角色**不同——声明"造名字"、语句"做流程"、表达式"出值"、类型"作描述"。后续每个阶段只跟其中一两族打交道（符号表主要看 Item/Let），分族让各阶段的遍历代码更短。骨架沿用手册 §2.4.1 的建议（`ASTNode` → `StmtNode`/`ExprNode`），扩展到语言实际需要的四族。

## 3. 每个节点的公共部分（基类负责）

1. **位置：起点 + 范围（span）**〔已确认〕
   - 每条记录 = 起点（行、列）+ 终点（行、列），合起来就是一个范围；
   - 来源：ANTLR 的 CST 节点自带 `ctx.start` / `ctx.stop`（起止 token），建 AST 时顺手带上；
   - 约定与现有诊断一致：**行从 1 数、列从 0 数、打印时列 +1**；
   - 为什么用范围而不是单点：语义报错、编辑器定位、调试打印都更从容，代价只是多存一个位置；
   - 文件路径不用每个节点存（driver 知道当前文件）；将来需要字符偏移可再加。
2. **accept(visitor) 入口**
   - 为什么：2.5 语义检查、代码生成、调试打印都要"走一遍整棵树"。统一入口后，每个阶段只写自己关心的处理方法，不用各写一个巨型"这是哪种节点?"分派；
   - 手册 §2.4.1 的示例正是这个做法。

## 4. 关键决策

### 决策 A：运算符用"统一节点 + 运算符枚举"，不为每个运算符建类
- Rx 运算符盘点（来自文法）：二元 `|| && == != < > <= >= | ^ & << >> + - * / %`（约 20 个）；赋值 `= += -= *= /= %= &= |= ^= <<= >>=`；一元 `- ! * & &mut`；`as` 强制转换。
- 每种一个类：光二元就 20 多个类，类型检查/代码生成写 20 多个分支；
- 统一成 `BinaryExpr(运算符, 左, 右)`：认运算符只写一次；Kotlin 枚举 + 穷尽检查能防漏；
- 附带好处：4 套语法变体到 AST 全部变成同一个 `BinaryExpr(加)`——**变体被压扁**。这是 AST 相对 CST 的核心价值。

### 决策 B：if / loop / while / block 归 Expr 族〔已确认〕
与手册示例（IfStmt 归 Stmt）不同，按**语言事实**来，证据是官方 codegen 用例原文：

> `let v = if x < 0 { return 90; } else if x == 0 { 7 } else { { x + 1 } };`
> `let y = if true { 10 } else { 20 } - 1;`

- if 出现在 `let` 初始值位置；块会**出值**（`7`、`{ x + 1 }` 是"尾表达式"）；if 甚至直接当二元运算的操作数；
- 若建成语句，这些写法建模不出来，将来要重构；
- 落地：`BlockExpr`（语句列表 + 可选尾表达式）、`IfExpr`（条件 + 然后块 + 否则"块/另一层 if"或空）、`LoopExpr`、`WhileExpr`；在语句位置时用薄壳 `ExprStmt` 包一层；
- 语义点（不进节点结构，交给 2.5）：if 无 else → 单元值；`while` 固定单元值；`loop` 的值来自 `break` 载荷；`return/break/continue` 是 never 类型（官方用例名："tails and return as never"）。

### 决策 C："语义小旗子"要留成字段
`let mut x`、`&mut x`、`&self / &mut self`、`#[derive(Copy, Clone, PartialEq, Eq)]`……
CST 里只是 token；AST 不显式存，2.5 就无法做检查（官方真实反例：`rej-immutable-struct-field-assignment.rx` 给不可变结构体字段赋值）。
→ AST 不是"变短的 CST"，而是**带少量精选语义标记**的树。

### 决策 D："源码写的类型"与"编译器理解的类型"分开
- `TypeRefNode`：用户写出的形式（`i32`、`&mut S`、`[i32; 3]`、`()`）——属于语法树；
- `Type`：编译器内部类型（整数、布尔、单元、never、结构体、引用、数组……）——属于语义阶段；
- 为什么分：`(i32)` 与 `i32` 写法不同、含义相同，语义阶段把 TypeRef"翻译"成 Type 时统一；代码生成只认 Type；
- 手册 §2.4.1 里"`ExprNode` 放 Type 成员"正是第二套；第一套手册没提，但文法里有，必须建。

### 决策 E：抽象 = 敢丢东西（丢有标准）
- **丢**：括号 `(x)`、分号、空语句、语法变体、注释空白；
- **留**：位置、名字、mut/&mut、derive、运算符种类、`break/return` 载荷、字面量值；
- 标准：**"会不会影响接受/拒绝，或影响生成的代码？"** 会 → 留；不会 → 丢。

## 5. 节点清单

### 根
| 节点 | 装什么 | 为什么 |
| --- | --- | --- |
| CrateNode | 顶层项列表 | 整个文件一棵树；visitor 起点 |

### Item 族（文法 `item`：use / fn / struct / const / impl）
| 节点 | 装什么 | 备注 |
| --- | --- | --- |
| UseNode | use 树（路径 / `*` / `{}` 组 / `as` 别名，递归小结构） | spec 中 use 语义"可丢弃"，但先忠实保存 |
| FnNode | 名字、泛型参数、参数表、返回类型?、where?、函数体(BlockExpr) | 参数首种特殊形式是 self（`self`/`&self`/`&mut self`）→ 即"方法" |
| StructNode | 名字、泛型、**derive 列表**、字段(名+类型)、where? | derive 决定 Copy/Clone/相等性语义 |
| ConstNode | 名字、类型、常量值 | 常量值形式受限（整数/布尔/路径/负号/括号），可复用一小撮 Expr 节点 |
| ImplNode | 泛型、目标类型(TypeRef)、where?、关联项(const/fn) | 方法归属类型 |

### Stmt 族
| 节点 | 装什么 | 为什么 |
| --- | --- | --- |
| LetNode | 名字、**mut?**、类型注解?(TypeRef)、初始值(Expr) | 唯一绑定语句；mut 是"不可变不可写"检查依据 |
| ExprStmtNode | 一个 Expr | 语句位置的表达式（if/loop/block 也经它包装） |
| ~~空语句~~ | — | 纯语法，丢 |

### Expr 族
| 节点 | 装什么 | 对应源码 |
| --- | --- | --- |
| IntLitNode | 数值 | `123` |
| BoolLitNode | 真假 | `true` / `false` |
| PathExprNode | 段列表（可带泛型实参） | `a::b::c`、`Self::x` |
| UnaryExprNode | 运算符(负/逻辑非/解引用)、操作数 | `-x`、`!x`、`*x` |
| ReferenceExprNode | **mut?**、操作数 | `&x`、`&mut x`；`&&x` 拆两层 |
| BinaryExprNode | 运算符(枚举)、左、右 | 4 套变体全归它 |
| AssignExprNode | 运算符(`=`/复合)、左、右 | 右结合 |
| CastExprNode | 表达式、目标类型(TypeRef) | `e as T` |
| CallExprNode | 被调者、实参表 | `f(a, b)`、`S::f(x)` |
| MethodCallExprNode | 接收者、方法名、实参表 | `x.m(a)` |
| FieldExprNode | 接收者、字段名 | `x.f` |
| IndexExprNode | 接收者、下标表达式 | `x[i]` |
| ArrayLitNode | 元素列表 **或** 重复形式(元素; 长度) | `[a, b]`、`[0; 8]` |
| StructLitNode | 类型路径、字段初始化列表 | `S { a: 1 }` |
| BlockExprNode | 语句列表 + 可选**尾表达式** | `{ ...; 尾值 }` |
| IfExprNode | 条件、然后块、否则(块/IfExpr)或空 | 决策 B |
| LoopExprNode | 块 | 值来自 `break` 载荷 |
| WhileExprNode | 条件、块 | 固定单元值 |
| ReturnExprNode | 载荷? | never |
| BreakExprNode | 载荷? | 可带值 |
| ContinueExprNode | 无 | — |

`ExprNode` 带一个 **Type 槽**〔已确认现在就放〕：2.4 阶段永远为空，2.5 往里填。

### TypeRef 族
| 节点 | 装什么 | 对应源码 |
| --- | --- | --- |
| PathTypeNode | 段列表（+泛型实参） | `i32`、`S` |
| RefTypeNode | **mut?**、生命周期?、内部类型 | `&T`、`&mut T`；`&&T` 两层 |
| ArrayTypeNode | 元素类型、长度(常量值) | `[T; N]` |
| UnitTypeNode | 无 | `()` |

（`(T)` 加括号写法：拆括号直接丢。）

### Type（语义用，本阶段只占坑）
Int / Bool / Unit / Never / Array(长度) / Struct(名字) / Ref(mut)……先立最小版，2.5 再长胖。

## 6. 已拍板决定记录

| 问题 | 决定 | 理由摘要 |
| --- | --- | --- |
| if/loop/while/block 归属 | **Expr 族** | 官方用例：`let v = if…`、`let y = if… - 1`；块尾值语义 |
| 位置存法 | **起点 + 范围（span）** | 报错/调试更从容；成本多一个 token 位置 |
| ExprNode 类型槽 | **现在就放（空）** | 与手册 §2.4.1 一致；避免后改所有节点 |

## 7. 分期落地建议（写代码时）

1. 第一刀：Crate、Item 五件套（简单形态）、Let/ExprStmt/Block、字面量/路径/二元/一元/调用/字段/索引、TypeRef 三种 + 单元 → 覆盖官方绝大多数用例；
2. 第二刀：If/While/Loop/Return/Break/Continue、StructLit、ArrayLit、Cast、MethodCall、引用；
3. 第三刀：泛型/生命周期/where/use 的完整形态（先存不查）。
每加一批，用官方 parser accept 用例做"能否建树"回归（47 个 crate 用例起步，之后按 `metadata.entry` 扩到 442 个 parser 用例）。

## 8. 明确"不做"的边界

- 不做名称解析、类型推断、借用检查（2.5 的活，AST 只供原料）；
- 不在节点里塞原始 token / 不引用 CST（AST 是独立的树）；
- 不预建空节点——按官方用例驱动，缺了再加。
