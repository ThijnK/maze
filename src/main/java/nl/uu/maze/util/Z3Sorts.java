package nl.uu.maze.util;

import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FPSort;
import com.microsoft.z3.Sort;

import sootup.core.types.ArrayType;
import sootup.core.types.ClassType;
import sootup.core.types.NullType;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.DoubleType;
import sootup.core.types.PrimitiveType.FloatType;
import sootup.core.types.PrimitiveType.IntType;
import sootup.core.types.PrimitiveType.LongType;
import sootup.core.types.VoidType;

/**
 * Provides global Z3 sorts.
 */
public class Z3Sorts {
    private static Z3Sorts instance;

    private Context ctx;

    private Sort refSort;
    /** Null constant, used for null comparisons etc. */
    private Expr<?> nullConst;
    private Sort voidSort;
    private Sort stringSort;

    private BitVecSort intSort;
    private BitVecSort longSort;
    private FPSort floatSort;
    private FPSort doubleSort;

    private Z3Sorts(Context ctx) {
        this.ctx = ctx;
        refSort = ctx.mkUninterpretedSort("Ref");
        nullConst = ctx.mkConst("null", refSort);
        voidSort = ctx.mkUninterpretedSort("Void");
        stringSort = ctx.mkStringSort();

        intSort = ctx.mkBitVecSort(Type.getValueBitSize(IntType.getInstance()));
        longSort = ctx.mkBitVecSort(Type.getValueBitSize(LongType.getInstance()));
        floatSort = ctx.mkFPSort32();
        doubleSort = ctx.mkFPSort64();
    }

    public static synchronized void initialize(Context ctx) {
        if (instance == null) {
            instance = new Z3Sorts(ctx);
        }
    }

    public static Z3Sorts getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Z3Sorts not initialized. Call initialize() first.");
        }
        return instance;
    }

    public Sort getRefSort() {
        return refSort;
    }

    public Expr<?> getNullConst() {
        return nullConst;
    }

    public Sort getVoidSort() {
        return voidSort;
    }

    public Sort getStringSort() {
        return stringSort;
    }

    public BitVecSort getIntSort() {
        return intSort;
    }

    public BitVecSort getLongSort() {
        return longSort;
    }

    public FPSort getFloatSort() {
        return floatSort;
    }

    public FPSort getDoubleSort() {
        return doubleSort;
    }

    /**
     * Determine the Z3 sort for the given Soot type.
     * 
     * @param sootType The Soot type
     * @return The Z3 sort
     * @throws UnsupportedOperationException If the type is not supported
     * @see Sort
     * @see Type
     */
    public Sort determineSort(Type sootType) {
        if (Type.isIntLikeType(sootType)) {
            // Int like types are all represented as integers by SootUp, so they get the bit
            // vector size for integers
            return getIntSort();
        } else if (sootType instanceof LongType) {
            return getLongSort();
        } else if (sootType instanceof DoubleType) {
            return getDoubleSort();
        } else if (sootType instanceof FloatType) {
            return getFloatSort();
        } else if (sootType instanceof ArrayType) {
            Sort elementSort = determineSort(((ArrayType) sootType).getElementType());
            return ctx.mkArraySort(getIntSort(), elementSort);
        } else if (sootType instanceof ClassType) {
            if (sootType.toString().equals("java.lang.String")) {
                return getStringSort();
            }
            return getRefSort();
        } else if (sootType instanceof NullType) {
            return getRefSort();
        } else if (sootType instanceof VoidType) {
            return getVoidSort();
        } else {
            throw new UnsupportedOperationException("Unsupported type: " + sootType);
        }
    }
}
