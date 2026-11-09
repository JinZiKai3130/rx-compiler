# 在线评测（rxoj.site）接入说明

在线评测机：<https://rxoj.site/>。评测机的源码约定（`config.mk`、命令占位符、依赖准备）
来自 <https://github.com/ACMClassCourse-2025/rx-online-judge>。

## 评测机如何运行你的仓库

1. 用公开 HTTPS Git 仓库 + ref 拉取源码；**不会拉取子模块**（`tests/official`、
   `vendor/REIMU` 都不会出现在评测沙箱里）。
2. 按所选语言准备依赖。本仓库用 `.rx-oj.toml` **关闭了依赖准备**：下载
   Kotlin 编译器 + Maven 插件约 120 MB，实测会超过评测机 180 秒的
   dependency preparation 时限（已实际遇到 `time_limit`）。
3. 在离线沙箱中执行 `config.mk` 的 `BUILD`，再把 `/workspace` 快照下来。
4. 每个测试用例执行一次 `SEMANTIC` / `IR` / `CODEGEN`；`RUN` 由评测机忽略，
   它总是用自己信任的 REIMU 镜像。
5. 只接受退出码 `0`（accept）或 `1`（reject）；崩溃、超时、其他退出码都算失败。

当前实现了语义阶段，因此只能先提交 **Semantic** 套件（评测机显示 239 条）。
IR / codegen / optimization 套件需要先实现对应阶段并填写 `config.mk` 的
`IR` / `CODEGEN`。

## 仓库里为此做的改动

| 文件 | 作用 |
| --- | --- |
| `dist/rxc.jar` + `dist/lib/` | **提交给评测机的预编译产物**。评测机直接 `java -cp` 运行它，不需要 Maven，也不会触发依赖准备。源文件改动后必须重新 `make dist` 并提交。 |
| `.rx-oj.toml` | `[dependencies] enabled = false`：告诉评测机跳过依赖准备。 |
| `config.mk` | `BUILD` 只检查 `dist/` 和 `java` 是否存在并创建空 `{runtime}`；`SEMANTIC` 用 `dist/` 里的 jar 运行编译器。`IR` / `CODEGEN` 暂为空。 |
| `pom.xml` | 本地用 Maven 构建 `dist/` 的工具链（Kotlin 2.1.0 + ANTLR runtime）；评测机不使用它。 |
| `src/main/java/rx/gen/` | 已提交的 ANTLR 生成代码，Maven 构建时直接编译。 |
| `compiler.mk` | 新增 `make dist`（构建并刷新 `dist/`）和 `make maven-parser`（修改 `grammar/` 后同步生成的 6 个 Java 文件）。 |
| `build.gradle.kts` | IDE 模型改为直接使用已提交的生成代码，避免和 Gradle 自己生成的类重复。 |
| `ExprChecker.kt` / `BodyChecker.kt` | 把“发散（diverges）”从类型中独立出来：`{ loop {}; 42 }` 类型是 i32 但会发散，外层无尾块因此得到 `!`。 |

本地开发仍然可以用 `make compiler` + `./scripts/rxc`，不需要 Maven。

## 本地验证（模拟评测机）

需要 Maven 才能刷新 `dist/`（本机未装时可把 Apache Maven 解压到任意目录，用
绝对路径传给 `MVN=`）：

```sh
# 1. 重新构建 dist/（源文件改动后必须执行）
make dist MVN=$HOME/tools/maven/apache-maven-3.9.9/bin/mvn

# 2. 模拟评测机的 SEMANTIC（不需要 Maven）
PATH=/usr/bin:/bin java -cp 'dist/rxc.jar:dist/lib/*' \
    rx.MainKt --stage semantic path/to/file.rx
echo $?   # 0 = accept, 1 = reject

# 3. 本仓库的语义语料（走本地 make/kotlinc 构建）
python3 scripts/check_semantic.py
```

2026-11-10 实测：`dist/rxc.jar` 对评测机当前 testcase commit（`1abfeaef`，
239 条 = 71 accept + 168 reject）**239/239 全部符合**；`SEMANTIC` 单次 ~0.5 s，
远低于 30 秒单用例上限。首次尝试用 Maven 现场构建时，依赖准备超过 180 秒被判
`time_limit`，因此改为提交预编译 `dist/`。

## 提交步骤

1. 确认 `dist/` 是最新的（源文件改动后先 `make dist`），然后提交并推送：

   ```sh
   git add .rx-oj.toml config.mk compiler.mk dist \
           src/main/java/rx/gen src/main/kotlin pom.xml build.gradle.kts \
           docs/online-judge.md
   git commit -m "build: submit prebuilt compiler distribution"
   git push
   ```

2. 确认仓库是公开的，并记下要评测的提交 SHA（比分支更稳定）。
3. 打开 <https://rxoj.site/>，登录后填写：
   - Repository：`https://github.com/<你的账号>/rx-compiler.git`
   - Git ref：提交 SHA（或 `main`）
   - Language：**Java**（不要用默认的 C / C++，也没有 Kotlin 选项）
   - Suite：**Semantic**
4. 提交后在 Run 页面查看 `BUILD` 日志和逐用例结果。选对 Java 时构建日志
   会直接进入 `BUILD`，不会出现 `Preparing maven dependencies…`。

## 注意事项

- **改了编译器源码就要重新 `make dist` 并提交 `dist/`**，否则评测机运行的是旧
  二进制。只改文档/测试不影响。
- `config.mk` 中的路径（`dist/rxc.jar` 等）是相对 `/workspace` 的；评测命令在
  工作区根目录执行。
- `{runtime}` 必须是普通文件。当前 `BUILD` 用 `: > {runtime}` 创建空文件。
- 评测机不会递归拉取子模块，所以构建不能依赖 `vendor/REIMU` 或 `tests/official`。
- 修改文法后务必运行 `make compiler && make maven-parser`，把重新生成的 6 个
  `src/main/java/rx/gen/*.java` 一起提交，再 `make dist`。
- 评测机的语义用例数比本地子模块新（239 vs 236）；本次已用评测机 commit
  `1abfeaef` 的完整目录验证 239/239。日后评测机更新 testcase 时，以 Run 页面
  结果为准补检查。
