package nl.uu.maze.main.cli;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** A diagnostic export has three destinations, rather than an on/off value. */
public enum ExportDestination {
    NONE(0), FILE(1), LOG(-1);

    private final int engineValue;

    ExportDestination(int engineValue) { this.engineValue = engineValue; }

    public int engineValue() { return engineValue; }

    public static final class Converter implements ITypeConverter<ExportDestination> {
        @Override public ExportDestination convert(String value) {
            return switch (value) {
                case "none" -> NONE;
                case "file" -> FILE;
                case "log" -> LOG;
                default -> throw new TypeConversionException("Expected none, file, or log: " + value);
            };
        }
    }
}
