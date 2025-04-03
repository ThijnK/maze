package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import nl.uu.maze.search.SearchStrategy;
import nl.uu.maze.search.SearchTarget;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/**
 * Search strategy for subpath-guided search.
 * It selects the next target whose length-n subpath has occurred least often.
 */
public class SubpathGuidedSearch<T extends SearchTarget> extends SearchStrategy<T> {
    private static final int SUBPATH_LENGTH = 2; // Length of the subpath to consider

    private final Map<Integer, Integer> subpathCounts = new HashMap<>();
    private final List<T> states = new ArrayList<>();
    private final Random random = new Random();

    public String getName() {
        return "SubpathGuidedSearch";
    }

    private int calculateSubpathHash(T state) {
        List<Integer> branchHistory = state.getBranchHistory();
        int subpathHash = 0;
        int i = Math.max(0, branchHistory.size() - SUBPATH_LENGTH);
        for (; i < branchHistory.size(); i++) {
            subpathHash = 31 * subpathHash + branchHistory.get(i);
        }
        return subpathHash;
    }

    @Override
    public void add(T state) {
        // If the state just branched, we have a new subpath to consider (because
        // subpaths in this context are only the branches that are taken, so a subpath
        // changes only when a branch is taken)
        if (state.getPrevStmt() instanceof JIfStmt || state.getPrevStmt() instanceof JSwitchStmt) {
            int subpathHash = calculateSubpathHash(state);
            subpathCounts.put(subpathHash, subpathCounts.getOrDefault(subpathHash, 0) + 1);
        }
        states.add(state);
    }

    @Override
    public T next() {
        if (states.size() <= 1) {
            return states.isEmpty() ? null : states.getFirst();
        }

        // Calculate subpath counts for all states
        Map<Integer, Integer> stateSubpathCounts = new HashMap<>();
        for (T state : states) {
            int subpathHash = calculateSubpathHash(state);
            stateSubpathCounts.put(subpathHash, subpathCounts.getOrDefault(subpathHash, 0));
        }

        // Select the state with the least frequent subpath, breaking ties randomly
        int minCount = Integer.MAX_VALUE;
        List<T> candidates = new ArrayList<>();
        for (T state : states) {
            int subpathHash = calculateSubpathHash(state);
            int count = stateSubpathCounts.get(subpathHash);
            if (count < minCount) {
                minCount = count;
                candidates.clear();
                candidates.add(state);
            } else if (count == minCount) {
                candidates.add(state);
            }
        }
        T selected = candidates.get(candidates.size() > 1 ? random.nextInt(candidates.size()) : 0);
        states.remove(selected);
        return selected;
    }

    @Override
    public void remove(T state) {
        states.remove(state);
    }

    @Override
    public void reset() {
        states.clear();
        subpathCounts.clear();
    }

    @Override
    public boolean requiresBranchHistoryData() {
        return true;
    }
}
