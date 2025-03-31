package nl.uu.maze.search.heuristic;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Validator class for use in the picocli command line interface to check
 * whether a given heuristic name is valid.
 */
public class SearchHeuristicValidator implements ITypeConverter<String> {
    @Override
    public String convert(String value) throws Exception {
        if (SearchHeuristicFactory.isValidHeuristic(value)) {
            return value;
        } else {
            throw new TypeConversionException("Unknown search heuristic: " + value);
        }
    }
}
