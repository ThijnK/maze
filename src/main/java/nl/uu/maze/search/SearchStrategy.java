package nl.uu.maze.search;

/** Root interface for search strategy hierarchy */
public interface SearchStrategy<T> {
    /**
     * Returns the full name of this search strategy.
     */
    public String getName();

    /**
     * Add an item to the search strategy.
     * 
     * @param item The new item to add
     */
    public void add(T item);

    /**
     * Remove a item from the search strategy.
     * 
     * @param item The item to remove
     */
    public void remove(T item);

    /**
     * Get the next item to explore.
     * 
     * @return The next item to explore, or null if there are no more items to
     *         explore
     */
    public T next();

    /**
     * Reset the search strategy to its initial state.
     */
    public void reset();

    /** Whether this search strategy requires data about statement coverage. */
    public boolean requiresCoverageData();
}
