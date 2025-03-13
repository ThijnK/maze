package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.FPNum;
import com.microsoft.z3.IntExpr;

import nl.uu.maze.transform.Z3ToJavaTransformer;
import nl.uu.maze.util.Z3ContextProvider;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;

public class Z3ToJavaTransformerTest {
    private static final Context ctx = Z3ContextProvider.getContext();
    private static Z3ToJavaTransformer transformer;

    @BeforeAll
    public static void setUp() {
        transformer = new Z3ToJavaTransformer();
    }

    @AfterAll
    public static void tearDown() {
        Z3ContextProvider.close();
    }

    @Test
    public void testTransform_BoolExpr() {
        BoolExpr exprTrue = ctx.mkTrue();
        BoolExpr exprFalse = ctx.mkFalse();
        Object resultTrue = transformer.transformExpr(exprTrue, PrimitiveType.getBoolean());
        Object resultFalse = transformer.transformExpr(exprFalse, PrimitiveType.getBoolean());

        assert (Boolean) resultTrue;
        assert !(Boolean) resultFalse;
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, -1, 69, -69, Integer.MAX_VALUE, Integer.MIN_VALUE })
    public void testTransform_IntExpr(int value) {
        IntExpr expr = ctx.mkInt(value);
        Object result = transformer.transformExpr(expr, PrimitiveType.getInt());
        assertEquals(value, result);

    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, -1, 69, -69, Integer.MAX_VALUE, Integer.MIN_VALUE })
    public void testTransform_BVExpr_Int(int value) {
        Type type = PrimitiveType.getInt();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr(expr, type);
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, -1, 69, -69, Long.MAX_VALUE, Long.MIN_VALUE })
    public void testTransform_BVExpr_Long(long value) {
        Type type = PrimitiveType.getLong();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr(expr, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Byte() {
        byte value = Byte.MAX_VALUE;
        Type type = PrimitiveType.getByte();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr(expr, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Short() {
        short value = Short.MAX_VALUE;
        Type type = PrimitiveType.getShort();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr(expr, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Char() {
        char value = 'a';
        Type type = PrimitiveType.getChar();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr(expr, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Boolean() {
        boolean value = true;
        Type type = PrimitiveType.getBoolean();
        BitVecExpr expr = ctx.mkBV(value ? 1 : 0, Type.getValueBitSize(type));
        Object result = transformer.transformExpr(expr, type);
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(floats = { 0.0f, 1.0f, -1.0f, 69.0f, -69.0f, Float.MAX_VALUE, Float.MIN_VALUE, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NaN })
    public void testTransform_FPExpr_Float(float value) {
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort32());
        Object result = transformer.transformExpr(expr, PrimitiveType.getFloat());
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0.0, 1.0, -1.0, 69.0, -69.0, Double.MAX_VALUE, Double.MIN_VALUE, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NaN })
    public void testTransform_FPExpr_Double(double value) {
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort64());
        Object result = transformer.transformExpr(expr, PrimitiveType.getDouble());
        assertEquals(value, result);
    }
}
