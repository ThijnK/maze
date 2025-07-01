package nl.uu.maze.benchmarks;

/**
 * Benchmark class that provides various bitwise manipulation methods.
 * <p>
 * Generally easy to generate exhaustive test cases for, but a good test of a
 * tool's support for bitwise manipulation and to balance harder benchmarks.
 */
public class BitwiseManipulator {
    public static int rotateLeft(int value, int bits) {
        return (value << bits) | (value >>> (32 - bits));
    }

    public static int rotateRight(int value, int bits) {
        return (value >>> bits) | (value << (32 - bits));
    }

    public static long maskRange(long value, int start, int end) {
        long mask = (~0L >>> (63 - (end - start))) << start;
        return value & mask;
    }

    public static int countSetBits(int v) {
        int count = 0;
        while (v != 0) {
            count += (v & 1);
            v >>>= 1;
        }
        return count;
    }

    public static boolean testBitPattern(int v) {
        // test for pattern: bits 0-3 == 0b1010
        int low4 = v & 0xF;
        if (low4 == 0b1010) {
            return true;
        } else if ((v & 1) == 1) {
            return v > 0;
        }
        return false;
    }

    /** Returns the parity (even/odd number of set bits) of an int. */
    public static int parity(int v) {
        v ^= v >>> 16;
        v ^= v >>> 8;
        v ^= v >>> 4;
        v ^= v >>> 2;
        v ^= v >>> 1;
        return v & 1;
    }

    /** Reverses the bits of an int. */
    public static int reverseBits(int v) {
        int r = 0;
        for (int i = 0; i < 32; i++) {
            r <<= 1;
            r |= (v & 1);
            v >>>= 1;
        }
        return r;
    }

    /** Returns the index (0-based) of the highest set bit, or -1 if zero. */
    public static int highestSetBit(int v) {
        if (v == 0)
            return -1;
        int idx = 0;
        while (v != 0) {
            v >>>= 1;
            idx++;
        }
        return idx - 1;
    }

    /** Returns the index (0-based) of the lowest set bit, or -1 if zero. */
    public static int lowestSetBit(int v) {
        if (v == 0)
            return -1;
        int idx = 0;
        while ((v & 1) == 0) {
            v >>>= 1;
            idx++;
        }
        return idx;
    }

    /** Population count for long. */
    public static int countSetBits(long v) {
        int count = 0;
        while (v != 0) {
            count += (v & 1L);
            v >>>= 1;
        }
        return count;
    }

    /** Interleaves the bits of two bytes into a short (Morton code). */
    public static short interleaveBits(byte a, byte b) {
        int x = a & 0xFF, y = b & 0xFF;
        int res = 0;
        for (int i = 0; i < 8; i++) {
            res |= ((x >> i) & 1) << (2 * i);
            res |= ((y >> i) & 1) << (2 * i + 1);
        }
        return (short) res;
    }

    /** Extracts a bitfield from value, starting at bit 'start', length 'len'. */
    public static int extractBitfield(int value, int start, int len) {
        return (value >>> start) & ((1 << len) - 1);
    }

    /**
     * Sets a bitfield in value, starting at bit 'start', length 'len', to
     * 'fieldValue'.
     */
    public static int setBitfield(int value, int start, int len, int fieldValue) {
        int mask = ((1 << len) - 1) << start;
        return (value & ~mask) | ((fieldValue << start) & mask);
    }

    /**
     * Loop whose number of iterations depends on the number of set bits in v.
     */
    public static int bitwiseLoop(int value) {
        int count = countSetBits(value);
        int sum = 0;
        for (int i = 0; i < count; i++) {
            if (((value >> i) & 1) == 1) {
                sum += i * value;
            } else {
                sum -= i;
            }
        }
        return sum;
    }

    /**
     * Conditionally flips elements in an array based on the mask's bits.
     */
    public static void conditionalArrayFlip(int[] arr, int mask) {
        for (int i = 0; i < arr.length; i++) {
            if (((mask >> i) & 1) == 1) {
                arr[i] = ~arr[i];
            } else if ((arr[i] & mask) == mask) {
                arr[i] = arr[i] ^ mask;
            }
        }
    }
}
