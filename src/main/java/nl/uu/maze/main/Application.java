package nl.uu.maze.main;

import nl.uu.maze.main.cli.MazeCLI;
import picocli.CommandLine;

/**
 * Entry point of the application.
 */
public class Application {
    public static void main(String[] args) {
        // Run the CLI application
        int exitCode = new CommandLine(new MazeCLI()).execute(args);
        System.exit(exitCode);
    }
}
