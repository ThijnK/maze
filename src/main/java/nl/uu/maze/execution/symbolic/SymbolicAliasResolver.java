package nl.uu.maze.execution.symbolic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.microsoft.z3.ArraySort;
import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.symbolic.HeapObjects.ArrayObject;
import nl.uu.maze.execution.symbolic.HeapObjects.HeapObject;
import nl.uu.maze.execution.symbolic.PathConstraint.AliasConstraint;
import nl.uu.maze.instrument.TraceManager;
import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.util.Pair;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Sorts;
import nl.uu.maze.util.Z3Utils;
import sootup.core.jimple.common.stmt.Stmt;

public class SymbolicAliasResolver {
    private static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx = Z3ContextProvider.getContext();

    private static final SymbolicRefExtractor refExtractor = new SymbolicRefExtractor();

    /**
     * Split the given symbolic state into multiple states where the given
     * unresolved references are resolved to a single alias.
     * If no splitting is needed, the original state is modified and an empty list
     * is returned.
     */
    public static List<SymbolicState> splitOnAliases(SymbolicState state, Stmt stmt, boolean replay) {
        Set<Expr<?>> unresolvedRefs = refExtractor.extract(stmt, state);
        if (unresolvedRefs.isEmpty()) {
            return List.of(); // No unresolved references, no need to split
        }

        List<SymbolicState> newStates = new ArrayList<>();

        // First collect all unresolved references and their aliases
        List<Pair<Expr<?>, Expr<?>[]>> refsToResolve = new ArrayList<>();
        for (Expr<?> ref : unresolvedRefs) {
            if (ref != null && !state.heap.isResolved(ref)) {
                Set<Expr<?>> aliases = state.heap.getAliases(ref);
                if (aliases != null) {
                    refsToResolve.add(Pair.of(ref, aliases.toArray(Expr<?>[]::new)));
                }
            }
        }

        // Handle replay case specially
        if (replay) {
            for (Pair<Expr<?>, Expr<?>[]> pair : refsToResolve) {
                Expr<?> ref = pair.getFirst();
                Expr<?>[] aliases = pair.getSecond();

                // Find first non-null alias
                int j = 0;
                for (; j < aliases.length; j++) {
                    if (!sorts.isNull(aliases[j])) {
                        break;
                    }
                }
                if (j >= aliases.length)
                    j = 0; // Default to first if all null

                Expr<?> alias = aliases[j];
                state.heap.setSingleAlias(ref, alias);

                // If symRef here is a select expression on an array which is symbolic, we need
                // to add as path constraint instead of engine constraint
                Expr<?> elemsExpr = Z3Utils.findExpr(ref, (e) -> e.getSort() instanceof ArraySort);
                if (elemsExpr != null) {
                    String arrRef = elemsExpr.toString().substring(0, elemsExpr.toString().indexOf("_"));
                    HeapObject heapObj = state.heap.get(arrRef);
                    if (heapObj instanceof ArrayObject arrObj && arrObj.isSymbolic) {
                        state.addPathConstraint(ctx.mkEq(ref, alias));
                        continue;
                    }
                }

                state.addEngineConstraint(ctx.mkEq(ref, alias));
            }
            return List.of();
        }

        splitOnAliasCombinations(state, refsToResolve, 0, new int[refsToResolve.size()], newStates);
        return newStates;
    }

    /**
     * Recursively generate all possible combinations of aliases for the given
     * references.
     */
    private static void splitOnAliasCombinations(
            SymbolicState state,
            List<Pair<Expr<?>, Expr<?>[]>> refs,
            int index,
            int[] currentCombination,
            List<SymbolicState> resultStates) {
        if (index == refs.size()) {
            // We have a complete combination, create a state for it
            SymbolicState newState = state.clone();

            // Set all aliases according to the current combination
            for (int i = 0; i < refs.size(); i++) {
                Expr<?> ref = refs.get(i).getFirst();
                Expr<?>[] aliases = refs.get(i).getSecond();
                int aliasIndex = currentCombination[i];
                Expr<?> alias = aliases[aliasIndex];
                newState.heap.setSingleAlias(ref, alias);

                AliasConstraint constraint = new AliasConstraint(state, ref, aliases, aliasIndex);

                // For parameters, add to path constraints, for others add to engine constraints
                if (state.getParamType(ref.toString()) != null) {
                    newState.addPathConstraint(constraint);
                } else {
                    newState.addEngineConstraint(constraint);
                }
            }

            resultStates.add(newState);
            return;
        }

        // Try each alias for the current reference
        Expr<?>[] aliases = refs.get(index).getSecond();
        for (int i = 0; i < aliases.length; i++) {
            currentCombination[index] = i;
            splitOnAliasCombinations(state, refs, index + 1, currentCombination, resultStates);
        }
    }

    /**
     * Resolve alias for reference parameters when replaying a trace.
     * The correct alias is recorded in the trace.
     */
    public static void resolveAliasForParameter(SymbolicState state, Expr<?> symRef) {
        TraceEntry entry = TraceManager.consumeEntry(state.getMethodSignature());
        if (entry == null || !entry.isAliasResolution()) {
            state.setExceptionThrown();
            return;
        }
        // For callee states, parameters are passed, so not symbolic and are thus
        // already resolved to a single alias
        if (state.getMethodType().isCallee() || state.heap.isResolved(symRef)) {
            return;
        }

        Set<Expr<?>> aliases = state.heap.getAliases(symRef);
        if (aliases == null) {
            return;
        }
        Expr<?>[] aliasArr = aliases.toArray(Expr<?>[]::new);

        // Find the right concrete reference for the parameter
        // If null, this is simply the null constant
        // Otherwise, it's the concrete reference of the aliased parameter
        // Note: value in trace is the index of the aliased parameter or -1 for null
        int aliasIndex = entry.getValue();
        Expr<?> alias = aliasIndex == -1 ? sorts.getNullConst()
                : state.heap.getSingleAlias(ArgMap.getSymbolicName(state.getMethodType(), aliasIndex));
        // Find the index of this alias in the aliasArr
        int i = 0;
        for (; i < aliasArr.length; i++) {
            if (aliasArr[i].equals(alias)) {
                break;
            }
        }
        // Constrain the parameter to the right alias
        state.heap.setSingleAlias(symRef, alias);
        AliasConstraint constraint = new AliasConstraint(state, symRef, aliasArr, i);
        state.addPathConstraint(constraint);
    }
}
