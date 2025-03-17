package nl.uu.maze.util;

import java.util.Objects;

/**
 * Represents a pair of two values.
 *
 * @param <First>  the type of the first value in the pair
 * @param <Second> the type of the second value in the pair
 */
public class Pair<First, Second> {
    private final First first;
    private final Second second;

    /**
     * Constructs a new Pair with the given values.
     *
     * @param first  the first value
     * @param second the second value
     */
    public Pair(First first, Second second) {
        this.first = first;
        this.second = second;
    }

    /**
     * Returns the first value of the pair.
     *
     * @return the first value
     */
    public First getFirst() {
        return first;
    }

    /**
     * Returns the second value of the pair.
     *
     * @return the second value
     */
    public Second getSecond() {
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
     * Checks if this Pair is equal to another object.
     *
     * @param obj the object to compare with
     * @return true if the other object is a Pair with equal values
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Pair<?, ?> pair = (Pair<?, ?>) obj;
        return Objects.equals(first, pair.first) &&
                Objects.equals(second, pair.second);
    }

    /**
     * Computes a hash code for this Pair.
     *
     * @return the hash code
     */
    @Override
    public int hashCode() {
        return Objects.hash(first, second);
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
