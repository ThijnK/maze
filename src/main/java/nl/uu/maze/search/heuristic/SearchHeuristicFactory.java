package nl.uu.maze.search.heuristic;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory class for creating search heuristics, which are used in
 * ProbabilisticSearch to determine the weight of each target in the random
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
            case "Uniform", "UniformHeuristic", "UH" -> new UniformHeuristic(weight);
            case "DistanceToUncovered", "DistanceToUncoveredHeuristic", "DTUH" ->
                new DistanceToUncoveredHeuristic(weight);
            case "RecentCoverageDensity", "RecentCoverageDensityHeuristic", "RCDH" ->
                new RecentCoverageDensityHeuristic(weight);
            case "RecentCoverageProximity", "RecentCoverageProximityHeuristic", "RCPH" ->
                new RecentCoverageProximityHeuristic(weight);
            case "QueryCost", "QueryCostHeuristic", "QCH" -> new QueryCostHeuristic(weight);
            case "SmallestDepth", "SmallestDepthHeuristic", "SDH" -> new DepthHeuristic(weight, false);
            case "GreatestDepth", "GreatestDepthHeuristic", "GDH" -> new DepthHeuristic(weight, true);
            case "SmallestCallDepth", "SmallestCallDepthHeuristic", "SCDH" ->
                new CallDepthHeuristic(weight, false);
            case "GreatestCallDepth", "GreatestCallDepthHeuristic", "GCDH" ->
                new CallDepthHeuristic(weight, true);
            case "ShortestWaitingTime", "ShortestWaitingTimeHeuristic", "SWTH" ->
                new WaitingTimeHeuristic(weight, false);
            case "LongestWaitingTime", "LongestWaitingTimeHeuristic", "LWTH" ->
                new WaitingTimeHeuristic(weight, true);
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
     */
    public static List<SearchHeuristic> createHeuristics(List<String> names, List<Double> weights) {
        List<SearchHeuristic> heuristics = new ArrayList<>(names.size());
        if (names.isEmpty()) {
            logger.warn("No search heuristics provided, using default Uniform heuristic with weight 1.0");
            return List.of(new UniformHeuristic(1.0));
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
     * Enum representing the valid search heuristics.
     * This enum is used for validation in the command line interface.
     */
    public enum ValidSearchHeuristic {
        Uniform, UniformHeuristic, UH,
        DistanceToUncovered, DistanceToUncoveredHeuristic, DTUH,
        RecentCoverage, RecentCoverageHeuristic, RCH,
        QueryCost, QueryCostHeuristic, QCH,
        SmallestDepth, SmallestDepthHeuristic, SDH,
        GreatestDepth, GreatestDepthHeuristic, GDH,
        SmallestCallDepth, SmallestCallDepthHeuristic, SCDH,
        GreatestCallDepth, GreatestCallDepthHeuristic, GCDH,
        ShortestWaitingTime, ShortestWaitingTimeHeuristic, SWTH,
        LongestWaitingTime, LongestWaitingTimeHeuristic, LWTH
    }
}
