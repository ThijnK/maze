package nl.uu.maze.search;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.search.ConcreteSearchStrategy.PathConditionCandidate;

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
     * @param name         The name of the search heuristic
     * @param weightString The weight of the search heuristic as a string, defaults
     *                     to 1.0 if empty
     * @return A search heuristic
     * @throws IllegalArgumentException If the name is unknown
     * @throws NumberFormatException    If the weight is not a valid double
     */
    public static SearchHeuristic<SymbolicState> createSymbolicHeuristic(String name, double weight) {
        return switch (name) {
            case "UniformHeuristic", "Uniform", "UH" -> new nl.uu.maze.search.symbolic.heuristics.UniformHeuristic();
            case "DistanceToUncoveredHeuristic", "DistanceToUncovered", "DTUH" ->
                new nl.uu.maze.search.symbolic.heuristics.DistanceToUncoveredHeuristic(weight);
            default -> throw new IllegalArgumentException("Unknown search heuristic: " + name);
        };
    }

    /**
     * Creates a search heuristic based on the given name and weight.
     * 
     * @param name         The name of the search heuristic
     * @param weightString The weight of the search heuristic as a string, defaults
     *                     to 1.0 if empty
     * @return A search heuristic
     * @throws IllegalArgumentException If the name is unknown
     * @throws NumberFormatException    If the weight is not a valid double
     */
    public static SearchHeuristic<PathConditionCandidate> createConcreteHeuristic(String name, double weight) {
        return switch (name) {
            case "UniformHeuristic", "Uniform", "UH" -> new nl.uu.maze.search.concrete.heuristics.UniformHeuristic();
            // case "DistanceToUncoveredHeuristic", "DistanceToUncovered", "DTUH" -> new
            // nl.uu.maze.search.concrete.heuristics.DistanceToUncoveredHeuristic(Double.parseDouble(weight));
            default -> throw new IllegalArgumentException("Unknown search heuristic: " + name);
        };
    }

    /**
     * Helper method to create an array of search heuristics based on the given
     * names and weights.
     */
    private static <T> List<SearchHeuristic<T>> createHeuristics(String namesString, String weightsString,
            BiFunction<String, Double, SearchHeuristic<T>> factory) {
        String[] names = namesString.split(",");
        String[] weights = weightsString.split(",");
        List<SearchHeuristic<T>> heuristics = new ArrayList<>(names.length);
        for (int i = 0; i < names.length; i++) {
            try {
                String weightString = weights.length >= i + 1 ? weights[i] : "";
                double weight = weightString.isEmpty() ? 1.0 : Double.parseDouble(weightString);
                if (weight <= 0) {
                    throw new IllegalArgumentException("Weight must be positive");
                }
                heuristics.add(factory.apply(names[i], weight));
            } catch (IllegalArgumentException e) {
                logger.warn("Unknown search heuristic: {}, skipping", names[i]);
            }
        }
        return heuristics;
    }

    /**
     * Creates an array of search heuristics based on the given names and weights.
     * 
     * @see #createSymbolicHeuristic(String, String)
     */
    public static List<SearchHeuristic<SymbolicState>> createSymbolicHeuristics(String names, String weights) {
        return createHeuristics(names, weights, SearchHeuristicFactory::createSymbolicHeuristic);
    }

    /**
     * Creates an array of search heuristics based on the given names and weights.
     * 
     * @see #createConcreteHeuristic(String, String)
     */
    public static List<SearchHeuristic<PathConditionCandidate>> createConcreteHeuristics(String names, String weights) {
        return createHeuristics(names, weights, SearchHeuristicFactory::createConcreteHeuristic);
    }
}
