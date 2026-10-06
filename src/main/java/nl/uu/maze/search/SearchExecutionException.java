package nl.uu.maze.search;

/** A search callback failed; engine recovery for subject exceptions must not consume it. */
public final class SearchExecutionException extends RuntimeException {
    SearchExecutionException(String identity, String callback, Throwable cause) {
        super(identity + ": " + callback + " failed: " + cause, cause);
    }
}
