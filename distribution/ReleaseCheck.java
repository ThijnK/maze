import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Exercises the unpacked distribution without the build image's Z3 installation. */
public class ReleaseCheck {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SUBJECT = "example.SmokeSubject";

    public static void main(String[] args) throws Exception {
        Path home = Path.of(args[0]).toAbsolutePath();
        Path work = Path.of("experiment with spaces").toAbsolutePath();
        Files.createDirectories(work);
        run(work, "javac", "-cp", home.resolve("maze.jar").toString(), "-d", "extensions",
                home.resolve("examples/search-extensions/src/research/DepthSearch.java").toString(),
                home.resolve("examples/search-extensions/src/research/DepthWindowHeuristic.java").toString());
        run(work, "jar", "--create", "--file", "research.jar", "-C", "extensions", ".");
        run(work, "javac", "-d", "classes",
                home.resolve("examples/search-extensions/subject/example/SmokeSubject.java").toString());
        for (String mode : List.of("symbolic", "concrete")) {
            check(home, work, mode, "baseline", List.of("--strategy", "BFS"));
            check(home, work, mode, "strategy", List.of("--plugin", "research.jar",
                    "--strategy", "research.DepthSearch"));
            check(home, work, mode, "heuristic", List.of("--plugin", "research.jar",
                    "--strategy", "PS", "--heuristic", "research.DepthWindowHeuristic"));
            check(home, work, mode, "composed", List.of("--plugin", "research.jar", "--search-config",
                    home.resolve("examples/search-extensions/search.json").toString()));
        }
        Path output = work.resolve("invalid");
        List<String> invalid = command(home, output, "symbolic", List.of("--strategy", "missing.Strategy"));
        if (new ProcessBuilder(invalid).directory(work.toFile()).inheritIO().start().waitFor() == 0) {
            throw new AssertionError("Invalid extension succeeded");
        }
        JsonNode status = JSON.readTree(output.resolve(SUBJECT + "-run-status.json").toFile());
        if (!status.path("outcome").asText().equals("failed")) {
            throw new AssertionError("Invalid extension did not record failure");
        }
        System.out.println("Release archive passed: 8 generation/extension/suite checks and loud failure.");
    }

    private static void check(Path home, Path work, String mode, String name, List<String> search) throws Exception {
        Path output = work.resolve(mode + "-" + name);
        run(work, command(home, output, mode, search).toArray(String[]::new));
        JsonNode status = JSON.readTree(output.resolve(SUBJECT + "-run-status.json").toFile());
        if (!status.path("outcome").asText().equals("completed")
                || !status.path("target").asText().equals(SUBJECT)
                || !status.path("mode").asText().equals(mode)) {
            throw new AssertionError("Missing matching completion: " + output);
        }
        String classpath = home.resolve("maze.jar") + ":" + work.resolve("classes") + ":" + output;
        run(work, "javac", "-cp", classpath, "-d", output.toString(), output.resolve("SmokeSubjectTest.java").toString());
        run(work, "java", "-cp", classpath, "org.junit.runner.JUnitCore", "SmokeSubjectTest");
    }

    private static List<String> command(Path home, Path output, String mode, List<String> search) {
        List<String> command = new ArrayList<>(List.of(home.resolve("maze").toString(),
                "--classpath", "classes", "--class-name", SUBJECT, "--output-path", output.toString(),
                "--time-budget", "5", "--minimization=true", "--junit-version", "JUnit4",
                "--concrete-driven=" + mode.equals("concrete")));
        command.addAll(search);
        return command;
    }

    private static void run(Path cwd, String... command) throws Exception {
        if (new ProcessBuilder(command).directory(cwd.toFile()).inheritIO().start().waitFor() != 0) {
            throw new AssertionError("Command failed: " + List.of(command));
        }
    }
}
