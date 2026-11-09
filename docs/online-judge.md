# 在线评测（rxoj.site）接入说明

在线评测机：<https://rxoj.site/>。评测机的源码约定（`config.mk`、命令占位符、依赖准备）
来自 <https://github.com/ACMClassCourse-2025/rx-online-judge>。

## 评测机如何运行你的仓库

1. 用公开 HTTPS Git 仓库 + ref 拉取源码；**不会拉取子模块**（`tests/official`、
   `vendor/REIMU` 都不会出现在评测沙箱里）。
2. 按所选语言准备依赖（本仓库选 **Java**，读取根目录 `pom.xml`，执行
   `mvn dependency:go-offline`）。
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
| `pom.xml` | 评测机的 Java 21 镜像没有 kotlinc/ANTLR，用 Maven 从 Maven Central 下载 Kotlin 编译器并编译。`BUILD` 产出 `target/rxc.jar` 和 `target/lib/`。 |
| `config.mk` | 评测机读取的五个命令：`BUILD` 用 Maven 构建，`SEMANTIC` 用 `java -cp` 启动编译器。`IR` / `CODEGEN` 暂为空。 |
| `src/main/java/rx/gen/` | 已提交的 ANTLR 生成代码。这样构建时不需要 ANTLR tool 和它 13.7 MB 的 ICU 依赖，依赖快照才能压在评测机 128 MiB 限制内。 |
| `compiler.mk` | 新增 `make maven-parser`：修改 `grammar/` 后重新生成并同步这 6 个 Java 文件。 |
| `build.gradle.kts` | IDE 模型改为直接使用已提交的生成代码，避免和 Gradle 自己生成的类重复。 |
| `ExprChecker.kt` / `BodyChecker.kt` | 把“发散（diverges）”从类型中独立出来：`{ loop {}; 42 }` 类型是 i32 但会发散，外层无尾块因此得到 `!`。 |

本地开发仍然可以用 `make compiler` + `./scripts/rxc`，不需要 Maven。

## 本地验证（模拟评测机）

需要 Maven（本机未装时可把 Apache Maven 解压到任意目录，用绝对路径调用）：

```sh
# 1. 依赖准备（联网一次）
mvn -B -ntp org.apache.maven.plugins:maven-dependency-plugin:3.8.1:go-offline

# 2. 离线构建（评测机用 MAVEN_ARGS="-o -s ..." 自动进入离线模式）
mvn -B -ntp -o -DskipTests package

# 3. 冒烟
java -cp 'target/rxc.jar:target/lib/*' rx.MainKt --stage semantic path/to/file.rx
echo $?   # 0 = accept, 1 = reject

# 4. 本仓库的语义语料
python3 scripts/check_semantic.py
```

2026-11-09 实测：依赖快照 tar 约 **120.7 MB**（限 128 MiB），单 CPU / 512 MB
堆下离线构建约 **20 秒**（限 120 秒）；本地 236 条语义用例全部通过
（69 accept + 167 reject）。另外下载了评测机当前 testcase commit（`1abfeaef`，
239 条 = 71 accept + 168 reject），用 Maven 构建的编译器实测 **239/239 全部符合**。

## 提交步骤

1. 提交并推送改动（注意 `tests/official` 子模块指针的本地改动与本次无关，
   可自行决定是否提交）：

   ```sh
   git add pom.xml config.mk compiler.mk build.gradle.kts src/main/java/rx/gen
   git commit -m "build: add Maven build for online judge"
   git push
   ```

2. 确认仓库是公开的，并记下要评测的提交 SHA（比分支更稳定）。
3. 打开 <https://rxoj.site/>，登录后填写：
   - Repository：`https://github.com/<你的账号>/rx-compiler.git`
   - Git ref：提交 SHA（或 `main`）
   - Language：**Java**
   - Suite：**Semantic**
4. 提交后在 Run 页面查看 `BUILD` 日志和逐用例结果。

## 注意事项

- `config.mk` 中的路径（`target/rxc.jar` 等）是相对 `/workspace` 的；评测命令
  在工作区根目录执行。
- `{runtime}` 必须是普通文件。当前 `BUILD` 用 `: > {runtime}` 创建空文件。
- 评测机不会递归拉取子模块，所以构建不能依赖 `vendor/REIMU` 或 `tests/official`。
- 修改文法后务必运行 `make compiler && make maven-parser`，把重新生成的 6 个
  `src/main/java/rx/gen/*.java` 一起提交，否则评测机和本地行为会不一致。
- 评测机的语义用例数比本地子模块新（239 vs 236）；本次已用评测机 commit
  `1abfeaef` 的完整目录验证 239/239。日后评测机更新 testcase 时，以 Run 页面
  结果为准补检查。
