# MAZE

MAZE (Multi-strategy Automated Symbolic Execution) is a **dynamic symbolic execution (DSE)** engine to do **automated testing** of Java programs. It can generate JUnit 5 (or JUnit 4) test cases and symbolically verify assertions. The engine analyzes JVM bytecode and uses a combination of symbolic and concrete execution to explore program paths and generate test cases that aim to maximize code coverage.
It supports various search strategies and can handle complex data structures, including arrays and objects.
Constraint solving is powered by the Z3 theorem prover.

In the default-mode MAZE generates test cases for a target Class Under Test (CUT) or a specific target method. The generated test cases also assert regression oracles. In the _verification-mode_ MAZE can target 'check-methods' that contain assertions. Along every program path explored by MAZE, assertions are checked _symbolically_.


### Example

Here is a simple example, of a class named `EX0`. Generally, a Java class may contain multiple methods. For simplicity, in this example `EX0` has just a single public method named `isSorted`:

```java
package MyPackage;
class EX0{
   int[] a ;
   public EX0(int[] a){ this.a = a ; }
   public int isSorted(){
     for (int k=0; k<a.length-1; k++) if (a[k]>a[k+1]) return k ;      
     return -1 ; }
}  
```

##### Generating tests

To generate tests for the example class we can do:

`> java -ea -jar maze.jar --classpath=somepath/classes --classname=MyPackage.EX0 --output-path=somepath/tests --minimalistic-suite=true -s=BFS -b=30`

Note: instead of `maze.jar`, distributed jar may be named `maze-<version>-jar-with-dependencies.jar`.

The above will generate a class named `EX0Test.java`, containing JUnit test cases targeting all the public methods of `EX0`. The given time budget is 30 seconds, and the used search strategy is Breadth First Search (BFS). If the -s option is removed, the default search strategy is Depth First Search (DFS). MAZE offers various search strategies --see the section _Search Strategies and Heuristics_.

The generated test cases look like as shown below. Notice that regression oracles are also generated.

```java
import MyPackage.EX0;
...
import org.junit.jupiter.api.Test;

public class EX0Test {
  @Test
  public void testIsSorted1() throws Exception {
    int[] carg0 = null;
    EX0 cut = new EX0(carg0);
    // This throws NullPointerException, which is actually unexpected and could be an error:
    Assertions.assertThrows(NullPointerException.class, () -> cut.isSorted());
  }
  ...
  @Test
  public void testIsSorted3() throws Exception {
    int[] carg0 = { 1, 0, 1, 1, 1, 1, 1, 1, 1 };
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
Current MAZE implementation targets public methods of the CUT. So, it does not generate tests for private methods, though private methods are indirectly tested if they are invoked by some public methods.

##### Verification mode

We can also verify assertions. To do this we write so-called _check-methods_. An example is shown below, to check that the value returned by `isSorted()` is always less than the length of the array `a` minus one:


```java
package MyPackage ;
class EX0_Check{
   public static void check(int[] a){
     if (a != null) assert new EX0(a).isSorted() < a.length-1 ; }
}  
```
To verify the property we can do:

`> java -ea -jar maze.jar --classpath=somepath/classes --classname=MyPackage.EX0_Check --indirectTarget=MyPackage.EX0 --output-path=somepath/tests --minimalistic-suite=true -s=BFS -b=30 --verificationMode=1`

The option `classname` specifies the target class, in this case it is the class containing check-methods. The `indirectTarget` option specifies that actual CUT; so, the class `EX0`. The option `verificationMode` enables the verification mode. This will cause the assertions in the the target check-methods to be symbolically verified along every program path that MAZE explores (and pass the assertions).

**Note:** don't forget to turn out Java `-ea` option to enable Java's own assertion checking.

If an assertion is violated, MAZE will report this, and a JUnit test  exposing the violation will be generated. If no violation is found, MAZE will also report this, though in this case no JUnit test will be generated. To be more precise, by virtue of its symbolic execution, even in the default testing mode MAZE also verifies assertions symbolically. The verification-mode differs that it only generates violation-exposing tests and it can target check-methods. In the testing-mode MAZE generates ordinary tests as well as violation-exposing tests, if there are any; the latter are marked with comments.

In the above example, the assertion is actually _NOT valid_. MAZE would find the violating case and generate a test like shown below, showing that that thing goes wrong when the array `a` is empty.

```java
public void testCheck11() throws Exception {
  int[] marg0 = {};
  // This throws AssertionError, which is unexpected:
  EX0_Check.check(marg0);
}
```

Do keep in mind that MAZE is a _bounded_ verification tool. It operates within various bounding. E.g. you specify what the allowed search budget. So, when it does not report any assertion violation, it does not necessarily mean that the CUT is bug free.

### Papers

In depth paper about MAZE, including its formal semantics, can be found here: [Kroon, T., _Evaluating Search Strategies in Dynamic Symbolic Execution for Java Test Generation_](https://studenttheses.uu.nl/handle/20.500.12932/49026).

## ▊▎ Getting Started

### ● Prerequisites

Before you begin, ensure you have the following software installed on your machine:

- Java Development Kit (JDK) 21 or higher
- Apache Maven (MAZE is organized as a Maven project, so you need Maven to build it)
- Z3 theorem prover (see [Installing Z3](#installing-z3))

#### Installing Z3

This might be a bit more involved. Z3 is a theorem prover developed by Microsoft, which is used by MAZE to solve constraints generated by its symbolic execution engine.

The easiest way to install it is to do binary install.
Start by downloading the native distribution for your platform from the [Z3 GitHub releases](https://github.com/Z3Prover/z3/releases) page, for example `z3-4.13.3-x64-win.zip` for Windows x64.
Extract the contents of the zip file to a directory of your choice, for example `C:\Program Files\z3`.

As MAZE is built with Maven, we now need to make Maven knows where Z3 jar is. We will do this by installing Z3 jar into yor local Maven repository, like this:

```bash
mvn install:install-file -Dfile="C:\Program Files\z3\bin\com.microsoft.z3.jar" -DgroupId=com.microsoft -DartifactId=z3 -Dversion=4.13.3 -Dpackaging=jar -DgeneratePom=true
```

Replace the path and version number in the command above with the correct values for your system and the version of Z3 you downloaded.

Additionally, when run, MAZE needs to load Z3 dynamic libraries. These are the files z3*.dylib in the bin-folder of Z3. JVM needs to know where to find these. How to do this depends on your OS:

  * **Windows:**
   Go to your system environment variables and set a variable `Z3_HOME` to the path where you extracted the zip file, for example `C:\Program Files\z3`.
   Add `%Z3_HOME%\bin` to your system `PATH` variable.

  * **Linux:** Append your-z3-root/bin to the environment variable `DYLD_LIBRARY_PATH`. If that variable is not defined yet, you can just set it to point to the path e.g. `export DYLD_LIBRARY_PATH=your-z3-root/bin`.

  * **MacOS:** unfortunately MacOS' System Integrity Protocol prevents the DYLD_LIBRARY_PATH to be passed to subprocesses, so the above Linux way does not work for MacOS. I have not found a way to get around this other than by copying z3*.dylib files from Z3 root to MAZE project root.


### ● Building the Project

 Assuming you already installed Maven and Z3.

Clone the repository of MAZE. Go to its project root (local) and build the project using Maven:

```bash
git clone https://github.com/ThijnK/maze.git
cd maze
mvn -DskipTests clean package
```

This will build MAZE jar files. You can find them in `./target`. The jar named `maze-<version>-jar-with-dependencies.jar` contains MAZE along with all its dependencies. This is the jar you use to run MAZE.


### ● Running MAZE

To run MAZE from the command line, use Java, passing to it MAZE's JAR file:

```bash
java -jar maze.jar --help
```
This will display the help message with available options and arguments.
For an overviiew of the command-line options, see [Command-Line Options](#command-line-options).

Note: instead of maze.jar, built/distributed jar may be named `maze-<version>-jar-with-dependencies.jar`.

For example, to run the application on a specific Java class located in the `./target/classes` directory using BFS (rather than the default DFS), use the following command:

```bash
java -ea -jar maze.jar --classPath=./target/classes --className=com.example.MyClass ---output-path=tests --strategy=BFS
```

You can also run MAZE using Maven, using the following Maven command from MAZE project homedir:

```bash
mvn exec:java -Dexec.args="--help"
```



### ● Working on MAZE in Eclipse

You can import MAZE project to Eclipse. Use File>import>Maven>Existing Maven Projects.

The Main is the class  `nl.uu.maze.main.Application`.


### ● Building Z3 from source, if needed

It is possible that Z3 will not work after installing it the above way, in which case your best bet is to build Z3 from source as described below.

Clone or download the Z3 repository from https://github.com/Z3Prover/z3, and run the following command in the Z3 repo:

```bash
python scripts/mk_make.py --java -x
```

If you need to build for x86 instead of x64, leave out the `-x` flag.
Now run the following commands to build the java bindings:

```bash
cd build
nmake
```

If you do not have `nmake`, install it using Visual Studio Installer.
You'll also need some other C++ build tools, which you can install using the Visual Studio Installer as well.

After building the java bindings, the `build` directory should contain the files needed to run Z3, including the `com.microsoft.z3.jar` file.
Set the environment variables and install into your local maven repository as described above.


## ▊▎ MAZE Command-Line Options

| Option              | Alias | Description                                                                | Required | Default     |
| ------------------- | ----- | -------------------------------------------------------------------------- | -------- | ----------- |
| `--help`            | `-h`  | Show help message                                                          | No       | -           |
| `--version`         | `-V`  | Show version information                                                   | No       | -           |
| `--classpath`       | `-c`  | Path to compiled classes                                                   | Yes      | -           |
| `--classname`       | `-n`  | Fully qualified name of the class under test (CUT) to generate tests for. In the verification-mode this specifies the name of a class containing check-methods                   | Yes      | -           |
| `--method-name`     | `-m`  | If specified, the name of the target method to generate tests for                                   | No       | All methods |
| `--output-path`     | `-o`  | Output path to write generated test files to                               | Yes      | -           |
| `--verificationMode` |  |  If it is some non-zero, this will turn on the verification-mode. MAZE stops after k violations. If k is -1 it will only stop after the time budget runs out. | No | 0 |
| --error-type-to-find |  | Only relevant if verification-mode is on. This specifies the error type to search. Two types are available: AssertionError, to find assert violation. UnexpectedException, to find uncaught exception which is NOT assert violation. If not set (default), then any type of uncaught exception is searched. | No | "any" |
| `--indirectTarget` |  | In the verification-mode, this specifies the fully qualified name of the actual CUT | No | - |
| `--minimalistic-suite` |  | If true, only tests that add new coverage are generated. Unless the `--path-length-cov` option is set, this looks at instruction and branch coverage. | No | `false` |
| `--path-length-cov` |  | If it is some non-zero k, MAZE will track coverage over elementary path of length k. If k is -1, MAZE will track coverage over prime paths. This option refers to paths over high level control flow graphs (CFG) of CUT's methods. | No | 0 |
| `--package-name`    | `-p`  | Package name to use for generated test files                               | No       | No package  |
| `--time-budget`     | `-b`  | Time budget for the engine (in seconds)                                    | No       | No budget   |
| `--strategy`        | `-s`  | One or multiple of the search strategies to use                            | No       | `DFS`       |
| `--heuristic`       | `-u`  | One or multiple of the search heuristics to use (for probabilistic search) | No       | `Uniform`   |
| `--weight`          | `-w`  | Weights for the provided heuristics                                        | No       | `1.0`       |
| `--max-depth`       | `-d`  | Maximum depth of the search                                                | No       | `200`       |
| `--test-timeout`    | `-t`  | Timeout to apply to generated test cases (in seconds)                      | No       | No timeout  |
| `--max-array-size` | | Specify the maximum size of arrays generated by MAZE | No | 20 |
| `--junit-version`   | `-j`  | JUnit version to target for generated test cases (JUnit4, JUnit5)          | No       | `JUnit5`    |
| `--concrete-driven` | `-C`  | Use concrete-driven DSE instead of symbolic-driven                         | No       | `false`     |
| `--constrain-FP-params-to-normal-numbers` | | Constrain the symbolic solver to generate normal numbers for floating-point-like methods parameters | No | `false` |
| `--surpress-regression-oracles` | | Generated regression oracles will be commented out | No | `false` |
| `--propagate-unexpected-exceptions` | | When a test throws an exception that is not declared as expected exception, it will be propagated | | `false` |
| `--log-level`       | `-l`  | Log level (OFF, INFO, WARN, ERROR, TRACE, DEBUG)                           | No       | `INFO`      |
| `--target-path-aging` |  |  If the option `--path-length-cov` is set, this option can be set to some non-negative k. It will cause a target path (to cover) to be deemed as unfeasible (and hence dropped) if it is not covered after k iterations of the symbolic engine. If k=0, the number of instructions in the CUT is used as k. | No | -1 |
| `--export-jimple` |  | If 1, will export the Jimple code of every target method to a file. If -1 will print it to log.info | No | 0 |
| `--export-HCFG` |  | If 1, will export the high-level CFG of every target method to a dot-file. If -1 will print it to log info |  No | 0 |
| `--export-target-paths` |  |  If 1, will export the target paths of every target method to a file. If -1 will print them to log info | No | 0 |
| `--export-pathcov` |  |  If 1, will export path coverage info to a file. If -1 will print it to log info | No | 0 |
| `--export-summary` |  | If true, will export basic test statistics to a csv file. |  |  `false` |

## ▊▎MAZE Symbolic Execution

As mentioned, MAZE explores the CUT's program paths. MAZE's engine executes the CUT _symbolically_ from the start, and follows every path through the program simultaneously.
Once the end of a path is reached, the engine will solve the path constraints and, if the constraints are satisfiable, generate a test case for that path.
If minimalization is turned on, only program paths that give new coverage will be turned into actual JUnit tests.
The engine will however use concrete execution for situations where it cannot symbolically execute the program, for example when the program calls a method F(x) whose code is not available (e.g. a library method). The engine will then execute F with a concrete input, and approximate the behavior of the method by observing its return value and side effects, and embedding these into the current symbolic state. Because of this mixing of symbolic and concrete execution, MAZE's approach falls into the category of dynamic symbolic execution (DSE).

The following two modes of DSE is available:


- **Symbolic-driven DSE**: this is the default behavior of MAZE engine, and works as described above.

- **Concrete-driven DSE**:
  The engine instruments the class under test (CUT) in such a way that executing it will record a trace which can be reused to replay that execution symbolically.
  The engine explores program paths by first executing the instrumented CUT with concrete inputs, and then replays the recorded trace symbolically to obtain the path constraints corresponding to the executed path.
  By negating constraints from the previous path, and solving the resulting set of constraints, the engine can derive concrete inputs that explore (potentially) new paths.
  This process continues until no more unexplored paths (up to the maximum depth) are found.
  In concrete-driven DSE, the search space consists of the branches of previously executed paths.

  This mode is still experimental. It is enabled by the `--concreteDriven` (or `-C`) option.


##### Symbolic Execution Access to Java Standard Library

Well, a bit complicated.
It's possible to give MAZE symbolic execution access to Java standard library classes by adding the path to the `rt.jar` file of your JDK to the classpath.
**However**, that that file is only available up to JDK 8.
In JDK 9 and later, the standard library is modularized and the classes are no longer in a single jar file.
By providing this jar file in the classpath, the engine will be able to symbolically execute standard library classes.
However, this does not necessarily lead to better test generation, since the standard library code can be quite complex, so it may just end up causing the engine to run into an issue and not being able to complete a path.
Therefore, it is generally _not_ recommended to do this.
By default, the tool will execute standard library classes with concrete inputs.


## ▊▎Search Strategies and Heuristics

Even a simple program can generate a huge amount of program paths. A _search strategy_ basically specifies an order with which we explore the paths so that it is more likely that MAZE can explore 'relevant' paths (paths that give new coverage, or expose violations) within the given time budget. MAZE comes with an array of search strategies (and heuristics).

Consider a target class C; for simplicity imagine it only has two public methods, m1(x) and m2(y), and both are static methods. MAZE exploration starts with the symbolic initial state of m1 and that of m2 placed in a work-list W.

   1. The search proceeds by taking out one symbolic state S from W.
   1. The program instruction _stmt_ that is enabled on the state S is symbolically executed to yield one or more next symbolic states (you get multiple next states, if _stmt_ was a branching instruction). If S was a state from m1, _stmt_ would also be the current instruction from m1.
   1. If a next state T is 'final': it reaches the end of a method (e.g. of m1), the execution leading to T was a full execution. The path constraints leading to the state (which was tracked during the search) is solved using a backend SMT solver to produce concrete inputs x for m1. This yields a test for m1, namely m1(x). The corresponding JUnit test-method will then be generated, and MAZE will also add regression oracle to the generated test.
   1. The remaining (non-final) next states are added W.
   1. Those steps are repeated until W becomes empty, or execution budget (`-b` option) is exhausted.   This symbolic states exploration is also bounded by a maximum depth specified by the `-d` option.

MAZE defines a _search strategy_ as a policy in selecting which state from the work-list is to be selected next (step-1 above) for exploration. Using the `--strategy` option you can set a specific search to use. The default is DFS (depth first search), which would work well for programs without a loop. It may not be the best search strategy for a program with some logic followed by a loop, as DSF will first explore different ways to iterate the loop, before it explores different ways to go through the logic that precedes the loop.

MAZE supports the following search strategies:

- **Depth-First Search (DFS)**:
  Explores paths by going as deep as possible before backtracking.
  DFS is memory-efficient compared to breadth-first approaches and can quickly find solutions that are deep in the execution tree.
- **Breadth-First Search (BFS)**:
  Explores all branches at the current depth before moving deeper.
  This approach guarantees finding the shortest path to a target state, which can be valuable when looking for minimal test cases or when path length directly impacts solving performance.
- **Subpath-Guided Search (SGS)**:
  Tracks frequency of execution subpaths and prioritizes states with rarely seen patterns.
  This drives exploration toward less-visited code regions.
  This strategy is inspired by the work of [Li et al.](https://doi.org/10.1145/2544173.2509553).
- **Random Path Search (RPS)**:
  Maintains an execution tree and selects paths by randomly walking from root to leaf.
  Designed specifically for symbolic-driven execution, it naturally favors states closer to the root, keeping path conditions shorter and easier for constraint solvers to handle compared to pure random search.
  This strategy is inspired by the work of [Cadar et al.](https://www.usenix.org/legacy/events/osdi08/tech/full_papers/cadar/cadar_html/) in their tool KLEE.
- **Path Covering Search (PCS)**: prioritize symbolic states that would lead to still uncovered elementary path-segment of length k. The k is set by the option `--path-length-cov`, and it refers to paths on the high level control flow graphs (CFGs) of CUT's methods.
- **Probabilistic Search (PS)**:
  Selects states based on a weighted probability distribution calculated from one or multiple so-called **search heuristics** (see [Search Heuristics](#search-heuristics) below).
  By combining multiple heuristics and playing around with their weights, you have the potential to create a wide variety of search strategies.
  Different heuristics can complement each other, allowing for a more nuanced evaluation of states.
- **Interleaved Search (IS)**:
  Alternates between multiple search strategies using a round-robin approach.
  This can help to prevent any single strategy from getting stuck in unproductive regions of the search space.
  Note, however, that using multiple search strategies may introduce some overhead, as each strategy will keep track of its own state.
  When MAZE is instructed to run with multiple search strategies, it will automatically use interleaved search.

The engine also provides search strategies obtained from predefined instances of the above mentioned probabilistic search (PS) based on specific heuristics:

- **Uniform Random Search (URS)**:
  this is PS with uniform distribution, effectively creating a random search.
  This is useful as a baseline or interleaved with other strategies to introduce some randomness.
- **Coverage Optimized Search (COS)**:
  Based on KLEE's coverage-optimized search strategy, which is based on the distance to an uncovered instruction, the call stack of the state, and whether it recently covered new code.
  In MAZE, this is translated to an instance of PS with the `DistanceToUnocvered`, `RecentCoverage`, and `SmallestCallDepth` heuristics.
  The strategy is designed to maximize code coverage by focusing on unexplored regions of the program.
- **Feasibility Optimized Search (FOS)**:
  Strategy designed to prioritize states that are most feasible to solve (in reasonable time).
  This is achieved by creating an instance of PS using the `QueryCost` and `WaitingTime` as heuristics, the former to prefer states with simpler path constraints and the latter to prefer states that have been waiting in the queue for a long time (similar to a breadth-first search, thus avoiding deep states, which are more likely to be harder to solve and are thus less feasible).

#### Search Heuristics

Search heuristics are used to determine the probability distribution for the probabilistic search (PS) mentioned above.
MAZE supports the following search heuristics:

  - **Uniform**:
    Assigns the same weight to every target, effectively creating a random search when used in isolation (no other heuristics).
    Useful as a baseline or in combination with other heuristics to introduce some randomness.
  - **Depth**:
    Assigns weights based on the depth of a target in the control flow graph, allowing a preference for deeper targets (or the opposite, to prefer shallower targets).
    Less effective for concrete-driven DSE since target depths aren't known at the time of negating a path constraint.
  - **Call Depth**:
    Assigns weights based on the call depth of a target, allowing a preference for deeply nested function calls (or the opposite, to prefer states which have not called a function).
    This may be useful to prevent the search from being dominated by recursive-heavy code, potentially leading to broader coverage of the program because recursion is unlikely to cover new code and is often expensive to solve.
  - **Distance To Uncovered**:
    Assigns weights based on how close a state is to reaching uncovered code.
    Targets that are fewer steps away from uncovered statements receive higher priority, guiding the search toward unexplored regions of the program.
  - **Recent Coverage**:
    Prioritizes targets that have recently discovered new code, focusing on "hot" exploration paths.
    This helps concentrate resources on targets that are actively expanding coverage rather than those that may have stagnated.
  - **Query Cost**:
    Favors targets with simpler path constraints that are (expected to be) cheaper to solve.
    Path constraint cost is estimated based on the complexity of boolean expressions and their argument types (with floating point operations generally more expensive than integer operations, for example).
    This helps avoid spending excessive time on targets with expensive solver queries.
  - **Waiting Time**:
    Assigns weights based on how long a target has been waiting in the queue since being added to the search strategy.
    The waiting time is based on the iterations, so the number of times the target was not selected for execution in the search strategy.
    This heuristic can be configured to prefer either long-waiting targets or short-waiting targets, depending on the desired behavior.
    Preferring long-waiting targets would result in behavior similar to a breadth-first search, while preferring short-waiting targets would result in behavior similar to a depth-first search.

#### Setting strategies and heuristics

Implemented search strategies (which you can pass as your option for ``--strategy`):

* `DepthFirst` or `DFS`: the aforementioned depth first search strategy.
* `BreadthFirst` or `BFS`: breadth first search strategy.
* `RandomPath` or `RPS`: random selection strategy.
* `Probabilistic` or `PS`: probabilistic search strategy, using one or more heuristics. The heuristics are set using the `--heuristic`. If multiple heuristics are used, their weight are specified using the `--weight`, in the same order. More on this is covered below.
* `SubpathGuided` or `SGS`: subpath guided search strategy.
* `UniformRandom` or `URS`: PS with uniform selection as the heuristic (UH).
* `CoverageOptimized` or `COS`: PS with three heuristics: DistanceToUncovered, RecentCoverageDensity, and RecentCoverageProximity.
* `FeasibilityOptimized` or `FOS`: PS with two heuristics: QueryCost and SmallestDepth.
* `RandomPath` or `RPS`
* `PCS`: path-covering search.

Available heuristics for `PS`:

* `Uniform` or `UH`
* `DistanceToUncovered` or `DTUH`
* `RecentCoverage` or `RCH`
* `QueryCost` or `QCH`
* `SmallestDepth` or `SDH`
* `GreatestDepth` or `GDH`
* `SmallestCallDepth` or `SCDH`
* `GreatestCallDepth` or `GCDH`
* `ShortestWaitingTime` or `SWTH`
* `LongestWaitingTime` or `LWTH`

#### Search strategies for concrete driven DSE

All the above mentioned strategies, except `PCS`, can also ne used when we run MAZE with its concrete-driven DSE mode, though some are more suited for one than the other (e.g., RPS is only really useful for symbolic-driven DSE).

The concrete driven search works one method at a time (whereas the default symbolic driven search simultaneously targets all methods in the CUT). Imagine it starts with m1. As before we will be working with a work-list W. However, rather than adding symbolic states to W, we will be adding path constraints to W. The concrete driven search starts by generating a concrete input x for m1. Then:

   1. It concretely executes m1(x). The execution is instrumented to construct the symbolic path constraint S passed by the execution. Suppose this constraint is a list P = [c1,c2,c3] that corresponds to three branch conditions, in the order of appearance, encountered during the execution m1(x).

   1. If P has been encountered before, we skip forward to step-3. Else, generate m1(x) as a new JUnit test method. Add regression oracle to it. We also add prefixes of P: [c1], [c1,c2], and [c1,c2,c3] to W.

   1. We take out a path-constraint p from W. A new path constrain q is constructed by negating the last condition in p (so q corresponds to an execution that follows p, but at the last decision point q takes a different decision). An SMT solver is used to solve q to produce a new concrete input x for m1.

   1. Those steps are repeated until W becomes empty, or execution budget (`-b` option) is exhausted.  

Similar to the default symbolic driven exploration, a search strategy determines the order with which a search target is popped from the worklist W (in step-1).

## ▊▎Test Oracles

By default MAZE also adds oracles in the test cases it generates. Note however that these are _regression oracles_. For example when a test case invokes a method _m(x)_ and it returns a value 0, an oracles that asserts this is added. This of course assumes that 0 is a correct return value of m for that test case. Generally, regression oracles _assume_ the Class Under Test (CUT) to be correct. These oracles are useful to check if future modifications on the CUT keep the functionality of the CUT unchanged.

You can use regression oracles to check the intrinsic correctness of the CUT, but you have to inspect them first, and manually validate them that they are correct.

Two types of oracles are generated:

   * _Return-value-oracles_:  asserting the value returned by the methods tested by the test cases.
   * _Exception-oracles_: if a method throws an exception, an oracle asserting the type of the thrown exception is 'also generated'. More on this is said below.

#### Supressing oracles generation

Use the option `--surpress-regression-oracles=true`. Though, oracles asserting expected exceptions are always generated.

#### Exception-oracles

When a method m(x) throws an exception, we call it an _expected exception_ if:

  * m itself declares that it can throw such an exception (this is declared in m's header e.g. as in `m(x) throws IOExpcetion`).
  * or, the thrown exception is an instance of `IllegalArgumentException`, which by intent means that m has been called with argument/s that are not allowed.

The thrown exception is _unexpected_ if it is not expected.

_Oracles for expected exceptions are always generated_. So, suppose a test case _tc_ targets a method m(x). So, _tc_ invokes _m_ with some arguments. Suppose _m_ throws an expected exception. An oracle asserting that this will happen is always added into _tc_. In other words, MAZE consider the exception as expected behavior, and is not an error.

Thrown _unexpected_ exception may signal an error, so it makes sense that a test case propagates such as exception rather than asserting it (as an oracle) as an expected behavior.

Whether or not unexpected exceptions are propagated is controlled by the options `--propagate-unexpected-exceptions` and `--surpress-regression-oracles`.

   * If either `--propagate-unexpected-exceptions=true` or `--surpress-regression-oracles=true`, test cases will propagate unexpected exceptions. So, when you run the tests, the tests will fail.

   * Otherwise, thrown unexpected exceptions will be turned into oracles, as if they are expected behavior. So, when you run the tests, the tests won't fail. Comments will be added to warn you.


## ▊▎MAZE Project Structure

The project is organized into the following main packages:

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

## ▊▎Benchmarking Frameworks

Two accompanying benchmarking frameworks for MAZE are provided
to measure performance (e.g. time to generate tests, code coverage, error detection).

### BM Framework-1: Unit Testing Generation

The benchmarking framework is provided [here](https://github.com/ThijnK/JUGE) to measure performance (e.g. time to generate tests, code coverage, and mutation kill rate). The framework can be used to study the performance of MAZE search strategies, also to compare them to other testing tools. Developers implementing new search strategies may want to use this framework. The framework is a fork of the [JUGE](https://github.com/JUnitContest/JUGE) benchmarking framework, which is designed for evaluating Java unit-test generation tools for the SBFT tool competitions. The fork is specifically set up to benchmark MAZE.
Further instructions and details on the benchmarking process can be found [there](https://github.com/ThijnK/JUGE).


##### MAZE Benchmark Set

The benchmark framework includes a set of 20 CUTs, listed below, that we used to benchmark MAZE. The benchmarking framework itself allows you to add more CUTs as targets.

The source code of these 20 subjects can be inspected  in the [`nl.uu.maze.benchmarks`](/src/main/java/nl/uu/maze/benchmarks/) package (in the benchmarking framework they are put as a jar). They were used to compare different search strategies.
These classes are designed to test the engine's capabilities and performance across various scenarios.
More information about the reasoning behind the design of each subject can be found in their respective source files.

- [`AckermannPeter`](/src/main/java/nl/uu/maze/benchmarks/AckermannPeter.java): Implementation of the Ackermann-Peter function.
- [`BinarySearch`](/src/main/java/nl/uu/maze/benchmarks/BinarySearch.java): Implementation of a binary search algorithm on an integer array.
- [`ConvergingPaths`](/src/main/java/nl/uu/maze/benchmarks/ConvergingPaths.java): Class where control flow paths repeatedly diverge and converge.
- [`ExprEvaluator`](/src/main/java/nl/uu/maze/benchmarks/ExprEvaluator.java): Evaluates simple arithmetic expressions in an array of characters (i.e., a string), using recursive descent parsing.
- [`FloatStatistics`](/src/main/java/nl/uu/maze/benchmarks/FloatStatistics.java): Provides methods for statistics and functions on floating-point numbers (e.g., mean, sqrt, etc.).
- [`MatrixAnalyzer`](/src/main/java/nl/uu/maze/benchmarks/MatrixAnalyzer.java): Performs operations on a 2D integer array.
- [`NestedLoops`](/src/main/java/nl/uu/maze/benchmarks/NestedLoops.java): Sorts an array with bubble sort while at the same time calculating a specific value dependent on the array's contents.
- [`QuickSort`](/src/main/java/nl/uu/maze/benchmarks/QuickSort.java): Implementation of the quicksort algorithm on an integer array.
- [`SinglyLinkedList`](/src/main/java/nl/uu/maze/benchmarks/SinglyLinkedList.java): Implements a singly linked list with various operations (e.g., add, delete, etc.).
- [`TriangleClassifier`](/src/main/java/nl/uu/maze/benchmarks/TriangleClassifier.java): Classifies a triangle based on its sides (e.g., equilateral, isosceles, etc.), with functions for integer, floating-point, and double precision inputs.
- [`BinaryTree`](/src/main/java/nl/uu/maze/benchmarks/BinaryTree.java): Provides a binary tree implementation and various traversal and utility methods (e.g., in-order, pre-order, post-order traversal, height calculation, finding certain values).
- [`BitwiseManipulator`](/src/main/java/nl/uu/maze/benchmarks/BitwiseManipulator.java): Class that performs various bitwise operations on integers.
- [`BracketBalancer`](/src/main/java/nl/uu/maze/benchmarks/BracketBalancer.java): Class that checks whether a string of brackets (represented as an array of characters) is balanced.
- [`ConnectedComponents`](/src/main/java/nl/uu/maze/benchmarks/ConnectedComponents.java): Calculates the number of connected components and detects components with cycles of a given length in a graph represented as an adjacency matrix.
- [`Dijkstra`](/src/main/java/nl/uu/maze/benchmarks/Dijkstra.java): Implements Dijkstra's algorithm to find the shortest path in a graph represented as an adjacency matrix, as well as a DFS traversal method to check whether a particular node is reachable from another node.
- [`GraphTraversal`](/src/main/java/nl/uu/maze/benchmarks/GraphTraversal.java): Implements DFS and BFS graph traversal algorithms on a graph represented as an adjacency matrix. The DFS algorithm is used by the `ConnectedComponents` class.
- [`HeapSort`](/src/main/java/nl/uu/maze/benchmarks/HeapSort.java): Implementation of the heap sort algorithm on an array of floating-point numbers.
- [`IntUtils`](/src/main/java/nl/uu/maze/benchmarks/IntUtils.java): Class that provides various utility methods for integers, such as calculating the GCD, LCM, and factorial.
- [`StringPatternMatcher`](/src/main/java/nl/uu/maze/benchmarks/StringPatternMatcher.java): Implements a simple string pattern matching algorithm based on regex-like syntax.
- [`StringUtils`](/src/main/java/nl/uu/maze/benchmarks/StringUtils.java): Class that provides various utility methods for strings, such as reversing a string, checking for palindromes, and finding really specific substrings (e.g., alternating digits and letters).


##### Sample of generated tests

Samples of generated tests for the above benchmark classes can be found in [src/test/java/nl/uu/maze/generated/benchmarks](/src/test/java/nl/uu/tests/maze/generated/benchmarks/). These were generated using BFS with 30 second time budget.
These tests achieve an overall 90% instruction coverage and 85% branch coverage.

### BM Framework-2: Verification

The benchmarking framework is provided [here](https://github.com/wooshrow/java-svcomp-t). It evaluates MAZE against verification tasks from the SV-COMP (International Competition on Software Verification) dataset. 

## ▊▎Troubleshooting

### ● Build Failure

If your build of the MAZE project fails with the following error:

```bash
Error:  Failed to execute goal on project maze: Could not resolve dependencies for project nl.uu:maze:jar:1.0
Error:  dependency: com.microsoft:z3:jar:4.13.3 (compile)
Error:  	Could not find artifact com.microsoft:z3:jar:4.13.3 in central (https://repo.maven.apache.org/maven2)
```

You don't have Z3 installed in your local maven repository.
Follow the instructions in the [Installing Z3](#installing-z3) section to install Z3.
If you have Z3 installed, but the build still fails, check that the version number in the `pom.xml` file matches the version of Z3 you installed.

### ● Test Generation

Some Java language constructs are not supported by MAZE, including:

- Dynamic invoke (`invokedynamic`), which is used for lambda expressions and method references.
- Static fields and static initializers.
- Enums (which are basically static fields).

If running MAZE on a class takes too long, consider reducing the maximum depth of the search with the `--max-depth` option or setting a time budget with the `--time-budget` option.

## ▊▎Dependencies

MAZE relies on the following libraries and frameworks to function effectively:

- [SootUp](https://soot-oss.github.io/SootUp/latest/) for Java bytecode analysis and transformation.
- [Z3 Theorem Prover](https://github.com/Z3Prover/z3) for constraint solving.
- [ASM](https://asm.ow2.io/) for bytecode manipulation.
- [JavaPoet](https://github.com/square/javapoet) for Java source code generation.
- [Logback](https://logback.qos.ch/) for logging.
- [JUnit 5](https://junit.org/junit5/) for testing.
- [Picocli](https://picocli.info/) for command-line argument parsing.

## ▊▎License

This project is licensed under the [MIT license](./LICENSE).
