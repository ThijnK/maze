package nl.uu.maze.instrument;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Logs trace messages to a file.
 */
public class TraceLogger {
    private static PrintWriter pw;

    static {
        try {
            FileWriter fw = new FileWriter(TraceManager.FILE_PATH, false);
            pw = new PrintWriter(fw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void log(String message) {
        pw.println(message);
        pw.flush();
    }
}
