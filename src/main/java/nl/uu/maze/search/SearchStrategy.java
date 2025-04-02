package nl.uu.maze.search;

/** Root interface for search strategy hierarchy */
public interface SearchStrategy<T> {
    /**
     * Returns the full name of this search strategy.
     */
    String getName();

    /**
     * Add an item to the search strategy.
     * 
     * @param item The new item to add
     */
    void add(T item);

    /**
     * Remove an item from the search strategy.
     * 
     * @param item The item to remove
     */
    void remove(T item);

    /**
     * Get the next item to explore.
     * 
     * @return The next item to explore, or null if there are no more items to
     *         explore
     */
    T next();

    /**
     * Reset the search strategy to its initial state.
     */
    void reset();

    /** Whether this search strategy requires data about statement coverage. */
    boolean requiresCoverageData();

    /** Whether this search strategy requires data about branch history. */
    boolean requiresBranchHistoryData();
}
