# CrossLangFuzzer 架构与模块介绍

> 📄 English version: [ARCHITECTURE-EN.md](./ARCHITECTURE-EN.md)

---

## 1. 项目总览

**CrossLangFuzzer** 是一款面向 JVM 语言编译器的模糊测试（fuzzing）工具，通过生成跨语言程序来发现编译器 bug。它支持
Kotlin、Java、Groovy、Scala 2、Scala 3 五种语言目标的差分测试（differential testing），目前已发现并报告了 24 个编译器 bug。

### 核心思路

大多数编译器模糊测试工具只生成单语言代码。CrossLangFuzzer 的独特之处在于利用 Kotlin 编译器（基于
IR）内部的数据结构来构建程序，再通过多语言代码生成器（printer）输出为不同语言的源码，送入对应编译器进行编译，从而天然实现了跨语言程序生成。

---

## 2. 模块结构

```
CrossLangFuzzer
├── tree/               # IR 数据结构定义（代码生成核心）
├── tree/tree-generator/# IR 节点的 Visitor 模式打印器（代码生成）
├── src/main/           # 主要逻辑
│   ├── generator/      # 程序生成器（IrDeclGenerator）
│   ├── mutator/        # 程序变异器（IrMutator）
│   ├── printer/        # 多语言代码输出
│   ├── validator/      # IR 语义校验（仅用于化简阶段）
│   ├── ir/             # IR 工具与序列化
│   ├── algorithm/      # 化简算法（DDMin）
│   └── config/         # 运行配置
├── runners/            # 各语言 runner（测试运行框架）
│   ├── common-runner/  # 通用逻辑（化简、差分测试、数据记录）
│   ├── kotlin-runner/  # Kotlin 编译器测试 runner
│   ├── scala-runner/   # Scala 编译器测试 runner
│   └── groovy-runner/  # Groovy 编译器测试 runner
├── test-framework/     # Kotlin 测试框架集成
└── ged/                # Graph Edit Distance — 程序相似度比较
```

---

## 3. tree — IR 数据结构

**路径:** `tree/src/` + `tree/gen/`

这是项目的核心抽象层。`tree/gen/` 由 `tree/tree-generator` 根据手写接口自动生成实现类；`tree/src/` 包含手写的 DSL
构建器、工具函数和 Visitor 模式定义。

### 核心类型

| 类型                      | 作用                                           |
|-------------------------|----------------------------------------------|
| `IrProgram`             | 顶层程序容器，包含一组 `IrClassDeclaration`             |
| `IrClassDeclaration`    | 类/接口声明，含成员函数、属性、类型参数                         |
| `IrFunctionDeclaration` | 函数声明，含参数列表、返回类型、override 标记                  |
| `IrParameter`           | 函数参数                                         |
| `IrTypeParameter`       | 类型参数（含上界）                                    |
| `IrType`                | 类型系统（支持泛型参数化类型、可空类型、平台类型等）                   |
| `IrClassifier`          | 类型分类符（简单类型 / 参数化类型）                          |
| `Language`              | 枚举：KOTLIN / JAVA / SCALA / GROOVY4 / GROOVY5 |
| `ClassKind`             | 枚举：ABSTRACT / INTERFACE / OPEN / FINAL       |

### 类型层级

```
IrType
├── IrSimpleClassifier        # 简单类型，如 Int, String, Any
├── IrParameterizedClassifier # 参数化类型，如 List<Int>
├── IrNullableType           # 可空类型，如 String?
├── IrDefinitelyNotNullType # 强制非空类型，如 T&Any
├── IrTypeParameter          # 类型参数，如 T（带上界）
├── IrPlatformType           # 平台类型（Kotlin 对 Java 的映射）
└── IrBuiltInType            # 内建类型（IrAny, IrNothing, IrUnit）
```

### Visitor 模式

`IrTopDownVisitor` 和 `IrTransformer` 提供树遍历和转换能力，支持对整个程序进行深度遍历、修改或信息收集。

---

## 4. generator — 程序生成器

**路径:** `src/main/kotlin/.../generator/`

`IrDeclGenerator` 是核心生成器，从空的 `IrProgram` 开始，通过配置驱动（`GeneratorConfig`
）的概率分布，有控制地生成类层次结构、成员函数、类型参数等。生成的 IR **天然合法**，不需要后续校验。

### 生成即合法

生成器内部直接使用 `collectFunctionSignatureMap` 和 `getOverrideCandidates` 在生成过程中检查 override
约束，无需依赖外部的 `IrValidator`。这保证了生成阶段产出的 IR 一定满足 JVM 继承规则。

### 关键能力

- **子类关系构建**：维护 `subClassMap` 和 `notSubClassCache`，支持泛型子类型判断
- **Override 检测**：生成过程中检查父类/接口方法，使用内部工具而非外部 Validator
- **类型选择**：支持顺序选择（sequential）和过滤选择（filtered），确保类型在语义上兼容当前上下文
- **DSL 构建器**：通过 `IrClassDeclarationBuilder`、`IrFunctionDeclarationBuilder` 等以类型安全的方式构造 IR

---

## 5. printer — 多语言代码生成

**路径:** `src/main/kotlin/.../printer/`

将 IR 渲染为各语言源码。核心抽象为 `IrPrinter`，实现类针对不同语言有细微差异（语言特性语法不同，如 Kotlin 的 `val` vs Java 的
`final`）。

| Printer               | 输出语言                           |
|-----------------------|--------------------------------|
| `KtIrClassPrinter`    | Kotlin（.kt）                    |
| `JavaIrClassPrinter`  | Java / Groovy（.java / .groovy） |
| `ScalaIrClassPrinter` | Scala（.scala）                  |
| `IrProgramPrinter`    | 顶层调度，将程序中每个类分发给对应 Printer      |

**注意**：Groovy 当前复用 `JavaIrClassPrinter`，因为 Groovy 4/5 与 Java 的类型语法高度兼容。未来可单独实现 Groovy
Printer（当前为 TODO）。

---

## 6. mutator — 程序变异器

**路径:** `src/main/kotlin/.../mutator/`

在已生成的 IR 程序基础上施加变异操作，增加程序多样性以触发更多编译器路径。**变异后不校验**，变异结果可能是语义非法的 IR。

已实现的变异策略：

| 变异                                               | 作用                       |
|--------------------------------------------------|--------------------------|
| `mutateGenericArgumentInParent`                  | 修改父类/接口的泛型实参             |
| `removeOverrideMemberFunction`                   | 移除 override 方法体（变为 stub） |
| `mutateGenericArgumentInMemberFunctionParameter` | 修改成员函数参数的泛型实参            |
| `mutateParameterNullability`                     | 修改参数可空性                  |
| `mutateClassTypeParameterUpperBoundNullability`  | 修改类的类型参数上限的可空性           |
| `mutateClassTypeParameterUpperBound`             | 修改类的类型参数上限               |

每种变异有权重（weight），由 `MutatorConfig` 控制是否启用及相对概率。

**设计原则**：变异后的 IR 可能违反 JVM 继承规则，但仍然可能触发跨语言编译器 bug，因此有意跳过校验。

---

## 7. validator — IR 语义校验（仅用于化简阶段）

**路径:** `src/main/kotlin/.../validator/`

`IrValidator` **不在生成或变异阶段使用**，而是仅在 `MinimizeRunner2` 的化简循环中调用。每次移除元素后，用
`IrValidator` 检查剩余程序是否仍合法；若验证失败则回滚该步化简，确保缩减后的程序仍能复现 bug。

核心检查：

- 类继承层次合法性（interface vs class）
- Override 方法的签名匹配
- 类型参数的上界约束
- 类型参数在作用域内的可用性

---

## 8. ir/serde — JSON 序列化

**路径:** `src/main/kotlin/.../ir/serde/`

将 `IrProgram` 序列化为 JSON（使用 Gson），支持保存和恢复 IR 以便后续复现、差分测试或增量变异。

---

## 9. algorithm — DDMin 化简算法

**路径:** `src/main/kotlin/.../algorithm/DDMin.kt`

实现 **DDMin（Delta-Debugging Minimization）** 算法，用于将导致 bug 的输入程序尽可能缩减，而不改变其导致 bug 的行为。

---

## 10. runners — 测试运行框架

### common-runner — 通用运行逻辑

**路径:** `runners/common-runner/src/`

提供所有 runner 共享的基础设施：

| 组件                     | 作用                                                               |
|------------------------|------------------------------------------------------------------|
| `CommonCompilerRunner` | 命令行入口，解析 `-m/--mode`、`-i/--input` 等参数                            |
| `RunMode`              | 运行模式：NormalTest / DifferentialTest / GenerateIROnly / ReduceOnly |
| `CompileResult`        | 封装编译结果（成功/失败 + 错误信息）                                             |
| `ICompiler`            | 编译器抽象接口                                                          |
| `DataRecorder`         | 记录编译结果数据                                                         |
| `MinimizeRunnerImpl`   | 旧版化简器（计划未来删除）                                                    |
| `MinimizeRunner2`      | 当前化简器实现，支持类/函数级别的程序化简                                            |
| `GroupedElement`       | 按语义分组元素以便分组化简                                                    |

### kotlin-runner — Kotlin 编译器测试

利用 Kotlin 官方的编译器测试框架来运行生成的测试用例，复用了 Kotlin 编译器的标准测试基础设施。

### scala-runner — Scala 编译器测试

差分测试 Scala 2.13 和 Scala 3 nightly 版本（版本暂时固定）。

### groovy-runner — Groovy 编译器测试

支持 Groovy 4.0.x 和 5.0.x 的差分测试。

---

## 11. test-framework — Kotlin 测试框架集成

**路径:** `test-framework/src/`

提供将生成的测试用例集成到 Kotlin 编译器测试框架的能力，生成标准格式的测试文件。（测试代码仍在逐步完善中。）

---

## 12. ged — 程序相似度比较

**路径:** `ged/src/`

使用 **Graph Edit Distance（GED）** 来量化两个 IR 程序之间的结构相似度。目前仍在实验性阶段，主要用于辅助去重。去重目前仍依赖手动方式。

---

## 13. 配置与运行

### 生成配置

`GeneratorConfig` 控制生成过程中的概率参数（类数量、继承深度、泛型复杂度等）。

### 变异配置

`MutatorConfig` 控制各变异策略的启用状态和权重。

### 运行配置

`RunConfig` 是顶层配置容器，同时包含 `GeneratorConfig` 和 `MutatorConfig`，以及变异次数等全局参数。

### 快速运行

```bash
# Download tree-generator
curl -L -o libs/tree-generator-common.jar \
  https://github.com/XYZboom/CrossLangFuzzer/releases/download/dev-ef4368/tree-generator-common.jar

# Kotlin runner
./gradlew :runners:kotlin-runner:run --args="-s" \
  -Dorg.gradle.java.home=/path/to/jdk17

# Groovy runner
./gradlew :runners:groovy-runner:run \
  --args="--gv 4.0.26,5.0.0-alpha-12" \
  -Dorg.gradle.java.home=/path/to/jdk17

# Scala runner
./gradlew :runners:scala-runner:run
```

发现 bug 后，结果会保存在对应 runner 的 `out/min` 目录下。

---

## 14. 数据流总览

```
生成阶段 (Generation)
┌─────────────────┐
│ GeneratorConfig │──→ IrDeclGenerator ──→ IrProgram（生成即合法）
└─────────────────┘

变异阶段 (Mutation) — 不校验
┌─────────────────┐    ┌──────────────────┐
│ MutatorConfig   │──→│ IrMutator         │──→ IrProgram（可能非法）
└─────────────────┘    └──────────────────┘

化简阶段 (Reduction) — 使用 Validator
                      IrProgram ──→ MinimizeRunner2
                                    ├── 移除元素
                                    ├── IrValidator 检查
                                    │      └── 失败则回滚
                                    └── 最终化简结果 → out/min/
                                       ↑
                          ┌────────────┘
                          ↓
              渲染阶段 (Printing) ──→ 差分测试 (Differential Testing)
              IrProgramPrinter            disagree → bug → 化简
              ├─ KtIrClassPrinter
              ├─ JavaIrClassPrinter
              └─ ScalaIrClassPrinter
```

**关键设计决策**：

1. **生成即合法**：`IrDeclGenerator` 在生成过程中使用内部工具（`collectFunctionSignatureMap`、`getOverrideCandidates`）检查 override 约束，无需外部 Validator
2. **变异后不校验**：`IrMutator` 直接修改 IR，变异后的 IR 可能是非法的。这是有意为之——非法 IR 仍可能触发跨语言编译器 bug
3. **Validator 仅用于化简**：`IrValidator` 仅在 `MinimizeRunner2` 化简循环中使用，每次移除元素后验证剩余程序是否仍能复现 bug

---

## 15. 术语表

| 中文              | English                     | 说明                                  |
|-----------------|-----------------------------|-------------------------------------|
| IR / 中间表示       | Intermediate Representation | 程序的结构化内存表示                          |
| 差分测试            | Differential Testing        | 用多个编译器编译同一程序，比较结果差异                 |
| Fuzzing / 模糊测试  | Fuzzing                     | 随机/半随机输入生成以触发 bug                   |
| Printer / 代码生成器 | Printer                     | 将 IR 转换为源码的模块                       |
| Mutator / 变异器   | Mutator                     | 对已生成程序施加变异的模块                       |
| Validator / 校验器 | Validator                   | 校验 IR 语义合法性的模块（仅用于化简阶段）           |
| DDMin           | DDMin                       | Delta-Debugging Minimization，程序化简算法 |
| Override stub   | Override stub               | 保留了签名但删除了实现的 override 方法            |
| 平台类型            | Platform Type               | Kotlin 对 Java 类型系统的映射类型             |
| GED / 图编辑距离     | Graph Edit Distance         | 量化程序间结构相似度的指标                       |

---

> 📄 English version: [ARCHITECTURE-EN.md](./ARCHITECTURE-EN.md)