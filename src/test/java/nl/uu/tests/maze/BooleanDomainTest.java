package nl.uu.tests.maze;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Expr;
import com.microsoft.z3.Status;
import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.symbolic.PathConstraint;
import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.tests.maze.CUTs.CUT_IntegerLong;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import sootup.core.jimple.common.ref.JParameterRef;
import sootup.core.types.PrimitiveType;
import sootup.core.types.Type;

class BooleanDomainTest {
    private static JavaAnalyzer analyzer;

    @BeforeAll static void setup() throws Exception {
        JavaAnalyzer.dropInstance();
        analyzer = JavaAnalyzer.initialize("target/test-classes", BooleanDomainTest.class.getClassLoader());
    }

    @ParameterizedTest @ValueSource(strings = {"parameter", "field", "array"})
    void symbolicBooleansHaveOnlyZeroAndOneAsModels(String source) throws Exception {
        var method = analyzer.getSootClass(analyzer.getClassType(CUT_IntegerLong.class.getName()))
                .getMethods().stream().filter(m -> m.getName().equals("BooleanParam")).findFirst().orElseThrow();
        var state = new SymbolicState(method, analyzer.getCFG(method));
        var boolType = PrimitiveType.getBoolean();
        var ctx = Z3ContextProvider.getContext();
        Expr<?> value;
        switch (source) {
            case "parameter" -> value = new JimpleToZ3Transformer().transform(new JParameterRef(boolType, 0), state);
            case "field" -> {
                state.store.put("box", state.heap.allocateObject(analyzer.getClassType(Boolean.class.getName())));
                value = state.heap.getField("box", "value", boolType);
            }
            case "array" -> {
                var reference = state.heap.allocateArray("array", Type.createArrayType(boolType, 1), boolType);
                value = state.heap.getArrayElement("element", "array", reference, ctx.mkBV(0, 32));
            }
            default -> throw new AssertionError(source);
        }
        var solver = ctx.mkSolver();
        solver.add(state.getEngineConstraints().stream().map(PathConstraint::getConstraint).toArray(BoolExpr[]::new));
        for (int legal : new int[] {0, 1}) {
            solver.push();
            solver.add(ctx.mkEq(value, ctx.mkBV(legal, 32)));
            assertEquals(Status.SATISFIABLE, solver.check(), source + " must allow " + legal);
            solver.pop();
        }
        solver.add(ctx.mkNot(ctx.mkEq(value, ctx.mkBV(0, 32))), ctx.mkNot(ctx.mkEq(value, ctx.mkBV(1, 32))));
        assertEquals(Status.UNSATISFIABLE, solver.check(), source + " must not produce noncanonical true values");
    }
}
