package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.*;

import nl.uu.maze.util.ArrayUtils;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.core.types.Type;

/**
 * Represents a symbolic state in the symbolic execution engine.
 * 
 * <p>
 * A symbolic state consists of:
 * <ul>
 * <li>The current statement being executed</li>
 * <li>The current depth of the symbolic execution</li>
 * <li>A mapping from variable names to symbolic values</li>
 * <li>The path condition of the execution path leading to this state</li>
 * </ul>
 * </p>
 */
public class SymbolicState {
    private static final Logger logger = LoggerFactory.getLogger(SymbolicState.class);

    private Context ctx;
    private Stmt currentStmt;
    private int currentDepth;

    private Map<String, Expr<?>> symbolicVariables;
    private List<BoolExpr> pathConstraints;

    // Keep track of (SootUp) types of symbolic variables
    private Map<String, Type> variableTypes;

    public SymbolicState(Context ctx, Stmt stmt) {
        this.currentStmt = stmt;
        this.symbolicVariables = new HashMap<>();
        this.ctx = ctx;
        this.pathConstraints = new ArrayList<>();
        this.variableTypes = new HashMap<>();
    }

    public SymbolicState(Context ctx, Stmt stmt, int depth, Map<String, Expr<?>> symbolicVariables,
            List<BoolExpr> pathConstraints, Map<String, Type> variableTypes) {
        this.currentStmt = stmt;
        this.currentDepth = depth;
        this.symbolicVariables = new HashMap<>(symbolicVariables);
        this.ctx = ctx;
        this.pathConstraints = new ArrayList<>(pathConstraints);
        this.variableTypes = new HashMap<>(variableTypes);
    }

    public int incrementDepth() {
        return ++currentDepth;
    }

    public Stmt getCurrentStmt() {
        return currentStmt;
    }

    public void setCurrentStmt(Stmt stmt) {
        this.currentStmt = stmt;
    }

    public void setVariable(String var, Expr<?> expression) {
        symbolicVariables.put(var, expression);
    }

    public Expr<?> getVariable(String var) {
        return symbolicVariables.getOrDefault(var, null);
    }

    public void setVariableType(String var, Type type) {
        variableTypes.put(var, type);
    }

    public Type getVariableType(String var) {
        return variableTypes.getOrDefault(var, null);
    }

    /**
     * Adds a new path constraint to the current path condition.
     * 
     * @param constraint The new path constraint to add
     */
    public void addPathConstraint(BoolExpr constraint) {
        pathConstraints.add(constraint);
    }

    /**
     * Returns the path condition of the current state as the conjunction of all
     * path constraints.
     * 
     * @return The path condition as a Z3 BoolExpr
     */
    public List<BoolExpr> getPathConstraints() {
        return pathConstraints;
    }

    /**
     * Generates a unique identifier for the current path condition.
     * 
     * @return A unique identifier for the path condition
     */
    public int getPathConditionIdentifier() {
        StringBuilder sb = new StringBuilder();
        for (BoolExpr constraint : pathConstraints) {
            sb.append(constraint.toString());
        }
        return sb.toString().hashCode();
    }

    /**
     * Checks if the current path condition is a new path condition by comparing it
     * and any of its prefixes to the set of explored paths.
     * 
     * @param exploredPaths The set of previously explored paths (represented by
     *                      their identifiers, obtained by
     *                      {@link #getPathConditionIdentifier()})
     * @return True if the path condition is new, false otherwise
     */
    public boolean isNewPathCondition(Set<Integer> exploredPaths) {
        StringBuilder sb = new StringBuilder();
        for (BoolExpr constraint : pathConstraints) {
            sb.append(constraint.toString());
            if (exploredPaths.contains(sb.toString().hashCode())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Finds a <i>new</i> satisfiable path condition by negating a random path
     * constraint.
     * 
     * @param validator     The symbolic state validator
     * @param exploredPaths The set of previously explored paths (represented by
     *                      their identifiers, obtained by
     *                      {@link #getPathConditionIdentifier()})
     * @return A Z3 model representing the new path condition, if found
     */
    public Optional<Model> findNewPathCondition(SymbolicStateValidator validator, Set<Integer> exploredPaths) {
        if (pathConstraints.isEmpty()) {
            return Optional.empty();
        }

        // Get the indices of path constraints in an array
        Integer[] indices = new Integer[pathConstraints.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        // Shuffle the array if indices to get a random order
        ArrayUtils.shuffle(indices);

        // Find the first path constraint for which negating it results in a new path
        // condition
        for (int index : indices) {
            BoolExpr constraint = pathConstraints.get(index);
            // Avoid double negation
            BoolExpr negated = constraint.isNot() ? (BoolExpr) constraint.getArgs()[0] : ctx.mkNot(constraint);
            logger.debug("Negating " + constraint + " -> " + negated);
            pathConstraints.set(index, negated);
            // Check if already explored
            if (isNewPathCondition(exploredPaths)) {
                // Validate to make sure path condition satisfiable
                Optional<Model> model = validator.validate(this);
                if (model.isPresent()) {
                    return model;
                }
            }
            pathConstraints.set(index, constraint);
        }

        return Optional.empty();
    }

    public boolean isFinalState(StmtGraph<?> cfg) {
        return cfg.getAllSuccessors(currentStmt).isEmpty();
    }

    public SymbolicState clone(Stmt stmt) {
        return new SymbolicState(ctx, stmt, currentDepth, symbolicVariables, pathConstraints, variableTypes);
    }

    public SymbolicState clone() {
        return clone(currentStmt);
    }

    @Override
    public String toString() {
        return "State: " + symbolicVariables + ", PC: " + getPathConstraints();
    }
}
