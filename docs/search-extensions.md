# Writing search strategies and heuristics

MAZE accepts search implementations from separate JARs. Compile against MAZE's
executable dependency JAR, implement its existing `SearchStrategy` or
`SearchHeuristic` abstraction, and select your class by its full Java name.
No MAZE source changes, service-registration files, or alias registration are needed.
Extensions run as trusted Java code in MAZE's JVM.

## Build and run the complete examples

If you have a Linux package, follow its included README to compile and run the
bundled examples. The API and lifecycle below apply to both packages and source builds.

For a source build, first [build MAZE](../README.md#develop-with-docker) to obtain its
`jar-with-dependencies` artifact. The [development container](development-guide.md)
includes Java 21, Maven, and matching Z3 4.13.3 Java bindings and native libraries,
so you do not need to install or configure them separately. For a local setup,
follow [Build locally](development-guide.md#build-locally).

If you use Docker, open a shell from the repository root:

```sh
docker compose run --rm dev bash
```

Run the following commands from the repository root, either in that container
shell or in your local environment. They prepare an independent directory,
compile the examples, and run them against the packaged MAZE JAR:

```sh
mkdir -p /tmp/my-maze-experiment
cp target/maze-1.2.0-jar-with-dependencies.jar /tmp/my-maze-experiment/maze.jar
cp -R examples/search-extensions/. /tmp/my-maze-experiment/
cd /tmp/my-maze-experiment

mkdir -p extension-classes subject-classes generated
javac -cp maze.jar -d extension-classes src/research/*.java
jar --create --file research.jar -C extension-classes .
javac -d subject-classes subject/example/*.java

java -jar maze.jar --plugin research.jar -s research.DepthSearch \
  -c subject-classes -n example.SmokeSubject -o generated/symbolic -b 10
java -jar maze.jar --plugin research.jar -s research.DepthSearch \
  -c subject-classes -n example.SmokeSubject -o generated/concrete -b 10 -C
```

On Linux the native library setting is, for example,
`export LD_LIBRARY_PATH=/path/to/z3/bin`; the development container sets it for you.
Do not put MAZE or its dependencies into `research.jar`: they are supplied by
MAZE's parent classloader. If your extension needs another library, pass its JAR
with another `--plugin`. The subject's `--classpath` is separate.
MAZE rejects a requested implementation class if more than one supplied JAR
defines it.

The two complete examples are:

- [DepthSearch.java](../examples/search-extensions/src/research/DepthSearch.java):
  keeps pending targets, selects by execution depth, and honors peer selection.
  Its optional `preferDeep` boolean defaults to `false`.
- [DepthWindowHeuristic.java](../examples/search-extensions/src/research/DepthWindowHeuristic.java):
  scores targets near an optional desired depth, `window`, which defaults to 20.

To use just the heuristic with shipped probabilistic search:

```sh
java -jar maze.jar --plugin research.jar \
  -s PS -u QueryCost,research.DepthWindowHeuristic -w 0.7,0.3 \
  -c subject-classes -n example.SmokeSubject -o generated/heuristic -b 10
```

To run the supplied configuration, including two separately configured instances
of the same strategy and a PS instance with a custom heuristic:

```sh
java -jar maze.jar --plugin research.jar --search-config search.json \
  -c subject-classes -n example.SmokeSubject -o generated/composed -b 10
```

Add `-C` to run either command in concrete-driven mode. Run a comparison with
`-s BFS`, keeping the subject, mode, budgets, and other engine settings the same.
Use separate output directories for different experiments and concurrent runs.

To run the automated artifact check, use your host shell at the repository root
(exit the container shell first if you opened one). The check compiles these
examples outside the checkout, runs both modes, and compiles **and executes** the
generated JUnit tests:

```sh
docker compose run --rm dev sh docker/extensions/verify.sh \
  target/maze-1.2.0-jar-with-dependencies.jar
```

## Constructors and options

A strategy extends `SearchStrategy<SearchTarget>` and provides a public
constructor taking `nl.uu.maze.search.SearchOptions`. A heuristic extends
`SearchHeuristic` and provides a public constructor taking `(double, SearchOptions)`:

```java
public MySearch(SearchOptions options) {
    window = options.getInt("window", 32);
    if (window <= 0) throw new IllegalArgumentException("window must be positive");
}

public MyHeuristic(double weight, SearchOptions options) {
    super(weight);
    window = options.getInt("window", 20);
}
```

Constructors configure the instance. Read analysis and coverage information in
exploration callbacks, after the engine has initialized them. MAZE does not make
private classes or constructors accessible.

`SearchOptions` hides JSON parsing and supplies strict readers:

| Value | Optional reader | Required reader |
| --- | --- | --- |
| 32-bit integer | `getInt(key, defaultValue)` | `getRequiredInt(key)` |
| 64-bit integer | `getLong(key, defaultValue)` | `getRequiredLong(key)` |
| Finite number | `getDouble(key, defaultValue)` | `getRequiredDouble(key)` |
| Boolean | `getBoolean(key, defaultValue)` | `getRequiredBoolean(key)` |
| String | `getString(key, defaultValue)` | `getRequiredString(key)` |
| Constructed heuristics | `getHeuristicsOrUniform(key)` | `getHeuristics(key)` |

An absent optional value uses its default. An absent required value fails.
Numbers are not parsed from strings, fractional numbers are not truncated to
integers, and explicit `null` is not treated as omission. Validate algorithm-specific
constraints in the constructor. The effective options record includes defaults
read by the constructor.

MAZE rejects **supplied keys the constructor did not read**. Read every supported
option during construction, even when another option makes its effect conditional.
Do not retain the options reader for use in callbacks; it is closed after
construction. This catches spelling mistakes without requiring a second schema.

An extension that owns heuristics can call `getHeuristics("heuristics")` to receive
a nonempty list constructed by MAZE, including external implementations and
callback guards. `getHeuristicsOrUniform` supplies a fresh uniform heuristic if the
key is absent. Forward your strategy's `reset()` to owned heuristics.

## Configuration and composition

The JSON format is deliberately flat and ordered:

```json
{
  "strategies": [
    {"name": "research.DepthSearch", "options": {"preferDeep": true}},
    {"name": "PS", "options": {"heuristics": [
      {"name": "QueryCost", "weight": 0.7},
      {"name": "research.DepthWindowHeuristic", "weight": 0.3,
       "options": {"window": 12}}
    ]}}
  ]
}
```

`name` is a shipped alias or a case-sensitive, fully qualified Java class name.
`options` is an object of construction inputs for **any** implementation, shipped
or external; omission means `{}`. Initially PS accepts `heuristics`; other shipped
strategies and heuristics have no additional options. URS, COS, and FOS retain
their fixed definitions. Use PS to configure your own combination.

`weight` is common heuristic metadata: it defaults to 1 and must be finite and
positive. PS's `heuristics` is inside **PS's options**, because PS consumes it.
Omitting it selects uniform search; an explicitly empty array is rejected.

One strategy runs directly. Multiple entries use MAZE's existing interleaving:
the active child selects a target, and peers receive `select(target)`. Every
occurrence creates a fresh instance, even identical entries or identical display
names. This also applies to comma-separated `-s` and to nested heuristics.
There is no nested interleaving configuration language.

Do not combine `--search-config` with an explicitly supplied `-s`, `-u`, or `-w`.
Implicit defaults do not conflict. Without a file, `-u` and `-w` configure PS;
explicit heuristic settings with no PS are an error. Omitted trailing weights
are 1; extra weights are an error. Unknown names, fields, duplicate JSON keys,
wrong types, and unread options stop the run instead of substituting another search.

## Strategy callbacks and lifecycle

| Callback | Contract |
| --- | --- |
| `getName()` | Human-readable label; not an instance identifier. |
| `supportsMode(mode)` | Defaults to true for both `SearchMode.SYMBOLIC` and `SearchMode.CONCRETE`. Declare any mode restriction; MAZE checks it after construction and before exploration. |
| `add(target)` | Add pending work; increment protected `count` if following built-in statistics. Collection addition defaults to repeated single addition. |
| `next()` | Select and remove the next target. `null` means exhausted, not a temporary yield. |
| `remove(target)` | Remove that target from pending work. |
| `select(target)` | A peer selected this target. Defaults to `remove`; tree strategies may also update parent tracking. |
| `size()` | Number of pending targets. |
| `getAll()` | Remaining work, also used for generating tests from unfinished symbolic paths. A snapshot is fine. |
| `reset()` | Clear pending work and reset owned components as appropriate. |
| `getTotalExploredCount()` | Defaults to protected `count`: built-ins count additions, commonly retaining it across resets. It is not a distinct-target or completed-execution count. |
| `requiresPathTargetingAndTracking()` | Defaults to false. Opt into engine path targeting only if your strategy uses it; configure the engine's path coverage settings accordingly. |

Concrete-driven execution resets the strategy before each method. Symbolic-driven
execution explores methods together. `SearchHeuristic.reset()` defaults to doing
nothing; PS calls it for every owned heuristic when PS resets. Override it to clear
history, or deliberately retain learned history. Reset is not reconstruction:
built-ins may retain counts, random-generator state, and other history.

Interleaving shares target objects. Keep private scheduling metadata in instance
fields, keyed by target identity when appropriate. Children must honor peer selection
and maintain compatible pending sets; an exhausted active child can end the search.
Fresh instances share engine coverage and existing random-generator behavior;
MAZE does not give each instance an independent random stream.

A heuristic's `calculateWeight(target)` returns its target score. Use finite,
nonnegative scores, with larger values favoring selection. The constructor's
`weight` is its relative influence in a mixture, distinct from a target score.
PS skips scoring with zero or one pending target, and samples at most 1,000 targets
per selection. Do not depend on being called once per engine step or every target.

## Execution-mode compatibility

Strategies and heuristics using the shared `SearchTarget` contract support both
execution modes by default. A mode-specific implementation overrides the same
method on `SearchStrategy` or `SearchHeuristic`, for example:

```java
@Override
public boolean supportsMode(nl.uu.maze.search.SearchMode mode) {
    return mode == nl.uu.maze.search.SearchMode.SYMBOLIC;
}
```

MAZE checks every configured strategy and heuristic, including heuristics loaded
through `SearchOptions`, before exploration. One incompatible component rejects
the entire configuration, with its class and configuration location in the error.
Interleaving and PS support a mode only when all their components support it.
A custom strategy that constructs components itself should likewise combine their
mode declarations. A declaration may depend on constructor options, but must remain
stable for the instance's lifetime.

PCS declares symbolic-only support through this same method. Mode checks happen
after construction, so constructor validation (including PCS's path-coverage
requirement) can fail first. This is configuration-time validation, not proof that
plugin code is correct: an incorrect declaration or an unsafe cast can still fail
at runtime, where MAZE reports the callback failure and stops the run.

## What an extension can observe

Extensions can use the documented `SearchTarget` observations in both execution
modes. PCS additionally depends on symbolic-specific path-targeting machinery:
it reads and changes target paths, uses high-level CFGs, and updates the coverage
tracker's set of feasible target paths. Those operations are outside the shared
`SearchTarget` contract. Their public Java classes are accessible from a plugin
JAR, but PCS's state-specific operations require a symbolic-specific implementation.

Use the `SearchTarget` interface in shared-mode implementations. Symbolic targets
are live symbolic states; concrete targets snapshot a branch point on a previously
executed path. After concrete selection the engine applies negation and may reject
a candidate as already explored or unsatisfiable. Selection does not guarantee
execution. Do not cast to `SymbolicState` if your implementation must work in both
modes. Declare mode restrictions with `supportsMode` as described above.

| Observation | Meaning and use |
| --- | --- |
| `getStmt()`, `getPrevStmt()`, `getCFG()` | Current and preceding statements, and the SootUp CFG; predecessors can be absent at entry. |
| `getConstraints()` | Available path constraints. `PathConstraint.getEstimatedCost()` estimates solver complexity; it is not measured solve time. Concrete candidates carry the recorded path's constraints before the selected negation. |
| `getDepth()` | Execution depth in the current state or recorded branch snapshot, not a prediction of the future negated path. |
| `getNewCoverageDepths()` | History of depths where new code was covered. |
| `getBranchHistory()` | Integer-encoded branch history along the recorded path. |
| `getCallDepth()`, `getCallStack()` | Call depth and statement/CFG frames for the current or recorded branch point. |
| `getWaitingTime()` | Waiting time in search iterations, set by PS immediately before heuristic evaluation. |

Treat engine-owned graphs, constraints, collections, and coverage as observations.
Copy data you intend to change. The existing public analysis and coverage services
are available in callbacks, just as they are for shipped heuristics:

```java
var coverage = nl.uu.maze.execution.symbolic.CoverageTracker.getInstance();
boolean coveredByTests = coverage.isStmtCovered(target.getStmt());
boolean coveredDuringExploration = coverage.isStmtCovered_byExpl(target.getStmt());

var analyzer = nl.uu.maze.analysis.JavaAnalyzer.getInstance();
var successors = analyzer.getSuccessors(target.getCFG(), target.getStmt());
if (target.getStmt().containsInvokeExpr()) {
    var signature = target.getStmt().getInvokeExpr().getMethodSignature();
    var callee = analyzer.tryGetSootMethod(signature);
    // When present and hasBody(), analyzer.getCFG(callee.get()) gives its CFG.
}
```

Coverage by generated tests and coverage encountered during exploration are
separate signals. Read the one your experiment intends to measure. Constraint and
CFG types are the same Z3/SootUp-backed types used by shipped implementations;
MAZE does not translate them into a separate extension model.

## Failures and experiment records

Construction and callback exceptions stop the CLI with nonzero status. Diagnostics
identify the configured occurrence, implementation, callback, and original cause.
Callback failures remain fatal across concrete mode's recovery for subject errors.
Generated tests already accumulated are retained and explicitly logged as partial;
search failures do not publish a success CSV or verification PASS.

Each CLI invocation writes `<class-name>-run-status.json` in its output directory.
It starts as `running`, becomes `failed` on a reported failure, and becomes
`completed` only after successful engine/output finalization and loader cleanup.
A normal time-budget stop counts as completion. A killed process may leave `running`.
The record includes:

- A fresh invocation ID, start/finish time, target, and engine mode.
- Ordered requested configuration and resolved instance locations such as
  `$.strategies[1].options.heuristics[0]`.
- Implementation class, JAR location and SHA-256, explicitly supplied plugin JARs,
  MAZE artifact identity, and effective options/defaults.

When running directly from class directories, the location is recorded but there
is no JAR hash. Keep the exact MAZE and extension JARs, configuration, subject,
and engine arguments with your experiment. This is provenance, not API version
negotiation. Rebuild extensions against the MAZE artifact used in the experiment.

A benchmark runner must require **both a zero process exit and a new, matching
`completed` invocation record**. Read the prior invocation ID before launch, or use
a fresh output directory. Argument parsing can fail before an invocation starts;
a leftover file alone never proves success. Never score partial output or reuse an
old success CSV. Ordinary successful CSV columns remain unchanged.

For comparisons, run MAZE directly with the commands above or use the
[companion JUGE integration](https://github.com/ThijnK/JUGE/blob/thijn/maze-external-search/docs/MAZE.md).
JUGE accepts named experiments containing MAZE arguments, so the same plugin JARs
and search JSON work there without changes to MAZE. It records each configuration
separately and requires fresh successful completion before metrics or aggregation.
Use the linked integration branch until it is merged.
