package nl.uu.maze.instrument;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class TraceLogger {
    private static final String FILE_NAME = "trace.log";

    // TODO: could probably be made more efficient
    public static void log(String message) {
        try (FileWriter fw = new FileWriter(FILE_NAME, true);
                PrintWriter pw = new PrintWriter(fw)) {
            pw.println(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
