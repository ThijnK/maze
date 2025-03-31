package nl.uu.maze.search.heuristic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating search heuristics, which are used in
 * ProbabilisticSearch to determine the weight of each state in the random
 * selection.
 */
public class SearchHeuristicFactory {
    private final static Logger logger = LoggerFactory.getLogger(SearchHeuristicFactory.class);
    private final static Set<String> validHeuristics = Set.of(
            "UniformHeuristic", "Uniform", "UH",
            "DistanceToUncoveredHeuristic", "DistanceToUncovered", "DTUH",
            "RecentCoverageHeuristic", "RecentCoverage", "RCH",
            "DepthHeuristic", "Depth", "DH",
            "QueryCostHeuristic", "QueryCost", "QCH",
            "CallDepthHeuristic", "CallDepth", "CDH");

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
            case "UniformHeuristic", "Uniform", "UH" -> new UniformHeuristic();
            case "DistanceToUncoveredHeuristic", "DistanceToUncovered", "DTUH" ->
                new DistanceToUncoveredHeuristic(weight);
            case "RecentCoverageHeuristic", "RecentCoverage", "RCH" -> new RecentCoverageHeuristic(weight);
            case "DepthHeuristic", "Depth", "DH" -> new DepthHeuristic(weight);
            case "QueryCostHeuristic", "QueryCost", "QCH" -> new QueryCostHeuristic(weight);
            case "CallDepthHeuristic", "CallDepth", "CDH" -> new CallDepthHeuristic(weight);
            default -> throw new IllegalArgumentException("Unknown search heuristic: " + name);
        };
    }

    /**
     * Creates a list of search heuristics based on the given names and weights.
     * Defaults to the Uniform heuristic with weight 1.0 if no names are provided.
     * 
     * @param names   The names of the search heuristics
     * @param weights The weights of the search heuristics
     * @return An array of search heuristics
     * @throws NumberFormatException    If a weight is not a valid double
     * @throws IllegalArgumentException If a weight is not positive
     * @see #createHeuristic(String, String)
     */
    public static List<SearchHeuristic> createHeuristics(List<String> names, List<Double> weights) {
        List<SearchHeuristic> heuristics = new ArrayList<>(names.size());
        if (names.isEmpty()) {
            logger.warn("No search heuristics provided, using default Uniform heuristic with weight 1.0");
            return List.of(new UniformHeuristic());
        }

        for (int i = 0; i < names.size(); i++) {
            try {
                double weight = weights.size() >= i + 1 ? weights.get(i) : 1.0;
                if (weight <= 0) {
                    throw new IllegalArgumentException("Weight must be positive");
                }
                heuristics.add(createHeuristic(names.get(i), weight));
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown search heuristic: {}, skipping", names.get(i));
            }
        }
        return heuristics;
    }

    /**
     * Checks if the given heuristic name is valid.
     * 
     * @param name The name of the search heuristic
     * @return True if the name is valid, false otherwise
     */
    public static boolean isValidHeuristic(String name) {
        return validHeuristics.contains(name.trim());
    }
}
