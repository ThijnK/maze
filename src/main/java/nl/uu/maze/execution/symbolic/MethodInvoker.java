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
import nl.uu.maze.execution.concrete.*;
import nl.uu.maze.execution.symbolic.HeapObjects.*;
import nl.uu.maze.transform.JavaToZ3Transformer;
import nl.uu.maze.transform.JimpleToJavaTransformer;
import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.ObjectUtils;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.constant.Constant;
import sootup.core.jimple.common.expr.*;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.ClassType;
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
    private final JimpleToJavaTransformer jimpleToJava = new JimpleToJavaTransformer();

    public MethodInvoker(ConcreteExecutor executor, SymbolicStateValidator validator, JavaAnalyzer analyzer) {
        this.executor = executor;
        this.validator = validator;
        this.analyzer = analyzer;
    }

    /**
     * Execute a method call, symbolically if available, and otherwise concretely.
     * 
     * @param state       The current symbolic state
     * @param expr        The method call expression to execute
     * @param storeResult Whether to store the result of the method call in the
     *                    return value of the state (i.e., for definition
     *                    statements)
     * @param replay      Whether we are replaying a symbolic trace
     * 
     * @return A new symbolic state if the method was executed symbolically, or
     *         an empty optional if the method was executed concretely (meaning
     *         execution should continue with whatever state this method was called
     *         with)
     */
    public Optional<SymbolicState> executeMethod(SymbolicState state, AbstractInvokeExpr expr, boolean storeResult,
            boolean replay) {
        if (expr instanceof JDynamicInvokeExpr) {
            throw new UnsupportedOperationException(expr.getClass().getSimpleName() + " is not supported");
        }
        Local base = expr instanceof AbstractInstanceInvokeExpr ? ((AbstractInstanceInvokeExpr) expr).getBase() : null;
        MethodSignature methodSig = expr.getMethodSignature();

        // If replaying a trace, do not symbolically execute java standard library
        // methods, because we do not have trace entries for those (not instrumented)
        if (replay && isStandardLibraryMethod(methodSig)) {
            executeConcrete(state, expr, base, storeResult);
            return Optional.empty();
        }

        // For interface invoke expressions, try to resolve the method call to a
        // concrete class
        if (expr instanceof JInterfaceInvokeExpr && base != null && base.getType() instanceof ClassType baseType) {
            // Substitute interface class type with class type of the base
            methodSig = new MethodSignature(baseType, methodSig.getSubSignature());
        }

        Optional<JavaSootMethod> methodOpt = analyzer.tryGetSootMethod(methodSig);
        // If available internally, we can symbolically execute it
        if (methodOpt.isPresent() && methodOpt.get().hasBody()) {
            return executeSymbolic(state, methodOpt.get(), expr, base);
        }
        // Otherwise, execute it concretely
        else {
            executeConcrete(state, expr, base, storeResult);
            return Optional.empty();
        }
    }

    /** Check if a method is part of the Java standard library. */
    private boolean isStandardLibraryMethod(MethodSignature methodSig) {
        return methodSig.getDeclClassType().getFullyQualifiedName().startsWith("java.") ||
                methodSig.getDeclClassType().getFullyQualifiedName().startsWith("javax.");
    }

    /** Execute a method call symbolically. */
    private Optional<SymbolicState> executeSymbolic(SymbolicState state, JavaSootMethod method, AbstractInvokeExpr expr,
            Local base) {
        // Create a fresh state that will enter the method call
        SymbolicState callee = new SymbolicState(method, analyzer.getCFG(method));
        callee.setCaller(state);
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
                // If the argument is a multidimensional array, copy the array indices
                // to the callee state
                callee.heap.setArrayIndices(argName, state.heap.getArrayIndices(arg.toString()));
            }

            // If the argument is a reference, link the heap object from caller state to
            // the callee state
            if (argExpr != null && sorts.isRef(argExpr)) {
                callee.heap.linkHeapObject(argExpr, state.heap);
            }
        }

        // Actual execution will be done by {@link DSEController}!
        return Optional.of(callee);
    }

    /** Execute a method call concretely. */
    private void executeConcrete(SymbolicState state, AbstractInvokeExpr expr, Local base, boolean storeResult) {
        MethodSignature methodSig = expr.getMethodSignature();
        boolean isCtor = methodSig.getName().equals("<init>");
        Object executable = getExecutable(methodSig, isCtor);
        if (executable == null)
            return;

        ArgMap argMap = null;
        Object instance = null;
        Object original = null;

        // Only need to evaluate the state if there are variables involved
        if (base != null || !expr.getArgs().isEmpty()) {
            Expr<?> symRef = base != null ? state.lookup(base.getName()) : null;
            HeapObject heapObj = state.heap.getHeapObject(symRef);
            if (base != null && heapObj == null) {
                state.setExceptionThrown();
                return;
            }

            Optional<ArgMap> argMapOpt = validator.evaluate(state, true);
            if (argMapOpt.isEmpty()) {
                state.setInfeasible();
                return;
            }
            argMap = argMapOpt.get();

            if (!isCtor && base != null) {
                try {
                    Class<?> clazz = analyzer.getJavaClass(heapObj.getType());
                    instance = argMap.toJava(base.getName(), clazz);
                    if (instance == null) {
                        throw new UnsupportedOperationException(
                                "Failed to create instance for base: " + base.getName());
                    }
                    if (Modifier.isAbstract(((Method) executable).getModifiers())) {
                        executable = clazz.getDeclaredMethod(methodSig.getName(),
                                ((Method) executable).getParameterTypes());
                    }
                    original = ObjectUtils.shallowCopy(instance, instance.getClass());
                    addConcretizationConstraints(state, heapObj, instance, symRef);
                } catch (ClassNotFoundException | NoSuchMethodException e) {
                    throw new UnsupportedOperationException(
                            "Failed to find class or method for base: " + base.getName());
                }
            }
            setMethodArguments(state, expr.getArgs(), isCtor, argMap);
        }

        ExecutionResult result = isCtor ? ObjectInstantiation.createInstance((Constructor<?>) executable, argMap)
                : executor.execute(instance, (Method) executable, argMap);
        if (result.isException()) {
            state.setExceptionThrown();
            return;
        }
        Object retval = result.retval();

        Type retType = methodSig.getType();
        // Store the return value in the state
        boolean setNullResult = !storeResult || retType.equals(VoidType.getInstance());
        state.setReturnValue(setNullResult ? null : javaToZ3.transform(retval, state, retType));
        if (base != null && instance != null) {
            updateModifiedFields(state, base, original, instance);
        }
    }

    private Object getExecutable(MethodSignature methodSig, boolean isCtor) {
        try {
            return isCtor ? analyzer.getJavaConstructor(methodSig) : analyzer.getJavaMethod(methodSig);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            logger.error("Failed to find {}: {}", isCtor ? "constructor" : "method", methodSig);
            return null;
        }
    }

    /**
     * Set concrete method arguments in the given ArgMap and add concretization
     * constraints if applicable.
     */
    private void setMethodArguments(SymbolicState state, List<Immediate> args, boolean isCtor, ArgMap argMap) {
        for (int i = 0; i < args.size(); i++) {
            Immediate arg = args.get(i);
            String name = ArgMap.getSymbolicName(isCtor ? MethodType.CTOR : MethodType.METHOD, i);

            // Constants can be immediately defined
            if (arg instanceof Constant constant) {
                constant.accept(jimpleToJava);
                Optional<Object> valueOpt = jimpleToJava.getResult();
                if (valueOpt.isPresent()) {
                    argMap.set(name, valueOpt.get());
                } else {
                    logger.warn("Failed to convert constant: {}", constant);
                }
                continue;
            }

            Expr<?> argExpr = jimpleToZ3.transform(arg, state);
            if (sorts.isRef(argExpr)) {
                // Try to concretize the object being referenced
                // I.e., if it has symbolic fields, those are converted to concrete values and
                // constraints are added to constrain the symbolic value to the concrete values
                // created (to preserve correctness of the path we are exploring)
                try {
                    Expr<?> alias = state.heap.getSingleAlias(argExpr);
                    if (sorts.isNull(alias)) {
                        // If the alias is null, we need to set the argument to null
                        argMap.set(name, null);
                        continue;
                    }
                    // If the argument is a reference, we need to concretize it
                    HeapObject argObj = state.heap.getHeapObject(argExpr);
                    Class<?> argClazz = analyzer.getJavaClass(argObj.getType());
                    // Concretize the object
                    // Note: the value created from this is stored in the argMap and will be reused
                    // when invoking the method
                    Object argVal = argMap.toJava(argExpr.toString(), argClazz);
                    addConcretizationConstraints(state, argObj, argVal, argExpr);
                } catch (ClassNotFoundException e) {
                    logger.warn("Failed to find class for reference: {}", argExpr);
                }
                // Set the argument to reference the symbolic reference
                // This will be resolved to the actual value later on
                // when the method is executed
                argMap.set(name, new ObjectRef(argExpr.toString()));
            } else {
                Object argVal = validator.evaluate(argExpr, arg.getType());
                argMap.set(name, argVal);
                addConcretizationConstraints(state, argExpr, argVal);
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
     * Add constraints to the symbolic state to ensure that the given argument
     * variable equals the given value
     */
    private void addConcretizationConstraints(SymbolicState state, Expr<?> arg, Object value) {
        Expr<?> valueExpr = javaToZ3.transform(value, state);
        if (valueExpr != null) {
            state.addEngineConstraint(ctx.mkEq(arg, valueExpr));
        }
    }

    /**
     * Go through the fields of the heap object, and add constraints for symbolic
     * field values to equal the concretized field values for the given object.
     */
    private void addConcretizationConstraints(SymbolicState state, HeapObject heapObj, Object object, Expr<?> symRef) {
        if (object == null) {
            // If the object is null, we need to set the symbolic reference to null
            state.addEngineConstraint(ctx.mkEq(symRef, sorts.getNullConst()));
            return;
        }

        // For arrays, we need to concretize the array elements
        if (heapObj instanceof ArrayObject arrObj) {
            // Traverse the array, select corresponding element from arrObj's symbolic
            // array, and add constraint that they are equal
            if (heapObj instanceof MultiArrayObject multiArrObj) {
                // Multi-dimensional arrays
                addConcretizationConstraints(state, multiArrObj, object, 0, new int[multiArrObj.getDim()]);
            } else {
                // Regular arrays
                for (int i = 0; i < Array.getLength(object); i++) {
                    Object arrElem = Array.get(object, i);
                    if (!arrElem.getClass().isPrimitive()) {
                        // Note: concreitization of object arrays not supported
                        continue;
                    }

                    Expr<?> arrElemExpr = javaToZ3.transform(arrElem, state);
                    Expr<?> arrSelectExpr = arrObj.getElem(i);
                    if (arrElemExpr.getSort().equals(arrSelectExpr.getSort())) {
                        state.addEngineConstraint(ctx.mkEq(arrSelectExpr, arrElemExpr));
                    }
                }
            }

            return;
        }

        for (Entry<String, HeapObjectField> field : heapObj.getFields()) {
            String fieldName = field.getKey();
            HeapObjectField heapField = field.getValue();
            Expr<?> fieldValue = heapField.getValue();
            if (symRef.equals(fieldValue)) {
                continue;
            }

            Optional<Object> fieldOpt = ObjectUtils.getField(object, fieldName);
            if (fieldOpt.isPresent()) {
                if (sorts.isRef(fieldValue)) {
                    HeapObject fieldObj = state.heap.getHeapObject(fieldValue);
                    addConcretizationConstraints(state, fieldObj, fieldOpt.get(), fieldValue);
                } else {
                    // Convert the field value to a symbolic expression
                    Expr<?> fieldExpr = javaToZ3.transform(fieldOpt.get(), state);
                    // Add a constraint that the field value must equal the symbolic value
                    state.addEngineConstraint(ctx.mkEq(fieldValue, fieldExpr));
                }
            }
        }
    }

    /**
     * Recursively add constraints for the elements of a multi-dimensional array to
     * equal the concretized elements of the given array.
     */
    private void addConcretizationConstraints(SymbolicState state, MultiArrayObject multiArrObj, Object object,
            int currrentDim, int[] indices) {
        if (currrentDim == multiArrObj.getDim()) {
            // We have reached the end of the array, add the constraint
            Expr<?> arrElemExpr = javaToZ3.transform(object, state);
            Expr<?> arrSelectExpr = multiArrObj.getElem(indices);
            if (arrElemExpr.getSort().equals(arrSelectExpr.getSort())) {
                state.addEngineConstraint(ctx.mkEq(arrSelectExpr, arrElemExpr));
            }
        } else {
            // Recursively traverse the array
            for (int i = 0; i < Array.getLength(object); i++) {
                indices[currrentDim] = i;
                addConcretizationConstraints(state, multiArrObj, Array.get(object, i), currrentDim + 1, indices);
            }
        }
    }
}
