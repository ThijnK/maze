# MAZE

**Generate Java tests, find counterexamples, and explore your own search ideas.**

MAZE (Multi-strategy Automated Symbolic Execution) generates JUnit tests from compiled Java classes. It combines symbolic and concrete execution with Z3 constraint solving to explore program paths, including those involving arrays, objects, and complex data structures. Use it to:

- Generate JUnit 5 or JUnit 4 tests with regression assertions.
- Find inputs that violate properties expressed as Java assertions.
- Compare search strategies, combine heuristics, or load your own implementation from a separate JAR.

[Getting started](#getting-started) · [Using MAZE](#using-maze) · [Search strategies](#search-strategies-and-heuristics) · [Command-line reference](#command-line-options) · [Research](#research-and-benchmarks)

## Getting started

MAZE can also be packaged with matching Z3 native libraries and a launcher, so
users only need Java 21. See [Linux packages](docs/distributions.md) for the archive
contents, Docker usage, and release validation. These packages are built locally
until a release publishes them.

To build from source, clone the repository and choose Docker or a local build:

```sh
git clone https://github.com/ThijnK/maze.git
cd maze
```

### Build with Docker

With Docker running and Docker Compose available, build MAZE with these two commands. The container includes Java 21, Maven, and matching Z3 4.13.3 Java bindings and native libraries, so you do not need to install or configure them separately:

```sh
docker compose build dev
docker compose run --rm dev mvn -DskipTests package
```

The first command builds the toolchain image; the second compiles and packages MAZE, skipping its tests. The executable JAR is now in `target/`.

To run the examples below inside the container, open a shell:

```sh
docker compose run --rm dev bash
```

The shell opens in `/workspace`, with your checkout mounted there. Follow [Run MAZE](#run-maze) below. See the [development guide](docs/development.md) for Docker configuration and troubleshooting.

### Build locally

#### Prerequisites

- JDK 21 or higher; the development container uses JDK 21.
- Apache Maven.
- Z3 4.13.3, with Java bindings and native libraries for the same release and architecture.

#### Installing Z3

Download the matching binary archive from the [Z3 releases](https://github.com/Z3Prover/z3/releases), such as `z3-4.13.3-x64-win.zip` for Windows x64, and extract it. Install its Java binding in your local Maven repository:

```sh
mvn install:install-file \
  -Dfile="/path/to/z3/bin/com.microsoft.z3.jar" \
  -DgroupId=com.microsoft -DartifactId=z3 -Dversion=4.13.3 \
  -Dpackaging=jar -DgeneratePom=true
```

Replace the file path with yours; on Windows, it might be `C:\Program Files\z3\bin\com.microsoft.z3.jar`. The version must match MAZE's `pom.xml` and the native libraries you will load.

The JVM must also be able to find Z3's native libraries:

- **Windows:** set `Z3_HOME` to the extracted directory and add `%Z3_HOME%\bin` to `PATH`.
- **Linux:** add the directory containing `libz3.so` and `libz3java.so` to `LD_LIBRARY_PATH`, for example `export LD_LIBRARY_PATH="/path/to/z3/bin${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"`.
- **macOS:** you may need to place the matching Z3 `.dylib` files in the directory from which you launch Java.

If Maven reports that it cannot find `com.microsoft:z3:jar:4.13.3`, check that the binding is installed in your local Maven repository under the version declared in `pom.xml`.

<details>
<summary>Building Z3 from source on Windows</summary>

If the binary distribution does not work for your setup, clone [Z3](https://github.com/Z3Prover/z3) and run these commands in its repository:

```sh
python scripts/mk_make.py --java -x
cd build
nmake
```

`-x` selects x64; omit it for x86. `nmake` and the required C++ build tools are available through Visual Studio Installer. After building, install `build/com.microsoft.z3.jar` in Maven and configure the native library location as above. Use sources matching MAZE's Z3 dependency.

</details>

#### Build the JAR

From MAZE's repository root:

```sh
mvn -DskipTests package
```

### Run MAZE

The executable is `target/maze-<version>-jar-with-dependencies.jar`. It contains MAZE and its Java dependencies; Z3's native libraries remain a runtime prerequisite. In the examples below, **`maze.jar` stands for the path to that executable JAR**.

```sh
java -jar maze.jar --help
```

A normal invocation needs a directory of compiled subject classes, a fully qualified class name, and an output directory:

```sh
java -jar maze.jar \
  --classpath subject/classes --class-name com.example.MyClass \
  --output-path generated --strategy BFS --time-budget 30
```

## Using MAZE

### Generate tests for a class

MAZE targets public methods; private methods can be exercised indirectly through them. Save this example as `MyPackage/EX0.java`. Its `isSorted()` method returns the index of the first out-of-order pair, or `-1` when the array is sorted:

```java
package MyPackage;

public class EX0 {
    private final int[] a;

    public EX0(int[] a) {
        this.a = a;
    }

    public int isSorted() {
        for (int k = 0; k < a.length - 1; k++) {
            if (a[k] > a[k + 1]) {
                return k;
            }
        }
        return -1;
    }
}
```

Compile it, then give MAZE 30 seconds to explore using breadth-first search:

```sh
mkdir -p somepath/classes somepath/tests
javac -d somepath/classes MyPackage/EX0.java
java -jar maze.jar \
  --classpath somepath/classes --class-name MyPackage.EX0 \
  --output-path somepath/tests --minimization=true \
  --strategy BFS --time-budget 30
```

This generates `EX0Test.java`. `--minimization=true` keeps tests that add coverage; `--method-name isSorted` would restrict generation to that method. Omitting `--strategy` uses DFS.

### Understand the generated tests

MAZE runs the generated inputs and adds **regression oracles**: assertions recording the observed return values or exception types. Here is the complete file generated by running the command above. Exact inputs and numbering can vary between runs:

```java
// Auto-generated by MAZE
import MyPackage.EX0;
import java.lang.Exception;
import java.lang.NullPointerException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EX0Test {
  @Test
  public void testIsSorted1() throws Exception {
    int[] carg0 = null;
    EX0 cut = new EX0(carg0);

    // This throws NullPointerException, which is actually unexpected and could be an error:
    Assertions.assertThrows(NullPointerException.class, () -> cut.isSorted());
  }

  @Test
  public void testIsSorted2() throws Exception {
    int[] carg0 = {};
    EX0 cut = new EX0(carg0);

    int retval = cut.isSorted();

    int expected = -1;
    Assertions.assertEquals(expected, retval);
  }

  @Test
  public void testIsSorted3() throws Exception {
    int[] carg0 = { 1, 0 };
    EX0 cut = new EX0(carg0);

    int retval = cut.isSorted();

    int expected = 0;
    Assertions.assertEquals(expected, retval);
  }

  @Test
  public void testIsSorted4() throws Exception {
    int[] carg0 = { -2147483647, 0 };
    EX0 cut = new EX0(carg0);

    int retval = cut.isSorted();

    int expected = -1;
    Assertions.assertEquals(expected, retval);
  }
}
```

Regression oracles help detect changes in behavior. They assume the observed behavior is correct, so inspect them before treating them as a correctness specification. To test whether the program satisfies a property you specify, see [Check a property](#check-a-property) below.

MAZE treats an exception as **expected** when the method declares it, or when it is an `IllegalArgumentException`. Expected exceptions always receive exception oracles. For unexpected exceptions:

| Setting | Generated test behavior |
| --- | --- |
| Default | Asserts the exception type and adds a warning comment; the test can pass despite a potential subject error. |
| `--propagate-unexpected-exceptions=true` | Lets the exception escape, causing the test to fail. |
| `--suppress-regression-oracles=true` | Comments out regression oracles and propagates unexpected exceptions; expected-exception oracles remain. |

### Check a property

If your goal is to check intended behavior rather than preserve existing behavior, express the property as a Java assertion. Verification mode searches for inputs that violate it and generates counterexample tests.

For example, save this as `MyPackage/EX0_Check.java` to claim that `isSorted()` always returns less than `a.length - 1` for non-null arrays:

```java
package MyPackage;

public class EX0_Check {
    public static void check(int[] a) {
        if (a != null) {
            assert new EX0(a).isSorted() < a.length - 1;
        }
    }
}
```

Compile both classes and run with Java assertions enabled:

```sh
javac -d somepath/classes MyPackage/EX0.java MyPackage/EX0_Check.java
java -ea -jar maze.jar \
  --classpath somepath/classes --class-name MyPackage.EX0_Check \
  --method-name check \
  --indirect-target MyPackage.EX0 --output-path somepath/tests \
  --minimization=true --strategy BFS --time-budget 30 \
  --verification=true
```

`--class-name` selects `EX0_Check`, and `--method-name check` explicitly selects its `check` method. Without `--method-name`, MAZE explores all public methods in the selected class; here, `check` is the only one. The name `check` has no special meaning, and verification mode does not select methods by name.

The call inside `check` exercises `EX0.isSorted()`. `--indirect-target MyPackage.EX0` additionally tracks coverage of `EX0`, and `--verification=true` enables verification, stopping after one violation by default. Set `--max-violations=5` for a different limit or `--max-violations=unlimited` to continue within the other bounds.

**`-ea` is a JVM option and must precede `-jar`.** Without it, the assertions in this example are disabled. Enable assertions when running the generated counterexample test too.

The property is false for an empty array: both `isSorted()` and `a.length - 1` are `-1`. A generated counterexample can expose it:

```java
public void testCheck11() throws Exception {
    int[] marg0 = {};
    // Unexpected AssertionError exposes the violated property.
    EX0_Check.check(marg0);
}
```

Verification mode generates tests only for discovered violations. Default generation mode also checks assertions symbolically, but produces ordinary tests as well as violation tests.

MAZE is a **bounded verification tool**. Time, depth, and other limits restrict exploration; finding no violation within those bounds does not establish that the program is bug-free.

### Read run results

Use `--export-summary=true` for a CSV of test-generation statistics. Each CLI invocation also writes `<class-name>-run-status.json`, recording its configuration, implementation identities, and `running`, `failed`, or `completed` outcome.

Search failures stop the run with a nonzero exit. Already generated tests are retained as partial output, without a normal success CSV or verification PASS. For automated comparisons, require both a zero exit and a new matching completion record; see [experiment records](docs/search-extensions.md#failures-and-experiment-records).

## How exploration works

A search strategy chooses which pending execution target to explore next. MAZE offers two execution modes, with different kinds of pending work.

### Symbolic-driven execution

This is the default. MAZE starts with symbolic states for the target methods in a shared worklist:

1. Select a state from the worklist and symbolically execute its next instruction. A branch can produce multiple successor states.
2. When a path reaches the end of a method, solve its constraints with Z3. Satisfiable constraints yield concrete inputs and a JUnit test with regression oracles.
3. Add unfinished successor states to the worklist and repeat until it is empty or a configured limit is reached.

Multiple methods are explored together. `--max-depth` bounds exploration depth, and `--time-budget` bounds runtime. With `--minimization=true`, MAZE retains tests that add coverage rather than a test for every completed path.

When code cannot be executed symbolically—for example, an unavailable library method—MAZE can execute it with concrete inputs and incorporate observed return values and side effects into the symbolic state. This combination of symbolic and concrete execution is why the approach is called *dynamic symbolic execution* (DSE).

### Concrete-driven execution

Enable this experimental mode with `--concrete-driven` or `-C`. It works one method at a time:

1. Execute the instrumented method with concrete inputs, recording a trace.
2. Replay that trace symbolically to obtain its path constraints. For a new path, generate a test and add its branch-point candidates to the worklist. For constraints `[c1, c2, c3]`, these correspond to prefixes `[c1]`, `[c1, c2]`, and `[c1, c2, c3]`.
3. Select a candidate, negate its final branch condition, and solve for inputs that may take a different path. Previously explored or unsatisfiable candidates can be discarded.
4. Repeat until the pending work or execution budget is exhausted.

The strategy selects branch candidates rather than live symbolic states. All shipped strategies except PCS can run in this mode, although their usefulness can differ; RPS was designed around symbolic execution trees.

### Library handling and limitations

By default, standard-library calls use concrete execution. Supplying library bytecode on the subject classpath can allow symbolic execution of that code, but complex library internals can also make exploration harder. Older setups used JDK 8's `rt.jar` for this; Java 9 and later use modules rather than that archive. MAZE itself still requires the JDK listed under [prerequisites](#prerequisites).

Known unsupported constructs include `invokedynamic` (used by lambdas and method references), static fields and initializers, and enums. If exploration takes too long, set a time budget, reduce maximum depth, or choose a different search strategy.

## Search strategies and heuristics

Even small programs can have many paths. Search determines which ones receive attention within the available budget. DFS may spend its time exploring loop iterations before reaching other logic; BFS can spread exploration across shallower states.

### Shipped strategies

Pass a short or long name to `--strategy`. The CLI's `--help` lists all accepted aliases.

| Short name | Long name | Selection policy |
| --- | --- | --- |
| `DFS` | `DepthFirst` | Follow a path deeply before backtracking; the default. Usually holds less pending work than BFS. |
| `BFS` | `BreadthFirst` | Explore shallower states before deeper ones. |
| `RPS` | `RandomPath` | Randomly walk an execution tree from root to leaf, naturally favoring states near the root. Inspired by [KLEE](https://www.usenix.org/legacy/events/osdi08/tech/full_papers/cadar/cadar_html/). |
| `SGS` | `SubpathGuided` | Prefer rarely encountered branch subpaths, with random tie-breaking. Inspired by [Li et al.](https://doi.org/10.1145/2544173.2509553). |
| `PCS` | `PathCoverSearch` | Prioritize states approaching uncovered path segments in the methods' high-level CFGs. Symbolic-driven only; requires `--path-length-coverage=-1` or a positive length. |
| `PS` | `Probabilistic` | Select according to scores from one or more heuristics. Defaults to uniform selection. |
| `URS` | `UniformRandom` | PS with `Uniform`, weight 1. |
| `COS` | `CoverageOptimized` | PS with `DistanceToUncovered`, `RecentCoverageDensity`, and `RecentCoverageProximity`, weights 0.6, 0.2, and 0.2. |
| `FOS` | `FeasibilityOptimized` | PS with `QueryCost` and `SmallestDepth`, weights 0.7 and 0.3. |

COS favors progress toward new coverage; FOS favors simpler constraints and shallower targets. The presets retain these fixed definitions. Use PS for a configurable combination.

### Heuristics for probabilistic search

A heuristic scores each target; PS uses these scores to form its selection distribution. Combine heuristics with `--heuristic` and give their relative weights in the same order with `--weight`:

```sh
java -jar maze.jar -c subject/classes -n com.example.MyClass -o generated \
  -s PS -u QueryCost,SmallestDepth -w 0.7,0.3 -b 30
```

| Short name | Long name | Preference |
| --- | --- | --- |
| `UH` | `Uniform` | Equal scores; random selection when used alone. |
| `DTUH` | `DistanceToUncovered` | Targets closer to uncovered code. |
| `RCDH` | `RecentCoverageDensity` | Paths that discovered more new code in a recent window. |
| `RCPH` | `RecentCoverageProximity` | Paths that discovered new code more recently. |
| `QCH` | `QueryCost` | Simpler path constraints, using estimated expression cost rather than measured solving time. |
| `SDH` / `GDH` | `SmallestDepth` / `GreatestDepth` | Shallower / deeper execution targets. |
| `SCDH` / `GCDH` | `SmallestCallDepth` / `GreatestCallDepth` | Fewer / more nested calls. |
| `SWTH` / `LWTH` | `ShortestWaitingTime` / `LongestWaitingTime` | Targets added more recently / waiting for more selection iterations. |

Depth in concrete mode describes a recorded branch snapshot, not the future path after negation. Call depth can help distinguish recursion-heavy paths. Waiting time can favor recent work or older pending work, yielding tendencies similar to depth-first or breadth-first selection.

### Interleave strategies

Supply multiple names to use MAZE's interleaved search:

```sh
java -jar maze.jar -c subject/classes -n com.example.MyClass -o generated \
  -s BFS,PS -u QueryCost,SmallestDepth -w 0.7,0.3 -b 30
```

MAZE alternates between strategies in time slices. Each maintains its own pending-work state and is notified when a peer selects a target, adding some memory and coordination overhead. Every requested occurrence creates a fresh instance, including repeated names. `IS` is a description of this composition, not a strategy name to pass on its own.

### Add your own strategy or heuristic

Build a separate JAR against MAZE's existing search abstractions and select its fully qualified Java class name:

```sh
java -jar maze.jar --plugin research.jar -s org.example.MySearch \
  -c subject/classes -n com.example.MyClass -o generated -b 30
```

External components can be combined with shipped ones. Use `--search-config` for JSON configuration, including constructor options, differently configured instances of the same strategy, and nested heuristics. Omitted optional options use defaults; supplied unknown options fail.

The [extension author guide](docs/search-extensions.md) includes complete Java examples, compile/run commands, observable engine signals, lifecycle rules, and an independent artifact check.

## Command-line options

Use `java -jar maze.jar --help` for the complete CLI and aliases. These tables cover the main controls. Long option names use lowercase kebab-case. Boolean options accept `true` or `false`; omitting the value enables the flag, so `--minimization` and `--minimization=true` are equivalent. Time values are in seconds.

### Targets and output

| Option | Default | Purpose |
| --- | --- | --- |
| `--classpath`, `-c` | Required | Directory of compiled subject classes. |
| `--class-name`, `-n` | Required | Fully qualified target class; in verification mode, the check class. |
| `--output-path`, `-o` | Required | Generated-test output directory. |
| `--method-name`, `-m` | All methods | Restrict generation to a named public method. |
| `--package-name`, `-p` | No package | Package for generated tests. |
| `--junit-version`, `-j` | `JUnit5` | Generate `JUnit4` or `JUnit5` tests. |
| `--test-timeout`, `-t` | No timeout | Timeout attached to generated tests. |
| `--minimization` | `false` | Keep tests that add instruction/branch coverage, or configured path coverage. |
| `--suppress-regression-oracles` | `false` | Comment out regression oracles; see [test interpretation](#understand-the-generated-tests). |
| `--propagate-unexpected-exceptions` | `false` | Let unexpected subject exceptions fail the generated test. |
| `--verification` | `false` | Generate only tests that expose violations. |
| `--max-violations` | `1` | Stop after this many violations; `unlimited` continues within the other bounds. Requires verification. |
| `--indirect-target` | None | Additional class whose coverage is tracked, such as the subject called by a check method. |

### Exploration and search

| Option | Default | Purpose |
| --- | --- | --- |
| `--concrete-driven`, `-C` | `false` | Use concrete-driven execution. |
| `--random-seeding` | `false` | Use random unconstrained parameter values in concrete-driven execution. |
| `--allow-field-changes-by-reflection` | `false` | Allow MAZE to change subject fields through reflection. |
| `--check-division-by-zero` | `false` | Actively search for division and remainder by zero. |
| `--time-budget`, `-b` | No budget | Search time budget. |
| `--max-depth`, `-d` | `200` | Exploration depth limit. |
| `--max-array-size` | `20` | Maximum generated array size. |
| `--constrain-fp-params-to-normal-numbers` | `false` | Restrict floating-point parameters to normal numbers. |
| `--strategy`, `-s` | `DFS` | One or more shipped names or external class names. |
| `--heuristic`, `-u` | `UH` | Heuristics for PS. |
| `--weight`, `-w` | `1.0` | Relative heuristic weights, in matching order. |
| `--plugin` | None | Extension/dependency JAR; repeat for additional JARs. |
| `--search-config` | None | Search JSON file; conflicts with explicit `-s`, `-u`, or `-w`. |
| `--path-length-coverage` | `0` | Cover elementary high-level CFG paths of length k; `-1` selects prime paths. |
| `--target-path-aging` | `-1` | Drop target paths after k iterations without coverage; `0` uses the subject's instruction count, `-1` disables aging. |

### Diagnostics and exports

| Option | Default | Purpose |
| --- | --- | --- |
| `--help`, `-h` | — | Show help. |
| `--version`, `-V` | — | Show version. |
| `--log-level`, `-l` | `INFO` | `OFF`, `INFO`, `WARN`, `ERROR`, `TRACE`, or `DEBUG`. |
| `--export-summary` | `false` | Write test-generation statistics to CSV. |
| `--export-jimple` | `none` | Export each target method's Jimple code. |
| `--export-hcfg` | `none` | Export high-level CFGs as DOT files. |
| `--export-target-paths` | `none` | Export target paths. |
| `--export-path-coverage` | `none` | Export path-coverage information. |

For the last four export options, use `none` to disable export, `file` to write files, or `log` to print to the log. These are destinations rather than booleans, for example `--export-hcfg=file`.

## Research and benchmarks

For MAZE's design and formal semantics, see [Kroon, T., *Evaluating Search Strategies in Dynamic Symbolic Execution for Java Test Generation*](https://studenttheses.uu.nl/handle/20.500.12932/49026).

### Benchmarking framework

The [MAZE fork of JUGE](https://github.com/ThijnK/JUGE) measures generation time, coverage, and mutation kill rate, and supports comparison with other test-generation tools. It is based on the [JUGE framework](https://github.com/JUnitContest/JUGE) used for the SBFT tool competitions. Setup and experiment instructions live in that repository.

The [companion JUGE integration](https://github.com/ThijnK/JUGE/blob/thijn/maze-external-search/docs/MAZE.md) accepts named experiments using a separate MAZE Linux package, external JARs, and the same search configuration documented here. It runs built-in and external strategies or heuristics through generation, coverage, mutation analysis, and aggregation in both modes. Failed or stale runs are excluded from scoring. Use that integration branch until it is merged; older adapters do not support this workflow.

### Benchmark subjects and generated tests

The original benchmark set contains 20 classes under test, covering recursion, loops, data structures, numeric operations, and other execution patterns. JUGE packages the subjects as a JAR and permits additional subjects. Their source files in [the benchmarks package](src/main/java/nl/uu/maze/benchmarks/) explain their design:

- [`AckermannPeter`](src/main/java/nl/uu/maze/benchmarks/AckermannPeter.java): Implementation of the Ackermann-Peter function.
- [`BinarySearch`](src/main/java/nl/uu/maze/benchmarks/BinarySearch.java): Implementation of a binary search algorithm on an integer array.
- [`ConvergingPaths`](src/main/java/nl/uu/maze/benchmarks/ConvergingPaths.java): Class where control flow paths repeatedly diverge and converge.
- [`ExprEvaluator`](src/main/java/nl/uu/maze/benchmarks/ExprEvaluator.java): Evaluates simple arithmetic expressions in an array of characters (i.e., a string), using recursive descent parsing.
- [`FloatStatistics`](src/main/java/nl/uu/maze/benchmarks/FloatStatistics.java): Provides methods for statistics and functions on floating-point numbers (e.g., mean, sqrt, etc.).
- [`MatrixAnalyzer`](src/main/java/nl/uu/maze/benchmarks/MatrixAnalyzer.java): Performs operations on a 2D integer array.
- [`NestedLoops`](src/main/java/nl/uu/maze/benchmarks/NestedLoops.java): Sorts an array with bubble sort while at the same time calculating a specific value dependent on the array's contents.
- [`QuickSort`](src/main/java/nl/uu/maze/benchmarks/QuickSort.java): Implementation of the quicksort algorithm on an integer array.
- [`SinglyLinkedList`](src/main/java/nl/uu/maze/benchmarks/SinglyLinkedList.java): Implements a singly linked list with various operations (e.g., add, delete, etc.).
- [`TriangleClassifier`](src/main/java/nl/uu/maze/benchmarks/TriangleClassifier.java): Classifies a triangle based on its sides (e.g., equilateral, isosceles, etc.), with functions for integer, floating-point, and double precision inputs.
- [`BinaryTree`](src/main/java/nl/uu/maze/benchmarks/BinaryTree.java): Provides a binary tree implementation and various traversal and utility methods (e.g., in-order, pre-order, post-order traversal, height calculation, finding certain values).
- [`BitwiseManipulator`](src/main/java/nl/uu/maze/benchmarks/BitwiseManipulator.java): Class that performs various bitwise operations on integers.
- [`BracketBalancer`](src/main/java/nl/uu/maze/benchmarks/BracketBalancer.java): Class that checks whether a string of brackets (represented as an array of characters) is balanced.
- [`ConnectedComponents`](src/main/java/nl/uu/maze/benchmarks/ConnectedComponents.java): Calculates the number of connected components and detects components with cycles of a given length in a graph represented as an adjacency matrix.
- [`Dijkstra`](src/main/java/nl/uu/maze/benchmarks/Dijkstra.java): Implements Dijkstra's algorithm to find the shortest path in a graph represented as an adjacency matrix, as well as a DFS traversal method to check whether a particular node is reachable from another node.
- [`GraphTraversal`](src/main/java/nl/uu/maze/benchmarks/GraphTraversal.java): Implements DFS and BFS graph traversal algorithms on a graph represented as an adjacency matrix. The DFS algorithm is used by the `ConnectedComponents` class.
- [`HeapSort`](src/main/java/nl/uu/maze/benchmarks/HeapSort.java): Implementation of the heap sort algorithm on an array of floating-point numbers.
- [`IntUtils`](src/main/java/nl/uu/maze/benchmarks/IntUtils.java): Class that provides various utility methods for integers, such as calculating the GCD, LCM, and factorial.
- [`StringPatternMatcher`](src/main/java/nl/uu/maze/benchmarks/StringPatternMatcher.java): Implements a simple string pattern matching algorithm based on regex-like syntax.
- [`StringUtils`](src/main/java/nl/uu/maze/benchmarks/StringUtils.java): Class that provides various utility methods for strings, such as reversing a string, checking for palindromes, and finding really specific substrings (e.g., alternating digits and letters).

[Sample generated tests](src/test/java/nl/uu/tests/maze/generated/benchmarks/) for these subjects were produced using BFS with a 30-second budget. The reported results for that sample are 90% instruction coverage and 85% branch coverage; these are results for that benchmark set, not a coverage guarantee for arbitrary programs.

## Working on MAZE

The [development guide](docs/development.md) covers building and testing in Docker, including checks of external strategies and heuristics against the packaged JAR.

### Run the tests

These checks are for development and are not required before using MAZE:

```sh
docker compose run --rm dev mvn clean verify
docker compose run --rm dev sh docker/smoke/verify.sh \
  target/maze-1.1.1-jar-with-dependencies.jar
```

`verify` builds, packages, and tests MAZE. The smoke check then uses the packaged JAR to generate tests in both execution modes, compiles those tests, and runs them. For a local build, run `mvn clean verify` directly.

### Run from Maven or Eclipse

To run the application through Maven from the repository root:

```sh
mvn exec:java -Dexec.args="--help"
```

In Eclipse, import the checkout through **File → Import → Maven → Existing Maven Projects**. The entry point is `nl.uu.maze.main.Application`.

### Project structure

- `nl.uu.maze.main`: Application entry point and command-line interface
- `nl.uu.maze.analysis`: Java program analysis utilities
- `nl.uu.maze.execution`: Core DSE execution engine
  - `nl.uu.maze.execution.concrete`: Concrete execution components
  - `nl.uu.maze.execution.symbolic`: Symbolic execution components
- `nl.uu.maze.generation`: Test case generation
- `nl.uu.maze.instrument`: Bytecode instrumentation
- `nl.uu.maze.search`: Search strategies and heuristics
- `nl.uu.maze.transform`: Transformers between Java, Z3, and Jimple (SootUp IR)
- `nl.uu.maze.util`: Utility classes
- `nl.uu.maze.examples`: Example classes for testing and demonstration purposes
- `nl.uu.maze.benchmarks`: Benchmark classes for evaluating and comparing search strategies

### Dependencies

- [SootUp](https://soot-oss.github.io/SootUp/latest/) for Java bytecode analysis and transformation.
- [Z3 Theorem Prover](https://github.com/Z3Prover/z3) for constraint solving.
- [ASM](https://asm.ow2.io/) for bytecode manipulation.
- [JavaPoet](https://github.com/square/javapoet) for Java source code generation.
- [Logback](https://logback.qos.ch/) for logging.
- [JUnit 5](https://junit.org/junit5/) for testing.
- [Picocli](https://picocli.info/) for command-line argument parsing.
- Jackson for strict search-configuration parsing and experiment records.

## License

This project is licensed under the [MIT license](LICENSE).
