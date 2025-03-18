package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import nl.uu.maze.instrument.TraceManager.BranchType;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Instruments Java bytecode files to record symbolic traces during (concrete)
 * execution.
 * Uses the ASM library.
 */
public class BytecodeInstrumentation {
    private static final String TRACE_MANAGER_PATH = TraceManager.class.getName().replace('.', '/');
    private static final String BRANCH_TYPE_PATH = BranchType.class.getName().replace('.', '/');

    /**
     * Instrument a class file to record symbolic traces.
     * 
     * @param classPath The path to the class file
     * @param className The name of the class
     * @return The instrumented class
     */
    public static Class<?> instrument(String classPath, String className) throws IOException {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String qualifiedName = className.replace(".", "/");
        String classPrefix = qualifiedName.substring(0, qualifiedName.lastIndexOf('/'));
        BytecodeClassLoader classLoader = new BytecodeClassLoader();
        Map<String, byte[]> instrumentedClasses = new HashMap<>();

        // Collect main and nested class files
        File classPathDir = new File(classPath, classPrefix);
        List<File> classFiles = new ArrayList<>();
        for (File file : classPathDir.listFiles()) {
            if (file.getName().startsWith(simpleName) && file.getName().endsWith(".class")) {
                classFiles.add(file);
            }
        }

        // Instrument all collected class files
        for (File file : classFiles) {
            String name = file.getName().substring(0, file.getName().length() - 6);
            byte[] classBytes = Files.readAllBytes(file.toPath());

            ClassReader classReader = new ClassReader(classBytes);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            String qualifiedClassName = classPrefix + '/' + name;
            ClassVisitor classVisitor = new SymbolicTraceClassVisitor(classWriter, qualifiedClassName);
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

            byte[] instrumentedBytes = classWriter.toByteArray();
            instrumentedClasses.put(name, instrumentedBytes);
        }

        // Load all instrumented classes
        Class<?> mainClass = null;
        for (Map.Entry<String, byte[]> entry : instrumentedClasses.entrySet()) {
            String name = (classPrefix + '.' + entry.getKey()).replace('/', '.');
            Class<?> cls = classLoader.defineClass(name, entry.getValue());
            if (entry.getKey().equals(simpleName)) {
                mainClass = cls;
            }
        }

        return mainClass;
    }

    /** Write bytecode of a class to a file in human-readable format (opcodes). */
    @SuppressWarnings("unused")
    private static void writeOpcodesToFile(byte[] classBytes) throws IOException {
        ClassReader classReader = new ClassReader(classBytes);
        try (PrintWriter writer = new PrintWriter(new FileWriter("logs/opcodes.txt"))) {
            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(writer);
            classReader.accept(traceClassVisitor, ClassReader.EXPAND_FRAMES);
        }
    }

    /**
     * Custom class loader that allows us to define classes from byte arrays.
     */
    public static class BytecodeClassLoader extends ClassLoader {
        private final Map<String, Class<?>> classes = new HashMap<>();

        /**
         * Register a class that is already loaded in the JVM to this class loader
         * 
         * @param clazz The class to register
         */
        public void registerClass(Class<?> clazz) {
            classes.put(clazz.getName(), clazz);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> clazz = classes.get(name);
            return clazz != null ? clazz : super.findClass(name);
        }

        /**
         * Define a class from a byte array.
         * 
         * @param name       The name of the class
         * @param classBytes The byte array containing the class data
         * @return The defined class
         */
        public Class<?> defineClass(String name, byte[] classBytes) {
            return defineClass(name, classBytes, 0, classBytes.length);
        }
    }

    /** Class visitor that instruments the class to record symbolic traces. */
    static class SymbolicTraceClassVisitor extends ClassVisitor {
        private final String className;

        public SymbolicTraceClassVisitor(ClassVisitor classVisitor, String className) {
            super(Opcodes.ASM9, classVisitor);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            String methodSignature = TraceManager.buildMethodSignature(className, name, descriptor);
            return new SymbolicTraceMethodVisitor(Opcodes.ASM9, methodVisitor, access, name, descriptor,
                    methodSignature);
        }
    }

    /** Method visitor that instruments the method to record symbolic traces */
    static class SymbolicTraceMethodVisitor extends AdviceAdapter {
        final String methodSignature;

        protected SymbolicTraceMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name,
                String descriptor, String methodSignature) {
            super(api, methodVisitor, access, name, descriptor);
            this.methodSignature = methodSignature;
        }

        private boolean isStatic() {
            return (methodAccess & Opcodes.ACC_STATIC) != 0;
        }

        // Instrument method enter to record argument aliasing
        @Override
        protected void onMethodEnter() {
            Type[] argTypes = Type.getArgumentTypes(methodDesc);
            int refParamCount = 0;
            for (Type type : argTypes) {
                if (type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY) {
                    refParamCount++;
                }
            }

            if (refParamCount > 0) {
                // Create an array for the parameters
                mv.visitLdcInsn(argTypes.length);
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

                // Store the arguments in the array
                int localVarIndex = isStatic() ? 0 : 1; // Start at 0 for static methods, 1 for instance methods
                for (int i = 0; i < argTypes.length; i++) {
                    if (isReferenceType(argTypes[i])) {
                        mv.visitInsn(Opcodes.DUP);
                        mv.visitLdcInsn(i); // Index in the array
                        mv.visitVarInsn(Opcodes.ALOAD, localVarIndex);
                        mv.visitInsn(Opcodes.AASTORE);
                    }
                    localVarIndex += argTypes[i].getSize();
                }

                // Now check for parameter aliasing and null references
                instrumentParameterAliasing(argTypes);
            }
        }

        private boolean isReferenceType(Type type) {
            // Treat String as a primitive type
            if (type.getClassName().equals("java.lang.String")) {
                return false;
            }

            return type.getSort() == Type.OBJECT || type.getSort() == Type.ARRAY;
        }

        /**
         * Instrument the method to check for parameter aliasing and null references.
         * 
         * @param argTypes The types of the method arguments
         */
        private void instrumentParameterAliasing(Type[] argTypes) {
            int argsVar = newLocal(Type.getType(Object[].class));
            mv.visitVarInsn(Opcodes.ASTORE, argsVar);

            // Check for aliasing and null references
            for (int i = 0; i < argTypes.length; i++) {
                if (!isReferenceType(argTypes[i])) {
                    continue;
                }

                // Check for null references
                mv.visitVarInsn(Opcodes.ALOAD, argsVar);
                mv.visitLdcInsn(i); // Index in the array
                mv.visitInsn(Opcodes.AALOAD);
                Label notNull = new Label();
                Label continueLabel = new Label();
                mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
                instrumentTraceLog(BranchType.ALIAS, -1); // -1 for null reference
                mv.visitJumpInsn(Opcodes.GOTO, continueLabel);

                mv.visitLabel(notNull);
                // Check for aliasing of previous parameters
                for (int j = 0; j < i; j++) {
                    // Only compare to other reference parameters
                    if (!isReferenceType(argTypes[j])) {
                        continue;
                    }

                    // Load both parameters
                    mv.visitVarInsn(Opcodes.ALOAD, argsVar);
                    mv.visitLdcInsn(i);
                    mv.visitInsn(Opcodes.AALOAD);
                    mv.visitVarInsn(Opcodes.ALOAD, argsVar);
                    mv.visitLdcInsn(j);
                    mv.visitInsn(Opcodes.AALOAD);

                    // Compare the two parameters
                    Label notEqual = new Label();
                    mv.visitJumpInsn(Opcodes.IF_ACMPNE, notEqual);
                    instrumentTraceLog(BranchType.ALIAS, j); // Param i is an alias of param j
                    mv.visitJumpInsn(Opcodes.GOTO, continueLabel);
                    mv.visitLabel(notEqual);
                }

                // No aliasing found
                instrumentTraceLog(BranchType.ALIAS, i); // Param i is not an alias of any previous param
                mv.visitLabel(continueLabel);
            }
        }

        // Instrument if statements to record the branch taken
        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (isConditionalJump(opcode)) {
                // Duplicate the value(s) on the stack to keep the original value(s) for the
                // actual jump
                if (requiresTwoOperands(opcode)) {
                    mv.visitInsn(Opcodes.DUP2);
                } else {
                    mv.visitInsn(Opcodes.DUP);
                }

                Label trueLabel = new Label();
                Label falseLabel = new Label();
                Label continueLabel = new Label();

                // Insert a duplicate of the jump which records the branch taken
                mv.visitJumpInsn(opcode, trueLabel);
                mv.visitLabel(falseLabel);
                instrumentTraceLog(BranchType.IF, 0); // 0 for false
                mv.visitJumpInsn(Opcodes.GOTO, continueLabel);

                mv.visitLabel(trueLabel);
                instrumentTraceLog(BranchType.IF, 1); // 1 for true
                mv.visitJumpInsn(Opcodes.GOTO, continueLabel);

                // Continue with the original jump
                mv.visitLabel(continueLabel);
            }

            super.visitJumpInsn(opcode, label);
        }

        /**
         * Check if the opcode is a conditional jump (if statement).
         * 
         * @param opcode The opcode to check
         * @return True if the opcode is a conditional jump, false otherwise
         */
        private boolean isConditionalJump(int opcode) {
            return (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE) || opcode == Opcodes.IFNULL
                    || opcode == Opcodes.IFNONNULL;
        }

        /**
         * Check if an opcode for a conditional jump requires two operands (two values
         * on the stack).
         * 
         * @param opcode The conditional opcode to check
         * @return True if the opcode requires two operands, false otherwise
         */
        private boolean requiresTwoOperands(int opcode) {
            return opcode >= Opcodes.IF_ICMPEQ && opcode <= Opcodes.IF_ACMPNE;
        }

        // Instrument sparse int switch statements (lookup switch)
        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            // Duplicate the value to keep the original value for the actual jump
            mv.visitInsn(Opcodes.DUP);

            // Create a copy of the switch statement with dummy labels that records the case
            Label[] dummyLbls = createDummyLabels(labels.length);
            Label dummyDflt = new Label();
            mv.visitLookupSwitchInsn(dummyDflt, keys, dummyLbls);
            Label continueLabel = new Label();
            for (int i = 0; i < keys.length; i++) {
                mv.visitLabel(dummyLbls[i]);
                instrumentTraceLog(BranchType.SWITCH, i);
                mv.visitJumpInsn(Opcodes.GOTO, continueLabel);
            }
            mv.visitLabel(dummyDflt);
            // Index/value for default case is number of keys
            instrumentTraceLog(BranchType.SWITCH, keys.length);

            // Continue with the original switch statement
            mv.visitLabel(continueLabel);

            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        // Instrument dense int switch statements (table switch)
        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            // Duplicate the value to keep the original value for the actual jump
            mv.visitInsn(Opcodes.DUP);

            // Create a copy of the switch statement with dummy labels that records the case
            Label[] dummyLbls = createDummyLabels(labels.length);
            Label dummyDflt = new Label();
            mv.visitTableSwitchInsn(min, max, dummyDflt, dummyLbls);
            Label continueLabel = new Label();
            // Notice the slight difference with a lookup switch: the keys are just the
            // values from min to max
            for (int i = min; i <= max; i++) {
                mv.visitLabel(dummyLbls[i - min]);
                instrumentTraceLog(BranchType.SWITCH, i - min);
                mv.visitJumpInsn(Opcodes.GOTO, continueLabel);
            }
            mv.visitLabel(dummyDflt);
            // Index/value for default case is number of keys
            instrumentTraceLog(BranchType.SWITCH, max - min + 1);

            // Continue with the original switch statement
            mv.visitLabel(continueLabel);

            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        private boolean isArrayLoad(int opcode) {
            return opcode >= Opcodes.IALOAD && opcode <= Opcodes.SALOAD;
        }

        private boolean isArrayStore(int opcode) {
            return opcode >= Opcodes.IASTORE && opcode <= Opcodes.SASTORE;
        }

        // Instrument array access instructions to record whether index is in bounds
        @Override
        public void visitInsn(int opcode) {
            if (isArrayLoad(opcode)) {
                // For array load, stack is [array, index]
                instrumentArrayBoundsCheck();
            } else if (isArrayStore(opcode)) {
                // For array store, stack is [array, index, value]
                int valueVar = storeValueForArrayStore(opcode);
                // Now stack is [array, index]
                instrumentArrayBoundsCheck();
                // Restore the value for the original store instruction
                restoreValueForArrayStore(opcode, valueVar);
            }
            super.visitInsn(opcode);
        }

        /**
         * Instrument array bounds check for array load/store instructions.
         */
        private void instrumentArrayBoundsCheck() {
            // Assumes the top of the stack is [array, index]
            mv.visitInsn(Opcodes.DUP2);
            int indexVar = newLocal(Type.INT_TYPE);
            int arrayVar = newLocal(Type.getType(Object.class));
            mv.visitVarInsn(Opcodes.ISTORE, indexVar); // Pop duplicate index
            mv.visitVarInsn(Opcodes.ASTORE, arrayVar); // Pop duplicate array

            Label inBounds = new Label();
            Label outOfBounds = new Label();
            Label continueLabel = new Label();

            // if (index < 0) goto outOfBounds
            mv.visitVarInsn(Opcodes.ILOAD, indexVar);
            mv.visitJumpInsn(Opcodes.IFLT, outOfBounds);

            // if (index < array.length) goto inBounds else goto outOfBounds
            mv.visitVarInsn(Opcodes.ILOAD, indexVar);
            mv.visitVarInsn(Opcodes.ALOAD, arrayVar);
            mv.visitInsn(Opcodes.ARRAYLENGTH);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, inBounds);
            mv.visitJumpInsn(Opcodes.GOTO, outOfBounds);

            mv.visitLabel(inBounds);
            instrumentTraceLog(BranchType.ARRAY, 1); // 1 indicates in bounds
            mv.visitJumpInsn(Opcodes.GOTO, continueLabel);

            mv.visitLabel(outOfBounds);
            instrumentTraceLog(BranchType.ARRAY, 0); // 0 indicates out-of-bounds
            mv.visitLabel(continueLabel);
        }

        /**
         * Store the value of an array store instruction in a local variable.
         */
        private int storeValueForArrayStore(int opcode) {
            int valueVar;
            switch (opcode) {
                case Opcodes.LASTORE:
                    valueVar = newLocal(Type.LONG_TYPE);
                    mv.visitVarInsn(Opcodes.LSTORE, valueVar);
                    break;
                case Opcodes.FASTORE:
                    valueVar = newLocal(Type.FLOAT_TYPE);
                    mv.visitVarInsn(Opcodes.FSTORE, valueVar);
                    break;
                case Opcodes.DASTORE:
                    valueVar = newLocal(Type.DOUBLE_TYPE);
                    mv.visitVarInsn(Opcodes.DSTORE, valueVar);
                    break;
                case Opcodes.AASTORE:
                    valueVar = newLocal(Type.getType(Object.class));
                    mv.visitVarInsn(Opcodes.ASTORE, valueVar);
                    break;
                default:
                    valueVar = newLocal(Type.INT_TYPE);
                    mv.visitVarInsn(Opcodes.ISTORE, valueVar);
                    break;
            }
            return valueVar;
        }

        /**
         * Restore the value of an array store instruction from a local variable.
         */
        private void restoreValueForArrayStore(int opcode, int valueVar) {
            switch (opcode) {
                case Opcodes.LASTORE:
                    mv.visitVarInsn(Opcodes.LLOAD, valueVar);
                    break;
                case Opcodes.FASTORE:
                    mv.visitVarInsn(Opcodes.FLOAD, valueVar);
                    break;
                case Opcodes.DASTORE:
                    mv.visitVarInsn(Opcodes.DLOAD, valueVar);
                    break;
                case Opcodes.AASTORE:
                    mv.visitVarInsn(Opcodes.ALOAD, valueVar);
                    break;
                default:
                    mv.visitVarInsn(Opcodes.ILOAD, valueVar);
                    break;
            }
        }

        /**
         * Instrument code to record a trace entry
         * 
         * @param type  The type of branch
         * @param value The value of the branch
         * @see TraceManager#recordTraceEntry(String, BranchType, int)
         */
        private void instrumentTraceLog(BranchType type, int value) {
            // Push arguments on the stack
            mv.visitLdcInsn(methodSignature);
            mv.visitFieldInsn(Opcodes.GETSTATIC, BRANCH_TYPE_PATH, type.name(),
                    String.format("L%s;", BRANCH_TYPE_PATH));
            mv.visitIntInsn(Opcodes.SIPUSH, value);

            // Call the recordTraceEntry method
            mv.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    TRACE_MANAGER_PATH,
                    "recordTraceEntry",
                    String.format("(Ljava/lang/String;L%s;I)V", BRANCH_TYPE_PATH),
                    false);
        }

        /**
         * Create an array of dummy labels
         * 
         * @param size The size of the array
         * @return An array of dummy labels
         */
        private Label[] createDummyLabels(int size) {
            Label[] labels = new Label[size];
            for (int i = 0; i < size; i++) {
                labels[i] = new Label();
            }
            return labels;
        }
    }
}
