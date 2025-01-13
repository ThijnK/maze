package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

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

    @Test
    public void testTransform_IntExpr_Positive() {
        int value = 42;
        IntExpr expr = ctx.mkInt(value);
        Object result = transformer.transform(expr, model, IntType.getInstance());
        assertEquals(value, result);

    }

    @Test
    public void testTransform_IntExpr_Negative() {
        int value = -42;
        IntExpr expr = ctx.mkInt(value);
        Object result = transformer.transform(expr, model, IntType.getInstance());
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_PositiveInt() {
        int value = Integer.MAX_VALUE;
        Type type = IntType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_NegativeInt() {
        int value = Integer.MIN_VALUE;
        Type type = IntType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_PositiveLong() {
        long value = Long.MAX_VALUE;
        Type type = LongType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transform(expr, model, type);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_NegativeLong() {
        long value = Long.MIN_VALUE;
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

    @Test
    public void testTransform_FPExpr_PositiveFloat() {
        float value = 42.0f;
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort32());
        Object result = transformer.transform(expr, model, FloatType.getInstance());
        assertEquals(value, result);
    }

    @Test
    public void testTransform_FPExpr_NegativeFloat() {
        float value = -42.0f;
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort32());
        Object result = transformer.transform(expr, model, FloatType.getInstance());
        assertEquals(value, result);
    }

    @Test
    public void testTransform_FPExpr_PositiveDouble() {
        double value = 42.0;
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort64());
        Object result = transformer.transform(expr, model, DoubleType.getInstance());
        assertEquals(value, result);
    }

    @Test
    public void testTransform_FPExpr_NegativeDouble() {
        double value = -42.0;
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort64());
        Object result = transformer.transform(expr, model, DoubleType.getInstance());
        assertEquals(value, result);
    }

    @Test
    public void testTransform_FPExpr_PositiveFloatInfinity() {
        float value = Float.POSITIVE_INFINITY;
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort32());
        Object result = transformer.transform(expr, model, FloatType.getInstance());
        assertEquals(value, result);
    }

    @Test
    public void testTransform_FPExpr_NegativeFloatInfinity() {
        float value = Float.NEGATIVE_INFINITY;
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort32());
        Object result = transformer.transform(expr, model, FloatType.getInstance());
        assertEquals(value, result);
    }
}
