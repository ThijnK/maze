package nl.uu.maze.util;

import org.slf4j.Logger;

import ch.qos.logback.classic.Level;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * A PrintStream that logs messages to a specified SLF4J Logger.
 */
public class LoggerOutputStream extends PrintStream {
    private final Logger logger;
    private final StringBuilder buffer = new StringBuilder();
    private final Level logLevel;

    public LoggerOutputStream(Logger logger, Level logLevel) {
        super(new ByteArrayOutputStream()); // Dummy output stream since we're only logging
        this.logger = logger;
        this.logLevel = logLevel;
    }

    @Override
    public void write(int b) {
        if (b == '\n') {
            flushBuffer();
        } else {
            buffer.append((char) b);
        }
    }

    @Override
    public void write(byte[] buf, int off, int len) {
        for (int i = 0; i < len; i++) {
            write(buf[off + i]);
        }
    }

    @Override
    public void flush() {
        flushBuffer();
        super.flush();
    }

    @Override
    public void close() {
        flush();
        super.close();
    }

    private void flushBuffer() {
        if (buffer.length() > 0) {
            String message = buffer.toString();
            buffer.setLength(0);

            // Log the message at the specified log level
            switch (logLevel.levelInt) {
                case Level.DEBUG_INT -> logger.debug(message);
                case Level.INFO_INT -> logger.info(message);
                case Level.WARN_INT -> logger.warn(message);
                case Level.ERROR_INT -> logger.error(message);
                case Level.TRACE_INT -> logger.trace(message);
                case Level.OFF_INT -> {
                }
                default -> logger.info(message);
            }
        }
    }
}
