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
        open();
    }

    public static void log(String message) {
        if (pw == null) {
            open();
        }
        pw.println(message);
        pw.flush();
    }

    public static void close() {
        pw.close();
        pw = null;
    }

    public static void open() {
        try {
            FileWriter fw = new FileWriter(TraceManager.FILE_PATH);
            pw = new PrintWriter(fw);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
