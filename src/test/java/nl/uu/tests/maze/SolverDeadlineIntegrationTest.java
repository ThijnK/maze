package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SolverDeadlineIntegrationTest {
    @TempDir Path output;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void solverRespectsRunBudgetAndRetainsCompletedTests(boolean concrete) throws Exception {
        String target = "nl.uu.tests.maze.CUTs.CUT_SolverDeadline";
        Path log = output.resolve("process.log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx512m", "-Djava.library.path=" + System.getProperty("java.library.path"),
                "-cp", classpath, "nl.uu.maze.main.Application",
                "--classpath=target/test-classes", "--class-name=" + target,
                "--output-path=" + output, "--concrete-driven=" + concrete,
                "--strategy=BFS", "--seed=1", "--time-budget=1", "--minimization=true")
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed;
        try {
            completed = process.waitFor(8, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        assertTrue(completed, () -> "Solver overran the one-second budget; see " + log);
        assertEquals(0, process.exitValue(), () -> read(log));
        var status = new ObjectMapper().readTree(output.resolve(target + "-run-status.json").toFile());
        assertEquals("completed", status.path("outcome").asText(), status.toString());
        assertTrue(Files.readString(output.resolve("CUT_SolverDeadlineTest.java")).contains("factor("));
        assertTrue(read(log).contains("Time budget exceeded during constraint solving"), () -> read(log));
        assertFalse(read(log).contains("Exception thrown during symbolic execution"), () -> read(log));
    }

    private static String read(Path path) {
        try { return Files.readString(path); }
        catch (java.io.IOException e) { return e.toString(); }
    }
}
