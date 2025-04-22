package nl.uu.maze.benchmarks;

/**
 * A benchmark class that calculates statistics and functions of floating-point
 * numbers.
 * <p>
 * This class tests the engine's ability to handle floating-point
 * arithmetic, and its performance in the presence of floating-point numbers.
 * Since floating-point numbers introduce considerable overhead from the
 * constraint solver, this class tests search strategies' ability to effectively
 * use the time budget to find as many distinct paths as possible.
 * Additionally, and importantly, this class contains multiple methods, so it
 * also tests a search strategy's ability to deal with multiple methods (e.g.,
 * which method should it be executing to reach new coverage the fastest).
 */
public class FloatStatistics {
    public static float mean(float[] numbers) {
        float sum = 0f;
        for (float num : numbers) {
            sum += num;
        }
        return sum / numbers.length;
    }

    public static float variance(float[] numbers) {
        float mean = mean(numbers);
        float variance = 0f;
        for (float num : numbers) {
            variance += Math.pow(num - mean, 2);
        }
        return variance / numbers.length;
    }

    public static float standardDeviation(float[] numbers) {
        return sqrt(variance(numbers));
    }

    public static float sqrt(float value) {
        if (value < 0) {
            throw new IllegalArgumentException("Cannot calculate square root of a negative number.");
        }
        float epsilon = 1e-10f; // Precision
        float guess = value / 2f;
        while (abs(guess * guess - value) > epsilon) {
            guess = (guess + value / guess) / 2f;
        }
        return guess;
    }

    public static float abs(float value) {
        return value < 0 ? -value : value;
    }
}
