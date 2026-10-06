package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.*;
import nl.uu.maze.execution.EngineConfiguration;
import org.junit.jupiter.api.Test;

class RandomSeedTest {
    @Test void seedsRepeatStreamsWithinRunsAndVaryAcrossRepetitions() {
        var config = EngineConfiguration.getInstance();
        Long original = config.globalRandomSeed;
        try {
            config.globalRandomSeed = 123L;
            long[] first = config.mkNewRandomGenerator().longs(16).toArray();
            assertArrayEquals(first, config.mkNewRandomGenerator().longs(16).toArray());
            config.globalRandomSeed = 124L;
            assertFalse(java.util.Arrays.equals(first, config.mkNewRandomGenerator().longs(16).toArray()));
        } finally {
            config.globalRandomSeed = original;
        }
    }
}
