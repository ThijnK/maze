import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.jar.*;
import javax.tools.ToolProvider;
import com.fasterxml.jackson.databind.*;

/** Standalone acceptance runner: no MAZE source/test classes on the classpath. */
public final class ArtifactCheck {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String TARGET = "example.SmokeSubject";
    static int checks;

    public static void main(String[] args) throws Exception {
        buildFailureFixtures();
        for (boolean concrete : List.of(false, true)) {
            String mode = concrete ? "concrete" : "symbolic";
            success(mode + "-baseline", concrete, List.of("-s", "BFS"));
            success(mode + "-default", concrete, List.of());
            for (String builtin : List.of("PS", "URS", "COS", "FOS", "RPS", "SGS")) {
                success(mode + "-" + builtin, concrete, List.of("-s", builtin));
            }
            success(mode + "-signals", concrete, List.of("--plugin", "fixtures.jar", "-s", "PS", "-u", "fixture.SignalProbe"));
            require(Files.readString(log(Path.of("target/generated-tests", mode + "-signals"))).contains("Observed shared search signals"), "signal probe was not called");
            success(mode + "-strategy", concrete, List.of("--plugin", "research.jar", "-s", "research.DepthSearch"));
            success(mode + "-heuristic", concrete, List.of("--plugin", "research.jar", "-s", "PS", "-u", "research.DepthWindowHeuristic"));
            success(mode + "-composed", concrete, List.of("--plugin", "research.jar", "--search-config", "search.json"));
            String compatible = concrete ? "Concrete" : "Symbolic";
            String incompatible = concrete ? "Symbolic" : "Concrete";
            success(mode + "-restricted-strategy", concrete, List.of("--plugin", "fixtures.jar", "-s", "fixture." + compatible + "Only"));
            success(mode + "-restricted-heuristic", concrete, List.of("--plugin", "fixtures.jar", "-s", "PS", "-u", "fixture." + compatible + "Heuristic"));
            modeFailure(mode + "-incompatible-strategy", concrete,
                    List.of("--plugin", "fixtures.jar", "-s", "BFS,fixture." + incompatible + "Only"), "$.strategies[1]");
            modeFailure(mode + "-incompatible-heuristic", concrete,
                    List.of("--plugin", "fixtures.jar", "-s", "PS", "-u", "fixture." + incompatible + "Heuristic"), "$.strategies[0].options.heuristics[0]");
            success(mode + "-dependency", concrete, List.of("--plugin", "fixtures.jar", "--plugin", "helper.jar", "-s", "PS", "-u", "fixture.WithDependency"));
            failure(mode + "-callback", concrete, List.of("--plugin", "fixtures.jar", "-s", "fixture.FailingSearch", "--verification=true"), "deliberate strategy failure", true);
            failure(mode + "-heuristic-callback", concrete, List.of("--plugin", "fixtures.jar", "-s", "PS", "-u", "fixture.FailingHeuristic", "--verification=true"), "deliberate heuristic failure", true);
            failure(mode + "-final-callback", concrete, List.of("--plugin", "fixtures.jar", "-s", "fixture.FinalFailure", "--verification=true"), "getTotalExploredCount failed", true);
        }
        success("symbolic-PCS", false, List.of("-s", "PCS", "--path-length-coverage=1"));
        Files.copy(Path.of("research.jar"), Path.of("duplicate.jar"));
        failure("ambiguous", false, List.of("--plugin", "research.jar", "--plugin", "duplicate.jar", "-s", "research.DepthSearch"), "ambiguous implementation", false);
        failure("missing-jar", false, List.of("--plugin", "missing.jar"), "missing.jar", false);
        failure("missing-class", false, List.of("--plugin", "research.jar", "-s", "research.Missing"), "ClassNotFoundException", false);
        failure("missing-dependency", false, List.of("--plugin", "fixtures.jar", "-s", "PS", "-u", "fixture.WithDependency"), "helper/Bias", false);
        failure("wrong-type", false, List.of("--plugin", "research.jar", "-s", "research.DepthWindowHeuristic"), "must extend", false);
        failure("wrong-constructor", false, List.of("--plugin", "fixtures.jar", "-s", "fixture.WrongConstructor"), "NoSuchMethodException", false);
        failure("private-constructor", false, List.of("--plugin", "fixtures.jar", "-s", "fixture.PrivateConstructor"), "NoSuchMethodException", false);
        failure("throwing-constructor", false, List.of("--plugin", "fixtures.jar", "-s", "fixture.ThrowingConstructor"), "deliberate constructor failure", false);
        failure("config-conflict", false, List.of("--search-config", "search.json", "-s", "BFS"), "cannot be combined", false);
        failure("heuristic-conflict", false, List.of("--search-config", "search.json", "-u", "UH"), "cannot be combined", false);
        failure("weight-conflict", false, List.of("--search-config", "search.json", "-w", "1"), "cannot be combined", false);
        failure("pcs-concrete", true, List.of("-s", "PCS", "--path-length-coverage=1"), "does not support concrete-driven mode", false);
        Path unwritableTests = Path.of("target/failures/output-write");
        Files.createDirectories(unwritableTests.resolve("SmokeSubjectTest.java"));
        failureAt(unwritableTests, false, List.of("-s", "BFS"), "Failed to write generated JUnit test cases", false);
        // Reuse an output directory that previously had successful completion and a CSV.
        Path reused = Path.of("target/generated-tests/symbolic-baseline");
        String previousId = status(reused).path("invocationId").asText();
        failureAt(reused, false, List.of("-s", "misspelled"), "misspelled", false);
        require(!previousId.equals(status(reused).path("invocationId").asText()), "stale invocation ID");
        // Do not run stale generated tests as part of the positive-suite validation below.
        try (var files = Files.walk(reused)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(file);
        }
        System.out.println("Artifact checks passed: " + checks);
    }

    static void success(String name, boolean concrete, List<String> extra) throws Exception {
        Path output = Path.of("target/generated-tests", name);
        int exit = launch(output, concrete, extra);
        require(exit == 0, name + " exited " + exit + ": " + Files.readString(log(output)));
        JsonNode status = status(output);
        require(status.path("outcome").asText().equals("completed"), name + " lacks completion");
        require(status.path("configuration").size() > 0, "missing ordered configuration");
        require(status.path("search").path("maze").path("sha256").asText().length() == 64, "missing MAZE JAR hash");
        require(status.path("search").path("instances").size() > 0, "missing implementation identity");
        require(Files.exists(output.resolve(TARGET + "-test-summary.csv")), "missing successful CSV");
        try (var files = Files.walk(output)) {
            require(files.anyMatch(p -> p.toString().endsWith(".java")), "no generated test source");
        }
        if (name.endsWith("-composed")) {
            require(status.path("configuration").size() == 3, "duplicate strategy removed");
            require(status.path("search").path("plugins").get(0).path("sha256").asText().length() == 64, "missing JAR hash");
        }
        checks++;
    }
    static void modeFailure(String name, boolean concrete, List<String> extra, String location) throws Exception {
        failure(name, concrete, extra, "does not support " + (concrete ? "concrete" : "symbolic") + "-driven mode", false);
        Path output = Path.of("target/failures", name);
        require(Files.readString(log(output)).contains(location), "missing incompatible component location");
        try (var files = Files.walk(output)) {
            require(files.noneMatch(p -> p.toString().endsWith(".java")), "incompatible search generated tests");
        }
    }
    static void failure(String name, boolean concrete, List<String> extra, String diagnostic, boolean partial) throws Exception {
        failureAt(Path.of("target/failures", name), concrete, extra, diagnostic, partial);
    }
    static void failureAt(Path output, boolean concrete, List<String> extra, String diagnostic, boolean partial) throws Exception {
        int exit = launch(output, concrete, extra);
        String log = Files.readString(log(output));
        require(exit != 0, "failure unexpectedly succeeded: " + extra);
        require(log.contains(diagnostic), "missing diagnostic " + diagnostic + ": " + log);
        require(!log.contains("Verification: PASS"), "failure reported PASS");
        require(status(output).path("outcome").asText().equals("failed"), "failure lacks failed status: " + log);
        require(!Files.exists(output.resolve(TARGET + "-test-summary.csv")), "failure published a success CSV");
        if (partial) {
            require(log.contains("tests are partial"), "partial results unmarked");
            try (var files = Files.walk(output)) {
                require(files.anyMatch(p -> p.toString().endsWith(".java")), "partial tests lost");
            }
        }
        checks++;
    }
    static int launch(Path output, boolean concrete, List<String> extra) throws Exception {
        return launch(output, concrete, TARGET, extra);
    }
    static int launch(Path output, boolean concrete, String target, List<String> extra) throws Exception {
        Files.createDirectories(output);
        var command = new ArrayList<>(List.of("java", "-ea", "-jar", "maze.jar", "-c", "target/classes", "--class-name", target,
                "-o", output.toString(), "-b", "10", "--export-summary=true", "--concrete-driven=" + concrete));
        if (extra.stream().noneMatch(a -> a.startsWith("--minimization"))) {
            command.add("--minimization=true");
        }
        command.addAll(extra);
        Process p = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log(output).toFile()).start();
        if (!p.waitFor(45, TimeUnit.SECONDS)) { p.destroyForcibly(); throw new AssertionError("MAZE timed out: " + command); }
        return p.exitValue();
    }
    static Path log(Path output) { return output.resolve("run.log"); }
    static JsonNode status(Path output) throws Exception { return JSON.readTree(output.resolve(TARGET + "-run-status.json").toFile()); }
    static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    static void buildFailureFixtures() throws Exception {
        compile("helper/Bias", "package helper; public final class Bias { public static double score() { return 1; } }", "helper-classes", "maze.jar");
        jar("helper.jar", "helper-classes");
        String imports = "package fixture; import nl.uu.maze.search.*; import nl.uu.maze.search.strategy.*; import nl.uu.maze.search.heuristic.*; ";
        compile("fixture/FailingSearch", imports + "public class FailingSearch extends BFS<SearchTarget> { public FailingSearch(SearchOptions o) {} public SearchTarget next() { throw new IllegalStateException(\"deliberate strategy failure\"); } }", "fixture-classes", "maze.jar");
        compile("fixture/FinalFailure", imports + "public class FinalFailure extends BFS<SearchTarget> { public FinalFailure(SearchOptions o) {} public int getTotalExploredCount() { throw new IllegalStateException(\"deliberate final failure\"); } }", "fixture-classes", "maze.jar");
        compile("fixture/FailingHeuristic", imports + "public class FailingHeuristic extends SearchHeuristic { public FailingHeuristic(double w, SearchOptions o) {super(w);} public String getName(){return \"fail\";} public <T extends SearchTarget> double calculateWeight(T t) {throw new IllegalStateException(\"deliberate heuristic failure\");} }", "fixture-classes", "maze.jar");
        compile("fixture/WithDependency", imports + "public class WithDependency extends SearchHeuristic { final double value; public WithDependency(double w, SearchOptions o) {super(w); value=helper.Bias.score();} public String getName(){return \"dependency\";} public <T extends SearchTarget> double calculateWeight(T t) {return value;} }", "fixture-classes", "maze.jar:helper.jar");
        compile("fixture/WrongConstructor", imports + "public class WrongConstructor extends BFS<SearchTarget> { public WrongConstructor() {} }", "fixture-classes", "maze.jar");
        compile("fixture/PrivateConstructor", imports + "public class PrivateConstructor extends BFS<SearchTarget> { private PrivateConstructor(SearchOptions o) {} }", "fixture-classes", "maze.jar");
        compile("fixture/ThrowingConstructor", imports + "public class ThrowingConstructor extends BFS<SearchTarget> { public ThrowingConstructor(SearchOptions o) { throw new IllegalArgumentException(\"deliberate constructor failure\"); } }", "fixture-classes", "maze.jar");
        compile("fixture/SignalProbe", imports + """
            public class SignalProbe extends SearchHeuristic {
                boolean observed;
                public SignalProbe(double w, SearchOptions o) { super(w); }
                public String getName() { return "signals"; }
                public <T extends SearchTarget> double calculateWeight(T t) {
                    java.util.Objects.requireNonNull(t.getStmt());
                    java.util.Objects.requireNonNull(t.getCFG());
                    t.getPrevStmt(); t.getDepth(); t.getCallDepth(); t.getWaitingTime();
                    t.getBranchHistory().size(); t.getNewCoverageDepths().size();
                    for (var frame : t.getCallStack()) { frame.first(); frame.second(); }
                    t.getConstraints().stream().mapToDouble(c -> c.getEstimatedCost()).sum();
                    var coverage = nl.uu.maze.execution.symbolic.CoverageTracker.getInstance();
                    coverage.isStmtCovered(t.getStmt()); coverage.isStmtCovered_byExpl(t.getStmt());
                    var analyzer = nl.uu.maze.analysis.JavaAnalyzer.getInstance();
                    analyzer.getSuccessors(t.getCFG(), t.getStmt());
                    if (t.getStmt().containsInvokeExpr()) {
                        var method = analyzer.tryGetSootMethod(t.getStmt().getInvokeExpr().getMethodSignature());
                        if (method.isPresent() && method.get().hasBody()) analyzer.getCFG(method.get());
                    }
                    if (!observed) { System.out.println("Observed shared search signals"); observed = true; }
                    return 1;
                }
            }
            """, "fixture-classes", "maze.jar");
        for (String mode : List.of("Symbolic", "Concrete")) {
            String targetType = mode.equals("Symbolic")
                    ? "nl.uu.maze.execution.symbolic.SymbolicState"
                    : "nl.uu.maze.execution.concrete.PathConditionCandidate";
            String declaration = "public boolean supportsMode(SearchMode mode) { return mode == SearchMode."
                    + mode.toUpperCase(Locale.ROOT) + "; }";
            compile("fixture/" + mode + "Only", imports + "public class " + mode + "Only extends BFS<" + targetType
                    + "> { public " + mode + "Only(SearchOptions o) {} " + declaration
                    + " public void add(" + targetType + " target) { super.add(target); } }", "fixture-classes", "maze.jar");
            compile("fixture/" + mode + "Heuristic", imports + "public class " + mode + "Heuristic extends SearchHeuristic {"
                    + " public " + mode + "Heuristic(double w, SearchOptions o) { super(w); } " + declaration
                    + " public String getName() { return \"restricted\"; } public <T extends SearchTarget> double calculateWeight(T t) {"
                    + " return ((" + targetType + ") t).getDepth() + 1; } }", "fixture-classes", "maze.jar");
        }
        jar("fixtures.jar", "fixture-classes");
    }
    static void compile(String name, String source, String output, String classpath) throws Exception {
        Path file = Path.of("fixture-sources", name + ".java");
        Files.createDirectories(file.getParent()); Files.createDirectories(Path.of(output)); Files.writeString(file, source);
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-cp", classpath, "-d", output, file.toString()) == 0, "fixture compilation failed");
    }
    static void jar(String name, String directory) throws Exception {
        Path root = Path.of(directory);
        try (var output = new JarOutputStream(Files.newOutputStream(Path.of(name))); var files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(root.relativize(file).toString())); Files.copy(file, output); output.closeEntry();
            }
        }
    }
}
