package nl.uu.maze.search;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.BoolExpr;
import com.microsoft.z3.Context;
import com.microsoft.z3.Model;

import nl.uu.maze.execution.symbolic.SymbolicState;
import nl.uu.maze.execution.symbolic.SymbolicStateValidator;

public abstract class ConcreteSearchStrategy implements SearchStrategy {
    private Set<Integer> exploredPaths = new HashSet<>();

    protected abstract void add(PathConditionCandidate candidate);

    public boolean add(SymbolicState state) {
        if (isExplored(state)) {
            return false;
        }

        // Add a candidate for every constraint in the path condition
        for (int i = 0; i < state.getPathConstraints().size(); i++) {
            add(new PathConditionCandidate(state.getPathConstraints(), i, state.getContext()));
        }
        return true;
    }

    protected abstract PathConditionCandidate next();

    public Optional<Model> next(SymbolicStateValidator validator) {
        // Find the first candidate that has not been explored yet and is satisfiable
        PathConditionCandidate candidate;
        while ((candidate = next()) != null) {
            candidate.applyNegation();
            if (!candidate.isExplored()) {
                Optional<Model> model = validator.validate(candidate.getPathConstraints());
                if (model.isPresent()) {
                    return model;
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Determines whether a path has been explored before, and adds it to the set of
     * explored paths if not already explored.
     * 
     * @param state
     * @return
     */
    protected boolean isExplored(SymbolicState state) {
        int path = PathConditionCandidate.hash(state.getPathConstraints());
        if (exploredPaths.contains(path)) {
            return true;
        }
        exploredPaths.add(path);
        return false;
    }

    protected class PathConditionCandidate {
        private List<BoolExpr> pathConstraints;
        private int index;
        private Context ctx;

        public PathConditionCandidate(List<BoolExpr> pathConstraints, int index, Context ctx) {
            this.pathConstraints = pathConstraints;
            this.index = index;
            this.ctx = ctx;
        }

        public List<BoolExpr> getPathConstraints() {
            return pathConstraints;
        }

        public int getIndex() {
            return index;
        }

        public List<BoolExpr> applyNegation() {
            BoolExpr constraint = pathConstraints.get(index);
            // Avoid double negation
            BoolExpr negated = constraint.isNot() ? (BoolExpr) constraint.getArgs()[0] : ctx.mkNot(constraint);
            pathConstraints.set(index, negated);
            return pathConstraints;
        }

        public static int hash(List<BoolExpr> pathConstraints) {
            StringBuilder sb = new StringBuilder();
            for (BoolExpr constraint : pathConstraints) {
                sb.append(constraint.toString());
            }
            return sb.toString().hashCode();
        }

        public boolean isExplored() {
            StringBuilder sb = new StringBuilder();
            for (BoolExpr constraint : pathConstraints) {
                sb.append(constraint.toString());
                if (exploredPaths.contains(sb.toString().hashCode())) {
                    return true;
                }
            }
            return false;
        }
    }
}
