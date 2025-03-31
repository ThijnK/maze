package nl.uu.maze.search.heuristic;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Converter class for use in the picocli command line interface to convert
 * a string to a double value representing the heuristic weight.
 */
public class SearchHeuristicWeightConverter implements ITypeConverter<Double> {
    @Override
    public Double convert(String value) throws Exception {
        try {
            double doubleValue = Double.parseDouble(value);
            if (Double.parseDouble(value) <= 0) {
                throw new TypeConversionException("Heuristic weight must be a positive double: " + value);
            }
            return doubleValue;
        } catch (NumberFormatException e) {
            throw new TypeConversionException("Heuristic weight must be a valid double: " + value);
        }
    }
}
