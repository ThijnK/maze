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

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.ArgMap;
import nl.uu.maze.execution.symbolic.SymbolicHeap.*;
import nl.uu.maze.transform.Z3ToJavaTransformer;
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

    private Solver solver;
    private Z3ToJavaTransformer transformer;
    /** Last created Z3 model */
    private Model model;

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
        return validate(state.getAllConstraints());
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

        for (FuncDecl<?> decl : model.getConstDecls()) {
            String var = decl.getName().toString();
            if (var.equals("null")) {
                continue;
            }
            Expr<?> expr = model.getConstInterp(decl);

            // For reference types (arrays + objects)
            boolean isNull = false;
            if (expr.getSort().equals(sorts.getRefSort())) {
                // If the variable is interpreted as null, set it to null
                if (expr.equals(nullExpr)) {
                    argMap.set(var, null);
                    isNull = true;
                }
                // If interpreted equal to another argument's reference, set it to the same
                // value
                if (refValues.containsKey(expr)) {
                    ObjectRef ref = refValues.get(expr);
                    argMap.set(var, ref);
                }
                // Store the reference value for equality checks to other argument references
                refValues.put(expr, new ObjectRef(var));
            }

            // Remove potential suffixes (e.g., _elems, _len)
            String varBase = var.contains("_") ? var.substring(0, var.indexOf('_')) : var;
            // If on the heap, then either array or object
            HeapObject heapObj = state.heap.get(varBase);
            if (heapObj != null) {
                // Arrays
                if (heapObj instanceof ArrayObject) {
                    // There can be multiple declarations for the same array (elems and len)
                    if (argMap.containsKey(varBase)) {
                        continue;
                    }
                    ArrayObject arrObj = (ArrayObject) heapObj;
                    Type elemType = arrObj.getType().getBaseType();
                    Object arr = transformer.transformArray(arrObj, model, elemType);
                    argMap.set(varBase, arr);
                }
                // Objects
                else {
                    if (!var.contains("_")) {
                        continue;
                    }
                    String fieldName = var.substring(var.indexOf('_') + 1);
                    Type fieldType = heapObj.getFieldType(fieldName);

                    ObjectInstance objFields = (ObjectInstance) argMap.getOrNew(varBase,
                            new ObjectInstance((ClassType) heapObj.getType()));
                    // If the field is a heap reference, handle it accordingly
                    Expr<?> conRef = state.heap.getSingleAlias(var);
                    if (conRef != null) {
                        // If heap reference is null, set field to null
                        if (isNull) {
                            objFields.setField(fieldName, null, fieldType);
                        }
                        // Otherwise, set field to reference the heap object
                        else {
                            ObjectRef ref = new ObjectRef(conRef.toString());
                            objFields.setField(fieldName, ref, fieldType);
                        }
                    }
                    // Otherwise, it's a primitive type
                    else {
                        Object value = transformer.transformExpr(expr, fieldType);
                        objFields.setField(fieldName, value, fieldType);
                    }
                }
                continue;
            }
            // Primitive types
            if (argMap.containsKey(var)) {
                // Skip if already set (e.g., to null)
                continue;
            }
            Object value = transformer.transformExpr(model.getConstInterp(decl), state.getParamType(var));
            argMap.set(var, value);
        }

        if (fillObjectFields) {
            fillObjectFields(state, argMap);
        }
        return argMap;
    }

    /**
     * Validates and evaluates the given symbolic state.
     * 
     * @param state            The symbolic state to validate and evaluate
     * @param fillObjectFields Whether to fill object fields with their current
     *                         values on the heap
     * @@return A map of values extracted from the model if the path condition is
     *          satisfiable
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
     * Validates and evaluates the given list of symbolic states.
     * 
     * @param states The symbolic states to validate and evaluate
     * @return A list of maps of values extracted from the model if the path
     *         condition is
     *         satisfiable
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
     * Evaluates the given expression with the given type.
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
     * Fills the fields of objects in the given symbolic state with their current
     * values on the heap, if not already set in the argument map.
     */
    private void fillObjectFields(SymbolicState state, ArgMap argMap) {
        // Go through the heap and set fields of objects that are not in the model
        for (Entry<Expr<?>, HeapObject> entry : state.heap.entrySet()) {
            String var = entry.getKey().toString();
            HeapObject heapObj = entry.getValue();
            if (heapObj instanceof ArrayObject) {
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
                Expr<?> fieldValue = field.getValue();
                // References to other objects stored as ObjectRef
                if (field.getValue().getSort().equals(sorts.getRefSort())) {
                    objFields.setField(fieldName, new ObjectRef(fieldValue.toString()), fieldType);
                } else {
                    Object value = transformer.transformExpr(fieldValue, fieldType);
                    objFields.setField(fieldName, value, fieldType);
                }
            }
        }
    }

    /**
     * Represents a reference to another variable.
     */
    public static class ObjectRef {
        private final String var;

        public ObjectRef(String var) {
            this.var = var;
        }

        public String getVar() {
            return var;
        }

        public int getIndex() {
            // Everything after "arg" is the index, but there can be other prefixes before
            return Integer.parseInt(var.substring(var.indexOf("arg") + 3));
        }

        @Override
        public String toString() {
            return var;
        }
    }

    /**
     * Represents a field of an object instance.
     */
    public static class ObjectField {
        private final Object value;
        private final Type type;

        public ObjectField(Object value, Type type) {
            this.value = value;
            this.type = type;
        }

        public Object getValue() {
            return value;
        }

        public Type getType() {
            return type;
        }
    }

    /**
     * Represents a object and its fields.
     */
    public static class ObjectInstance {
        private final ClassType type;
        private Class<?> typeClass;
        private final Map<String, ObjectField> fields;

        public ObjectInstance(ClassType type) {
            this.type = type;
            this.typeClass = null;
            this.fields = new HashMap<>();
        }

        public ObjectInstance(Class<?> type) {
            this.typeClass = type;
            this.type = null;
            this.fields = new HashMap<>();
        }

        public ClassType getType() {
            return type;
        }

        public Class<?> getTypeClass() {
            return typeClass;
        }

        public Class<?> getTypeClass(JavaAnalyzer analyzer) {
            if (typeClass == null) {
                typeClass = analyzer.tryGetJavaClass(type).orElse(null);
            }
            return typeClass;
        }

        public void setTypeClass(Class<?> typeClass) {
            this.typeClass = typeClass;
        }

        public Map<String, ObjectField> getFields() {
            return fields;
        }

        public void setField(String name, Object value, Type type) {
            fields.put(name, new ObjectField(value, type));
        }

        public boolean hasField(String name) {
            return fields.containsKey(name);
        }
    }
}
