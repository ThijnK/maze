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

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.transform.Z3ToJavaTransformer;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.Type;
import sootup.core.types.PrimitiveType.*;

public class Z3ToJavaTransformerTest {
    private static Z3ToJavaTransformer transformer;
    private static Context ctx;
    private static Model model;
    private static SymbolicState state;

    @BeforeAll
    public static void setUp() {
        ctx = new Context();
        Z3Sorts.initialize(ctx);
        transformer = new Z3ToJavaTransformer(ctx);
        Solver solver = ctx.mkSolver();
        // BoolExpr[] constraints = new BoolExpr[] { ctx.mkTrue() };
        // solver.add(constraints);
        solver.check();
        model = solver.getModel();
        state = new SymbolicState(ctx, null);
        state.setParamType("int", IntType.getInstance());
        state.setParamType("long", LongType.getInstance());
        state.setParamType("float", FloatType.getInstance());
        state.setParamType("double", DoubleType.getInstance());
        state.setParamType("byte", ByteType.getInstance());
        state.setParamType("short", ShortType.getInstance());
        state.setParamType("char", CharType.getInstance());
        state.setParamType("boolean", BooleanType.getInstance());
    }

    @Test
    public void testTransform_BoolExpr() {
        BoolExpr exprTrue = ctx.mkTrue();
        BoolExpr exprFalse = ctx.mkFalse();
        Object resultTrue = transformer.transformExpr("", exprTrue, model, state);
        Object resultFalse = transformer.transformExpr("", exprFalse, model, state);

        assert (Boolean) resultTrue;
        assert !(Boolean) resultFalse;
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, -1, 69, -69, Integer.MAX_VALUE, Integer.MIN_VALUE })
    public void testTransform_IntExpr(int value) {
        IntExpr expr = ctx.mkInt(value);
        Object result = transformer.transformExpr("int", expr, model, state);
        assertEquals(value, result);

    }

    @ParameterizedTest
    @ValueSource(ints = { 0, 1, -1, 69, -69, Integer.MAX_VALUE, Integer.MIN_VALUE })
    public void testTransform_BVExpr_Int(int value) {
        Type type = IntType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr("int", expr, model, state);
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(longs = { 0, 1, -1, 69, -69, Long.MAX_VALUE, Long.MIN_VALUE })
    public void testTransform_BVExpr_Long(long value) {
        Type type = LongType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr("long", expr, model, state);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Byte() {
        byte value = Byte.MAX_VALUE;
        Type type = ByteType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr("byte", expr, model, state);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Short() {
        short value = Short.MAX_VALUE;
        Type type = ShortType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr("short", expr, model, state);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Char() {
        char value = 'a';
        Type type = CharType.getInstance();
        BitVecExpr expr = ctx.mkBV(value, Type.getValueBitSize(type));
        Object result = transformer.transformExpr("char", expr, model, state);
        assertEquals(value, result);
    }

    @Test
    public void testTransform_BVExpr_Boolean() {
        boolean value = true;
        Type type = BooleanType.getInstance();
        BitVecExpr expr = ctx.mkBV(value ? 1 : 0, Type.getValueBitSize(type));
        Object result = transformer.transformExpr("boolean", expr, model, state);
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(floats = { 0.0f, 1.0f, -1.0f, 69.0f, -69.0f, Float.MAX_VALUE, Float.MIN_VALUE, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NaN })
    public void testTransform_FPExpr_Float(float value) {
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort32());
        Object result = transformer.transformExpr("float", expr, model, state);
        assertEquals(value, result);
    }

    @ParameterizedTest
    @ValueSource(doubles = { 0.0, 1.0, -1.0, 69.0, -69.0, Double.MAX_VALUE, Double.MIN_VALUE, Double.POSITIVE_INFINITY,
            Double.NEGATIVE_INFINITY, Double.NaN })
    public void testTransform_FPExpr_Double(double value) {
        FPNum expr = ctx.mkFP(value, ctx.mkFPSort64());
        Object result = transformer.transformExpr("double", expr, model, state);
        assertEquals(value, result);
    }
}
