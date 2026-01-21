package nl.uu.maze.util;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BitVecSort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FPSort;
import com.microsoft.z3.Sort;

import sootup.core.signatures.PackageName;
import sootup.core.types.*;
import sootup.core.types.PrimitiveType.*;
import sootup.java.core.types.JavaClassType;

/**
 * Provides global Z3 sorts and related utility methods.
 */
public class Z3Sorts {
    private static Z3Sorts instance;
    private static final Context ctx() { return Z3ContextProvider.getContext(); }

    private final Sort refSort;
    /** Null constant, used for null comparisons etc. */
    private final Expr<?> nullConst;
    private final Sort voidSort;
    private final Sort stringSort;

    private final BitVecSort intSort;
    private final BitVecSort longSort;
    private final FPSort floatSort;
    private final FPSort doubleSort;

    private Z3Sorts() {
        refSort = ctx().mkUninterpretedSort("Ref");
        nullConst = ctx().mkConst("null", refSort);
        voidSort = ctx().mkUninterpretedSort("Void");
        stringSort = ctx().mkStringSort();

        intSort = ctx().mkBitVecSort(getIntBitSize());
        longSort = ctx().mkBitVecSort(getLongBitSize());
        floatSort = ctx().mkFPSort32();
        doubleSort = ctx().mkFPSort64();
    }

    public static Z3Sorts getInstance() {
        if (instance == null) {
            instance = new Z3Sorts();
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
     * Check if the given Z3 expression is a reference.
     */
    public boolean isRef(Expr<?> expr) {
        return expr.getSort().equals(refSort);
    }

    /**
     * Check if the given Z3 sort is a reference.
     */
    public boolean isRef(Sort sort) {
        return sort.equals(refSort);
    }

    /**
     * Check if the given Z3 expression is the null constant.
     */
    public boolean isNull(Expr<?> expr) {
        return expr.equals(nullConst);
    }

    /**
     * Check if the given Z3 expression is a string.
     */
    public boolean isString(Expr<?> expr) {
        return expr.getSort().equals(stringSort);
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
            return ctx().mkArraySort(getIntSort(), elementSort);
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

    /**
     * Determine the SootUp type for the given Java class.
     * 
     * @param clazz The Java class
     * @return The SootUp type
     * @see Type
     */
    public Type determineType(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        if (clazz == int.class || clazz == Integer.class) {
            return PrimitiveType.getInt();
        }
        if (clazz == byte.class || clazz == Byte.class) {
            return PrimitiveType.getByte();
        }
        if (clazz == short.class || clazz == Short.class) {
            return PrimitiveType.getShort();
        }
        if (clazz == char.class || clazz == Character.class) {
            return PrimitiveType.getChar();
        }
        if (clazz == boolean.class || clazz == Boolean.class) {
            return PrimitiveType.getBoolean();
        }
        if (clazz == long.class || clazz == Long.class) {
            return PrimitiveType.getLong();
        }
        if (clazz == float.class || clazz == Float.class) {
            return PrimitiveType.getFloat();
        }
        if (clazz == double.class || clazz == Double.class) {
            return PrimitiveType.getDouble();
        }
        if (clazz.isArray()) {
            int dim = 0;
            Class<?> base = clazz;
            while (base.isArray()) {
                dim++;
                base = base.getComponentType();
            }
            Type elemType = determineType(base);
            return ArrayType.createArrayType(elemType, dim);
        }

        PackageName packageName = new PackageName(clazz.getPackageName());
        return new JavaClassType(clazz.getName(), packageName);
    }

    /**
     * Determine the SootUp type for the given Z3 sort.
     * 
     * @param sort The Z3 sort
     * @return The SootUp type
     * @see Type
     */
    public Type determineType(Sort sort) {
        if (sort instanceof BitVecSort bvSort) {
            if (bvSort.getSize() == 64) {
                return PrimitiveType.getLong();
            }
            return PrimitiveType.getInt();
        } else if (sort instanceof FPSort fpSort) {
            if (fpSort.getSBits() == 64) {
                return PrimitiveType.getDouble();
            }
            return PrimitiveType.getFloat();
        } else if (sort.equals(refSort)) {
            return new JavaClassType(Object.class.getName(), new PackageName(Object.class.getPackageName()));
        } else if (sort.equals(voidSort)) {
            return VoidType.getInstance();
        } else if (sort.equals(stringSort)) {
            return new JavaClassType(String.class.getName(), new PackageName(String.class.getPackageName()));
        } else {
            throw new UnsupportedOperationException("Unsupported sort: " + sort);
        }
    }

    /**
     * Get the bit size of the given Soot type.
     * 
     * @param sootType The Soot type
     * @return The bit size
     * @throws UnsupportedOperationException If the type is not supported
     */
    public int getBitSize(Type sootType) {
        if (Type.isIntLikeType(sootType)) {
            return getIntBitSize();
        } else if (sootType instanceof LongType) {
            return getLongBitSize();
        } else if (sootType instanceof DoubleType) {
            return Type.getValueBitSize(PrimitiveType.getDouble());
        } else if (sootType instanceof FloatType) {
            return Type.getValueBitSize(PrimitiveType.getFloat());
        } else {
            throw new UnsupportedOperationException("Unsupported type: " + sootType);
        }
    }

    /**
     * Get the bit size of an integer.
     */
    public int getIntBitSize() {
        return Type.getValueBitSize(PrimitiveType.getInt());
    }

    /**
     * Get the bit size of a long.
     */
    public int getLongBitSize() {
        return Type.getValueBitSize(PrimitiveType.getLong());
    }

    /**
     * Get a default value as a Z3 expression for the given Soot type.
     */
    public Expr<?> getDefaultValue(Type sootType) {
        if (Type.isIntLikeType(sootType)) {
            return ctx().mkBV(0, getBitSize(sootType));
        } else if (sootType instanceof FloatType) {
            return ctx().mkFP(0.0f, getFloatSort());
        } else if (sootType instanceof DoubleType) {
            return ctx().mkFP(0.0, getDoubleSort());
        } else if (sootType instanceof ArrayType || sootType instanceof ClassType || sootType instanceof NullType) {
            return getNullConst();
        } else {
            throw new UnsupportedOperationException("Unsupported type: " + sootType);
        }
    }

    /**
     * Get a default integer value as a Z3 expression.
     */
    public BitVecExpr getDefaultInt() {
        return ctx().mkBV(0, getIntBitSize());
    }
}
