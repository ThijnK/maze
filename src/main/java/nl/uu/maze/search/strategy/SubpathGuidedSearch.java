package nl.uu.maze.search.strategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import nl.uu.maze.search.SearchTarget;
import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/**
 * Subpath-Guided Search (SGS) strategy.
 * <p>
 * Tracks frequency of execution subpaths and prioritizes states with
 * rarely seen patterns. This drives exploration toward less-visited code
 * regions.
 */
public class SubpathGuidedSearch<T extends SearchTarget> extends SearchStrategy<T> {
    private static final int SUBPATH_LENGTH = 2; // Length of the subpath to consider

    private final Map<Integer, Integer> subpathCounts = new HashMap<>();
    private final List<T> targets = new ArrayList<>();
    private final Random random = new Random();

    public String getName() {
        return "SubpathGuidedSearch";
    }

    private int calculateSubpathHash(T target) {
        List<Integer> branchHistory = target.getBranchHistory();
        int subpathHash = 0;
        int i = Math.max(0, branchHistory.size() - SUBPATH_LENGTH);
        for (; i < branchHistory.size(); i++) {
            subpathHash = 31 * subpathHash + branchHistory.get(i);
        }
        return subpathHash;
    }

    @Override
    public void add(T target) {
        // If the state just branched, we have a new subpath to consider (because
        // subpaths in this context are only the branches that are taken, so a subpath
        // changes only when a branch is taken)
        if (target.getPrevStmt() instanceof JIfStmt || target.getPrevStmt() instanceof JSwitchStmt) {
            int subpathHash = calculateSubpathHash(target);
            subpathCounts.put(subpathHash, subpathCounts.getOrDefault(subpathHash, 0) + 1);
        }
        targets.add(target);
    }

    @Override
    public T next() {
        if (targets.size() <= 1) {
            return targets.isEmpty() ? null : targets.removeFirst();
        }

        // Calculate subpath counts for all states
        Map<Integer, Integer> stateSubpathCounts = new HashMap<>();
        for (T target : targets) {
            int subpathHash = calculateSubpathHash(target);
            stateSubpathCounts.put(subpathHash, subpathCounts.getOrDefault(subpathHash, 0));
        }

        // Select the state with the least frequent subpath, breaking ties randomly
        int minCount = Integer.MAX_VALUE;
        List<T> candidates = new ArrayList<>();
        for (T state : targets) {
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
        targets.remove(selected);
        return selected;
    }

    @Override
    public void remove(T target) {
        targets.remove(target);
    }

    @Override
    public int size() {
        return targets.size();
    }

    @Override
    public void reset() {
        targets.clear();
        subpathCounts.clear();
    }

    @Override
    public Collection<T> getAll() {
        return targets;
    }

    @Override
    public boolean requiresBranchHistoryData() {
        return true;
    }
}
