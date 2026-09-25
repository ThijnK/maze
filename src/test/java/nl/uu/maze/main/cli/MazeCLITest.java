package nl.uu.maze.main.cli;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import picocli.CommandLine;

class MazeCLITest {
    private static CommandLine parse(String... options) {
        var args = new ArrayList<>(List.of("-c", "classes", "-n", "example.Subject", "-o", "generated"));
        args.addAll(List.of(options));
        var command = new CommandLine(new MazeCLI());
        command.parseArgs(args.toArray(String[]::new));
        return command;
    }

    @Test void visibleLongNamesUseKebabCase() {
        var command = new CommandLine(new MazeCLI());
        for (var option : command.getCommandSpec().options()) {
            if (option.hidden()) continue;
            for (String name : option.names()) {
                if (name.startsWith("--")) assertTrue(name.matches("--[a-z]+(?:-[a-z0-9]+)*"), name);
            }
        }
        String help = command.getUsageMessage();
        assertTrue(help.contains("--minimization"));
        assertTrue(help.contains("--class-name"));
        assertFalse(help.contains("--classname"));
        assertFalse(help.contains("--verificationMode"));
        assertFalse(help.contains("--minimalistic-suite"));
    }

    @Test void everyBooleanAcceptsBareAndExplicitValuesButNotNumbers() {
        var spec = new CommandLine(new MazeCLI()).getCommandSpec();
        for (var option : spec.options()) {
            if (option.hidden() || option.type() != boolean.class) continue;
            String name = option.longestName();
            for (String value : List.of("true", "false")) {
                var parsed = parse(name + "=" + value);
                assertEquals(Boolean.valueOf(value), parsed.getCommandSpec().findOption(name).getValue(), name);
            }
            assertEquals(Boolean.TRUE, parse(name).getCommandSpec().findOption(name).getValue(), name);
            assertThrows(CommandLine.ParameterException.class, () -> parse(name + "=1"), name);
            assertThrows(CommandLine.ParameterException.class, () -> parse(name + "=0"), name);
        }
        assertEquals(Boolean.FALSE, parse("--minimization", "false").getCommandSpec().findOption("--minimization").getValue());
    }

    @ParameterizedTest @CsvSource({"none,0", "file,1", "log,-1"})
    void exportDestinationsAcceptOnlyReadableValues(String named, int engineValue) {
        for (String option : List.of("--export-jimple", "--export-hcfg", "--export-target-paths", "--export-path-coverage")) {
            var current = parse(option + "=" + named).getCommandSpec().findOption(option).getValue();
            assertEquals(engineValue, ((ExportDestination) current).engineValue());
            for (String invalid : List.of("0", "1", "-1", "2", "true")) {
                assertThrows(CommandLine.ParameterException.class, () -> parse(option + "=" + invalid));
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {
        "--classname=example.Other", "--minimalistic-suite=true", "--indirectTarget=example.Other",
        "--allow-CUTfieldschange-by-reflection=true", "--constrain-FP-params-to-normal-numbers=true",
        "--surpress-regression-oracles=true", "--check-divbyZero=true", "--export-HCFG=file",
        "--export-pathcov=log", "--path-length-cov=2", "--verificationMode=1"
    }) void removedOptionsAreRejected(String option) {
        assertThrows(CommandLine.UnmatchedArgumentException.class, () -> parse(option));
    }

    @Test void longAndShortClassNameOptionsSatisfyTheRequiredTarget() {
        for (String option : List.of("--class-name", "-n")) {
            new CommandLine(new MazeCLI()).parseArgs("-c", "classes", option, "example.Subject", "-o", "generated");
        }
        assertThrows(CommandLine.MissingParameterException.class,
                () -> new CommandLine(new MazeCLI()).parseArgs("-c", "classes", "-o", "generated"));
    }

    private static int verification(String... args) {
        return ((MazeCLI) parse(args).getCommand()).verificationLimit();
    }

    @Test void verificationEnablementAndLimitAreSeparate() {
        assertEquals(0, verification());
        assertEquals(0, verification("--verification=false"));
        assertEquals(1, verification("--verification=true"));
        assertEquals(3, verification("--verification", "--max-violations=3"));
        assertEquals(-1, verification("--verification", "--max-violations=unlimited"));
        assertThrows(IllegalArgumentException.class, () -> verification("--max-violations=3"));
        assertThrows(IllegalArgumentException.class, () -> verification("--verification=false", "--max-violations=3"));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "-1", "-2", "true", "1.5", "2147483648"})
    void invalidNewViolationLimitsAreRejected(String value) {
        assertThrows(CommandLine.ParameterException.class, () -> parse("--verification", "--max-violations=" + value));
    }
}
