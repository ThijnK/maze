package nl.uu.maze.execution.symbolic;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Optional;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.*;

import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.ArgMap.*;
import nl.uu.maze.execution.symbolic.HeapObjects.*;
import nl.uu.maze.transform.Z3ToJavaTransformer;
import nl.uu.maze.util.Pair;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.maze.util.Z3Sorts;
import sootup.core.types.ClassType;
import sootup.core.types.Type;

/**
 * Validates symbolic states by checking the satisfiability of the path
 * condition and evaluating the resulting Z3 models.
 */
public class SymbolicStateValidator {
    private static final Logger logger = LoggerFactory.getLogger(SymbolicStateValidator.class);
    private static final Z3Sorts sorts = Z3Sorts.getInstance();
    private static final Context ctx = Z3ContextProvider.getContext();

    private final Solver solver;
    private final Z3ToJavaTransformer transformer;
    /** Last created Z3 model */
    private Model model;

    public SymbolicStateValidator() {
        this.solver = ctx.mkSolver();
        this.transformer = new Z3ToJavaTransformer();
    }

    /**
     * Validates the given list of path constraints. If the path condition is
     * satisfiable, the corresponding model is returned.
     * 
     * @param pathConstraints The path constraints to validate
     * @return An optional model if the path condition is satisfiable
     */
    public Optional<Model> validate(List<PathConstraint> pathConstraints) {
        solver.add(pathConstraints.stream().map(PathConstraint::getConstraint).toArray(BoolExpr[]::new));
        Status status = solver.check();
        logger.debug("Path condition {}: {}", status.toString(), pathConstraints);
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
        return validate(state.getConstraints());
    }

    /**
     * Checks the given symbolic state for satisfiability. If the path condition is
     * satisfiable, this returns {@code true} and otherwise {@code false}.
     */
    public boolean isSatisfiable(SymbolicState state) {
        return validate(state).isPresent();
    }

    /**
     * Evaluates the model and returns a map of known parameters.
     * 
     * @param model            The Z3 model to evaluate
     * @param state            The symbolic state to evaluate
     * @param fillObjectFields Whether to fill object fields with their current
     *                         values on the heap
     * @return A map of known parameters
     */
    public ArgMap evaluate(Model model, SymbolicState state, boolean fillObjectFields) {
        ArgMap argMap = new ArgMap();

        // nullValue is the interpreted value of the null constant
        // If any argument is interpreted to be equal to nullValue, it is considered
        // null
        // This would be the case if there was a null comparison in the path condition
        Expr<?> nullExpr = model.eval(Z3Sorts.getInstance().getNullConst(), true);

        // Keep track of reference values that we've encountered, to be able to set two
        // arguments to the same object when they are interpreted to be equal
        Map<Expr<?>, ObjectRef> refValues = new HashMap<>();

        // Keep track of array objects, to evaluate those last
        // This is necessary for arrays of objects to ensure that all objects are
        // already evaluated
        List<Pair<String, ArrayObject>> arrayObjects = new ArrayList<>();

        for (FuncDecl<?> decl : model.getConstDecls()) {
            String var = decl.getName().toString();
            if (var.equals("null")) {
                continue;
            }
            Expr<?> expr = model.getConstInterp(decl);

            // For reference types (arrays + objects)
            boolean isNull = false;
            if (sorts.isRef(expr)) {
                // If the variable is interpreted as null, set it to null
                if (expr.equals(nullExpr)) {
                    argMap.set(var, null);
                    isNull = true;
                }
                // If interpreted equal to another reference, set it to the same
                // value
                else if (refValues.containsKey(expr)) {
                    ObjectRef ref = refValues.get(expr);
                    // If this var is a concrete ref, then the ObjectRef here is a symbolic ref, so
                    // we need to switch them
                    if (state.heap.isConRef(var)) {
                        ObjectRef newRef = new ObjectRef(var);
                        argMap.set(ref.getVar(), newRef);
                        refValues.put(expr, newRef);
                    } else {
                        argMap.set(var, ref);
                    }
                } else {
                    // Store the reference value for equality checks to other argument references
                    refValues.put(expr, new ObjectRef(var));
                }
            }

            // Remove potential suffixes (e.g., _elems, _len)
            String varBase = var.contains("_") ? var.substring(0, var.indexOf('_')) : var;
            // If on the heap, then either array or object
            HeapObject heapObj = state.heap.get(varBase);
            if (heapObj != null) {
                // Arrays
                if (heapObj instanceof ArrayObject arrObj) {
                    arrayObjects.add(new Pair<>(varBase, arrObj));
                }
                // Objects
                else {
                    // Convert boxed primitives back to primitive types
                    if (heapObj instanceof BoxedPrimitiveObject boxedObj) {
                        Object value = transformer.transformExpr(model.eval(boxedObj.getValue(), true),
                                boxedObj.getValueType());
                        argMap.set(varBase, value);
                        continue;
                    }
                    if (!var.contains("_")) {
                        continue;
                    }
                    String fieldName = var.substring(var.indexOf('_') + 1);
                    Type fieldType = heapObj.getFieldType(fieldName);

                    ObjectInstance objInst = (ObjectInstance) argMap.getOrNew(varBase,
                            new ObjectInstance((ClassType) heapObj.getType()));
                    // If the field is a heap reference, handle it accordingly
                    Expr<?> conRef = state.heap.getSingleAlias(var);
                    if (conRef != null) {
                        // If heap reference is null, set field to null
                        if (isNull) {
                            objInst.setField(fieldName, null, fieldType);
                        }
                        // Otherwise, set field to reference the heap object
                        else {
                            ObjectRef ref = new ObjectRef(conRef.toString());
                            objInst.setField(fieldName, ref, fieldType);
                        }
                    }
                    // Otherwise, it's a primitive type
                    else {
                        Object value = transformer.transformExpr(expr, fieldType);
                        objInst.setField(fieldName, value, fieldType);
                    }
                }
                continue;
            }
            // Primitive types
            if (argMap.containsKey(var) || expr.isArray()) {
                // Skip if already set (e.g., to null)
                continue;
            }
            Object value = transformer.transformExpr(expr, state.getParamType(var));
            argMap.set(var, value);
        }

        // Handle arrays last, since they may contain references to other objects
        for (Pair<String, ArrayObject> pair : arrayObjects) {
            // There can be multiple declarations for the same array (elems and len)
            String arrRef = pair.getFirst();
            if (argMap.containsKey(arrRef)) {
                continue;
            }
            Type elemType = pair.getSecond().getType().getBaseType();
            Object arr = transformer.transformArray(pair.getSecond(), model, elemType, arrRef, refValues);
            argMap.set(pair.getFirst(), arr);
        }

        if (fillObjectFields) {
            fillObjectFields(state, argMap, model);
        }
        return argMap;
    }

    /**
     * Validates and evaluates the given symbolic state.
     * 
     * @param state            The symbolic state to validate and evaluate
     * @param fillObjectFields Whether to fill object fields with their current
     *                         values on the heap
     * @return A map of values extracted from the model if the path condition is
     *         satisfiable
     */
    public Optional<ArgMap> evaluate(SymbolicState state, boolean fillObjectFields) {
        Optional<Model> model = validate(state);
        model.ifPresent(m -> this.model = m);
        return model.map(m -> evaluate(m, state, fillObjectFields));
    }

    /**
     * Validates and evaluates the given symbolic state.
     * 
     * @param state The symbolic state to validate and evaluate
     * @return A map of values extracted from the model if the path condition is
     *         satisfiable
     */
    public Optional<ArgMap> evaluate(SymbolicState state) {
        return evaluate(state, false);
    }

    /**
     * Evaluates the given expression with the given type on the most recently
     * created model.
     * 
     * @param expr The expression to evaluate
     * @param type The type of the expression
     * @return The evaluated objects
     */
    public Object evaluate(Expr<?> expr, Type type) {
        if (model != null) {
            return transformer.transformExpr(model.eval(expr, true), type);
        }
        return transformer.transformExpr(expr, type);
    }

    /**
     * Fills fields of objects in the current state's heap into the argMap, if not
     * already set.
     * This is necessary if an object field may not appear as a declaration in the
     * model, but still needs to be evaluated to get a concrete value.
     */
    private void fillObjectFields(SymbolicState state, ArgMap argMap, Model model) {
        // Go through the heap and set fields of objects that are not in the model
        for (Entry<Expr<?>, HeapObject> entry : state.heap.entrySet()) {
            String var = entry.getKey().toString();
            HeapObject heapObj = entry.getValue();
            if (heapObj instanceof ArrayObject || heapObj instanceof BoxedPrimitiveObject) {
                // Arrays will already be defined in the argument map
                continue;
            }
            ObjectInstance objFields = (ObjectInstance) argMap.getOrNew(var,
                    new ObjectInstance((ClassType) heapObj.getType()));
            for (Entry<String, HeapObjectField> fieldEntry : heapObj.getFields()) {
                String fieldName = fieldEntry.getKey();
                if (objFields.hasField(fieldName)) {
                    continue;
                }
                HeapObjectField field = fieldEntry.getValue();

                Type fieldType = field.getType();
                Expr<?> fieldValue = model.eval(field.getValue(), true);
                // References to other objects stored as ObjectRef
                if (sorts.isRef(field.getValue())) {
                    objFields.setField(fieldName, new ObjectRef(fieldValue.toString()), fieldType);
                } else {
                    Object value = transformer.transformExpr(fieldValue, fieldType);
                    objFields.setField(fieldName, value, fieldType);
                }
            }
        }

        // Also go through the variables store of the state and set object references
        for (Entry<String, Expr<?>> entry : state.store.entrySet()) {
            String var = entry.getKey();
            Expr<?> value = entry.getValue();
            if (value != null && sorts.isRef(value)) {
                ObjectRef ref = new ObjectRef(value.toString());
                argMap.set(var, ref);
            }
        }
    }
}
