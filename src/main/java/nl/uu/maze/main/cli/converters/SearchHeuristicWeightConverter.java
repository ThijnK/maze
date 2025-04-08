package nl.uu.maze.main.cli.converters;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Type converter for double values representing heuristic weights in the CLI.
 */
public class SearchHeuristicWeightConverter implements ITypeConverter<Double> {
    @Override
    public Double convert(String value) {
        try {
            double doubleValue = Double.parseDouble(value);
            if (Double.parseDouble(value) <= 0) {
                throw new TypeConversionException(
                        "Heuristic weight must be a positive double: " + value);
            }
            return doubleValue;
        } catch (NumberFormatException e) {
            throw new TypeConversionException("Heuristic weight must be a valid double: " + value);
        }
    }
}
