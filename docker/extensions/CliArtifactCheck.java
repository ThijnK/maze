import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

/** Actual packaged-CLI behavior beyond parsing, in the same independent directory. */
public final class CliArtifactCheck {
    private static final String SUBJECT = "example.ViolationSubject";
    private static int checks;

    public static void main(String[] args) throws Exception {
        ArtifactCheck.compile("example/ViolationSubject", """
            package example;
            public class ViolationSubject {
                public static void first(int x) { assert x != 0; }
                public static void second(int x) { assert x != 1; }
                public static void third(int x) { assert x != 2; }
            }
            """, "target/classes", "maze.jar");
        for (boolean concrete : List.of(false, true)) {
            String mode = concrete ? "concrete" : "symbolic";
            verify(mode + "-one", concrete, List.of("--verification=true"), 1);
            verify(mode + "-two", concrete, List.of("--verification", "--max-violations=2"), 2);
            verify(mode + "-unlimited", concrete, List.of("--verification", "--max-violations=unlimited"), 3);
        }
        for (String destination : List.of("file", "log", "none")) {
            String name = "exports-" + destination;
            ArtifactCheck.success(name, false, List.of("-s", "BFS", "--path-length-coverage=1",
                    "--export-jimple=" + destination, "--export-hcfg=" + destination,
                    "--export-target-paths=" + destination, "--export-path-coverage=" + destination));
            Path output = Path.of("target/generated-tests", name);
            String log = Files.readString(ArtifactCheck.log(output));
            for (String file : List.of("SmokeSubject_classify.jimple", "SmokeSubject_classify.dot",
                    "SmokeSubject_classify-targetpaths.txt", "example.SmokeSubject-pathcov.txt")) {
                require(Files.exists(output.resolve(file)) == destination.equals("file"), "incorrect export destination for " + file);
            }
            for (String message : List.of("Jimple code of", "Dot file", "Target paths:", "Path-coverage info:")) {
                require(log.contains(message) == destination.equals("log"), "incorrect log destination for " + message);
            }
            checks++;
        }
        System.out.println("CLI artifact checks passed: " + checks);
    }

    private static void verify(String name, boolean concrete, List<String> options, int expected) throws Exception {
        Path output = Path.of("target/verification-checks", name);
        var args = new ArrayList<>(List.of("-s", "BFS")); args.addAll(options);
        int exit = ArtifactCheck.launch(output, concrete, SUBJECT, args);
        require(exit == 0, "verification failed: " + Files.readString(ArtifactCheck.log(output)));
        var status = ArtifactCheck.JSON.readTree(output.resolve(SUBJECT + "-run-status.json").toFile());
        require(status.path("outcome").asText().equals("completed"), "verification not completed");
        List<String> csv = Files.readAllLines(output.resolve(SUBJECT + "-test-summary.csv"));
        List<String> header = List.of(csv.get(0).split(","));
        String[] row = csv.get(1).split(",");
        require(Integer.parseInt(row[header.indexOf("#errors")]) == expected, "wrong violation count for " + name);
        require(Integer.parseInt(row[header.indexOf("#test")]) == expected, "verification generated non-violation tests");

        Path classes = output.resolve("compiled"); Files.createDirectories(classes);
        require(ToolProvider.getSystemJavaCompiler().run(null, null, null, "-cp", "maze.jar:target/classes", "-d", classes.toString(),
                output.resolve("ViolationSubjectTest.java").toString()) == 0, "generated counterexamples did not compile");
        try (var loader = new URLClassLoader(new java.net.URL[] {classes.toUri().toURL(), Path.of("target/classes").toUri().toURL()}, CliArtifactCheck.class.getClassLoader())) {
            loader.setDefaultAssertionStatus(true);
            Class<?> tests = loader.loadClass("ViolationSubjectTest");
            Object instance = tests.getConstructor().newInstance();
            int failures = 0;
            for (var method : tests.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(org.junit.jupiter.api.Test.class)) continue;
                try { method.invoke(instance); }
                catch (InvocationTargetException e) {
                    require(e.getCause() instanceof AssertionError, "unexpected generated-test failure: " + e.getCause());
                    failures++;
                }
            }
            require(failures == expected, "counterexamples did not reproduce the expected assertion violations");
        }
        checks++;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
