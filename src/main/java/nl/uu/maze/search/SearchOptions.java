package nl.uu.maze.search;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.uu.maze.search.heuristic.SearchHeuristic;

/**
 * Constructor-only, strictly typed extension options. Optional readers record their
 * defaults; supplied but unread keys are rejected when construction finishes.
 * Read supported options even when another option makes their effect conditional.
 */
public final class SearchOptions {
    private final ObjectNode supplied;
    private final ObjectNode effective = SearchConfiguration.JSON.createObjectNode();
    private final Set<String> read = new HashSet<>();
    private final String location;
    private final Function<List<SearchConfiguration.Component>, List<SearchHeuristic>> resolver;
    private boolean finished;

    SearchOptions(ObjectNode supplied, String location,
            Function<List<SearchConfiguration.Component>, List<SearchHeuristic>> resolver) {
        this.supplied = supplied;
        this.location = location;
        this.resolver = resolver;
    }

    private JsonNode value(String key, Object fallback, boolean required) {
        if (finished) throw new IllegalStateException("SearchOptions may only be read during construction");
        read.add(key);
        JsonNode value = supplied.get(key);
        if (value == null) {
            if (required) throw SearchConfiguration.error(location + "." + key, "required option is missing");
            value = SearchConfiguration.JSON.valueToTree(fallback);
        }
        effective.set(key, value);
        return value;
    }

    private IllegalArgumentException type(String key, String expected) {
        return SearchConfiguration.error(location + "." + key, "expected " + expected);
    }

    private int integer(String key, int fallback, boolean required) {
        JsonNode v = value(key, fallback, required);
        if (!v.isIntegralNumber() || !v.canConvertToInt()) throw type(key, "a 32-bit integer");
        return v.intValue();
    }
    public int getInt(String key, int fallback) { return integer(key, fallback, false); }
    public int getRequiredInt(String key) { return integer(key, 0, true); }

    private long longInteger(String key, long fallback, boolean required) {
        JsonNode v = value(key, fallback, required);
        if (!v.isIntegralNumber() || !v.canConvertToLong()) throw type(key, "a 64-bit integer");
        return v.longValue();
    }
    public long getLong(String key, long fallback) { return longInteger(key, fallback, false); }
    public long getRequiredLong(String key) { return longInteger(key, 0, true); }

    private double number(String key, double fallback, boolean required) {
        JsonNode v = value(key, fallback, required);
        if (!v.isNumber() || !Double.isFinite(v.doubleValue())) throw type(key, "a finite number");
        return v.doubleValue();
    }
    public double getDouble(String key, double fallback) { return number(key, fallback, false); }
    public double getRequiredDouble(String key) { return number(key, 0, true); }

    private boolean bool(String key, boolean fallback, boolean required) {
        JsonNode v = value(key, fallback, required);
        if (!v.isBoolean()) throw type(key, "a boolean");
        return v.booleanValue();
    }
    public boolean getBoolean(String key, boolean fallback) { return bool(key, fallback, false); }
    public boolean getRequiredBoolean(String key) { return bool(key, false, true); }

    private String string(String key, String fallback, boolean required) {
        JsonNode v = value(key, fallback, required);
        if (!v.isTextual()) throw type(key, "a string");
        return v.textValue();
    }
    public String getString(String key, String fallback) { return string(key, fallback, false); }
    public String getRequiredString(String key) { return string(key, null, true); }

    /** Resolve a required, nonempty list through the same loader as top-level search. */
    public List<SearchHeuristic> getHeuristics(String key) {
        return heuristics(key, null, true);
    }

    /** Resolve the supplied list, or a fresh uniform heuristic when absent. */
    public List<SearchHeuristic> getHeuristicsOrUniform(String key) {
        return heuristics(key, List.of(java.util.Map.of("name", "UH", "weight", 1.0)), false);
    }

    private List<SearchHeuristic> heuristics(String key, Object fallback, boolean required) {
        return resolver.apply(SearchConfiguration.components(value(key, fallback, required), location + "." + key, true));
    }

    ObjectNode finish() {
        finished = true;
        supplied.fieldNames().forEachRemaining(key -> {
            if (!read.contains(key)) throw SearchConfiguration.error(location + "." + key, "unrecognized option");
        });
        return effective.deepCopy();
    }
}
