package nl.uu.maze.search;

/** Root interface for search strategy hierarchy */
public interface SearchStrategy {
    public void reset();

    /** Whether this search strategy requires data about statement coverage. */
    public boolean requiresCoverageData();
}
