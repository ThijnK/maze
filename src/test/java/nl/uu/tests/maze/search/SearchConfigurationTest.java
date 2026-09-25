package nl.uu.tests.maze.search;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.AbstractCollection;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import nl.uu.maze.search.*;
import nl.uu.maze.search.heuristic.SearchHeuristic;
import nl.uu.maze.search.strategy.SearchStrategy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchConfigurationTest {
    private static String named(Class<?> type, String options) {
        return "{\"name\":\"" + type.getName() + "\",\"options\":" + options + "}";
    }
    private static SearchConfiguration config(String entries) throws Exception {
        return SearchConfiguration.parse("{\"strategies\":[" + entries + "]}");
    }
    private static String failure(String entries) {
        return assertThrows(Exception.class, () -> {
            try (var session = new SearchSession(List.of())) {
                session.createStrategy(config(entries), 1000, false);
            }
        }).getMessage();
    }

    @Test void omittedOptionalOptionsRecordDefaults() throws Exception {
        try (var session = new SearchSession(List.of())) {
            session.createStrategy(config(named(OptionsSearch.class, "{}")), 1000, false);
            var instances = (List<?>) session.describe().get("instances");
            var options = (Map<?, ?>) ((Map<?, ?>) instances.getFirst()).get("options");
            assertEquals(Map.of("window", 32, "large", 5000000000L, "ratio", 0.5, "enabled", true, "label", "test"), options);
        }
    }

    @ParameterizedTest @ValueSource(strings = {"\"32\"", "1.5", "null", "true", "2147483648", "[]", "{}"})
    void integersAreNotCoerced(String value) {
        assertTrue(failure(named(OptionsSearch.class, "{\"window\":" + value + "}"))
                .contains("$.strategies[0].options.window"));
    }

    @Test void suppliedUnknownOptionsFailButAbsentOptionalOnesDoNot() {
        assertTrue(failure(named(OptionsSearch.class, "{\"windwo\":4}")).contains("options.windwo: unrecognized"));
        assertTrue(failure(named(RequiredSearch.class, "{}")).contains("options.window: required"));
        assertTrue(failure(named(OptionsSearch.class, "{\"window\":0}")).contains("window must be positive"));
    }

    @ParameterizedTest @ValueSource(strings = {
        "{}", "{\"strategies\":[]}", "{\"strategies\":null}",
        "{\"strategies\":[{\"name\":\"BFS\",\"name\":\"DFS\"}]}",
        "{\"strategies\":[{\"name\":\"BFS\",\"options\":{\"x\":1,\"x\":2}}]}",
        "{\"strategies\":[{\"name\":\"BFS\",\"weight\":1}]}",
        "{\"strategies\":[{\"name\":\"BFS\",\"options\":null}]}",
        "{\"strategies\":[{\"name\":\"BFS\"}],\"typo\":1}",
        "{\"strategies\":[{\"name\":\"BFS\"}]} {}"
    }) void malformedStructureFails(String json) {
        assertThrows(Exception.class, () -> SearchConfiguration.parse(json));
    }

    @Test void psDefaultsAndExplicitEmptyListDiffer() throws Exception {
        try (var session = new SearchSession(List.of())) {
            assertTrue(session.createStrategy(config("{\"name\":\"PS\"}"), 0, false).getName().contains("UniformHeuristic"));
        }
        assertTrue(failure("{\"name\":\"PS\",\"options\":{\"heuristics\":[]}}").contains("options.heuristics: expected a nonempty"));
    }

    @Test void weightsAndUnusedCliHeuristicsFailLoudly() {
        for (double weight : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> SearchConfiguration.fromCli(List.of("PS"), List.of("UH"), List.of(weight), true));
        }
        assertThrows(IllegalArgumentException.class, () -> SearchConfiguration.fromCli(List.of("DFS"), List.of("UH"), List.of(1.0), true));
        assertThrows(IllegalArgumentException.class, () -> SearchConfiguration.fromCli(List.of("PS"), List.of("UH"), List.of(1.0, 2.0), true));
        assertTrue(failure("{\"name\":\"PS\",\"options\":{\"heuristics\":[{\"name\":\"UH\",\"weight\":\"1\"}]}}").contains("heuristics[0].weight"));
    }

    @Test void duplicateOccurrencesOwnStateAndReceivePeerSelection() throws Exception {
        RecordingSearch.instances.clear();
        String entry = named(RecordingSearch.class, "{}");
        try (var session = new SearchSession(List.of())) {
            var strategy = session.createStrategy(config(entry + "," + entry), 1000, false);
            assertEquals(2, RecordingSearch.instances.size());
            assertNotSame(RecordingSearch.instances.get(0), RecordingSearch.instances.get(1));
            var target = new TestTarget();
            strategy.add(target);
            assertSame(target, strategy.next());
            assertTrue(RecordingSearch.instances.stream().allMatch(s -> s.size() == 0));
            assertEquals(1, RecordingSearch.instances.get(1).selected);
        }
    }

    @Test void nestedHeuristicsAreFreshAndReceiveReset() throws Exception {
        RecordingHeuristic.instances.clear();
        String h = named(RecordingHeuristic.class, "{}");
        String ps = "{\"name\":\"PS\",\"options\":{\"heuristics\":[" + h + "," + h + "]}}";
        try (var session = new SearchSession(List.of())) {
            var strategy = session.createStrategy(config(ps + "," + ps), 1000, false);
            assertEquals(4, RecordingHeuristic.instances.size());
            strategy.reset();
            assertTrue(RecordingHeuristic.instances.stream().allMatch(x -> x.resets == 1));
        }
    }

    @Test void callbacksAndLazyCollectionsPreserveFailureIdentity() throws Exception {
        try (var session = new SearchSession(List.of())) {
            var strategy = session.createStrategy(config(named(FailingSearch.class, "{}")), 1000, false);
            var next = assertThrows(SearchExecutionException.class, strategy::next);
            assertTrue(next.getMessage().contains("$.strategies[0]"));
            assertInstanceOf(IllegalStateException.class, next.getCause());
            assertEquals("deliberate failure", next.getCause().getMessage());
            assertTrue(assertThrows(SearchExecutionException.class, strategy::getAll).getMessage().contains("getAll/iteration"));
            // Conversion must not dispatch the extension's overridable helper.
            assertThrows(SearchExecutionException.class, () -> strategy.toConcrete().next());
        }
    }

    @Test void invalidNamesFail() throws Exception {
        assertTrue(failure("{\"name\":\"BFFS\"}").contains("BFFS"));
        assertTrue(failure("{\"name\":\"PS\",\"options\":{\"heuristics\":[{\"name\":\"RCH\"}]}}").contains("RCH"));
        try (var session = new SearchSession(List.of())) {
            assertNotNull(session.createStrategy(SearchConfiguration.fromCli(List.of("PS"), List.of("RCDH", "RCPH"), List.of(), true), 1000, false));
        }
    }

    @ParameterizedTest @ValueSource(strings = {"getName", "add", "addCollection", "remove", "select", "next",
            "size", "reset", "getAll", "getTotalExploredCount", "requiresPathTargetingAndTracking"})
    void everyStrategyCallbackClassifiesEvenPlainErrors(String callback) throws Exception {
        try (var session = new SearchSession(List.of())) {
            var strategy = session.createStrategy(config(named(CallbackFailure.class,
                    "{\"callback\":\"" + callback + "\"}")), 1000, false);
            var target = new TestTarget();
            var failure = assertThrows(SearchExecutionException.class, () -> {
                switch (callback) {
                    case "getName" -> strategy.getName();
                    case "add" -> strategy.add(target);
                    case "addCollection" -> strategy.add(List.of(target));
                    case "remove" -> strategy.remove(target);
                    case "select" -> strategy.select(target);
                    case "next" -> strategy.next();
                    case "size" -> strategy.size();
                    case "reset" -> strategy.reset();
                    case "getAll" -> strategy.getAll();
                    case "getTotalExploredCount" -> strategy.getTotalExploredCount();
                    case "requiresPathTargetingAndTracking" -> strategy.requiresPathTargetingAndTracking();
                    default -> throw new AssertionError(callback);
                }
            });
            assertEquals(Error.class, failure.getCause().getClass());
            assertEquals("deliberate " + callback, failure.getCause().getMessage());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"getName", "calculateWeight", "reset"})
    void heuristicFailuresRetainTheirLocationThroughTheOwner(String callback) throws Exception {
        String h = named(HeuristicFailure.class, "{\"callback\":\"" + callback + "\"}");
        try (var session = new SearchSession(List.of())) {
            var strategy = session.createStrategy(config(named(HeuristicOwner.class,
                    "{\"heuristics\":[" + h + "]}")), 1000, false);
            var failure = assertThrows(SearchExecutionException.class, () -> {
                switch (callback) {
                    case "getName" -> strategy.getName();
                    case "reset" -> strategy.reset();
                    default -> strategy.next();
                }
            });
            assertTrue(failure.getMessage().startsWith("$.strategies[0].options.heuristics[0]"));
            assertEquals("deliberate " + callback, failure.getCause().getMessage());
        }
    }

    public static class CallbackFailure extends RecordingSearch {
        final String callback;
        public CallbackFailure(SearchOptions options) { super(options); callback = options.getRequiredString("callback"); }
        void fail(String current) { if (callback.equals(current)) throw new Error("deliberate " + current); }
        @Override public String getName() { fail("getName"); return super.getName(); }
        @Override public void add(SearchTarget t) { fail("add"); super.add(t); }
        @Override public void add(Collection<SearchTarget> ts) { fail("addCollection"); super.add(ts); }
        @Override public void remove(SearchTarget t) { fail("remove"); super.remove(t); }
        @Override public void select(SearchTarget t) { fail("select"); super.select(t); }
        @Override public SearchTarget next() { fail("next"); return super.next(); }
        @Override public int size() { fail("size"); return super.size(); }
        @Override public void reset() { fail("reset"); super.reset(); }
        @Override public Collection<SearchTarget> getAll() { fail("getAll"); return super.getAll(); }
        @Override public int getTotalExploredCount() { fail("getTotalExploredCount"); return super.getTotalExploredCount(); }
        @Override public boolean requiresPathTargetingAndTracking() { fail("requiresPathTargetingAndTracking"); return false; }
    }
    public static class HeuristicOwner extends RecordingSearch {
        final List<SearchHeuristic> heuristics;
        public HeuristicOwner(SearchOptions options) { super(options); heuristics = options.getHeuristics("heuristics"); }
        @Override public String getName() { return heuristics.getFirst().getName(); }
        @Override public SearchTarget next() { heuristics.getFirst().calculateWeight(new TestTarget()); return super.next(); }
        @Override public void reset() { heuristics.forEach(SearchHeuristic::reset); super.reset(); }
    }
    public static class HeuristicFailure extends SearchHeuristic {
        final String callback;
        public HeuristicFailure(double weight, SearchOptions options) { super(weight); callback = options.getRequiredString("callback"); }
        void fail(String current) { if (callback.equals(current)) throw new Error("deliberate " + current); }
        @Override public String getName() { fail("getName"); return "heuristic"; }
        @Override public <T extends SearchTarget> double calculateWeight(T target) { fail("calculateWeight"); return 1; }
        @Override public void reset() { fail("reset"); }
    }

    public static class RecordingSearch extends SearchStrategy<SearchTarget> {
        static final List<RecordingSearch> instances = new ArrayList<>();
        final List<SearchTarget> targets = new ArrayList<>();
        int selected;
        public RecordingSearch(SearchOptions options) { instances.add(this); }
        @Override public String getName() { return "same display name"; }
        @Override public void add(SearchTarget t) { targets.add(t); count++; }
        @Override public void remove(SearchTarget t) { targets.remove(t); }
        @Override public void select(SearchTarget t) { selected++; remove(t); }
        @Override public SearchTarget next() { return targets.isEmpty() ? null : targets.removeFirst(); }
        @Override public int size() { return targets.size(); }
        @Override public void reset() { targets.clear(); }
        @Override public Collection<SearchTarget> getAll() { return targets; }
    }
    public static class OptionsSearch extends RecordingSearch {
        public OptionsSearch(SearchOptions options) {
            super(options);
            if (options.getInt("window", 32) <= 0) throw new IllegalArgumentException("window must be positive");
            options.getLong("large", 5000000000L);
            options.getDouble("ratio", 0.5);
            options.getBoolean("enabled", true);
            options.getString("label", "test");
        }
    }
    public static class RequiredSearch extends RecordingSearch {
        public RequiredSearch(SearchOptions options) { super(options); options.getRequiredInt("window"); }
    }
    public static class RecordingHeuristic extends SearchHeuristic {
        static final List<RecordingHeuristic> instances = new ArrayList<>();
        int resets;
        public RecordingHeuristic(double weight, SearchOptions options) { super(weight); instances.add(this); }
        @Override public String getName() { return "same heuristic name"; }
        @Override public <T extends SearchTarget> double calculateWeight(T target) { return 1; }
        @Override public void reset() { resets++; }
    }
    public static class FailingSearch extends RecordingSearch {
        public FailingSearch(SearchOptions options) { super(options); }
        @Override public SearchTarget next() { throw new IllegalStateException("deliberate failure"); }
        @Override public nl.uu.maze.search.strategy.ConcreteSearchStrategy toConcrete() {
            throw new AssertionError("must not call plugin conversion");
        }
        @Override public Collection<SearchTarget> getAll() {
            return new AbstractCollection<>() {
                @Override public int size() { return 1; }
                @Override public Iterator<SearchTarget> iterator() { throw new IllegalStateException("iterator failed"); }
            };
        }
    }
}
