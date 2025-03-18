package nl.uu.maze.util;

/**
 * Represents a pair of two values.
 *
 * @param <First>  the type of the first value in the pair
 * @param <Second> the type of the second value in the pair
 */
public record Pair<First, Second>(First first, Second second) {
    /**
     * Constructs a new Pair with the given values.
     *
     * @param first  the first value
     * @param second the second value
     */
    public Pair {
    }

    /**
     * Returns the first value of the pair.
     *
     * @return the first value
     */
    @Override
    public First first() {
        return first;
    }

    /**
     * Returns the second value of the pair.
     *
     * @return the second value
     */
    @Override
    public Second second() {
        return second;
    }

    /**
     * Creates a new Pair.
     *
     * @param first    the first value
     * @param second   the second value
     * @param <First>  the type of the first value
     * @param <Second> the type of the second value
     * @return a new Pair with the given values
     */
    public static <First, Second> Pair<First, Second> of(First first, Second second) {
        return new Pair<>(first, second);
    }

    /**
     * Returns a string representation of this Pair.
     *
     * @return a string representation
     */
    @Override
    public String toString() {
        return "Pair{" +
                "first=" + first +
                ", second=" + second +
                '}';
    }
}
