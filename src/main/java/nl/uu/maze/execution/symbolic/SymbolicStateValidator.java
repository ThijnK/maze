package nl.uu.maze.execution.symbolic;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;

import nl.uu.maze.transform.Z3ToJavaTransformer;

public class SymbolicStateValidator {
    private static final Logger logger = LoggerFactory.getLogger(SymbolicStateValidator.class);

    private Solver solver;
    private Z3ToJavaTransformer transformer;

    public SymbolicStateValidator(Context ctx) {
        this.solver = ctx.mkSolver();
        this.transformer = new Z3ToJavaTransformer(ctx);
    }

    /**
     * Validates the given symbolic state. If the path condition is satisfiable, the
     * corresponding model is returned.
     * 
     * @param state The symbolic state to validate
     * @return An optional model if the path condition is satisfiable
     */
    public Optional<Model> validate(SymbolicState state) {
        logger.debug("Final state: " + state);
        solver.add(state.getPathCondition());
        Status status = solver.check();
        logger.debug("Path condition " + status.toString());
        Optional<Model> model = Optional.empty();
        if (status == Status.SATISFIABLE) {
            model = Optional.ofNullable(solver.getModel());
        }
        solver.reset();
        return model;
    }

    /**
     * Validates the given list of symbolic states. If the path condition is
     * satisfiable, the corresponding model is returned.
     * 
     * @param states The symbolic states to validate
     * @return A list of models for the satisfiable path conditions
     */
    public List<Model> validate(List<SymbolicState> states) {
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
    public Map<String, Object> evaluate(Model model) {
        Map<String, Object> knownParams = new HashMap<>();
        for (FuncDecl<?> decl : model.getConstDecls()) {
            String var = decl.getName().toString();
            Expr<?> expr = model.getConstInterp(decl);

            Object value = transformer.transform(expr, model);
            knownParams.put(var, value);
        }
        return knownParams;
    }

    /**
     * Evaluates the list of models and returns a list of known parameters.
     * 
     * @param models The list of Z3 models to evaluate
     * @return A list of known parameters
     */
    public List<Map<String, Object>> evaluate(List<Model> models) {
        List<Map<String, Object>> knownParams = new ArrayList<>();
        for (Model model : models) {
            knownParams.add(evaluate(model));
        }
        return knownParams;
    }
}
