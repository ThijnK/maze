package nl.uu.maze.main.cli.converters;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Type converter for long values representing test timeouts in the CLI.
 */
public class TestTimeoutConverter implements ITypeConverter<Long> {
    @Override
    public Long convert(String value) {
        try {
            if (value == null || value.isEmpty() || value.equalsIgnoreCase("no timeout")) {
                return 0L; // No timeout specified
            }
            long longValue = Long.parseLong(value);
            if (longValue < 0) {
                throw new TypeConversionException(
                        "Test timeout must be a positive long: " + value);
            }
            return longValue;
        } catch (NumberFormatException e) {
            throw new TypeConversionException("Test timeout must be a valid long: " + value);
        }
    }
}
