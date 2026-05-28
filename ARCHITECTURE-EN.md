# CrossLangFuzzer Architecture & Module Guide

> 📄 中文版：[ARCHITECTURE-zh.md](./ARCHITECTURE-zh.md)

---

## 1. Project Overview

**CrossLangFuzzer** is a fuzzing tool designed for JVM-based language compilers. It generates cross-language programs to trigger compiler bugs through differential testing across Kotlin, Java, Groovy, Scala 2, and Scala 3. It has found and reported 24 compiler bugs so far.

### Core Idea

Most compiler fuzzers generate code in a single language only. CrossLangFuzzer's key insight is to build programs using Kotlin compiler's internal IR data structures, then render them into multiple JVM languages via language-specific printers for compilation by each target compiler. This naturally achieves cross-language program generation.

---

## 2. Module Structure

```
CrossLangFuzzer
├── tree/               # IR data structure definitions (code generation core)
├── tree/tree-generator/# Visitor-pattern printer for IR nodes (code generation)
├── src/main/           # Core logic
│   ├── generator/      # Program generator (IrDeclGenerator)
│   ├── mutator/        # Program mutator (IrMutator)
│   ├── printer/        # Multi-language code output
│   ├── validator/      # IR semantic validation (used only in reduction)
│   ├── ir/             # IR utilities & serialization
│   ├── algorithm/      # Reduction algorithm (DDMin)
│   └── config/         # Runtime configuration
├── runners/            # Per-language testing run framework
│   ├── common-runner/  # Shared logic (reduction, differential testing, data recording)
│   ├── kotlin-runner/  # Kotlin compiler testing runner
│   ├── scala-runner/   # Scala compiler testing runner
│   └── groovy-runner/  # Groovy compiler testing runner
├── test-framework/     # Kotlin test framework integration
└── ged/                # Graph Edit Distance — program similarity comparison
```

---

## 3. tree — IR Data Structures

**Path:** `tree/src/` + `tree/gen/`

This is the project's core abstraction layer. `tree/gen/` is auto-generated from hand-written interface definitions via `tree/tree-generator`; `tree/src/` contains hand-written builder DSLs, utilities, and visitor definitions.

### Core Types

| Type                    | Purpose                                                                        |
|-------------------------|--------------------------------------------------------------------------------|
| `IrProgram`             | Top-level program container, holds `IrClassDeclaration` set                    |
| `IrClassDeclaration`    | Class/interface declaration with member functions, properties, type parameters |
| `IrFunctionDeclaration` | Function declaration with parameter list, return type, override flag           |
| `IrParameter`           | Function parameter                                                             |
| `IrTypeParameter`       | Type parameter (with upper bound)                                              |
| `IrType`                | Type system (parameterized generics, nullable types, platform types, etc.)     |
| `IrClassifier`          | Type classifier (simple / parameterized)                                       |
| `Language`              | Enum: KOTLIN / JAVA / SCALA / GROOVY4 / GROOVY5                                |
| `ClassKind`             | Enum: ABSTRACT / INTERFACE / OPEN / FINAL                                      |

### Type Hierarchy

```
IrType
├── IrSimpleClassifier        # Simple types, e.g. Int, String, Any
├── IrParameterizedClassifier # Parameterized types, e.g. List<Int>
├── IrNullableType           # Nullable types, e.g. String?
├── IrDefinitelyNotNullType # Definitely-not-null types, e.g. T&Any
├── IrTypeParameter          # Type parameters, e.g. T (with upper bound)
├── IrPlatformType           # Platform types (Kotlin's mapping of Java types)
└── IrBuiltInType            # Built-in types (IrAny, IrNothing, IrUnit)
```

### Visitor Pattern

`IrTopDownVisitor` and `IrTransformer` provide tree traversal and transformation capabilities, supporting deep traversal, modification, or information collection over the entire program.

---

## 4. generator — Program Generator

**Path:** `src/main/kotlin/.../generator/`

`IrDeclGenerator` is the core generator. Starting from an empty `IrProgram`, it builds class hierarchies, member functions, and type parameters using configuration-driven (`GeneratorConfig`) probability distributions. Generated IR is **valid by construction** — no post-hoc validation required.

### Valid by Construction

The generator uses internal utility functions `collectFunctionSignatureMap` and `getOverrideCandidates` during generation to check override constraints. This ensures the generated IR always conforms to JVM inheritance rules, without needing an external `IrValidator`.

### Key Capabilities

- **Subclass relationship building**: Maintains `subClassMap` and `notSubClassCache` to support generic subtype inference
- **Override detection**: Checks parent/interface methods during generation using internal tools (not external Validator)
- **Type selection**: Supports both sequential and filtered type selection, ensuring types are semantically compatible with the current context
- **DSL builders**: Constructs IR in a type-safe manner through `IrClassDeclarationBuilder`, `IrFunctionDeclarationBuilder`, etc.

---

## 5. printer — Multi-Language Code Generation

**Path:** `src/main/kotlin/.../printer/`

Renders IR into source code for each target language. The core abstraction is `IrPrinter`, with language-specific implementations handling syntactic differences (e.g., Kotlin `val` vs Java `final`).

| Printer               | Output Language                                                                     |
|-----------------------|-------------------------------------------------------------------------------------|
| `KtIrClassPrinter`    | Kotlin (.kt)                                                                        |
| `JavaIrClassPrinter`  | Java / Groovy (.java / .groovy)                                                     |
| `ScalaIrClassPrinter` | Scala (.scala)                                                                      |
| `IrProgramPrinter`    | Top-level dispatcher, routes each class in the program to the corresponding printer |

**Note**: Groovy currently reuses `JavaIrClassPrinter` because Groovy 4/5 shares compatible type syntax with Java. A dedicated Groovy Printer can be implemented in the future (currently TODO).

---

## 6. mutator — Program Mutator

**Path:** `src/main/kotlin/.../mutator/`

Applies mutations to generated IR programs to increase diversity and trigger more compiler code paths. **Mutations are not validated** — the resulting IR may be semantically invalid.

Implemented mutation strategies:

| Mutation                                         | Purpose                                                   |
|--------------------------------------------------|-----------------------------------------------------------|
| `mutateGenericArgumentInParent`                  | Modifies generic arguments in parent class/interface      |
| `removeOverrideMemberFunction`                   | Removes override method body (converts to stub)           |
| `mutateGenericArgumentInMemberFunctionParameter` | Modifies generic arguments in member function parameters  |
| `mutateParameterNullability`                     | Modifies parameter nullability                            |
| `mutateClassTypeParameterUpperBoundNullability`  | Modifies nullability of class type parameter upper bounds |
| `mutateClassTypeParameterUpperBound`             | Modifies class type parameter upper bounds                |

Each mutation has a weight controlled by `MutatorConfig`, which determines whether it is enabled and its relative probability.

**Design rationale**: Invalid IR after mutation is intentional — a semantically broken program may still trigger cross-language compiler bugs worth reporting.

---

## 7. validator — IR Semantic Validation (used only in reduction)

**Path:** `src/main/kotlin/.../validator/`

`IrValidator` is **not used during generation or mutation**. It is called **only** inside `MinimizeRunner2`'s reduction loop. After each element removal, `IrValidator` checks whether the remaining program is still valid; if validation fails, the reduction step is rolled back, ensuring the minimized program still reproduces the bug.

Core checks:
- Class inheritance hierarchy validity (interface vs class)
- Override method signature matching
- Type parameter upper bound constraints
- Type parameter availability within its scope

---

## 8. ir/serde — JSON Serialization

**Path:** `src/main/kotlin/.../ir/serde/`

Serializes `IrProgram` to JSON (using Gson), supporting persistence, reproduction, differential testing, or incremental mutation workflows.

---

## 9. algorithm — DDMin Reduction Algorithm

**Path:** `src/main/kotlin/.../algorithm/DDMin.kt`

Implements **DDMin (Delta-Debugging Minimization)** to reduce bug-triggering programs to minimal reproducibles while preserving the bug-triggering behavior.

---

## 10. runners — Testing Run Framework

### common-runner — Shared Runner Logic

**Path:** `runners/common-runner/src/`

Provides infrastructure shared by all runners:

| Component              | Purpose                                                                |
|------------------------|------------------------------------------------------------------------|
| `CommonCompilerRunner` | CLI entry point, parses `-m/--mode`, `-i/--input`, etc.                |
| `RunMode`              | Run modes: NormalTest / DifferentialTest / GenerateIROnly / ReduceOnly |
| `CompileResult`        | Encapsulates compilation result (success/failure + error messages)     |
| `ICompiler`            | Compiler abstraction interface                                         |
| `DataRecorder`         | Records compilation result data                                        |
| `MinimizeRunnerImpl`   | Legacy reducer (planned for future removal)                            |
| `MinimizeRunner2`      | Current reducer, supports class/function-level reduction               |
| `GroupedElement`       | Groups elements by semantic similarity for grouped reduction           |

### kotlin-runner — Kotlin Compiler Testing

Uses Kotlin compiler's built-in codegen test framework to execute generated test cases, reusing Kotlin compiler's standard testing infrastructure.

### scala-runner — Scala Compiler Testing

Differential testing between Scala 2.13 and Scala 3 nightly builds (versions currently fixed).

### groovy-runner — Groovy Compiler Testing

Supports Groovy 4.0.x and 5.0.x differential testing.

---

## 11. test-framework — Kotlin Test Framework Integration

**Path:** `test-framework/src/`

Provides the ability to integrate generated test cases into the Kotlin compiler test framework, generating standardized test files. (Test code is still being refined.)

---

## 12. ged — Program Similarity Comparison

**Path:** `ged/src/`

Uses **Graph Edit Distance (GED)** to quantify structural similarity between IR programs. Currently experimental, primarily used to assist deduplication. Deduplication currently still relies on manual processes.

---

## 13. Configuration & Running

### Generator Config

`GeneratorConfig` controls probability parameters during generation: class count, inheritance depth, generic complexity, etc.

### Mutator Config

`MutatorConfig` controls the enable state and weights for each mutation strategy.

### Run Config

`RunConfig` is the top-level configuration container bundling `GeneratorConfig`, `MutatorConfig`, and global parameters such as mutation count.

### Quick Start

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

When a bug is found, results are saved in each runner's `out/min` directory.

---

## 14. Data Flow Overview

```
Generation Phase
┌─────────────────┐
│ GeneratorConfig │──→ IrDeclGenerator ──→ IrProgram (valid by construction)
└─────────────────┘

Mutation Phase — no validation
┌─────────────────┐    ┌──────────────────┐
│ MutatorConfig   │──→│ IrMutator         │──→ IrProgram (may be invalid)
└─────────────────┘    └──────────────────┘

                                          ┌──────────────┐
                                          ↓              │
Printing Phase                    Differential Testing
IrProgramPrinter ──→ .kt/.java/.scala  →  agree → next round
├─ KtIrClassPrinter                          disagree → bug → MinimizeRunner2
├─ JavaIrClassPrinter                                        │
└─ ScalaIrClassPrinter                                          ↓
                                                     Reduction Phase — validator used here
                                                     MinimizeRunner2
                                                     ├── remove element
                                                     ├── IrValidator checks
                                                     │      └── invalid → rollback
                                                     └── minimized → out/min/
                                                        ↑
                                           ┌────────────┘
```

### Key Design Decisions

1. **Valid by construction**: `IrDeclGenerator` uses internal tools (`collectFunctionSignatureMap`, `getOverrideCandidates`) during generation to check override constraints. No external `IrValidator` is needed at generation time.
2. **No validation after mutation**: `IrMutator` directly modifies IR without calling `IrValidator`. Invalid IR after mutation is intentional — it may still trigger cross-language compiler bugs.
3. **Validator only in reduction**: `IrValidator` is called **only** inside `MinimizeRunner2`. Each element removal is validated; if invalid, the step is rolled back. This ensures the minimized program still reproduces the bug.

---

## 15. Glossary

| English                          | 中文            | Note                                                                     |
|----------------------------------|---------------|--------------------------------------------------------------------------|
| IR / Intermediate Representation | IR / 中间表示     | Structured in-memory representation of a program                         |
| Differential Testing             | 差分测试          | Compiling the same program with multiple compilers and comparing results |
| Fuzzing                          | 模糊测试          | Random/semi-random input generation to trigger bugs                      |
| Printer                          | 代码生成器         | Module that converts IR to source code                                   |
| Mutator                          | 变异器           | Module that applies mutations to generated programs                      |
| Validator                        | 校验器           | Module that validates IR semantic correctness (used only in reduction)    |
| DDMin                            | DDMin         | Delta-Debugging Minimization, a program reduction algorithm              |
| Override stub                    | Override stub | An override method that retains its signature but has its body removed   |
| Platform Type                    | 平台类型          | Kotlin's mapping type for Java's type system                             |
| GED / Graph Edit Distance        | GED / 图编辑距离   | Metric for quantifying structural similarity between programs            |

---

> 📄 中文版：[ARCHITECTURE-zh.md](./ARCHITECTURE-zh.md)