package nl.uu.maze.main.cli.converters;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Separates verification's stopping limit from its boolean enablement. */
public final class ViolationLimitConverter implements ITypeConverter<Integer> {
    @Override public Integer convert(String value) {
        if (value.equals("unlimited")) return -1;
        try {
            int limit = Integer.parseInt(value);
            if (limit > 0) return limit;
        } catch (NumberFormatException ignored) {
            // Report the same supported domain for malformed and out-of-range values.
        }
        throw new TypeConversionException("Expected a positive violation count or unlimited: " + value);
    }
}
