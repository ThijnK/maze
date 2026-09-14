import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Status;
import com.microsoft.z3.Version;

/** Verifies that the container's Z3 Java binding can load JNI and solve a query. */
public final class Z3Smoke {
    public static void main(String[] args) {
        try (var context = new Context()) {
            var solver = context.mkSolver();
            var number = context.mkIntConst("number");
            solver.add(new BoolExpr[] { context.mkEq(number, context.mkInt(42)) });
            if (solver.check() != Status.SATISFIABLE) {
                throw new IllegalStateException("Z3 failed the satisfiable query");
            }
            solver.add(new BoolExpr[] { context.mkNot(context.mkEq(number, context.mkInt(42))) });
            if (solver.check() != Status.UNSATISFIABLE) {
                throw new IllegalStateException("Z3 failed the unsatisfiable query");
            }
            System.out.println("Z3 " + Version.getString() + ": JNI and solver checks passed");
        }
    }
}
