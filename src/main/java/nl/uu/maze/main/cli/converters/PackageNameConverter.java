package nl.uu.maze.main.cli.converters;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * Type converter for package names in the CLI.
 */
public class PackageNameConverter implements ITypeConverter<String> {
    @Override
    public String convert(String value) {
        if (value == null || value.isEmpty() || value.equalsIgnoreCase("no package")) {
            return ""; // No package will be defined
        }

        if (!value.matches("^([a-zA-Z_][a-zA-Z0-9_]*)(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$")) {
            throw new TypeConversionException("Invalid package name: " + value);
        }

        return value;
    }
}
