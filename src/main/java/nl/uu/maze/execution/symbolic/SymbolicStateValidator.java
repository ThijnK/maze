package nl.uu.maze.execution.symbolic;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.symbolic.SymbolicHeap.ArrayObject;
import nl.uu.maze.transform.Z3ToJavaTransformer;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.ArrayType;
import sootup.core.types.Type;

/**
 * Validates symbolic states by checking the satisfiability of the path
 * condition and evaluating the resulting Z3 models.
 */
public class SymbolicStateValidator {
    private static final Logger logger = LoggerFactory.getLogger(SymbolicStateValidator.class);
    private static final Z3Sorts sorts = Z3Sorts.getInstance();

    private Solver solver;
    private Z3ToJavaTransformer transformer;

    public SymbolicStateValidator(Context ctx) {
        this.solver = ctx.mkSolver();
        this.transformer = new Z3ToJavaTransformer(ctx);
    }

    /**
     * Validates the given list of path constraints. If the path condition is
     * satisfiable, the corresponding model is returned.
     * 
     * @param pathConstraints The path constraints to validate
     * @return An optional model if the path condition is satisfiable
     */
    public Optional<Model> validate(List<BoolExpr> pathConstraints) {
        solver.add(pathConstraints.toArray(new BoolExpr[0]));
        Status status = solver.check();
        logger.debug("Path condition " + status.toString() + ": " + pathConstraints);
        Optional<Model> model = Optional.empty();
        if (status == Status.SATISFIABLE) {
            model = Optional.ofNullable(solver.getModel());
        }
        solver.reset();
        return model;
    }

    /**
     * Validates the given symbolic state. If the path condition is satisfiable, the
     * corresponding model is returned.
     * 
     * @param state The symbolic state to validate
     * @return An optional model if the path condition is satisfiable
     */
    public Optional<Model> validate(SymbolicState state) {
        return validate(state.getPathConstraints());
    }

    /**
     * Validates the given list of symbolic states. If the path condition is
     * satisfiable, the corresponding model is returned.
     * 
     * @param states The symbolic states to validate
     * @return A list of models for the satisfiable path conditions
     */
    public List<Model> validateAll(List<SymbolicState> states) {
        List<Model> result = new ArrayList<>();
        for (SymbolicState state : states) {
            Optional<Model> model = validate(state);
            if (model.isPresent()) {
                result.add(model.get());
            }
        }
        return result;
    }

    /**
     * Evaluates the model and returns a map of known parameters.
     * 
     * @param model The Z3 model to evaluate
     * @return A map of known parameters
     */
    public ArgMap evaluate(Model model, SymbolicState state) {
        ArgMap argMap = new ArgMap();

        // nullValue is the interpreted value of the null constant
        // If any argument is interpreted to be equal to nullValue, it is considered
        // null
        // This would be the case if there was a null comparison in the path condition
        Expr<?> nullExpr = model.eval(Z3Sorts.getInstance().getNullConst(), true);

        // Keep track of reference values that we've encountered, to be able to set two
        // arguments to the same object when they are interpreted to be equal
        Map<Expr<?>, ObjectRef> refValues = new HashMap<>();

        for (FuncDecl<?> decl : model.getConstDecls()) {
            String var = decl.getName().toString();
            if (!var.contains("arg")) {
                continue;
            }
            Expr<?> expr = model.getConstInterp(decl);

            // Remove potential suffixes (e.g., _elems, _len)
            var = var.contains("_") ? var.substring(0, var.indexOf('_')) : var;
            Type type = state.getParamType(var);

            // For reference types (arrays + objects)
            if (expr.getSort().equals(sorts.getRefSort())) {
                // If the variable is interpreted as null, set it to null
                if (expr.equals(nullExpr)) {
                    argMap.set(var, null);
                    continue;
                }
                // If interpreted equal to another reference, set it to the same value, choosing
                // the value of the more constrained reference
                if (refValues.containsKey(expr)) {
                    ObjectRef ref = refValues.get(expr);
                    // Compare the two references, whichever one is more constrained in path
                    // condition is the "base" reference
                    if (state.isMoreConstrained(var, ref.getVar())) {
                        // If the current var is more constrained, set it as new base reference
                        // and evaluate it (so no continue)
                        refValues.put(expr, new ObjectRef(var));
                        argMap.set(ref.getVar(), new ObjectRef(var));
                    } else {
                        // If the other var is more constrained, set the current var to the same value
                        argMap.set(var, ref);
                        continue;
                    }
                }
            }

            // For arrays
            if (type instanceof ArrayType) {
                // There can be multiple declarations for the same array (elems and len)
                if (argMap.containsKey(var)) {
                    if (expr.getSort().equals(sorts.getRefSort())) {
                        refValues.put(expr, new ObjectRef(var));
                    }
                    continue;
                }

                Expr<?> arrRef = state.heap.newRef(var);
                ArrayObject arrObj = state.getArrayObject(arrRef);
                Type elemType = ((ArrayType) type).getBaseType();
                Object arr = transformer.transformArray(arrObj, model, elemType);
                argMap.set(var, arr);
                if (expr.getSort().equals(sorts.getRefSort())) {
                    refValues.put(expr, new ObjectRef(var));
                }
            } else {
                // Primitive types
                Object value = transformer.transformExpr(model.getConstInterp(decl), type);
                argMap.set(var, value);
            }
        }
        return argMap;
    }

    /**
     * Validates and evaluates the given symbolic state.
     * 
     * @param state The symbolic state to validate and evaluate
     * @return A map of known parameters if the path condition is satisfiable
     */
    public Optional<ArgMap> evaluate(SymbolicState state) {
        Optional<Model> model = validate(state);
        return model.map(m -> evaluate(m, state));
    }

    /**
     * Validates and evaluates the given list of symbolic states.
     * 
     * @param states The symbolic states to validate and evaluate
     * @return A list of known parameters for the satisfiable path conditions
     */
    public List<ArgMap> evaluate(List<SymbolicState> states) {
        List<ArgMap> result = new ArrayList<>();
        for (SymbolicState state : states) {
            Optional<ArgMap> params = evaluate(state);
            params.ifPresent(result::add);
        }
        return result;
    }

    /**
     * Object value representing that the value of an argument is a reference to
     * another object of the given variable name.
     */
    public static class ObjectRef {
        private final String var;

        public ObjectRef(String var) {
            this.var = var;
        }

        public String getVar() {
            return var;
        }

        @Override
        public String toString() {
            return var;
        }
    }
}
