package nl.uu.tests.maze.search;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import nl.uu.maze.execution.EngineConfiguration;
import nl.uu.maze.search.*;
import nl.uu.maze.search.heuristic.SearchHeuristic;
import nl.uu.maze.search.strategy.BFS;
import nl.uu.maze.search.strategy.PathCoverSearch;
import nl.uu.maze.search.strategy.SearchStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SearchModeTest {
    private static String component(Class<?> type, SearchMode mode) {
        return "{\"name\":\"" + type.getName() + "\",\"options\":{\"mode\":\"" + mode + "\"}}";
    }

    private static SearchStrategy<SearchTarget> create(String entries, SearchMode mode) throws Exception {
        try (var session = new SearchSession(List.of())) {
            return session.createStrategy(SearchConfiguration.parse("{\"strategies\":[" + entries + "]}"),
                    1000, mode == SearchMode.CONCRETE);
        }
    }

    @ParameterizedTest @EnumSource(SearchMode.class)
    void strategiesDeclareTheirModeAndRejectIncompatibleInterleaving(SearchMode supported) throws Exception {
        String entries = "{\"name\":\"BFS\"}," + component(ModeSearch.class, supported);
        var search = create(entries, supported);
        assertTrue(search.supportsMode(supported));
        assertFalse(search.supportsMode(other(supported)));
        var failure = assertThrows(IllegalArgumentException.class, () -> create(entries, other(supported)));
        assertTrue(failure.getMessage().contains("$.strategies[1] (" + ModeSearch.class.getName() + ")"));
        assertTrue(failure.getMessage().contains("does not support"));
    }

    @ParameterizedTest @EnumSource(SearchMode.class)
    void heuristicsAreCheckedForBothBuiltInAndExternalOwners(SearchMode supported) throws Exception {
        String heuristic = component(ModeHeuristic.class, supported);
        for (String owner : List.of("PS", SearchConfigurationTest.HeuristicOwner.class.getName())) {
            String entry = "{\"name\":\"" + owner + "\",\"options\":{\"heuristics\":[" + heuristic + "]}}";
            var search = create(entry, supported);
            assertTrue(search.supportsMode(supported));
            if (owner.equals("PS")) assertFalse(search.supportsMode(other(supported)));
            var failure = assertThrows(IllegalArgumentException.class, () -> create(entry, other(supported)));
            assertTrue(failure.getMessage().contains("$.strategies[0].options.heuristics[0]"));
            assertTrue(failure.getMessage().contains(ModeHeuristic.class.getName()));
        }
    }

    @Test void pcsAndItsExternalSubclassesUseTheSameCompatibilityCheck() throws Exception {
        var configuration = EngineConfiguration.getInstance();
        int previous = configuration.pathLengthCoverage;
        configuration.pathLengthCoverage = 1;
        try {
            for (String name : List.of("PCS", "PathCoverSearch", ExternalPCS.class.getName())) {
                String entry = "{\"name\":\"" + name + "\"}";
                assertTrue(create(entry, SearchMode.SYMBOLIC).supportsMode(SearchMode.SYMBOLIC));
                var failure = assertThrows(IllegalArgumentException.class, () -> create(entry, SearchMode.CONCRETE));
                assertTrue(failure.getMessage().contains("does not support concrete-driven mode"));
            }
        } finally {
            configuration.pathLengthCoverage = previous;
        }
    }

    @Test void compatibilityCallbackFailuresKeepTheirComponentIdentity() {
        String strategy = "{\"name\":\"" + BrokenModeSearch.class.getName() + "\"}";
        String heuristic = "{\"name\":\"PS\",\"options\":{\"heuristics\":[{\"name\":\""
                + BrokenModeHeuristic.class.getName() + "\"}]}}";
        for (String entry : List.of(strategy, heuristic)) {
            var failure = assertThrows(SearchExecutionException.class, () -> create(entry, SearchMode.SYMBOLIC));
            assertTrue(failure.getMessage().contains("supportsMode"));
            assertTrue(failure.getMessage().contains(entry.equals(strategy)
                    ? BrokenModeSearch.class.getName() : BrokenModeHeuristic.class.getName()));
            assertEquals("deliberate mode failure", failure.getCause().getMessage());
        }
    }

    @Test void defaultsAndEngineAdaptersDescribeTheirSupportedModes() throws Exception {
        var search = create("{\"name\":\"BFS\"}", SearchMode.SYMBOLIC);
        for (SearchMode mode : SearchMode.values()) assertTrue(search.supportsMode(mode));
        assertTrue(search.toSymbolic().supportsMode(SearchMode.SYMBOLIC));
        assertFalse(search.toSymbolic().supportsMode(SearchMode.CONCRETE));
        assertTrue(search.toConcrete().supportsMode(SearchMode.CONCRETE));
        assertFalse(search.toConcrete().supportsMode(SearchMode.SYMBOLIC));
    }

    private static SearchMode other(SearchMode mode) {
        return mode == SearchMode.SYMBOLIC ? SearchMode.CONCRETE : SearchMode.SYMBOLIC;
    }

    public static class ModeSearch extends BFS<SearchTarget> {
        private final SearchMode supported;
        public ModeSearch(SearchOptions options) { supported = SearchMode.valueOf(options.getRequiredString("mode")); }
        @Override public boolean supportsMode(SearchMode mode) { return mode == supported; }
    }

    public static class ModeHeuristic extends SearchHeuristic {
        private final SearchMode supported;
        public ModeHeuristic(double weight, SearchOptions options) {
            super(weight);
            supported = SearchMode.valueOf(options.getRequiredString("mode"));
        }
        @Override public boolean supportsMode(SearchMode mode) { return mode == supported; }
        @Override public String getName() { return "mode-specific"; }
        @Override public <T extends SearchTarget> double calculateWeight(T target) { return 1; }
    }

    public static class ExternalPCS extends PathCoverSearch {
        public ExternalPCS(SearchOptions options) {}
    }

    public static class BrokenModeSearch extends BFS<SearchTarget> {
        public BrokenModeSearch(SearchOptions options) {}
        @Override public boolean supportsMode(SearchMode mode) { throw new Error("deliberate mode failure"); }
    }

    public static class BrokenModeHeuristic extends SearchHeuristic {
        public BrokenModeHeuristic(double weight, SearchOptions options) { super(weight); }
        @Override public boolean supportsMode(SearchMode mode) { throw new Error("deliberate mode failure"); }
        @Override public String getName() { return "broken"; }
        @Override public <T extends SearchTarget> double calculateWeight(T target) { return 1; }
    }
}
