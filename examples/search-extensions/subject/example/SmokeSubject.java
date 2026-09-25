package example;

/** Small deterministic subject for checking the complete test-generation path. */
public final class SmokeSubject {
    public static int classify(int value) {
        if (value < 0) {
            return -1;
        }
        if (value == 0) {
            return 0;
        }
        return 1;
    }
}
