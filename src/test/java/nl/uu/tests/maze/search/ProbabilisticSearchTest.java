package nl.uu.tests.maze.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.search.heuristic.SearchHeuristic;
import nl.uu.maze.search.strategy.ProbabilisticSearch;
import org.junit.jupiter.api.Test;

class ProbabilisticSearchTest {
    @Test
    void interleavedInstancesMeasureTheirOwnWaitingTime() {
        var firstScores = new RecordingHeuristic();
        var secondScores = new RecordingHeuristic();
        var first = new ProbabilisticSearch<TestTarget>(List.of(firstScores));
        var second = new ProbabilisticSearch<TestTarget>(List.of(secondScores));
        for (int i = 0; i < 10; i++) {
            first.next();
        }
        var targets = List.of(new TestTarget(), new TestTarget());
        first.add(targets);
        second.add(targets);

        first.next();
        second.next();

        assertEquals(List.of(1, 1), firstScores.ages);
        assertEquals(List.of(1, 1), secondScores.ages);
    }

    @Test
    void readdedTargetsReceiveANewArrivalAfterSelectionRemovalAndReset() {
        var scores = new RecordingHeuristic();
        var search = new ProbabilisticSearch<TestTarget>(List.of(scores));
        var target = new TestTarget();
        search.add(target);
        search.select(target);
        search.next();
        search.add(target);
        search.remove(target);
        search.next();
        search.add(target);
        search.reset();
        search.next();

        search.add(List.of(target, new TestTarget()));
        search.next();
        assertEquals(List.of(1, 1), scores.ages);
    }

    @Test
    void equalityBasedRemovalReleasesTheActualObjectAndRetainsRepeatedReferences() {
        var scores = new RecordingHeuristic();
        var search = new ProbabilisticSearch<TestTarget>(List.of(scores));
        var first = new EqualTarget();
        var second = new EqualTarget();
        search.add(List.of(first, second, new TestTarget()));
        search.remove(second); // Existing List semantics remove the equal first object.
        search.next();
        assertEquals(List.of(1, 1), scores.ages);

        search.reset();
        scores.ages.clear();
        var repeated = new TestTarget();
        search.add(List.of(repeated, repeated, new TestTarget()));
        search.select(repeated);
        search.next();
        assertEquals(List.of(1, 1), scores.ages);
    }

    private static final class EqualTarget extends TestTarget {
        @Override public boolean equals(Object other) { return other instanceof EqualTarget; }
        @Override public int hashCode() { return 1; }
    }

    private static final class RecordingHeuristic extends SearchHeuristic {
        private final List<Integer> ages = new ArrayList<>();
        private RecordingHeuristic() { super(1); }
        @Override public String getName() { return "recording"; }
        @Override public <T extends SearchTarget> double calculateWeight(T target) {
            ages.add(target.getWaitingTime());
            return 1;
        }
    }
}
