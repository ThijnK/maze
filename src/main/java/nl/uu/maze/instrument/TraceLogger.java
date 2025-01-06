package nl.uu.maze.instrument;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class TraceLogger {
    private static final String FILE_PATH = "logs/trace.log";
    private static PrintWriter pw;

    static {
        try {
            FileWriter fw = new FileWriter(FILE_PATH, true);
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
