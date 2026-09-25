package nl.uu.maze.search;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.uu.maze.search.heuristic.SearchHeuristic;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory;
import nl.uu.maze.search.heuristic.SearchHeuristicFactory.ValidSearchHeuristic;
import nl.uu.maze.search.strategy.InterleavedSearch;
import nl.uu.maze.search.strategy.SearchStrategy;
import nl.uu.maze.search.strategy.SearchStrategyFactory;
import nl.uu.maze.search.strategy.SearchStrategyFactory.ValidSearchStrategy;

/**
 * Owns configured search instances and their JAR loader for one invocation.
 * Keep this session open until exploration and output finalization have finished.
 */
public final class SearchSession implements AutoCloseable {
    private final PluginLoader plugins;
    private final List<Map<String, Object>> instances = new ArrayList<>();
    private final Map<String, Map<String, String>> codeSources = new LinkedHashMap<>();
    private boolean constructed;

    public SearchSession(List<Path> jars) throws IOException {
        plugins = new PluginLoader(jars);
        try {
            artifact(SearchSession.class);
        } catch (IOException failure) {
            try { plugins.close(); } catch (IOException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public SearchStrategy<SearchTarget> createStrategy(SearchConfiguration configuration,
            long timeBudget, boolean concreteDriven) {
        if (constructed) throw new IllegalStateException("A search session constructs one search configuration");
        constructed = true;
        SearchMode mode = concreteDriven ? SearchMode.CONCRETE : SearchMode.SYMBOLIC;
        var strategies = new ArrayList<SearchStrategy<SearchTarget>>();
        for (var component : configuration.strategies()) {
            SearchOptions options = options(component, mode);
            ValidSearchStrategy builtin = alias(ValidSearchStrategy.class, component.name());
            @SuppressWarnings("unchecked")
            SearchStrategy<SearchTarget> strategy = builtin == null
                    ? (SearchStrategy<SearchTarget>) plugins.construct(component.name(), SearchStrategy.class,
                            new Class<?>[] { SearchOptions.class }, new Object[] { options }, component.location())
                    : SearchStrategyFactory.createBuiltin(builtin, () -> options.getHeuristicsOrUniform("heuristics"));
            var guarded = SearchGuards.strategy(strategy, identity(component, strategy));
            requireMode(guarded.supportsMode(mode), mode, identity(component, strategy));
            record(component, strategy, options);
            strategies.add(guarded);
        }
        return strategies.size() == 1 ? strategies.getFirst()
                : SearchGuards.strategy(new InterleavedSearch<>(strategies, timeBudget), "$.strategies (InterleavedSearch)");
    }

    private SearchOptions options(SearchConfiguration.Component component, SearchMode mode) {
        return new SearchOptions(component.options(), component.location() + ".options",
                components -> heuristics(components, mode));
    }

    private List<SearchHeuristic> heuristics(List<SearchConfiguration.Component> components, SearchMode mode) {
        var result = new ArrayList<SearchHeuristic>();
        for (var component : components) {
            SearchOptions options = options(component, mode);
            ValidSearchHeuristic builtin = alias(ValidSearchHeuristic.class, component.name());
            SearchHeuristic heuristic = builtin == null
                    ? (SearchHeuristic) plugins.construct(component.name(), SearchHeuristic.class,
                            new Class<?>[] { double.class, SearchOptions.class },
                            new Object[] { component.weight(), options }, component.location())
                    : SearchHeuristicFactory.createHeuristic(component.name(), component.weight());
            var guarded = SearchGuards.heuristic(heuristic, identity(component, heuristic));
            requireMode(guarded.supportsMode(mode), mode, identity(component, heuristic));
            record(component, heuristic, options);
            result.add(guarded);
        }
        return List.copyOf(result);
    }

    private static void requireMode(boolean supported, SearchMode mode, String identity) {
        if (!supported) {
            String name = mode == SearchMode.CONCRETE ? "concrete-driven" : "symbolic-driven";
            throw SearchConfiguration.error(identity, "does not support " + name + " mode");
        }
    }

    private static <E extends Enum<E>> E alias(Class<E> type, String name) {
        try { return Enum.valueOf(type, name); }
        catch (IllegalArgumentException ignored) { return null; }
    }

    private String identity(SearchConfiguration.Component component, Object instance) {
        return component.location() + " (" + instance.getClass().getName() + ")";
    }

    private void record(SearchConfiguration.Component component, Object instance, SearchOptions options) {
        var record = new LinkedHashMap<String, Object>();
        record.put("location", component.location());
        record.put("name", component.name());
        record.put("implementation", instance.getClass().getName());
        try {
            record.put("artifact", artifact(instance.getClass()));
        } catch (IOException e) {
            throw new IllegalArgumentException(identity(component, instance) + ": cannot fingerprint implementation", e);
        }
        if (instance instanceof SearchHeuristic) record.put("weight", component.weight());
        record.put("options", SearchConfiguration.JSON.convertValue(options.finish(), Map.class));
        instances.add(record);
    }

    /** Detached, JSON-compatible provenance; locations identify occurrences, not display names. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> describe() {
        return SearchConfiguration.JSON.convertValue(Map.of("plugins", plugins.artifacts(), "instances", instances,
                "maze", codeSources.values().iterator().next()), Map.class);
    }

    private Map<String, String> artifact(Class<?> type) throws IOException {
        var source = type.getProtectionDomain().getCodeSource();
        String location = source == null ? "unknown" : source.getLocation().toExternalForm();
        if (!codeSources.containsKey(location)) {
            var description = new LinkedHashMap<String, String>();
            description.put("location", location);
            if (source != null && source.getLocation().getProtocol().equals("file")) {
                try {
                    Path path = Path.of(source.getLocation().toURI());
                    if (java.nio.file.Files.isRegularFile(path)) description.put("sha256", PluginLoader.sha256(path));
                } catch (java.net.URISyntaxException e) {
                    throw new IOException("Invalid implementation location: " + location, e);
                }
            }
            codeSources.put(location, Map.copyOf(description));
        }
        return codeSources.get(location);
    }

    @Override public void close() throws IOException { plugins.close(); }
}
