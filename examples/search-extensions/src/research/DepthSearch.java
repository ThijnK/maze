package research;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import nl.uu.maze.search.SearchOptions;
import nl.uu.maze.search.SearchTarget;
import nl.uu.maze.search.strategy.SearchStrategy;

/** Small example: favor shallow targets by default, or deep ones when configured. */
public final class DepthSearch extends SearchStrategy<SearchTarget> {
    private final List<SearchTarget> pending = new ArrayList<>();
    private final boolean preferDeep;

    public DepthSearch(SearchOptions options) {
        preferDeep = options.getBoolean("preferDeep", false);
    }

    @Override public String getName() { return "DepthSearch(preferDeep=" + preferDeep + ")"; }
    @Override public void add(SearchTarget target) { pending.add(target); count++; }
    @Override public void remove(SearchTarget target) { pending.removeIf(item -> item == target); }
    @Override public int size() { return pending.size(); }
    @Override public void reset() { pending.clear(); }
    @Override public Collection<SearchTarget> getAll() { return List.copyOf(pending); }

    @Override public SearchTarget next() {
        if (pending.isEmpty()) return null;
        int best = 0;
        for (int i = 1; i < pending.size(); i++) {
            int comparison = Integer.compare(pending.get(i).getDepth(), pending.get(best).getDepth());
            if (preferDeep ? comparison > 0 : comparison < 0) best = i;
        }
        return pending.remove(best);
    }
    // Inherited select(target) calls remove(target), honoring interleaved peers.
}
