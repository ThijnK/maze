package nl.uu.maze.execution.symbolic;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;
import com.microsoft.z3.Model;
import com.microsoft.z3.Solver;
import com.microsoft.z3.Status;

import nl.uu.maze.util.Pair;

public class SymbolicStateValidator {
    private static final Logger logger = LoggerFactory.getLogger(SymbolicStateValidator.class);

    private Solver solver;

    public SymbolicStateValidator(Context ctx) {
        this.solver = ctx.mkSolver();
    }

    /**
     * Validates the given symbolic state. If the path condition is satisfiable, the
     * corresponding model is returned.
     * 
     * @param state The symbolic state to validate
     * @return An optional model if the path condition is satisfiable, null
     *         otherwise
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
     * @return A list of models for the satisfiable path conditions, along with the
     *         corresponding symbolic states
     */
    public List<Pair<Model, SymbolicState>> validate(List<SymbolicState> states) {
        List<Pair<Model, SymbolicState>> result = new ArrayList<>();
        for (SymbolicState state : states) {
            Optional<Model> model = validate(state);
            if (model.isPresent()) {
                result.add(Pair.of(model.get(), state));
            }
        }
        return result;
    }
}
