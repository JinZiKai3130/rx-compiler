# Rx 编译器语义检查设计（2.5）

> 状态：WP0 已完成（2026-10-13）：类型系统、`rx.semantic` 骨架、CLI `--stage semantic`、本地 runner 就绪。基线 **accept 69/69、reject 0/167**（runner 全量约 63s）。下一步：WP1。

---

## 0. 目标与现状

**目标**：对 AST 做语义检查，使编译器在官方 semantic 语料上达到 **236/236**（accept 全过、reject 全拒）。

**现状**：

- 词法/语法/AST 已全部完成（三刀已 commit，HEAD 与 origin/main 同步）。
- 实测：`tests/official/semantic/` 全部 **236 条源码 236/236 能 parse 且建树成功**（无 exit 3 缺口）。
- 语义检查为空 → 当前"前端通过即接受"，即基线 **69/236**（167 个 reject 全被误收）。
- AST 已预留接口：`ExprNode.type`（内部类型槽位）、`rx/ast/Type.kt`（现有 `i32/bool/()/!` 占位）。
  `rx.ast.AstVisitor` 目前只有 `AstPrinter` 一个实现；语义检查**不使用** visitor，每个阶段手写递归（原因见 §2.4）。

```
source → [lex] → [parse] → [build AST] → [semantic] → 接受(exit 0) / 拒绝(exit 1)
```

---

## 1. 验收合同

| 项 | 内容 |
| --- | --- |
| 语料 | `tests/official/semantic/`，45 个目录，**236 条 = 69 accept + 167 reject** |
| 判定 | `config.mk` 的 `SEMANTIC` 命令退出码：**0 = 接受，1 = 拒绝**（诊断措辞不限） |
| 红线 | reject 必须"正常拒绝"：不许 crash、不许超时（执行器 30s 超时；退出码非 0/1 一律判失败） |
| 本地验收 | `scripts/check_semantic.py`（新增，逐条比对 manifest 的 `compilation_success` 与退出码） |
| 官方验收 | `make test STAGE=semantic`（需改 `config.mk`，见 §3.3） |

---

## 2. 总体设计

### 2.1 模块划分

```
src/main/kotlin/rx/semantic/
  SemanticAnalyzer.kt   总入口：按固定顺序调用各阶段（不含检查逻辑）
  SemanticContext.kt    共享上下文：全局作用域、当前 Self/作用域/self 参数、resolveType
  Builtins.kt           内建环境：原生类型、Box/Vec、I/O 函数
  CrateIndex.kt         阶段 1：声明收集（struct/fn/const → 全局作用域）
  ImplRegistry.kt       阶段 2：结构体身份 + impl 关联项登记
  EntryChecker.kt       阶段 3：main 契约
  ConstEval.kt          阶段 4：常量求值（const 初值 / 数组长度 / repeat + 环检测）
  StructResolver.kt     阶段 5：结构体字段类型
  FunctionResolver.kt   阶段 6：函数与方法签名
  GlobalConstraints.kt  阶段 7：递归布局有限大小 + derive 合法性/能力
  BodyChecker.kt        阶段 8：函数体——作用域、语句、块
  ExprChecker.kt        阶段 8：表达式检查（类型检查后续接入）
  Scope.kt / Symbols.kt / SemanticError.kt   基础设施
```

内部类型沿用并扩展 `rx/ast/Type.kt`（`AstNode.type` 的既有槽位指向它）。

### 2.2 内部类型系统（扩展 Type.kt）

现有占位：`IntType / BoolType / UnitType / NeverType`。目标集合：

- 标量：`i32 / u32 / isize / usize / bool / () / !`
- `StructType(name, decl)` —— **名义类型**（同形状不同名 = 不同型）
- `RefType(mutable, inner)`
- `ArrayType(element, length)`
- `BoxType(element)` / `VecType(element)`

由 `TypeRefNode` 翻译而来（延续 ast-design.md 决策 D：语法写法 → 编译器语义）。

### 2.3 符号表与作用域

- `Scope` 链（parent），块级进出；**两个名字空间**：类型 / 值（函数与常量共享值空间）。
- 两遍结构（手册 §2.5.3/2.5.4 的推荐做法）：
  1. **全局收集**：struct/fn/const/impl、查重名、保护名、impl 合法性——支持前向引用；
  2. **逐函数体检查**：局部作用域、类型检查。
- 内建环境预注册：类型名（`i32` 等 + `Box/Vec`）、内建函数（`print_i32 / get_i32` …）。
- **名字解析结果回填 AST**（如 `PathExprNode.resolved` 指向 Symbol）：语义检查是"查错 + 产出"，产物（表达式类型、符号绑定）留给代码生成，避免 codegen 重做解析。

### 2.4 检查器

- 每个阶段是一个独立 pass，只遍历自己关心的子树（阶段 1 不进函数体、阶段 5 只看字段、阶段 6 只看签名、阶段 8 才走完所有表达式）。**不使用** `rx.ast.AstVisitor`：`visit(n)` 没有返回值，装不下"表达式 → 类型"；而且各阶段的遍历范围、顺序、同一子树的重入次数都不同。
- `BodyChecker` / `ExprChecker` 持有"当前作用域 + 当前函数上下文（返回类型、循环栈）"。
- 表达式检查采用 `check(expr, expected: Type?) -> Type`：**期望类型**是硬需求——字面量选型顺序 = 后缀 > 期望 > i32 兜底；if/loop/break/数组元素/赋值 RHS 都要它。
- `never` 类型参与合并：`return / break / continue` 以及 diverge 的块；if 无 else = `()`；while = `()`；loop 值 = break 载荷合并。
- **place（可赋值地点）分析**：`= / 复合赋值 / &mut / 可变方法接收者` 需要 place + 可变性链（绑定 mut → 引用 mut → 字段/索引逐层）。
- 具体检查功能按 §4 的 WP 分批落地。

### 2.5 错误策略

- 诊断格式 `file:line:col: error: [semantic] msg`（沿用现有约定：行 1 起、列打印 +1）。
- 起步 **fail-fast**：首个错误抛 `SemanticError` → 顶层捕获 → 报告 → `exit 1`；最外层兜底保证任何情况下不 crash。
- 错误恢复（一次报多个）合同不要求，后续可选。
- 退出码：`0 = 接受 / 1 = 前端（词法|语法|语义）错误 / 2 = 用法或 IO`；语义阶段不应再出现 3。

---

## 3. 工程接线

### 3.1 CLI

`./scripts/rxc --stage semantic <file.rx>`：lex → parse → build AST → 语义检查；任一阶段失败 exit 1。
（`--stage lex|parse` 行为不变；usage 文本同步更新。）

### 3.2 本地 runner

`scripts/check_semantic.py`（模仿 `check_frontend.py`）：

- 遍历 `tests/official/semantic/**/manifest.json`，对 `stage == "semantic"` 的条目跑 `rxc --stage semantic`；
- 退出码与 `compilation_success` 比对；输出按目录的进度（accept 是否全绿 / reject 消灭数）；
- 支持 `SUBSTR` 过滤与 `-v`（打印命中的诊断）；
- 退出码：0 全对 / 1 有差异 / 2 环境问题。
- WP0 实测（2026-10-13）：accept 69/69、reject 0/167，全量约 63 秒。

### 3.3 官方 runner 接入（WP6）

`config.mk`：

```
SEMANTIC = ./scripts/rxc --stage semantic {source}
```

- semantic 阶段不检查 `{output}` 内容，可以不写；
- `BUILD` 可置空：runner 以空 `runtime.s` 占位也能通过例行检查（真正的 BUILD 留到 IR/CODEGEN 里程碑）；
- 验收命令：`make test STAGE=semantic`（全绿 → 2.5 里程碑达成）。

---

## 4. 分步计划（WP0–WP6）

**执行原则**：

1. **每个 WP 的硬约束：69 条 accept 不许出现误报**（每步结束全量回归）；
2. reject 按目录统计消灭数；WP 内部按"简单 → 复杂"排序（纯符号表检查先于类型规则）；
3. 每步收尾：`make compiler` 绿 + lexer/parser/semantic 全量回归 + commit；
4. 每个目录动工前：读该目录全部 rej 用例 + 对应规范章节。

### 4.1 WP0 任务清单（可直接开工）

- [x] 扩展 `rx/ast/Type.kt`：补全类型集合（§2.2）
- [x] 新建 `rx/semantic/`：`Scope / Symbols / SemanticError / SemanticAnalyzer`（先空壳）
- [x] `Main.kt`：加 `--stage semantic`，接好退出码
- [x] 新建 `scripts/check_semantic.py`
- [x] 跑基线：确认 69 accept 全绿、167 reject 有目录级分类清单、无 crash
- [ ] commit

### 4.2 步骤总表

| 步骤 | 主要工作 | 覆盖目录（reject 数） | reject 目标（约） | 出口标准 |
| --- | --- | --- | --- | --- |
| **WP0 基础设施** | 类型骨架、Scope、Analyzer 空壳、CLI、runner、checklist | — | 0/167 | 基线报告可用 |
| **WP1 顶层收集+命名空间+入口** | 收集与重名、前向引用、保护名、main 检查、impl 合法性（目标必须是 struct；容器/原生/数组无固有 impl；关联项重名） | namespace-errors(15)、invalid-impls-and-generics(11)、entry(4)、protected-names(1)、methods-and-self(部分 2) | ~33 | 纯符号表类 reject 全灭 |
| **WP2 函数体作用域+名字解析** | 参数/let、shadowing、初始化器不可见自身、块局部不外逃、未定义名（含不可达代码） | names-and-shadowing(2)、lifetimes-and-use(1)、unreachable-checks(部分 1) | ~4 | 名字解析类全灭 |
| **WP3 核心类型规则** | 整数/位/比较/位移同型规则、bool 运算、字面量后缀与边界、cast、期望类型传播、never、block tail/unit、赋值 place、复合赋值、调用参数个数/类型、Box/Vec 构造具体实参、builtin-io 签名 | integer-arithmetic(4)、loops-and-jumps(7)、blocks-if-and-never(5)、boolean-and-short-circuit(4)、casts-and-literals(4)、expected-types(4)、compound-assignment(4)、builtin-io(4)、calls-recursion-and-abi(3)、shifts(2)、unreachable-checks(3) | ~44 | 核心类型规则成型 |
| **WP4 引用/可变性/方法** | &/&mut/deref、autoref 方法查找、place 可变性链、共享/可变引用运算、引用 LUB、aggregate 参数、引用相等 | vec-index-mutability(14)、references-and-mutability(6)、scalar-reference-operators(4)、reference-coercions(4)、methods-and-self(3)、evaluation-order-and-temporaries(2)、aggregate-arguments-and-reference-fields(2)、reference-lub(1)、trait-dispatch(1) | ~37 | 引用体系成型 |
| **WP5 聚合/容器/常量/derive/布局** | 结构体字面量与字段、数组规则、Box、Vec 全套、嵌套容器、常量求值+循环检测、derive 递归条件（Copy/Clone/PartialEq/Eq）、递归布局环检测 | constant-errors(9)、copy-clone-and-equality(9)、structs-and-fields(6)、vec-operations(6)、arrays(5)、box-and-moves(4)、recursive-layout(4)、constants-and-paths(2)、nested-containers(2)、recursive-traits(1)、trait-dispatch(1) | ~49 | 剩余 reject 全灭 |
| **WP6 收尾验收** | 236/236 全量 + lexer 53 + parser 442 回归；`config.mk` 接入；`make test STAGE=semantic` | — | 167/167 | **里程碑达成** |

> reject 按目录为近似分配（总数 = 167），部分目录拆到多个 WP。

---

## 5. 规范边界

### 5.1 UB —— 测试不会出现，**不用实现**

- 字面量越界、负数无符号常量等数值范围问题；
- **经运算符/借用的期望类型反向传播**（不要求）；
- `Box::new / Vec::new` 缺具体类型实参；
- 函数当作值使用；
- 声明受保护名字（`u32/Box/Vec/Copy/...`）、局部名 `get_i32/print_i32/println_i32`；
- **不可达代码里的 place-mutability 违规**（注意与 §5.2 第一条的区别）；
- 索引操作数是 `!`；文档注释；let/参数与可见常量的重名碰撞；
- **跨类型 `==`**（不报也算对）；LUB 无公共目标；多个匹配方法；
- use 兼容性违规；非法生命周期；可观测的零尺寸数据。

### 5.2 必须注意的边界

- **不可达代码里的名字/类型错误仍必须报**（`rej-unknown-name-after-return`、`rej-unreachable-break-participates-in-loop-result-typing`、`rej-wrong-type-in-an-untaken-branch`）→ 不要做"不可达剪枝跳过检查"。
- use 声明整条丢弃，但其余错误照查（`rej-discarding-a-valid-use-does-not-suppress-a-type-error`）。
- 生命周期语法（声明/标注/实参/bound/where）丢弃并信任合法性（已在 AST 侧丢弃）。
- 结构体是**名义类型**；无真泛型：`<i32>` 只是具体实参，struct/fn 带类型参数必须拒。
- 字面量选型顺序：**后缀 > 期望 > i32**。
- reject 必须 exit 1（不能 3、不能 crash）。

---

## 6. 目录清单与进度表（236 条）

状态：☐ 未开始 / ◐ 进行中 / ☑ 全绿（accept 无误报 + reject 全灭）

| 目录 | 条数 | acc | rej | WP | 状态 |
| --- | ---: | ---: | ---: | --- | --- |
| aggregate-arguments-and-reference-fields | 3 | 1 | 2 | WP4 | ☐ |
| arrays | 6 | 1 | 5 | WP5 | ☐ |
| blocks-if-and-never | 6 | 1 | 5 | WP3 | ☐ |
| boolean-and-short-circuit | 5 | 1 | 4 | WP3 | ☐ |
| box-and-moves | 5 | 1 | 4 | WP5 | ☐ |
| builtin-io | 6 | 2 | 4 | WP3 | ☐ |
| calls-recursion-and-abi | 4 | 1 | 3 | WP3 | ☐ |
| casts-and-literals | 7 | 3 | 4 | WP3 | ☐ |
| compound-assignment | 5 | 1 | 4 | WP3 | ☐ |
| comprehensive-large-frame | 1 | 1 | 0 | WP6 | ☐ |
| comprehensive-owned-tree | 1 | 1 | 0 | WP5 | ☐ |
| comprehensive-quicksort | 1 | 1 | 0 | WP3 | ☐ |
| comprehensive-shortest-path | 1 | 1 | 0 | WP5 | ☐ |
| comprehensive-stack-machine | 1 | 1 | 0 | WP3 | ☐ |
| constant-errors | 10 | 1 | 9 | WP5 | ☐ |
| constants-and-paths | 3 | 1 | 2 | WP5 | ☐ |
| copy-clone-and-equality | 12 | 3 | 9 | WP5 | ☐ |
| entry | 5 | 1 | 4 | WP1 | ☐ |
| evaluation-order-and-temporaries | 4 | 2 | 2 | WP4 | ☐ |
| expected-types | 5 | 1 | 4 | WP3 | ☐ |
| integer-arithmetic | 8 | 4 | 4 | WP3 | ☐ |
| invalid-impls-and-generics | 12 | 1 | 11 | WP1 | ☐ |
| lifetimes-and-use | 2 | 1 | 1 | WP2 | ☐ |
| live-state-calls | 1 | 1 | 0 | WP6 | ☐ |
| loop-state-merges | 1 | 1 | 0 | WP6 | ☐ |
| loops-and-jumps | 8 | 1 | 7 | WP3 | ☐ |
| methods-and-self | 6 | 1 | 5 | WP1/WP4 | ☐ |
| names-and-shadowing | 3 | 1 | 2 | WP2 | ☐ |
| namespace-errors | 16 | 1 | 15 | WP1 | ☐ |
| nested-containers | 3 | 1 | 2 | WP5 | ☐ |
| protected-names | 2 | 1 | 1 | WP1 | ☐ |
| recursive-layout | 6 | 2 | 4 | WP5 | ☐ |
| recursive-traits | 2 | 1 | 1 | WP5 | ☐ |
| reference-coercions | 5 | 1 | 4 | WP4 | ☐ |
| reference-lub | 2 | 1 | 1 | WP4 | ☐ |
| references-and-mutability | 7 | 1 | 6 | WP4 | ☐ |
| runtime-value-merges | 1 | 1 | 0 | WP6 | ☐ |
| scalar-reference-operators | 6 | 2 | 4 | WP4 | ☐ |
| shifts | 7 | 5 | 2 | WP3 | ☐ |
| source-main-calls | 1 | 1 | 0 | WP1 | ☐ |
| structs-and-fields | 7 | 1 | 6 | WP5 | ☐ |
| trait-dispatch-and-reference-equality | 3 | 1 | 2 | WP4/WP5 | ☐ |
| unreachable-checks | 6 | 2 | 4 | WP2/WP3 | ☐ |
| vec-index-mutability | 22 | 8 | 14 | WP4 | ☐ |
| vec-operations | 8 | 2 | 6 | WP5 | ☐ |
| **合计** | **236** | **69** | **167** | | |

---

## 7. 参考资料

- 规范（mdBook）：https://acmclasscourse-2025.github.io/rx-compiler-specification/
  - 重点章节：`undefined-behavior`（范围界定）、`names`、`types`、`references`、`const_eval`、`builtin-traits`、`expressions/*`、`grammar`。
- 手册 §2.5（`dev/manual-zh.txt` 约 2429–2610 行）：符号/作用域/两遍检查的推荐结构。
- `docs/ast-design.md`：AST 结构与决策 A–E（语义标记、"源码类型 vs 内部类型"分离等）。
- `tests/official/manifest.schema.json`：测试合同字段（`compilation_success` 等）。
- `scripts/test.py`：官方 runner 的判定逻辑（退出码 0/1、超时、crash 判定）。
