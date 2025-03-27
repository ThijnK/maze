package nl.uu.maze.search.heuristic;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating search heuristics, which are used in
 * ProbabilisticSearch to determine the weight of each state in the random
 * selection.
 */
public class SearchHeuristicFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchHeuristicFactory.class);

    /**
     * Creates a search heuristic based on the given name and weight.
     * 
     * @param name   The name of the search heuristic
     * @param weight The weight of the search heuristic
     * @return A search heuristic
     * @throws IllegalArgumentException If the name is unknown
     * @throws NumberFormatException    If the weight is not a valid double
     */
    public static SearchHeuristic createHeuristic(String name, double weight) {
        return switch (name.trim()) {
            case "UniformHeuristic", "Uniform", "UH" -> new nl.uu.maze.search.heuristic.UniformHeuristic();
            case "DistanceToUncoveredHeuristic", "DistanceToUncovered", "DTUH" ->
                new nl.uu.maze.search.heuristic.DistanceToUncoveredHeuristic(weight);
            case "RecentCoverageHeuristic", "RecentCoverage", "RCH" ->
                new nl.uu.maze.search.heuristic.RecentCoverageHeuristic(weight);
            default -> throw new IllegalArgumentException("Unknown search heuristic: " + name);
        };
    }

    /**
     * Creates an array of search heuristics based on the given names and weights.
     * 
     * @param namesString   The names of the search heuristics as a comma-separated
     *                      string
     * @param weightsString The weights of the search heuristics as a
     *                      comma-separated
     *                      string
     * @return An array of search heuristics
     * @throws NumberFormatException    If a weight is not a valid double
     * @throws IllegalArgumentException If a weight is not positive
     * @see #createHeuristic(String, String)
     */
    public static List<SearchHeuristic> createHeuristics(String namesString, String weightsString) {
        String[] names = namesString.split(",");
        String[] weights = weightsString.split(",");
        List<SearchHeuristic> heuristics = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++) {
            try {
                String weightString = weights.length >= i + 1 ? weights[i] : "";
                double weight = weightString.isEmpty() ? 1.0 : Double.parseDouble(weightString);
                if (weight <= 0) {
                    throw new IllegalArgumentException("Weight must be positive");
                }
                heuristics.add(createHeuristic(names[i], weight));
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown search heuristic: {}, skipping", names[i]);
            }
        }
        return heuristics;
    }
}
