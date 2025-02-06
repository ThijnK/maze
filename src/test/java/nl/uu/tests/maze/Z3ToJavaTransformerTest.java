package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.microsoft.z3.BitVecExpr;
import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.FPNum;
import com.microsoft.z3.IntExpr;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;

import nl.uu.maze.transform.Z3ToJavaTransformer;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.*;

public class Z3ToJavaTransformerTest {
    private static Z3ToJavaTransformer transformer;
    private static Context ctx;
    private static Model model;

    @BeforeAll
    public static void setUp() {
        transformer = new Z3ToJavaTransformer();
        ctx = new Context();
        Solver solver = ctx.mkSolver();
        // BoolExpr[] constraints = new BoolExpr[] { ctx.mkTrue() };
        // solver.add(constraints);
        solver.check();
        model = solver.getModel();
    }

    @Test
    public void testTransform_BoolExpr() {
        BoolExpr exprTrue = ctx.mkTrue();
        BoolExpr exprFalse = ctx.mkFalse();
        Object resultTrue = transformer.transform(exprTrue, model, BooleanType.getInstance());
        Object resultFalse = transformer.transform(exprFalse, model, BooleanType.getInstance());

        assert (Boolean) resultTrue;
        assert !(Boolean) resultFalse;
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, -1, 69, -69, Integer.MAX_VALUE, Integer.MIN_VALUE })
    public void testTransform_IntExpr(int value) {
        IntExpr expr = ctx.mkInt(value);
        Object result = transformer.transform(expr, model, IntType.getInstance());
        assertEquals(value, result);

    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, -1, 69, -69, Integer.MAX_VALUE, Integer.MIN_VALUE })
    public void testTransform_BVExpr_Int(int value) {
        Type type = IntType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, -1, 69, -69, Long.MAX_VALUE, Long.MIN_VALUE })
    public void testTransform_BVExpr_Long(long value) {
        Type type = LongType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Byte() {
        byte value = Byte.MAX_VALUE;
        Type type = ByteType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Short() {
        short value = Short.MAX_VALUE;
        Type type = ShortType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Char() {
        char value = 'a';
        Type type = CharType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Boolean() {
        boolean value = true;
        Type type = BooleanType.getInstance();
        BitVecExpr expr = ctx.mkBV(value ? 1 : 0, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(floats = { 0.0f, 1.0f, -1.0f, 69.0f, -69.0f, Float.MAX_VALUE, Float.MIN_VALUE, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NaN })
    public void testTransform_FPExpr_Float(float value) {
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort32());
        Object result = transformer.transform(expr, model, FloatType.getInstance());
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0.0, 1.0, -1.0, 69.0, -69.0, Double.MAX_VALUE, Double.MIN_VALUE, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NaN })
    public void testTransform_FPExpr_Double(double value) {
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort64());
        Object result = transformer.transform(expr, model, DoubleType.getInstance());
        assertEquals(value, result);
    }
}
