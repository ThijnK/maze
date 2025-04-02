package nl.uu.maze.search.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import nl.uu.maze.execution.symbolic.SymbolicState;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/**
 * Symbolic-driven search strategy for subpath-guided search.
 * It selects the next state whose length-n subpath has occurred least often.
 */
public class SubpathGuidedSearch extends SymbolicSearchStrategy {
    private static final int SUBPATH_LENGTH = 2; // Length of the subpath to consider

    private final Map<Integer, Integer> subpathCounts = new HashMap<>();
    private final List<SymbolicState> states = new ArrayList<>();
    private final Random random = new Random();

    public String getName() {
        return "SubpathGuidedSearch";
    }

    private int calculateSubpathHash(SymbolicState state) {
        List<Integer> branchHistory = state.getBranchHistory();
        int subpathHash = 0;
        int i = Math.max(0, branchHistory.size() - SUBPATH_LENGTH);
        for (; i < branchHistory.size(); i++) {
            subpathHash = 31 * subpathHash + branchHistory.get(i);
        }
        return subpathHash;
    }

    @Override
    public void add(SymbolicState state) {
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
    public SymbolicState next() {
        if (states.isEmpty()) {
            return null;
        }

        // Calculate subpath counts for all states
        Map<Integer, Integer> stateSubpathCounts = new HashMap<>();
        for (SymbolicState state : states) {
            int subpathHash = calculateSubpathHash(state);
            stateSubpathCounts.put(subpathHash, subpathCounts.getOrDefault(subpathHash, 0));
        }

        // Select the state with the least frequent subpath, breaking ties randomly
        int minCount = Integer.MAX_VALUE;
        List<SymbolicState> candidates = new ArrayList<>();
        for (SymbolicState state : states) {
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
        SymbolicState selected = candidates.get(candidates.size() > 1 ? random.nextInt(candidates.size()) : 0);
        states.remove(selected);
        return selected;
    }

    @Override
    public void remove(SymbolicState state) {
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
