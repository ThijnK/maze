package nl.uu.maze.main.cli;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import com.fasterxml.jackson.databind.ObjectMapper;

/** Positive completion evidence for one CLI invocation, independent of test/CSV leftovers. */
final class RunStatus {
    private final Path path;
    private final Map<String, Object> data = new LinkedHashMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();

    RunStatus(Path output, String target, boolean concrete, Map<String, Object> requested) throws IOException {
        Files.createDirectories(output);
        path = output.resolve(target + "-run-status.json");
        // Invalidate old completion before any configuration parsing or extension construction.
        Files.deleteIfExists(path);
        Files.deleteIfExists(output.resolve(target + "-test-summary.csv"));
        data.put("invocationId", UUID.randomUUID().toString());
        data.put("startedAt", Instant.now().toString());
        data.put("target", target);
        data.put("mode", concrete ? "concrete" : "symbolic");
        data.put("requestedSearch", requested);
        data.put("outcome", "running");
        write();
    }

    void search(Map<String, Object> search) throws IOException {
        data.put("search", search);
        write();
    }

    void replay(int limit, Map<String, Integer> aborted) throws IOException {
        data.put("candidateReplay", Map.of("maxTraceEntries", limit, "maxSymbolicSteps", limit, "aborted", aborted));
        write();
    }

    void seed(Long seed) throws IOException {
        data.put("seed", seed);
        write();
    }

    void configuration(java.util.List<Map<String, Object>> configuration) throws IOException {
        data.put("configuration", configuration);
        write();
    }

    void finish(Throwable failure) throws IOException {
        data.put("outcome", failure == null ? "completed" : "failed");
        data.put("finishedAt", Instant.now().toString());
        if (failure != null) data.put("error", failure.toString());
        write();
    }

    private void write() throws IOException {
        Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), ".maze-status-", ".json");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), data);
            try {
                Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
