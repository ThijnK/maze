package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ReplayLimitIntegrationTest {
    @TempDir Path output;

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void skipsOversizedCandidateAndPreservesOtherTests(boolean concrete) throws Exception {
        JsonNode status = run("CUT_ReplayLimits", concrete, 20);
        JsonNode aborted = status.path("candidateReplay").path("aborted");
        assertTrue(aborted.path("trace_entries").asInt() + aborted.path("replay_steps").asInt() > 0);
        String tests = Files.readString(output.resolve("CUT_ReplayLimitsTest.java"));
        assertTrue(tests.contains("aSmall("), tests);
        assertTrue(tests.contains("cSmall("), tests);
        assertFalse(tests.contains("bLong("), tests);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cyclicCutExceptionRemainsAnOrdinaryTestOracle(boolean concrete) throws Exception {
        JsonNode status = run("CUT_CyclicExceptionCause", concrete, 10000);
        assertTrue(status.path("candidateReplay").path("aborted").isEmpty());
        String tests = Files.readString(output.resolve("CUT_CyclicExceptionCauseTest.java"));
        assertTrue(tests.contains("assertThrows(RuntimeException.class"), tests);
    }

    private JsonNode run(String subject, boolean concrete, int limit) throws Exception {
        String target = "nl.uu.tests.maze.CUTs." + subject;
        Path log = output.resolve("process.log");
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> command = List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx512m", "-Djava.library.path=" + System.getProperty("java.library.path"),
                "-cp", classpath, "nl.uu.maze.main.Application",
                "--classpath=target/test-classes", "--class-name=" + target,
                "--output-path=" + output, "--concrete-driven=" + concrete,
                "--strategy=BFS", "--seed=1", "--time-budget=2", "--max-replay-steps=" + limit);
        // A separate JVM isolates MAZE's singleton state and lets us kill a regressed hang.
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        boolean completed;
        try {
            completed = process.waitFor(15, TimeUnit.SECONDS);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        }
        assertTrue(completed, () -> "CLI exceeded its deadline; see " + log);
        assertEquals(0, process.exitValue(), () -> readLog(log));
        JsonNode status = new ObjectMapper().readTree(output.resolve(target + "-run-status.json").toFile());
        assertEquals("completed", status.path("outcome").asText(), status.toString());
        return status;
    }

    private static String readLog(Path path) {
        try { return Files.readString(path); }
        catch (java.io.IOException e) { return e.toString(); }
    }
}
