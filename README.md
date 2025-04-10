# Maze

Maze is a **dynamic symbolic execution (DSE)** engine for **automated test generation** of Java programs.

The engine analyzes JVM bytecode and uses a combination of symbolic and concrete execution to explore program paths and generate JUnit 5 test cases that maximize code coverage.
It supports various search strategies and can handle complex data structures, including arrays and objects.
Constraint solving is powered by the Z3 theorem prover.

This project was developed as part of a master's thesis at Utrecht University.
The thesis focuses on comparing the effectiveness of different search strategies and heuristics in automated test generation using DSE.

## Getting Started

### Prerequisites

Before you begin, ensure you have the following software installed on your machine:

- Java Development Kit (JDK) 21 or higher
- Apache Maven
- Z3 Theorem Prover (see [Installing Z3](#installing-z3))

### Building the Project

Clone the repository and build the project using Maven:

```bash
git clone https://github.com/ThijnK/maze.git
cd maze
mvn clean install
```

### Running the Application

You can run the application using the following Maven command:

```bash
mvn exec:java -Dexec.args="--help"
```

This will display the help message with available options and arguments.
For an overviiew of the command-line options, see [Command-Line Options](#command-line-options).

Alternatively, you can run the packaged JAR file directly:

```bash
java -jar target/maze-1.0-jar-with-dependencies.jar --help
```

For example, to run the application on a specific Java class located in the `./target/classes` directory using BFS (rather than the default DFS), use the following command:

```bash
java -jar target/maze-1.0-jar-with-dependencies.jar --classPath target/classes --className com.example.MyClass --outPath tests --strategy BFS
```

### Installing Z3

Z3 is a theorem prover developed by Microsoft, which is used in this project to solve constraints in the symbolic execution engine.

Start by downloading the native distribution for your platform from the [Z3 GitHub releases](https://github.com/Z3Prover/z3/releases) page, for example `z3-4.13.3-x64-win.zip` for Windows x64.
Extract the contents of the zip file to a directory of your choice, for example `C:\Program Files\z3`.
Go to your system environment variables and set a variable `Z3_HOME` to the path where you extracted the zip file, for example `C:\Program Files\z3`.
Add `%Z3_HOME%\bin` to your system `PATH` variable.

Now to use the Z3 jar with maven, you need to install it to your local maven repository:

```bash
mvn install:install-file -Dfile="C:\Program Files\z3\bin\com.microsoft.z3.jar" -DgroupId=com.microsoft -DartifactId=z3 -Dversion=4.13.3 -Dpackaging=jar -DgeneratePom=true
```

Replace the path and version number in the command above with the correct values for your system and the version of Z3 you downloaded.

**Note**: It is possible that Z3 will not work after installing it this way, in which case your best bet is to build Z3 from source as described below.

#### Building Z3 from source

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

## Command-Line Options

Maze provides the following command-line options:

| Option              | Alias | Description                                                                | Required | Default    |
| ------------------- | ----- | -------------------------------------------------------------------------- | -------- | ---------- |
| `--help`            | `-h`  | Show help message                                                          | No       | -          |
| `--version`         | `-V`  | Show version information                                                   | No       | -          |
| `--classpath`       | `-c`  | Path to compiled classes                                                   | Yes      | -          |
| `--classname`       | `-n`  | Fully qualified name of the class to generate tests for                    | Yes      | -          |
| `--output-path`     | `-o`  | Output path to write generated test files to                               | Yes      | -          |
| `--package-name`    | `-p`  | Package name to use for generated test files                               | No       | No package |
| `--log-level`       | `-l`  | Log level (OFF, INFO, WARN, ERROR, TRACE, DEBUG)                           | No       | `INFO`     |
| `--strategy`        | `-s`  | One or multiple of the search strategies to use                            | No       | `DFS`      |
| `--heuristic`       | `-u`  | One or multiple of the search heuristics to use (for probabilistic search) | No       | `Uniform`  |
| `--weight`          | `-w`  | Weights for the provided heuristics                                        | No       | `1.0`      |
| `--max-depth`       | `-d`  | Maximum depth of the search                                                | No       | `50`       |
| `--time-budget`     | `-b`  | Time budget for the engine (in seconds)                                    | No       | No budget  |
| `--test-timeout`    | `-t`  | Timeout to apply to generated test cases (in seconds)                      | No       | No timeout |
| `--concrete-driven` | `-C`  | Use concrete-driven DSE instead of symbolic-driven                         | No       | `false`    |

## Project Structure

The project is organized into the following main packages:

- `nl.uu.maze.main`: Main application class
- `nl.uu.maze.analysis`: Java program analysis utilities
- `nl.uu.maze.execution`: Core DSE execution engine
  - `nl.uu.maze.execution.concrete`: Concrete execution components
  - `nl.uu.maze.execution.symbolic`: Symbolic execution components
- `nl.uu.maze.generation`: Test case generation
- `nl.uu.maze.instrument`: Bytecode instrumentation
- `nl.uu.maze.search`: Search strategies and heuristics
- `nl.uu.maze.transform`: Transformers between Java, Z3, and Jimple (SootUp IR)
- `nl.uu.maze.util`: Utility classes

## Architecture

### Dynamic Symbolic Execution (DSE)

Over time, the concept of dynamic symoblic execution (DSE) and concolic execution has evolved, but these terms are often used interchangeably.
Maze uses the term DSE to refer to the combination of symbolic and concrete execution.
We distinguish between two types of DSE, symbolic-driven and concrete-driven:

- **Concrete-driven DSE**:
  The engine instruments the class under test (CUT) in such a way that executing it will record a trace which can be reused to replay that execution symbolically.
  The engine explores program paths by first executing the instrumented CUT with concrete inputs, and then replays the recorded trace symbolically to obtain the path constraints corresponding to the executed path.
  By negating constraints from the previous path, and solving the resulting set of constraints, the engine can derive concrete inputs that explore (potentially) new paths.
  This process continues until no more unexplored paths (up to the maximum depth) are found.
  In concrete-driven DSE, the search space consists of the branches of previously executed paths.
- **Symbolic-driven DSE**:
  The engine executes the CUT symbolically from the start, and follows every path through the program simultaneously.
  Once the end of a path is reached, the engine will solve the path constraints and generate a test case for that path.
  What makes this approach a form of DSE is that the engine will use concrete execution for situations where it cannot symbolically execute the program, for example when the program calls a method whose code is not available (e.g. a library method).
  The engine will then execute the method with concrete inputs, and approximate the behavior of the method through its return value and side effects.
  In symbolic-driven DSE, the search space consists of the active symbolic states.

By default, the engine will use symbolic-driven DSE, but you can switch to concrete-driven DSE using the `--concreteDriven` option.

### Search Strategies

Maze supports the following search strategies:

- **Depth-First Search (DFS)**:
  Explores paths by going as deep as possible before backtracking.
  DFS is memory-efficient compared to breadth-first approaches and can quickly find solutions that are deep in the execution tree.
  Well-suited for exploring complex program paths when memory is limited.
- **Breadth-First Search (BFS)**:
  Explores all nodes at the current depth before moving deeper.
  This approach guarantees finding the shortest path to a target state, which can be valuable when looking for minimal test cases or when path length directly impacts solving performance.
- **Subpath-Guided Search (SGS)**:
  Tracks frequency of execution subpaths and prioritizes states with rarely seen patterns.
  This drives exploration toward less-visited code regions.
  This strategy is inspired by the work of [Li et al.](https://doi.org/10.1145/2544173.2509553).
- **Random Path Search (RPS)**:
  Maintains an execution tree and selects paths by randomly walking from root to leaf.
  Designed specifically for symbolic-driven execution, it naturally favors states closer to the root, keeping path conditions shorter and easier for constraint solvers to handle compared to pure random search.
  This strategy is inspired by the work of [Cadar et al.](https://www.usenix.org/legacy/events/osdi08/tech/full_papers/cadar/cadar_html/) in their tool KLEE.
- **Probabilistic Search (PS)**:
  Selects states based on a weighted probability distribution calculated from one or multiple search heuristics (see [Search Heuristics](#search-heuristics) below).
  By combining multiple heuristics and playing around with their weights, you have the potential to create a wide variety of search strategies.
  Different heuristics can complement each other, allowing for a more nuanced evaluation of states.
- **Interleaved Search (IS)**:
  Alternates between multiple search strategies using a round-robin approach.
  This can help to prevent any single strategy from getting stuck in unproductive regions of the search space.
  When Maze is run with multiple search strategies, it will automatically use interleaved search.

Each of these strategies can be used for both symbolic-driven and concrete-driven DSE, though some are more suited for one than the other (e.g., RPS is only really useful for symbolic-driven DSE).
The engine also provides some predefined search strategies for probabilistic search based on specific heuristics, such as coverage optimized search and random search (uniform distribution), the names for which can be found in the help message of the CLI.

#### Search Heuristics

Search heuristics are used to determine the probability distrbution for probabilistic search.
Maze supports the following search heuristics:

- **Uniform**:
  Assigns the same weight to every target, effectively creating a random search when used in isolation (no other heuristics).
  Useful as a baseline or in combination with other heuristics to introduce some randomness.
- **Depth**:
  Assigns weights based on the depth of a target in the control flow graph, allowing a preference for deeper targets (or the opposite, to prefer shallower targets).
  Less effective for concrete-driven DSE since target depths aren't known at the time of negating a path constraint.
- **Call Depth**:
  Assigns weights based on the call depth of a target, allowing a preference for deeply nested function calls (or the opposite, to prefer states which have not called a function).
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

## Troubleshooting

### Build Failure

If your build of the Maze project fails with the following error:

```bash
Error:  Failed to execute goal on project maze: Could not resolve dependencies for project nl.uu:maze:jar:1.0
Error:  dependency: com.microsoft:z3:jar:4.13.3 (compile)
Error:  	Could not find artifact com.microsoft:z3:jar:4.13.3 in central (https://repo.maven.apache.org/maven2)
```

You don't have Z3 installed in your local maven repository.
Follow the instructions in the [Installing Z3](#installing-z3) section to install Z3.
If you have Z3 installed, but the build still fails, check that the version number in the `pom.xml` file matches the version of Z3 you installed.

### Test Generation

Some Java language constructs are not supported by Maze, including:

- Dynamic invoke (`invokedynamic`), which is used for lambda expressions and method references.
- Static fields and static initializers.
- Enums (which are basically static fields).

If running Maze on a class takes too long due, consider reducing the maximum depth of the search with the `--maxDepth` option.

## Dependencies

Maze relies on the following libraries and frameworks to function effectively:

- [SootUp](https://soot-oss.github.io/SootUp/latest/) for Java bytecode analysis and transformation.
- [Z3 Theorem Prover](https://github.com/Z3Prover/z3) for constraint solving.
- [ASM](https://asm.ow2.io/) for bytecode manipulation.
- [JavaPoet](https://github.com/square/javapoet) for Java source code generation.
- [Logback](https://logback.qos.ch/) for logging.
- [JUnit 5](https://junit.org/junit5/) for testing.
- [Picocli](https://picocli.info/) for command-line argument parsing.

## License

This project is licensed under the [MIT license](./LICENSE).
