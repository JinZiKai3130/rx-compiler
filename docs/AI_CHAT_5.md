# AI_CHAT_5 —— 语义检查阶段的对话记录

- 导出日期：2026-10-20
- 仓库：`/home/kai/ACM_class_project/rx-compiler`
- 内容：按时间顺序导出本对话框中「你」与「Copilot」的消息。
- 说明：为便于阅读，工具调用过程与中间输出未收录；代码与命令均按对话原文保留。

---

## 1. 语义检查计划（2026-10-12）

**你**

接下来，我们将要考虑语义检查（semantic）环节，请你大概说一下，我们需要做什么，按照什么步骤，进行一下计划

**Copilot**

所有事实都核清楚了。先给你结论性的现状，再给完整计划。

### 一、验收合同（先把目标钉死）

- **官方语义语料：45 个目录、236 条用例 = 69 accept + 167 reject**（刚才统计过）。
- 判定方式（`scripts/test.py`）：`config.mk` 里的 `SEMANTIC` 命令**退出码 0 = 接受、1 = 拒绝**，诊断措辞不限；reject 必须"正常拒绝"，不许 crash / 超时（30s）。
- **现状**：我们前端已经完全就绪——刚才实测全部 236 条源码 **236/236 都能 parse + 建树成功**，没有 exit 3 缺口。但语义检查为空，所以现在跑语义阶段 = **69/236**（167 个 reject 全被误收）。这就是基线，目标 236/236。

### 二、要做的事（三块）

**A. 设计（先定，后难改）**

1. **内部类型系统**：`Type.kt` 已有 `i32/bool/()/!` 占位，需扩到：`u32/isize/usize`、结构体（**名义类型**，带声明引用）、引用（含可变性）、数组（元素+长度）、`Box<T>`、`Vec<T>`。挂到 `ExprNode.type`（占位已存在）。
2. **符号表**：Scope 链 + 两个名字空间（类型 / 值）；**全局先收集**（支持前向引用）+ 内建环境（类型名、`print_i32/get_i32`、`Box/Vec`）。手册 §2.5 明确建议这个两遍结构。
3. **检查器**：`SemanticAnalyzer`（实现现有 `AstVisitor`，像 `AstPrinter` 那样）；表达式检查带**期望类型**参数（字面量选型顺序 = 后缀 > 期望 > i32 兜底；if/loop/break/数组元素都靠它）；never 类型；**place（可赋值地点）+ 可变性链**分析。关键决策：**名字解析结果要回填 AST**（如 PathExprNode 加 resolved 字段），否则 codegen 阶段还得重做一遍解析。
4. **错误策略**：诊断格式沿用 `file:line:col: error: [semantic] msg`；起步 fail-fast（抛异常 → exit 1），顶层兜底保证不 crash；错误恢复以后再说（合同不需要）。

**B. 检查功能**：45 个目录分 6 组（见下表），大部分目录实现前先读对应规范章节（`names.html`、`types.html`、`references.html`、`const_eval.html`、`builtin-traits.html`、`expressions/*` 等）。

**C. 工程接线**：CLI 加 `--stage semantic`；写 `scripts/check_semantic.py`（仿 `check_frontend.py`，逐条比对 manifest 的 `compilation_success` 与退出码，输出按目录的进度表）；后期 `config.mk` 里 `SEMANTIC = ./scripts/rxc --stage semantic {source}`（BUILD 可置空，runner 对空 runtime 文件也放行）→ `make test STAGE=semantic` 全绿 = 里程碑。

### 三、步骤计划

| 步骤 | 内容 | 覆盖目录（约） | reject 目标 | 出口标准 |
| --- | --- | --- | --- | --- |
| **WP0 基础设施** | SemType、Scope、Analyzer 空壳、CLI 接线、runner、逐目录 checklist | — | 0/167 | 69 accept 全绿；167 reject 有分类清单；无 crash；commit |
| **WP1 顶层收集+命名空间+入口** | struct/fn/const/impl 收集、重名、前向引用、保护名、main 检查、impl 合法性 | namespace-errors 15、invalid-impls 11、entry 4、protected-names 1 | ~31 | 纯符号表类 reject 全灭 |
| **WP2 函数体作用域+名字解析** | 参数/let、shadowing、初始化器不自见、块局部不外逃、未定义名 | names-and-shadowing 2、lifetimes-and-use 1、unreachable-checks 部分 | ~5 | 名字解析类全灭 |
| **WP3 核心类型规则（最大头）** | 整数/位/比较/位移、bool 运算、字面量边界、cast、期望类型传播、never、block tail/unit、赋值 place、复合赋值、调用参数、builtin-io | integer 4、loops 7、blocks 5、boolean 4、casts 4、expected 4、compound 4、builtin-io 4、calls 3、shifts 2、unreachable 4 | ~45 | 核心类型规则成型 |
| **WP4 引用/可变性/方法** | &/&mut/deref、autoref 方法查找、place 可变性链、引用运算、引用 LUB、aggregate 参数 | vec-index-mutability 14、references 6、methods-and-self 5、scalar-ref 4、coercions 4、evaluation-order 2、aggregate 2、trait-dispatch 2、lub 1 | ~40 | 引用体系成型 |
| **WP5 聚合/容器/常量/derive/布局** | 结构体字段、数组、Box、Vec、嵌套容器、常量求值+循环检测、derive 递归条件、递归布局环检测 | constant-errors 9、copy-clone 9、structs 6、vec-ops 6、arrays 5、box 4、recursive-layout 4、constants 2、nested 2、recursive-traits 1 | ~46 | 剩余 reject 全灭 |
| **WP6 收尾验收** | 236/236 全量 + lexer 53 + parser 442 回归；config.mk 接入；`make test STAGE=semantic` 绿 | — | 167/167 | **里程碑达成** |

执行原则：**每步先保 accept（69 条不许出现误报）再灭 reject**；每个目录动工前先读该目录全部 rej 案例 + 对应规范章节；每步结束全量回归 + commit。

### 四、必须记住的规范边界（容易踩）

- **这些是 UB，测试不会出现，不用实现**：字面量越界、经运算符/借用的期望类型反向传播、`Box::new/Vec::new` 缺类型实参、函数作值、声明受保护名、**不可达代码里的 place-mutability 违规**、跨类型 `==`、LUB 无公共目标、多个匹配方法、use/lifetime 违规。
- **但不可达代码里的名字/类型错误仍必须报**（`rej-unknown-name-after-return` 等）——别做"不可达剪枝跳过检查"。
- use 声明丢弃，但别的错误照查；结构体名义类型；无真泛型（`<i32>` 只是具体实参，struct/fn 带类型参数要拒）。
- reject 必须 exit 1（不能是 3）：目前没有 exit 3 泄漏，新代码保持。

### 五、建议

先做 **WP0**：半天量的骨架（SemType / Scope / CLI / runner），做完就有"按目录亮灯"的进度表，之后每一步的收益都是精确数字。

---

## 2. 写入 semantic_design.md（2026-10-13）

**你**

请你把这个部分写到semantic_design.md

**Copilot**

已写入 `docs/semantic_design.md`（放在 `docs/` 下，与 `docs/ast-design.md` 同级）。

文档内容：

- **§0 目标与现状**——目标 236/236；前端 236/236 建树已验证；当前基线 69/236。
- **§1 验收合同**——45 目录、236 条（69 acc / 167 rej）、exit 0/1 判定、不许 crash、本地与官方两条验收路径。
- **§2 总体设计**——`rx.semantic` 包划分、Type.kt 类型集合、Scope 两遍结构（含名字解析回填 AST 给 codegen 用）、期望类型检查器、错误策略与退出码。
- **§3 工程接线**——CLI `--stage semantic`、`scripts/check_semantic.py`、`config.mk`/`make test STAGE=semantic` 接入方式。
- **§4 分步计划**——WP0 任务清单（可直接开工）+ WP0–WP6 总表（含每步 reject 目标和出口标准）。
- **§5 规范边界**——UB 不用做的清单 + 必须注意的坑（不可达代码、use 丢弃等）。
- **§6 进度表**——45 个目录逐行列出条数/acc/rej/归属 WP/状态勾选框。
- **§7 参考资料**——规范章节、手册 §2.5、runner 判定逻辑等。

需要的话，下一步就可以按 §4.1 开工 WP0。

---

## 3. 实现 WP0（2026-10-13）

**你**

（附件：`tests/official/semantic/aggregate-arguments-and-reference-fields/rej-by-value-array-parameter-checks-its-fixed-length.rx`）

ok现在开始写WP0，并请告诉我写了什么，会如何用在代码中

**Copilot**

WP0 完成，全部验证通过。以下是写了什么、以及它们在代码里怎么用。

### 新增/修改的文件

| 文件 | 内容 | 在代码里怎么用 |
| --- | --- | --- |
| `rx/ast/Type.kt`（重写） | 补全内部类型：`I32/U32/Isize/Usize`（公共接口 `IntegerType(signed, bits)`）、`Bool/Unit/Never`、`StructType`（**名义相等**，只比名字；可选 `decl`）、`RefType`、`ArrayType`、`BoxType`、`VecType`，都带可读 `toString`（`&mut i32`、`[i32; 3]`…） | 语义阶段把 `TypeRefNode`（源码写法）翻译成 `Type` 填到 `ExprNode.type`；WP3 的整数同型规则用 `IntegerType`；诊断和打印直接用 `toString`；codegen 以后直接消费 |
| `rx/semantic/SemanticError.kt` | 语义错误异常，`render()` = `file:line:col: error: [semantic] msg`（列打印 +1，与前端诊断同格式） | 分析器发现错误即 `throw`；`Main` 捕获后打印并 exit 1 |
| `rx/semantic/Symbols.kt` | 符号骨架：`StructSymbol / FunctionSymbol(receiver) / ConstantSymbol / LocalSymbol(mutable, span)` | 名字解析产物，存进 Scope；WP1 起在子类上补字段 |
| `rx/semantic/Scope.kt` | 作用域链：双名字空间（值/类型）；`child()` 进块、`declareXxx` 返回 false=本层重名、`lookupXxx` 沿父链上溯、`lookupXxxHere` 只查本层 | 手册 §2.5.2 结构：全局作用域收集顶层声明，函数体/块逐层 `child()`；重名、未定义名、shadowing 都走它 |
| `rx/semantic/SemanticAnalyzer.kt` | 空壳：持有 `globals = Scope(null)`，入口 `analyze(crate)` | WP1–WP5 的挂载点（KDoc 标了每步接什么）；CLI 每次编译 new 一个 |
| `rx/Main.kt`（改） | 新 `--stage semantic`（要求 `--entry crate`）；与 `--dump-ast` 共用"解析→建树"后调 `analyze`；`SemanticError` → exit 1 | 就是将来 `config.mk` 里 `SEMANTIC` 命令接入的形态 |
| `scripts/check_semantic.py`（新） | 逐条跑 236 条语义语料，比对 `compilation_success` 与退出码；输出按目录进度表（backlog 降序）+ accept/reject 计数；支持 `SUBSTR` 过滤和 `-v` | 每一步的进度度量工具 |
| `docs/semantic_design.md`（改） | §0 状态、§4.1 前五项勾选、基线实测数字 | 计划文档保持更新 |

### 调用链（现在跑一次语义阶段会发生什么）

```text
./scripts/rxc --stage semantic x.rx
  → lex + parse（有错 → 诊断 → exit 1）
  → AstBuilder：CST → CrateNode
  → SemanticAnalyzer().analyze(crate)      ← WP0 这里是空的
       WP1+：globals 收集 struct/fn/const/impl、查重名/main/impl 合法性
       WP2+：函数体进 Scope、查名字
       WP3+：check(expr, expected) 填 ExprNode.type、查类型
       出错 → throw SemanticError → file:line:col: error: [semantic] … → exit 1
  → 全过 → exit 0
```

### 验证结果

- `make compiler` 绿；冒烟：accept 用例 rc=0、`--stage semantic --entry item` rc=2（用法错误）、parse 路径行为不变。
- **基线全量（62.6 秒）：accept 69/69 全绿、reject 0/167**——与预测一致（167 个 reject 目前仍被误收，从 WP1 开始逐个消灭）。
- 前端全量回归无退化：lex 53/53、parse 442/442、accept 建树 365/365。
- 你打开的那条 `rej-by-value-array-parameter-checks-its-fixed-length.rx` 现在 rc=0 属正常基线，它的检查归属 WP4（数组长度实参匹配）。

工作树还没 commit（清单最后一项留给你）。下一步是 WP1：顶层收集 + 命名空间 + main/impl 检查，目标消灭约 33 条 reject。要开始就说一声。

---

## 4. 为什么需要 Type.kt（2026-10-14）

**你**

我没明白为什么要写一个Type.kt，他的作用是什么，如果说他可以作为节点，为什么在建立AST的时候不需要用到？

**Copilot**

好问题——这里容易混淆，核心是一句话：**`Type` 不是节点，它是"编译器理解的意思"；节点是 `TypeRefNode`（源码里写出来的字）**。两者是 AST 设计的"决策 D"（`docs/ast-design.md`）：语法形式和语义含义分开。

### 两套东西的对照

| | `TypeRefNode`（TypeRefs.kt） | `Type`（Type.kt） |
| --- | --- | --- |
| 是什么 | 用户**写出来**的类型写法 | 编译器**理解**的类型含义 |
| 属于树吗 | 是节点（继承 `AstNode`，有 span、有 accept） | 不是节点，是"值对象"（无 span、无 accept） |
| 谁造 | AstBuilder（2.4） | 语义阶段翻译/推断（2.5） |
| 存在哪 | AST 树里的子树 | 挂在 `ExprNode.type`、或符号表里的签名上 |
| 例子 | `i32`、`(i32)`、`S`、`[i32; N]`、`Vec<&'a i32>` | `I32Type`、`StructType("S")`、`ArrayType(I32, 3)` |

注意左边这些**写法**：`(i32)` 和 `i32` 是不同的字、同一个意思；`[i32; N]` 里的 `N` 是个名字，真正含义要等常量求值才知道。`Type` 就是把这些"字"归一化后的结果。

### 为什么建 AST 时用不到它

**1. 建树是纯语法翻译，输入输出都是"用户写的字"。** AstBuilder 的职责是 CST → AST 的结构搬运：看到 `[i32; 3]` 就建 `ArrayTypeNode(element=PathTypeNode("i32"), length=IntLit(3))`。它**没有能力**知道：`i32` 是内建类型还是用户结构体？`S` 指向哪个声明？`N` 等于几？——这些都要等整个 crate 收集完、符号表建好才行。

**2. 名字解析不能在建树时做（前向引用）。** `fn f(x: S) {}` 可以写在 `struct S` 前面，Rust 允许。建树时 `S` 还是悬空的，必须先把全部顶层声明收集起来才能解析。

**3. 最关键：很多 `Type` 在源码里根本没有对应的字。**

```rust
let x = 1 + 2;              // x 的类型 i32 是推断出来的，没写
let y = if c { 1 } else { 2 };  // if 表达式的类型是合并出来的
loop { break 5; }           // loop 的类型来自 break 载荷合并
```

`a + b` 这个表达式节点上，源码里既没有 `i32` 也没有别的类型词。`i32` 是语义阶段**算出来**的，算出来后要有个地方放——这就是 `ExprNode.type: Type?`（手册 §2.4.1 里明确要求 ExprNode 带一个 Type 成员，我们照做的）。建树时它保持 `null`，2.5 填。

### 用你的例子走一遍

```rust
fn f(a: [i32; 3]) {}
fn main() { f([1, 2]); }
```

- **2.4 建树**：`FnParam(a, type = ArrayTypeNode(i32, IntLit(3)))`。只是把"写的字"装进树。
- **2.5 语义**：把 `i32` 查表翻成 `I32Type`，把 `3` 求值成 `3` → 得到 `ArrayType(I32Type, 3)` 存进签名；再看调用 `f([1,2])`，算出实参类型 `ArrayType(I32Type, 2)`，两者**不相等** → 报错。这条就是官方那条 `rej-by-value-array-parameter-checks-its-fixed-length`。比较必须是 `Type` 比 `Type`（长度是数值 3 vs 2），拿两棵 `TypeRefNode` 树去比是没法比的。
- **代码生成**：读出 `ArrayType(I32Type, 3)` 才能算布局（12 字节）、决定怎么传参。

### 为什么单独一个类，而不是复用 TypeRefNode

- **职责不同**：TypeRefNode 回答"源码长什么样"，Type 回答"是什么类型"。混用会导致 `(i32)` 和 `i32` 被当成不同类型这种错误。
- **相等性语义不同**：`Type` 的相等是为语义设计的——`StructType` 按名字相等（名义类型：两个字段相同的结构体仍是不同类型），`RefType` 结构相等。TypeRefNode 的相等是"树相等"，没有语义。
- **有些 Type 不是"翻译"来的，是"推断"来的**（见上面第 3 点），它们压根没有对应的源码写法，想复用节点也没得复用。
- **没有位置**：类型错误要报在**表达式**的位置（比如调用点），而不是"类型"的位置；Type 不携带 span，报错时用所在节点的 span。

### 那为什么文件放在 `rx/ast` 包里？

纯粹是工程便利：`ExprNode`（也在 `rx.ast`）有 `var type: Type?` 字段，把 `Type` 放同包就不用反向依赖 `rx.semantic`。放哪个包不影响它的本质——**它不继承 `AstNode`，没有 span、没有 accept，从不出现在 CST→AST 的建树过程里**，只在"写出来的字"被翻译/推断成"编译器知道的意思"之后出现。

一句话总结：AST 里存的是**用户写的字**（TypeRefNode）；`Type` 是语义阶段对这些字（以及无字的表达式）的**解读结果**，产出后回填到节点上，供类型检查和代码生成使用。建树阶段还没到"解读"这一步，所以用不到它。

---

## 5. 解释 semantic_design.md §2.3（2026-10-14）

**你**

我是一个刚刚学习编译器的初学者，我现在正在写semantic部分，请你解释一下semantic_design的2.3的部分，这一步是在做什么，达到什么效果，需要些什么达到这个效果？

**Copilot**

好问题——§2.3 是语义阶段的地基，看起来"没做什么检查"，但后面所有检查都踩在它上面。用初学者能理解的方式拆开讲。

### 一句话概括

§2.3 要做的是一个 **"名字 → 定义"的查询系统**（符号表 + 作用域）。语义检查本质上就是不停追问："这个 `x` 是谁？"——§2.3 负责让这个问题有答案。

### 1. 这一步在做什么

编译器看到表达式时，眼前只有名字，不知道它们指向什么：

```rust
fn helper(x: i32) -> i32 { x + N }   // x、N 是谁？
fn main() { let y = helper(1); }      // helper 是谁？y 存哪？
let p: Point = Point { x: 1 };        // Point 是类型还是值？有哪些字段？
let n: i32 = 3;                       // i32 又是谁定义的？源码里根本没写
```

答案分别是：`x` 是参数（局部）、`N` 是常量、`helper` 是函数、`y` 是新局部、`Point` 是结构体（类型名字空间）也是构造方式、`i32` 是**内建**类型。

符号表就是一本"名字→定义"的字典；作用域把这本字典**分层**——查名字时从最近的一层开始，逐层往外找（像在房间里喊人，先看房间里，再看门外）。手册 §2.5.2 的 `Scope`（带 `members` + `parentScope`）就是干这个的，我们的 `Scope.kt` / `Symbols.kt` 是它的骨架版。

### 2. 达到什么效果

**能发现这类错误**（都是官方语料里真实存在的意图）：

| 效果 | 例子（官方用例） |
| --- | --- |
| 未定义名字报错 | `namespace-errors/rej-unresolved-value-name` |
| 重复定义报错 | `rej-duplicate-top-level-function`、`rej-duplicate-parameter` |
| 作用域边界正确 | `rej-a-block-local-name-cannot-escape`（块里的变量出不了块） |
| 声明顺序正确 | `rej-a-binding-is-invisible-in-its-own-initializer`（`let x = x;` 里的 `x` 不能用自己） |
| 名字空间不串 | `acc-named-field-struct-and-function-may-share-a-spelling`（`struct S` 和 `fn S` 可同名）；但 `rej-function-and-constant-share-the-value-namespace`（函数和常量不行） |

**解锁两个关键能力**：

1. **前向引用**：Rust 允许先调用后定义，比如递归 `fn fact(n) { fact(n - 1) }`——函数体里引用自己，声明却"还没走完"。只有把全程序的名字先收齐，引用才可能成立。
2. **给下一层（WP3 类型检查 + codegen）提供底座**：名字都绑定到唯一定义之后，才能问"这个表达式的类型是什么"、"这个变量分配在哪"。

### 3. 达到这个效果需要什么

**a) 数据结构**（WP0 骨架已备）

- `Scope`：一个节点 = 两张表（值 / 类型）+ parent 指针；`child()` 进块、`lookup*` 往上找、`declare*` 往本层写。
- `Symbol`：定义本身——结构体、函数（含方法）、常量、局部变量/参数，各自携带声明节点（以后还要带类型、签名）。

为什么分两个名字空间？因为**用名字的语法位置决定该查哪张表**：类型位置（`let x: T`）查类型表；表达式位置（`f(1)`、`N`、`x`）查值表。`struct S` 和 `fn S` 分别进两张表，所以不冲突；而函数和常量都出现在表达式位置，必须共享值表——这就是那条 rej 用例的由来。

**b) 两遍结构**（手册 §2.5.3 的"符号收集 → 检查"）

- **第 1 遍（收集）**：只扫顶层声明（struct/fn/const/impl），把名字注册进全局作用域，顺手查重名、保护名。**不进入函数体**。
- **第 2 遍（检查）**：这时全程序名字已就绪，再逐个进函数体，建局部作用域、解析引用。

走查一个小例子：

```rust
const N: i32 = 3;
fn helper(x: i32) -> i32 { x + N }
fn main() { let y = helper(1); { let y = 2; } }
```

```text
第 1 遍：globals = {值: N, helper, main；类型: {}}      ← 一次性收齐
第 2 遍：
  进 helper → 子作用域 {x}，查 N → 沿 parent 找到 ✓
  进 main   → 子作用域 {y}，查 helper → 找到 ✓
     进内层块 → 再子作用域 {y(新的)}，这里 y 遮蔽(shadow)外层 y
     出块 → 内层 y 消失，外层的 y 又能看到        ← 作用域进出的效果
```

**c) 作用域边界与"声明时机"的细节**

- 每个块/函数体 → `child()` 建新层，退出还原。
- `let` 的**初始化器要先查、再声明**：先算等号右边（此时 `x` 还不可见），成功后才把 `x` 放进当前层。
- 同名规则分种类：`let` 允许 shadowing（同名新变量，官方 acc 用例 `acc-shadowing-creates-new-storage...`），但重复参数、重复顶层函数是错误——这正是 `Scope.declareValue` 返回 `Boolean` 的用途（本层重名 = false）。

**d) 两个收尾件**

- **内建环境预注册**：`i32/u32/bool/...`、`Box/Vec` 进类型表，`print_i32/get_i32` 等进值表——用户的代码从没声明过它们，编译器得先摆好。
- **解析结果回填 AST**（如 `PathExprNode.resolved`）：语义阶段不只是"查错"，还把"这个名字指向哪个定义"写回节点。因为 codegen 阶段还要问同样的问题（这是哪个函数？哪个局部变量？），不存下来就得重做一遍解析。

### 4. 在我们项目里的落点

- **WP1（下一步）**：写第 1 遍——内建预注册 + 遍历 `crate.items` 收集 + 重名/保护名检查。对应消费的 backlog：`namespace-errors`(15) + `invalid-impls-and-generics`(11) + `entry`(4) 等约 33 条。
- **WP2**：写第 2 遍——函数体作用域进出、初始化器时机、shadowing、未定义名；对应 `names-and-shadowing`、`unresolved-*` 等。

验证方式就是你已有的 `scripts/check_semantic.py`：做完一个目录，看进度表里那行的 backlog 是否清零、accept 是否仍然全绿（防误报）。

一句话总结：§2.3 是在回答"每个名字指到哪个定义"，它是**查错的开始**（未定义/重复/越界），也是**后续一切分析的前提**（没有它，类型检查无从谈起，代码生成也不知道给谁分配空间）。

---

## 6. Scope 的实现方式（2026-10-14）

**你**

ok，那我们现在开始考虑实现的问题，我们需要弄一个scope，来判断一个变量是否属于这个scope，那我应该用什么方式实现这个？

**Copilot**

你这句话里其实混着两个不同的问题，先分开——这是实现前最容易绕晕的点：

1. **可见性**："这个名字现在能看到吗、指向谁？"——用在**查名字**时，要沿作用域链**往上找**。
2. **本层归属**："当前这一层里已经声明过它吗？"——用在**声明**时（重名检查），只看本层。

### 用什么方式：作用域链

`Scope` = 一张"名字→符号"的哈希表 + 一个 `parent` 指针，一层套一层：

```text
globals         { helper, main, N }   parent = null
   └ helper 作用域 { x }              parent = globals
        └ 内层块 { tmp }              parent = helper 作用域
```

一层的生命周期就四个动作：

- **进入**：`current = current.child()`
- **离开**：恢复到保存的旧引用——整层直接丢掉，**不用逐个删名字**，"块内变量出不去"自动成立
- **查找**：先查本层表，miss 就沿 parent 上溯；一路找不到返回 null → 报"未定义名字"
- **声明**：写进本层表

为什么选它（对比其他实现思路）："单张大表 + 深度标记"退出作用域时要按深度清理、遮蔽记账更绕；"不可变 Map"是函数式风格、没必要。链式结构的优点就是**和语言的块结构同构**：进出只是指针走走，遮蔽（shadowing）天然正确——内层同名盖住外层，退出后自动恢复。

### 现有骨架 + 一个必须改的点

`src/main/kotlin/rx/semantic/Scope.kt` 已经有 `child()` / `lookupValue()` / `declareValue()` 这些。但你要先想清楚 **shadowing 的规则**，这是第一个大坑：

```rust
let x = 1;
let x = 2;                    // 合法！新变量遮蔽旧的
fn f(a: i32) { let a = 1; }   // 参数也能被 let 遮蔽
```

而顶层重名是**错误**：

```rust
fn foo() {}
fn foo() {}    // 错（rej-duplicate-top-level-function）
```

所以"本层已有同名"不能一律报错。建议把 API 按**意图**分成两个：

```kotlin
/** 严格声明：本层已有同名 → false 且不覆盖。用于顶层声明/参数等"重名是错"的场合。 */
fun declareValue(symbol: Symbol): Boolean

/** 局部绑定：允许遮蔽，直接覆盖本层同名条目。用于 let。 */
fun bindValue(symbol: Symbol)
```

现在的 `declareValue` 重名时"返回 false 且不写入"，就是第一种；补一个 `bindValue` 就齐了。（类型名字空间暂时用不到遮蔽，`declareType` 一个就够。）

### 在遍历里怎么接线

`AstVisitor` 的方法没有参数，所以在 `SemanticAnalyzer` 里持一个"当前作用域"，进出时保存/恢复：

```kotlin
private var scope: Scope = globals

private fun withChildScope(body: () -> Unit) {
    val saved = scope
    scope = scope.child()
    body()
    scope = saved
}
```

用的地方只有两类：**函数体**和**每个 `{}` 块**；收集遍直接往 `globals` 里放。（我们 fail-fast：出错直接抛异常终止整个分析，所以不需要 try/finally 兜底恢复。）

### 初学者最常踩的四个坑

1. **声明时机**：`let` 必须"先查初始化器、后写入绑定"。`let x = x;` 右边的 `x` 要解析到**外层/之前的** x（官方用例 `rej-a-binding-is-invisible-in-its-own-initializer`）。先写后查就会解析到自己。
2. **别混用两种查找**：重名检查用 `lookupValueHere`（只看本层）或 declare 的返回值；"这个名字是谁"用 `lookupValue`（会上溯）。拿后者做重名判断会把外层同名误报成冲突。
3. **进入/离开要配对**：每个块、每个函数体进出成对，否则后续所有查找都错位。忘记了就恢复成 saved 引用。
4. **内建只注册在全局**：`i32`、`print_i32` 这些"凭空可用"的名字，在分析开始前一次性放进 `globals`，不要放每层；顺带还能支撑 `protected-names` 那组检查。

### 落到当前进度

- **WP1（下一步）**：写收集遍——把 `crate.items` 里的 `FnNode / StructNode / ConstNode` 变成 `FunctionSymbol / StructSymbol / ConstantSymbol` 放进 `globals`，用 `declareValue` 的返回值报顶层重名。完成后 `python3 scripts/check_semantic.py namespace-errors -v` 能看到这组 backlog 开始清零。
- **WP2**：函数体遍历 + `withChildScope` + 上面两条时机/遮蔽规则。

如果你想自己动手练，推荐顺序：①给 `Scope` 补 `bindValue`；②写 `collect(items)` 并报重名；③用 `rej-duplicate-top-level-function` 和 `rej-function-and-constant-share-the-value-namespace` 两个用例验证。

---

## 7. 导出对话（2026-10-20）

**你**

请你将这个对话框里的内容导出为markdown格式，命名为AI_CHAT_5.md

**Copilot**

已将本次对话整理并导出为 `docs/AI_CHAT_5.md`（即本文件）。
