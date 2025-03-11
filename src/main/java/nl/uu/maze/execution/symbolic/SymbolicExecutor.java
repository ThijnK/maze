package nl.uu.maze.execution.symbolic;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

import com.microsoft.z3.*;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.ArgMap.ObjectRef;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.concrete.ObjectInstantiator;
import nl.uu.maze.execution.symbolic.SymbolicHeap.*;
import nl.uu.maze.instrument.TraceManager.TraceEntry;
import nl.uu.maze.transform.JavaToZ3Transformer;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.ObjectUtils;
import nl.uu.maze.util.Z3Sorts;
import nl.uu.maze.util.Z3Utils;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.LValue;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.basic.Value;
import sootup.core.jimple.common.constant.IntConstant;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JDynamicInvokeExpr;
import sootup.core.jimple.common.ref.*;
import sootup.core.jimple.common.stmt.*;
import sootup.core.jimple.javabytecode.stmt.JSwitchStmt;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.Type;
import sootup.core.types.VoidType;
import sootup.java.core.JavaSootMethod;

/**
 * Provides symbolic execution capabilities.
 */
public class SymbolicExecutor {
    private static final Logger logger = LoggerFactory.getLogger(SymbolicExecutor.class);
    private static final Z3Sorts sorts = Z3Sorts.getInstance();

    private final Context ctx;
    private final ConcreteExecutor executor;
    private final SymbolicStateValidator validator;
    private final JavaAnalyzer analyzer;
    private final JimpleToZ3Transformer jimpleToZ3;
    private final JavaToZ3Transformer javaToZ3;
    private final SymbolicRefExtractor refExtractor = new SymbolicRefExtractor();

    public SymbolicExecutor(Context ctx, ConcreteExecutor executor, SymbolicStateValidator validator,
            JavaAnalyzer analyzer) {
        this.ctx = ctx;
        this.executor = executor;
        this.validator = validator;
        this.analyzer = analyzer;
        this.jimpleToZ3 = new JimpleToZ3Transformer(ctx);
        this.javaToZ3 = new JavaToZ3Transformer(ctx);
    }

    /**
     * Execute a single step of symbolic execution on the given symbolic state.
     * 
     * @param state The symbolic state
     * @return A list of successor symbolic states
     */
    public List<SymbolicState> step(SymbolicState state) {
        return step(state, null);
    }

    /**
     * Execute a single step of symbolic execution on the symbolic state.
     * If replaying a trace, follows the branch indicated by the trace.
     * 
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <code>null</code> if
     *                 not replaying a trace
     * @return A list of successor symbolic states
     */
    public List<SymbolicState> step(SymbolicState state, Iterator<TraceEntry> iterator) {
        Stmt stmt = state.getStmt();

        if (stmt instanceof JIfStmt)
            return handleIfStmt((JIfStmt) stmt, state, iterator);
        else if (stmt instanceof JSwitchStmt)
            return handleSwitchStmt((JSwitchStmt) stmt, state, iterator);
        else if (stmt instanceof AbstractDefinitionStmt)
            return handleDefStmt((AbstractDefinitionStmt) stmt, state, iterator);
        else if (stmt instanceof JInvokeStmt)
            return handleInvokeStmt(stmt.getInvokeExpr(), state, iterator);
        else if (stmt instanceof JThrowStmt) {
            state.setExceptionThrown();
            return handleOtherStmts(state, iterator);
        } else if (stmt instanceof JReturnStmt)
            return handleReturnStmt((JReturnStmt) stmt, state, iterator);
        else
            return handleOtherStmts(state, iterator);
    }

    /**
     * Symbolically execute an if statement.
     * 
     * @param stmt     The if statement as a Jimple statement ({@link JIfStmt})
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <code>null</code> if
     *                 not replaying a trace
     * @return A list of successor symbolic states after executing the if statement
     */
    private List<SymbolicState> handleIfStmt(JIfStmt stmt, SymbolicState state, Iterator<TraceEntry> iterator) {
        // Split the state if the condition contains a symbolic reference with multiple
        // aliases (i.e. reference comparisons)
        Expr<?> symRef = refExtractor.extract(stmt.getCondition(), state);
        Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef);
        if (splitStates.isPresent()) {
            return splitStates.get();
        }

        List<Stmt> succs = state.getSuccessors();
        BoolExpr cond = (BoolExpr) jimpleToZ3.transform(stmt.getCondition(), state);
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        // If replaying a trace, follow the branch indicated by the trace
        if (iterator != null) {
            // If end of iterator is reached, it means exception was thrown
            if (!iterator.hasNext()) {
                // Set this state as an exceptional state and return it so it will be counted as
                // a final state
                state.setExceptionThrown();
                return List.of(state);
            }

            TraceEntry entry = iterator.next();
            int branchIndex = entry.getValue();
            state.addPathConstraint(branchIndex == 0 ? Z3Utils.negate(ctx, cond) : cond);
            state.setStmt(succs.get(branchIndex));
            newStates.add(state);
        }
        // Otherwise, follow both branches
        else {
            // False branch
            SymbolicState newState = state.clone();
            newState.setStmt(succs.get(0));
            newState.addPathConstraint(Z3Utils.negate(ctx, cond));
            newStates.add(newState);

            // True branch
            state.addPathConstraint(cond);
            state.setStmt(succs.get(1));
            newStates.add(state);
        }

        return newStates;
    }

    /**
     * Symbolically execute a switch statement.
     * 
     * @param stmt     The switch statement as a Jimple statement
     *                 ({@link JSwitchStmt})
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <code>null</code> if
     *                 not replaying a trace
     * @return A list of successor symbolic states after executing the switch
     */
    private List<SymbolicState> handleSwitchStmt(JSwitchStmt stmt, SymbolicState state, Iterator<TraceEntry> iterator) {
        List<Stmt> succs = state.getSuccessors();
        Expr<?> var = state.lookup(stmt.getKey().toString());
        List<IntConstant> values = stmt.getValues();
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();

        // If replaying a trace, follow the branch indicated by the trace
        if (iterator != null) {
            // If end of iterator is reached, it means exception was thrown
            if (!iterator.hasNext()) {
                state.setExceptionThrown();
                return List.of(state);
            }

            TraceEntry entry = iterator.next();
            int branchIndex = entry.getValue();
            if (branchIndex >= values.size()) {
                // Default case constraint is the negation of all other constraints
                for (int i = 0; i < values.size(); i++) {
                    state.addPathConstraint(ctx.mkNot(mkSwitchConstraint(var, values.get(i))));
                }
            } else {
                // Otherwise add constraint for the branch that was taken
                state.addPathConstraint(mkSwitchConstraint(var, values.get(branchIndex)));
            }
            state.setStmt(succs.get(branchIndex));
            newStates.add(state);
        }
        // Otherwise, follow all branches
        else {
            // The last successor is the default case, whose constraint is the negation of
            // all other constraints
            SymbolicState defaultCaseState = state.clone();
            // For all cases, except the default case
            for (int i = 0; i < succs.size() - 1; i++) {
                SymbolicState newState = i == succs.size() - 2 ? state : state.clone();

                BoolExpr constraint = mkSwitchConstraint(var, values.get(i));
                newState.addPathConstraint(constraint);
                defaultCaseState.addPathConstraint(ctx.mkNot(constraint));
                newState.setStmt(succs.get(i));
                newStates.add(newState);
            }
            defaultCaseState.setStmt(succs.get(succs.size() - 1));
            newStates.add(defaultCaseState);
        }

        return newStates;
    }

    /**
     * Create a constraint for a switch case (i.e., the variable being switched over
     * equals the case value)
     */
    private BoolExpr mkSwitchConstraint(Expr<?> var, IntConstant value) {
        return ctx.mkEq(var, ctx.mkBV(value.getValue(), sorts.getIntBitSize()));
    }

    /**
     * Symbolically execute a definition statement (assign or identity).
     * 
     * @param stmt     The definition statement
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <code>null</code> if
     *                 not replaying a trace
     * @return A list of successor symbolic states after executing the definition
     *         statement
     */
    private List<SymbolicState> handleDefStmt(AbstractDefinitionStmt stmt, SymbolicState state,
            Iterator<TraceEntry> iterator) {
        // If either the lhs or the rhs contains a symbolic reference (e.g., an object
        // or array reference) with more than one potential alias, then we split the
        // state into one state for each alias, and set the symbolic reference to the
        // corresponding alias in each state
        // But only for assignments, not for identity statements
        if (stmt instanceof JAssignStmt) {
            Expr<?> symRef = refExtractor.extract(stmt.getRightOp(), state);
            symRef = symRef == null ? refExtractor.extract(stmt.getLeftOp(), state) : symRef;
            Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef);
            if (splitStates.isPresent()) {
                return splitStates.get();
            }
        }

        LValue leftOp = stmt.getLeftOp();
        Value rightOp = stmt.getRightOp();

        // For array access on symbolic arrays (i.e., parameters), we split the state
        // into one where the index is outside the bounds of the array (throws
        // exception) and one where it is not
        // This is to ensure that the engine can create an array of the correct size
        // when generating test cases
        SymbolicState outOfBoundsState = null;
        if (stmt.containsArrayRef()) {
            JArrayRef ref = leftOp instanceof JArrayRef ? (JArrayRef) leftOp : (JArrayRef) rightOp;
            if (state.isParam(ref.getBase().getName())) {
                BitVecExpr index = (BitVecExpr) jimpleToZ3.transform(ref.getIndex(), state);
                BitVecExpr len = (BitVecExpr) state.heap.getArrayLength(ref.getBase().getName());
                if (len == null) {
                    // If length is null, means we have a null reference, and exception is thrown
                    return handleOtherStmts(state, iterator);
                }

                // If replaying a trace, we should have a trace entry for the array access
                if (iterator != null) {
                    TraceEntry entry;
                    // If end of iterator is reached or not an array access, something is wrong
                    if (!iterator.hasNext() || !(entry = iterator.next()).isArrayAccess()) {
                        state.setExceptionThrown();
                        return List.of(state);
                    }

                    // If the entry value is 1, inside bounds, otherwise out of bounds
                    if (entry.getValue() == 0) {
                        state.addPathConstraint(ctx.mkOr(ctx.mkBVSLT(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                ctx.mkBVSGE(index, len)));
                        state.setExceptionThrown();
                        return handleOtherStmts(state, iterator);
                    } else {
                        state.addPathConstraint(ctx.mkAnd(ctx.mkBVSGE(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                ctx.mkBVSLT(index, len)));
                    }
                } else {
                    // If not replaying a trace, split the state into one where the index is out of
                    // bounds and one where it is not
                    if (state.isParam(ref.getBase().getName())) {
                        outOfBoundsState = state.clone();
                        outOfBoundsState
                                .addPathConstraint(ctx.mkOr(ctx.mkBVSLT(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                        ctx.mkBVSGE(index, len)));
                        outOfBoundsState.setExceptionThrown();
                        state.addPathConstraint(ctx.mkAnd(ctx.mkBVSGE(index, ctx.mkBV(0, sorts.getIntBitSize())),
                                ctx.mkBVSLT(index, len)));
                    }
                }
            }
        }

        Expr<?> value;
        if (stmt.containsInvokeExpr()) {
            // If this is an invocation, first check if a return value is already available
            // (i.e., the method was already executed)
            if (state.getReturnValue() != null) {
                value = state.getReturnValue();
                state.setReturnValue(null);
            }
            // If not already executed, first execute the method
            else {
                boolean executed = executeMethod(state, stmt.getInvokeExpr());
                // If executed here is false, the method will be executed symbolically by
                // {@link DSEController}, so relinquish control here
                if (!executed) {
                    return List.of(state);
                }
                // If executed concretely, the return value is stored in the state
                value = state.getReturnValue();
            }
        } else {
            value = jimpleToZ3.transform(rightOp, state, leftOp.toString());
        }

        if (leftOp instanceof JArrayRef) {
            JArrayRef ref = (JArrayRef) leftOp;
            BitVecExpr index = (BitVecExpr) jimpleToZ3.transform(ref.getIndex(), state);
            state.heap.setArrayElement(ref.getBase().getName(), index, value);
        } else if (leftOp instanceof JStaticFieldRef) {
            // Static field assignments are considered out of scope
        } else if (leftOp instanceof JInstanceFieldRef) {
            JInstanceFieldRef ref = (JInstanceFieldRef) leftOp;
            state.heap.setField(ref.getBase().getName(), ref.getFieldSignature().getName(), value,
                    ref.getFieldSignature().getType());
        } else {
            state.assign(leftOp.toString(), value);
        }

        // Definition statements follow the same control flow as other statements
        List<SymbolicState> succStates = handleOtherStmts(state, iterator);
        // For optional out of bounds state, check successors for potential catch blocks
        if (outOfBoundsState != null) {
            succStates.addAll(handleOtherStmts(outOfBoundsState, iterator));
        }
        return succStates;
    }

    /**
     * Symbolically or concretely execute an invoke statement.
     * 
     * @param expr     The invoke expression ({@link AbstractInvokeExpr})
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <code>null</code> if
     *                 not replaying a trace
     * @return A list of successor symbolic states after executing the invocation
     */
    private List<SymbolicState> handleInvokeStmt(AbstractInvokeExpr expr, SymbolicState state,
            Iterator<TraceEntry> iterator) {
        // Resolve aliases
        Expr<?> symRef = refExtractor.extract(expr, state);
        Optional<List<SymbolicState>> splitStates = splitOnAliases(state, symRef);
        if (splitStates.isPresent()) {
            return splitStates.get();
        }

        // Handle method invocation
        boolean executed = executeMethod(state, expr);
        // If executed concretely, immediately continue with the next statement
        if (executed) {
            return handleOtherStmts(state, iterator);
        }
        return List.of(state);
    }

    /**
     * Handle non-void return statements by storing the return value.
     * 
     * @param stmt     The return statement as a Jimple statement
     *                 ({@link JReturnStmt})
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <code>null</code> if
     *                 not replaying a trace
     * @return A list of successor symbolic states after executing the return
     */
    private List<SymbolicState> handleReturnStmt(JReturnStmt stmt, SymbolicState state, Iterator<TraceEntry> iterator) {
        Expr<?> value = jimpleToZ3.transform(stmt.getOp(), state);
        state.setReturnValue(value);
        return handleOtherStmts(state, iterator);
    }

    /**
     * Return a list of successor symbolic states for the current statement.
     * 
     * @param state    The current symbolic state
     * @param iterator The iterator over the trace entries or <code>null</code> if
     *                 not replaying a trace
     * @return A list of successor symbolic states
     */
    private List<SymbolicState> handleOtherStmts(SymbolicState state, Iterator<TraceEntry> iterator) {
        List<SymbolicState> newStates = new ArrayList<SymbolicState>();
        List<Stmt> succs = state.getSuccessors();

        // Final state
        if (succs.isEmpty()) {
            // If call stack is also empty, we have reached the end of the MUT
            if (state.isCallStackEmpty()) {
                state.setFinalState();
                return List.of(state);
            }
            // Otherwise, pop the call stack and continue from there
            SymbolicState poppedState = state.popCallStack();
            // If the popped state is a definition statement, we still need to complete the
            // assignment using the return value of the method that just finished execution
            if (poppedState.getStmt() instanceof AbstractDefinitionStmt) {
                poppedState.setReturnValue(state.getReturnValue());
                return handleDefStmt((AbstractDefinitionStmt) poppedState.getStmt(), poppedState, iterator);
            }
            return handleOtherStmts(poppedState, iterator);
        }

        // Note: generally non-branching statements will not have more than 1 successor,
        // but it can happen for exception-throwing statements inside a try block
        for (int i = 0; i < succs.size(); i++) {
            Stmt succ = succs.get(i);
            // If the state is exceptional, and this succ is catch block, reset the
            // exception flag
            if (state.isExceptionThrown() && isCatchBlock(succ)) {
                state.setExceptionThrown(false);
            }

            SymbolicState newState = i == succs.size() - 1 ? state : state.clone();
            newState.setStmt(succ);
            newStates.add(newState);
        }

        return newStates;
    }

    /** Check whether a statement represents that start of a catch block. */
    private boolean isCatchBlock(Stmt stmt) {
        return stmt instanceof JIdentityStmt && ((JIdentityStmt) stmt).getRightOp() instanceof JCaughtExceptionRef;
    }

    /**
     * Split the current symbolic state into multiple states based if the given
     * symbolic reference has multiple aliases.
     */
    private Optional<List<SymbolicState>> splitOnAliases(SymbolicState state, Expr<?> symRef) {
        if (symRef == null || state.heap.isResolved(symRef)) {
            return Optional.empty();
        }
        Set<Expr<?>> aliases = state.heap.getAliases(symRef);
        if (aliases != null) {
            List<SymbolicState> newStates = new ArrayList<SymbolicState>(aliases.size());
            int i = 0;
            for (Expr<?> alias : aliases) {
                SymbolicState newState = i == aliases.size() - 1 ? state : state.clone();
                newState.heap.setSingleAlias(symRef, alias);
                newState.addEngineConstraint(ctx.mkEq(symRef, alias));
                newStates.add(newState);
                i++;
            }
            if (newStates.size() > 1) {
                return Optional.of(newStates);
            }
        }
        return Optional.empty();
    }

    /**
     * Execute a method call, either symbolically or concretely.
     * 
     * @return <code>true</code> if the method was executed concretely,
     *         <code>false</code> if it was executed symbolically
     */
    private boolean executeMethod(SymbolicState state, AbstractInvokeExpr expr) {
        if (expr instanceof JDynamicInvokeExpr) {
            throw new UnsupportedOperationException("Dynamic invocation is not supported");
        }

        Local base = expr instanceof AbstractInstanceInvokeExpr ? ((AbstractInstanceInvokeExpr) expr).getBase() : null;
        Optional<JavaSootMethod> methodOpt = analyzer.tryGetSootMethod(expr.getMethodSignature());
        // If available internally, we can symbolically execute it
        if (methodOpt.isPresent()) {
            executeSymbolic(state, methodOpt.get(), expr, base);
            return false;
        }
        // Otherwise, execute it concretely
        else {
            executeConcrete(state, expr, base);
            return true;
        }
    }

    /** Execute a method call symbolically. */
    private void executeSymbolic(SymbolicState state, JavaSootMethod method, AbstractInvokeExpr expr, Local base) {
        // Turn the current state into the state for the method call
        // That is, we push a clone of the current state onto the call stack
        SymbolicState clone = state.clone();
        state.pushCallStack(clone);
        state.setCFG(analyzer.getCFG(method));
        state.setMethodType(MethodType.CALLEE);

        // TODO: make sure the objects are shared between the states

        // Set the instance reference to "this"
        if (base != null) {
            Expr<?> symRef = state.lookup(base.getName());
            state.assign("this", symRef);
        }

        // Set the arguments for the method call
        List<Immediate> args = expr.getArgs();
        for (int i = 0; i < args.size(); i++) {
            Immediate arg = args.get(i);
            Expr<?> argExpr = jimpleToZ3.transform(arg, state);
            state.assign(ArgMap.getSymbolicName(MethodType.CALLEE, i), argExpr);
        }

        // Actual execution will be done by {@link DSEController}!
    }

    /** Execute a method call concretely. */
    private void executeConcrete(SymbolicState state, AbstractInvokeExpr expr, Local base) {
        MethodSignature methodSig = expr.getMethodSignature();
        boolean isCtor = methodSig.getName().equals("<init>");
        Object executable = getExecutable(methodSig, isCtor);
        if (executable == null)
            return;

        ArgMap argMap = null;
        Object instance = null;
        Object original = null;

        // Only need to evalute the state if there are variables involved
        if (base != null || expr.getArgs().size() > 0) {
            Expr<?> symRef = base != null ? state.lookup(base.getName()) : null;
            HeapObject heapObj = state.heap.getHeapObject(symRef);
            if (base != null && heapObj == null) {
                state.setExceptionThrown();
                return;
            }

            Optional<ArgMap> argMapOpt = validator.evaluate(state, true);
            if (!argMapOpt.isPresent()) {
                state.setInfeasible();
                return;
            }
            argMap = argMapOpt.get();

            if (!isCtor && base != null) {
                try {
                    Class<?> clazz = analyzer.getJavaClass(heapObj.getType());
                    if (Modifier.isAbstract(((Method) executable).getModifiers())) {
                        executable = clazz.getDeclaredMethod(methodSig.getName(),
                                ((Method) executable).getParameterTypes());
                    }
                    instance = argMap.toJava(base.getName(), clazz);
                    if (instance == null) {
                        logger.warn("Failed to find instance for base: " + base.getName());
                        return;
                    }
                    original = ObjectUtils.shallowCopy(instance, instance.getClass());
                    addConcretizationConstraints(state, heapObj, instance);
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    logger.error("Failed to find class or method for base: " + base.getName());
                    return;
                }
            }
            setMethodArguments(state, expr.getArgs(), isCtor, argMap);
        }

        Object retval = isCtor ? ObjectInstantiator.createInstance((Constructor<?>) executable, argMap)
                : executor.execute(instance, (Method) executable, argMap);
        if (retval instanceof Exception) {
            state.setExceptionThrown();
            return;
        }

        Type retType = methodSig.getType();
        // Store the return value in the state
        state.setReturnValue(
                !retType.equals(VoidType.getInstance()) ? javaToZ3.transform(retval, state, retType) : null);
        if (base != null && instance != null) {
            updateModifiedFields(state, base, original, instance);
        }
    }

    private Object getExecutable(MethodSignature methodSig, boolean isCtor) {
        try {
            return isCtor ? analyzer.getJavaConstructor(methodSig) : analyzer.getJavaMethod(methodSig);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            logger.error("Failed to find " + (isCtor ? "constructor" : "method") + ": " + methodSig);
            return null;
        }
    }

    private void setMethodArguments(SymbolicState state, List<Immediate> args, boolean isCtor, ArgMap argMap) {
        for (int i = 0; i < args.size(); i++) {
            Immediate arg = args.get(i);
            Expr<?> argExpr = jimpleToZ3.transform(arg, state);
            String name = ArgMap.getSymbolicName(isCtor ? MethodType.CTOR : MethodType.METHOD, i);
            if (argExpr.getSort().equals(sorts.getRefSort())) {
                try {
                    HeapObject argObj = state.heap.getHeapObject(argExpr);
                    Class<?> argClazz = analyzer.getJavaClass(argObj.getType());
                    addConcretizationConstraints(state, argObj, argMap.toJava(argExpr.toString(), argClazz));
                } catch (ClassNotFoundException e) {
                    logger.warn("Failed to find class for reference: " + argExpr.toString());
                }
                argMap.set(name, new ObjectRef(argExpr.toString()));
            } else {
                Object argVal = validator.evaluate(argExpr, arg.getType());
                argMap.set(name, argVal);
            }
        }
    }

    private void updateModifiedFields(SymbolicState state, Local base, Object original, Object instance) {
        ObjectUtils.shallowCompare(original, instance, (path, oldValue, newValue) -> {
            String fieldName = path[0].getName();
            Type fieldType = sorts.determineType(path[0].getType());
            Expr<?> fieldExpr = javaToZ3.transform(newValue, state, fieldType);
            state.heap.setField(base.getName(), fieldName, fieldExpr, fieldType);
        });
    }

    /**
     * Go through the fields of the heap object, and add constraints for symbolic
     * field values to equal the concretized field values for the given object.
     */
    private void addConcretizationConstraints(SymbolicState state, HeapObject heapObj, Object object) {
        // For arrays, we need to concretize the array elements
        if (heapObj instanceof ArrayObject) {
            ArrayObject arrObj = (ArrayObject) heapObj;
            // Traverse the array, select corresponding element from arrObj's symbolic
            // array, and add constraint that they are equal
            if (heapObj instanceof MultiArrayObject) {
                // TODO: not supported
            } else {
                // Regular arrays
                for (int i = 0; i < Array.getLength(object); i++) {
                    Object arrElem = Array.get(object, i);
                    Expr<?> arrElemExpr = javaToZ3.transform(arrElem, state);
                    state.addEngineConstraint(ctx.mkEq(arrObj.getElem(i), arrElemExpr));
                }
            }

            return;
        }

        for (Entry<String, HeapObjectField> field : heapObj.getFields()) {
            String fieldName = field.getKey();
            HeapObjectField heapField = field.getValue();
            Expr<?> fieldValue = heapField.getValue();
            if (fieldValue.getSort().equals(sorts.getRefSort())) {
                HeapObject fieldObj = state.heap.getHeapObject(fieldValue);
                addConcretizationConstraints(state, fieldObj, ObjectUtils.getField(object, fieldName));
            } else {
                // Get the field value from the object
                Object objField = ObjectUtils.getField(object, fieldName);
                if (objField != null) {
                    // Convert the field value to a symbolic expression
                    Expr<?> fieldExpr = javaToZ3.transform(objField, state);
                    // Add a constraint that the field value must equal the symbolic value
                    state.addEngineConstraint(ctx.mkEq(fieldValue, fieldExpr));
                }
            }
        }
    }
}
