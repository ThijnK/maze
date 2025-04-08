package nl.uu.maze.main.cli.converters;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;
import ch.qos.logback.classic.Level;

/**
 * Type converter for the log level option of the CLI.
 */
public class LogLevelConverter implements ITypeConverter<Level> {
    @Override
    public Level convert(String value) {
        Level level = Level.toLevel(value.toUpperCase());
        if (level.equals(Level.DEBUG) && !value.equalsIgnoreCase("DEBUG")) {
            throw new TypeConversionException(
                    "expected one of [DEBUG, INFO, WARN, ERROR, TRACE, OFF] (case-insensitive) but was '" + value
                            + "'");
        }
        return level;
    }
}