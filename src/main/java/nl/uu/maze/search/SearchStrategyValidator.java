package nl.uu.maze.search;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Validator class for use in the picocli command line interface to check
 * whether a given strategy name is valid.
 */
public class SearchStrategyValidator implements ITypeConverter<String> {
    @Override
    public String convert(String value) throws Exception {
        if (SearchStrategyFactory.isValidStrategy(value)) {
            return value;
        } else {
            throw new TypeConversionException("Unknown search strategy: " + value);
        }
    }
}
