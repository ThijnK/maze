package nl.uu.maze.execution.symbolic;

import java.lang.reflect.*;
import java.util.List;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Context;
import com.microsoft.z3.Expr;

import java.util.Optional;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.ArgMap.ObjectRef;
import nl.uu.maze.execution.MethodType;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.concrete.ObjectInstantiator;
import nl.uu.maze.execution.symbolic.HeapObjects.*;
import nl.uu.maze.transform.JavaToZ3Transformer;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.ObjectUtils;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.expr.AbstractInstanceInvokeExpr;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.jimple.common.expr.JDynamicInvokeExpr;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.Type;
import sootup.core.types.VoidType;
import sootup.java.core.JavaSootMethod;

/**
 * Responsible for executing method calls, symbolically if the class is
 * available internally, and otherwise concretely.
 */
public class MethodInvoker {
    private static final Logger logger = LoggerFactory.getLogger(MethodInvoker.class);
    private static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx = Z3ContextProvider.getContext();

    private final ConcreteExecutor executor;
    private final SymbolicStateValidator validator;
    private final JavaAnalyzer analyzer;
    private final JimpleToZ3Transformer jimpleToZ3 = new JimpleToZ3Transformer();
    private final JavaToZ3Transformer javaToZ3 = new JavaToZ3Transformer();

    public MethodInvoker(ConcreteExecutor executor, SymbolicStateValidator validator, JavaAnalyzer analyzer) {
        this.executor = executor;
        this.validator = validator;
        this.analyzer = analyzer;
    }

    /**
     * Execute a method call, symbolically if available, and otherwise concretely.
     * 
     * @return A new symbolic state if the method was executed symbolically, or
     *         an empty optional if the method was executed concretely (meaning
     *         execution should continue with whatever state this method was called
     *         with)
     */
    public Optional<SymbolicState> executeMethod(SymbolicState state, AbstractInvokeExpr expr) {
        if (expr instanceof JDynamicInvokeExpr) {
            throw new UnsupportedOperationException("Dynamic invocation is not supported");
        }

        Local base = expr instanceof AbstractInstanceInvokeExpr ? ((AbstractInstanceInvokeExpr) expr).getBase() : null;
        Optional<JavaSootMethod> methodOpt = analyzer.tryGetSootMethod(expr.getMethodSignature());
        // If available internally, we can symbolically execute it
        if (methodOpt.isPresent()) {
            return executeSymbolic(state, methodOpt.get(), expr, base);
        }
        // Otherwise, execute it concretely
        else {
            executeConcrete(state, expr, base);
            return Optional.empty();
        }
    }

    /** Execute a method call symbolically. */
    private Optional<SymbolicState> executeSymbolic(SymbolicState state, JavaSootMethod method, AbstractInvokeExpr expr,
            Local base) {
        // Create a fresh state that will enter the method call
        SymbolicState callee = new SymbolicState(method.getSignature(), analyzer.getCFG(method));
        callee.setCaller(state);
        callee.setMethodType(MethodType.CALLEE);
        // Also set the constraints to be the same as the caller state
        // This will copy references, so original constraints will be modified if the
        // callee state adds new constraints (intentionally)
        callee.setConstraints(state.getPathConstraints(), state.getEngineConstraints());
        // Copy the heap counter to avoid interference of constraints added by callee
        // with constraints added by caller after the method call
        callee.heap.setCounters(state.heap.getHeapCounter(), state.heap.getRefCounter());
        callee.heap.setResolvedRefs(state.heap.getResolvedRefs());

        // Copy object reference for "this" (if needed)
        if (base != null) {
            Expr<?> symRef = state.lookup(base.getName());
            callee.assign("this", symRef);
            // Link the heap object from caller state to the callee state
            callee.heap.linkHeapObject(symRef, state.heap);
        }

        // Copy arguments for the method call to the fresh state
        List<Immediate> args = expr.getArgs();
        for (int i = 0; i < args.size(); i++) {
            Immediate arg = args.get(i);
            Expr<?> argExpr = jimpleToZ3.transform(arg, state);
            String argName = ArgMap.getSymbolicName(MethodType.CALLEE, i);
            callee.assign(argName, argExpr);
            if (state.heap.isMultiArray(arg.toString())) {
                // If the argument is a multi-dimensional array, copy the array indices
                // to the callee state
                callee.heap.setArrayIndices(argName, state.heap.getArrayIndices(arg.toString()));
            }

            // If the argument is a reference, link the heap object from caller state to
            // the callee state
            if (sorts.isRef(argExpr)) {
                callee.heap.linkHeapObject(argExpr, state.heap);
            }
        }

        // Actual execution will be done by {@link DSEController}!
        return Optional.of(callee);
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
            if (sorts.isRef(argExpr)) {
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
            if (sorts.isRef(fieldValue)) {
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
