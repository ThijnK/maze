package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import nl.uu.maze.instrument.TraceManager.BranchType;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BytecodeInstrumenter {
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
        String classString = className.replace(".", "/");
        String classFile = classPath + '/' + classString + ".class";

        byte[] classBytes = Files.readAllBytes(Paths.get(classFile));

        // Read the existing class file
        ClassReader classReader = new ClassReader(classBytes);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // Instrument the class
        ClassVisitor classVisitor = new SymbolicTraceClassVisitor(classWriter);
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

        // TODO: only write opcodes to file for debugging purposes
        writeOpcodesToFile(classBytes);

        BytecodeClassLoader classLoader = new BytecodeClassLoader();
        byte[] instrumentedBytes = classWriter.toByteArray();
        return classLoader.defineClass(className, instrumentedBytes);
    }

    /** Write bytecode of a class to a file in human-readable format (opcodes). */
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
    static class BytecodeClassLoader extends ClassLoader {
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
        public SymbolicTraceClassVisitor(ClassVisitor classVisitor) {
            super(Opcodes.ASM9, classVisitor);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                String[] exceptions) {
            MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new SymbolicTraceMethodVisitor(Opcodes.ASM9, methodVisitor, access, name, descriptor);
        }
    }

    /** Method visitor that instruments the method to record symbolic traces */
    static class SymbolicTraceMethodVisitor extends AdviceAdapter {
        String methodName;

        protected SymbolicTraceMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name,
                String descriptor) {
            super(api, methodVisitor, access, name, descriptor);
            this.methodName = name;
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

        /**
         * Instrument code to record a trace entry
         * 
         * @param type  The type of branch
         * @param value The value of the branch
         * @see TraceManager#recordTraceEntry(String, BranchType, int)
         */
        private void instrumentTraceLog(BranchType type, int value) {
            // Push arguments on the stack
            mv.visitLdcInsn(methodName);
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
