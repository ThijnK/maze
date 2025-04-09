package nl.uu.maze.main.cli.converters;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Type converter for long values representing time budgets in the CLI.
 */
public class TimeBudgetConverter implements ITypeConverter<Long> {
    @Override
    public Long convert(String value) {
        try {
            if (value == null || value.isEmpty() || value.equalsIgnoreCase("no budget")) {
                return 0L; // No timeout specified
            }
            long longValue = Long.parseLong(value);
            if (longValue < 0) {
                throw new TypeConversionException(
                        "Time budget must be a positive long: " + value);
            }
            return longValue;
        } catch (NumberFormatException e) {
            throw new TypeConversionException("Time budget must be a valid long: " + value);
        }
    }
}
