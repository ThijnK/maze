package nl.uu.maze.search;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** An ordered search description, shared by the command line and JSON input routes. */
public final class SearchConfiguration {
    static final ObjectMapper JSON = new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    record Component(String name, double weight, ObjectNode options, String location) {}
    private final List<Component> strategies;

    private SearchConfiguration(List<Component> strategies) {
        this.strategies = List.copyOf(strategies);
    }

    public static SearchConfiguration read(Path path) throws IOException {
        return parse(java.nio.file.Files.readString(path));
    }

    public static SearchConfiguration parse(String json) throws IOException {
        JsonNode root = JSON.readTree(json);
        object(root, "$", Set.of("strategies"));
        return new SearchConfiguration(components(root.get("strategies"), "$.strategies", false));
    }

    /** Missing weights default to one. Explicit settings must be consumed by PS. */
    public static SearchConfiguration fromCli(List<String> names, List<String> heuristics,
            List<Double> weights, boolean explicitHeuristics) {
        if (names.isEmpty()) {
            throw error("--strategy", "at least one strategy is required");
        }
        if (weights.size() > heuristics.size()) {
            throw error("--weight", "more weights than heuristics");
        }
        var root = JSON.createObjectNode();
        var entries = root.putArray("strategies");
        boolean consumed = false;
        for (String name : names) {
            var entry = entries.addObject().put("name", name);
            if (isProbabilistic(name)) {
                consumed = true;
                var hs = entry.putObject("options").putArray("heuristics");
                for (int i = 0; i < heuristics.size(); i++) {
                    double weight = i < weights.size() ? weights.get(i) : 1;
                    checkWeight(weight, "--weight[" + i + "]");
                    hs.addObject().put("name", heuristics.get(i)).put("weight", weight);
                }
            }
        }
        if (explicitHeuristics && !consumed) {
            throw error("--heuristic/--weight", "these settings require a PS strategy; use --search-config for custom options");
        }
        return new SearchConfiguration(components(entries, "$.strategies", false));
    }

    static boolean isProbabilistic(String name) {
        return Set.of("PS", "Probabilistic", "ProbabilisticSearch").contains(name);
    }

    List<Component> strategies() { return strategies; }

    /** Detached, ordered configuration suitable for experiment records. */
    public List<java.util.Map<String, Object>> describe() {
        return strategies.stream().map(component -> java.util.Map.<String, Object>of(
                "name", component.name(), "options", JSON.convertValue(component.options(), java.util.Map.class)))
                .toList();
    }

    static List<Component> components(JsonNode node, String path, boolean heuristic) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            throw error(path, "expected a nonempty array");
        }
        var result = new ArrayList<Component>();
        for (int i = 0; i < node.size(); i++) {
            String location = path + "[" + i + "]";
            JsonNode entry = node.get(i);
            object(entry, location, heuristic ? Set.of("name", "options", "weight") : Set.of("name", "options"));
            JsonNode name = entry.get("name");
            if (name == null || !name.isTextual() || name.textValue().isBlank()) {
                throw error(location + ".name", "expected a nonempty string");
            }
            JsonNode options = entry.has("options") ? entry.get("options") : JSON.createObjectNode();
            if (!options.isObject()) {
                throw error(location + ".options", "expected an object");
            }
            double weight = 1;
            if (entry.has("weight")) {
                if (!entry.get("weight").isNumber()) {
                    throw error(location + ".weight", "expected a number");
                }
                weight = entry.get("weight").doubleValue();
            }
            checkWeight(weight, location + ".weight");
            result.add(new Component(name.textValue(), weight, ((ObjectNode) options).deepCopy(), location));
        }
        return result;
    }

    static void object(JsonNode node, String path, Set<String> fields) {
        if (node == null || !node.isObject()) {
            throw error(path, "expected an object");
        }
        node.fieldNames().forEachRemaining(key -> {
            if (!fields.contains(key)) {
                throw error(path + "." + key, "unrecognized field");
            }
        });
    }

    static void checkWeight(double weight, String path) {
        if (!Double.isFinite(weight) || weight <= 0) {
            throw error(path, "weight must be finite and positive");
        }
    }

    static IllegalArgumentException error(String path, String message) {
        return new IllegalArgumentException(path + ": " + message);
    }
}
