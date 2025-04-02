package nl.uu.maze.search.concrete;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import sootup.core.jimple.common.stmt.JIfStmt;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;

/**
 * Symbolic-driven search strategy for subpath-guided search.
 * It selects the next candidate whose length-n subpath has occurred least
 * often.
 */
public class SubpathGuidedSearch extends ConcreteSearchStrategy {
    private static final int SUBPATH_LENGTH = 2; // Length of the subpath to consider

    private final Map<Integer, Integer> subpathCounts = new HashMap<>();
    private final List<PathConditionCandidate> candidates = new ArrayList<>();
    private final Random random = new Random();

    public String getName() {
        return "SubpathGuidedSearch";
    }

    private int calculateSubpathHash(PathConditionCandidate candidate) {
        List<Integer> branchHistory = candidate.getBranchHistory();
        int subpathHash = 0;
        int i = Math.max(0, branchHistory.size() - SUBPATH_LENGTH);
        for (; i < branchHistory.size(); i++) {
            subpathHash = 31 * subpathHash + branchHistory.get(i);
        }
        return subpathHash;
    }

    @Override
    public void add(PathConditionCandidate candidate) {
        // If the candidate just branched, we have a new subpath to consider (because
        // subpaths in this context are only the branches that are taken, so a subpath
        // changes only when a branch is taken)
        if (candidate.getPrevStmt() instanceof JIfStmt || candidate.getPrevStmt() instanceof JSwitchStmt) {
            int subpathHash = calculateSubpathHash(candidate);
            subpathCounts.put(subpathHash, subpathCounts.getOrDefault(subpathHash, 0) + 1);
        }
        candidates.add(candidate);
    }

    @Override
    public PathConditionCandidate next() {
        if (candidates.size() <= 1) {
            return candidates.isEmpty() ? null : candidates.getFirst();
        }

        // Calculate subpath counts for all candidates
        Map<Integer, Integer> candidateSubpathCounts = new HashMap<>();
        for (PathConditionCandidate candidate : candidates) {
            int subpathHash = calculateSubpathHash(candidate);
            candidateSubpathCounts.put(subpathHash, subpathCounts.getOrDefault(subpathHash, 0));
        }

        // Select the candidate with the least frequent subpath, breaking ties randomly
        int minCount = Integer.MAX_VALUE;
        List<PathConditionCandidate> selectionCandidates = new ArrayList<>();
        for (PathConditionCandidate candidate : candidates) {
            int subpathHash = calculateSubpathHash(candidate);
            int count = candidateSubpathCounts.get(subpathHash);
            if (count < minCount) {
                minCount = count;
                selectionCandidates.clear();
                selectionCandidates.add(candidate);
            } else if (count == minCount) {
                selectionCandidates.add(candidate);
            }
        }
        PathConditionCandidate selected = selectionCandidates
                .get(selectionCandidates.size() > 1 ? random.nextInt(selectionCandidates.size()) : 0);
        candidates.remove(selected);
        return selected;
    }

    @Override
    public void remove(PathConditionCandidate candidate) {
        candidates.remove(candidate);
    }

    @Override
    public void reset() {
        candidates.clear();
        subpathCounts.clear();
    }

    @Override
    public boolean requiresBranchHistoryData() {
        return true;
    }
}
