package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class BytecodeInstrumenter {
    private static final String LOGGER_CLASS = TraceLogger.class.getName();
    private static final String LOGGER_CLASS_PATH = LOGGER_CLASS.replace('.', '/');

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

        writeOpcodesToFile(classBytes);

        // Register the TraceLogger class to this class loader, so the instrumented
        // code will have access to the logging functionality
        BytecodeClassLoader classLoader = new BytecodeClassLoader();
        classLoader.registerClass(TraceLogger.class);
        byte[] instrumentedBytes = classWriter.toByteArray();
        return classLoader.defineClass(className, instrumentedBytes);
    }

    private static void writeOpcodesToFile(byte[] classBytes) throws IOException {
        ClassReader classReader = new ClassReader(classBytes);
        try (PrintWriter writer = new PrintWriter(new FileWriter("logs/opcodes.txt"))) {
            TraceClassVisitor traceClassVisitor = new TraceClassVisitor(writer);
            classReader.accept(traceClassVisitor, ClassReader.EXPAND_FRAMES);
        }
    }

    static class BytecodeClassLoader extends ClassLoader {
        private final Map<String, Class<?>> classes = new HashMap<>();

        public void registerClass(Class<?> clazz) {
            classes.put(clazz.getName(), clazz);
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Class<?> clazz = classes.get(name);
            return clazz != null ? clazz : super.findClass(name);
        }

        public Class<?> defineClass(String name, byte[] b) {
            return defineClass(name, b, 0, b.length);
        }
    }

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

    static class SymbolicTraceMethodVisitor extends AdviceAdapter {
        protected SymbolicTraceMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name,
                String descriptor) {
            super(api, methodVisitor, access, name, descriptor);
        }

        // To handle if statements
        @Override
        public void visitJumpInsn(int opcode, Label label) {
            if (isConditionalJump(opcode)) {
                logMessage("Branch " + opcodeToString(opcode));
                logCondition(opcode);
            }

            super.visitJumpInsn(opcode, label);
        }

        private boolean isConditionalJump(int opcode) {
            return (opcode >= Opcodes.IFEQ && opcode <= Opcodes.IF_ACMPNE) || opcode == Opcodes.IFNULL
                    || opcode == Opcodes.IFNONNULL;
        }

        private boolean requiresTwoOperands(int opcode) {
            return opcode >= Opcodes.IF_ICMPEQ && opcode <= Opcodes.IFNONNULL;
        }

        // To handle switch statements
        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            // TODO Auto-generated method stub
            // Should keep in memory some mapping between keys and labels
            // Then at the next label, log the branch taken based on the key
            super.visitLookupSwitchInsn(dflt, keys, labels);
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            // Bytecode instruction for dense int switch: maps [min, max] to jump labels

            // TODO Auto-generated method stub
            super.visitTableSwitchInsn(min, max, dflt, labels);
        }

        /** Insert code to log a symbolic trace message */
        protected void logMessage(String message) {
            mv.visitLdcInsn(message);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, LOGGER_CLASS_PATH, "log", "(Ljava/lang/String;)V", false);
        }

        /** Insert code to evaluate and log a branch condition */
        protected void logCondition(int opcode) {
            if (requiresTwoOperands(opcode)) {
                mv.visitInsn(Opcodes.DUP2);
            } else {
                mv.visitInsn(Opcodes.DUP);
            }

            Label trueLabel = new Label();
            Label falseLabel = new Label();
            Label continueLabel = new Label();

            mv.visitJumpInsn(opcode, trueLabel);
            mv.visitLabel(falseLabel);
            logMessage("Condition FALSE");
            mv.visitJumpInsn(Opcodes.GOTO, continueLabel);

            mv.visitLabel(trueLabel);
            logMessage("Condition TRUE");
            mv.visitJumpInsn(Opcodes.GOTO, continueLabel);

            mv.visitLabel(continueLabel);
        }

        private String opcodeToString(int opcode) {
            switch (opcode) {
                case Opcodes.IFEQ:
                    return "IFEQ";
                case Opcodes.IFNE:
                    return "IFNE";
                case Opcodes.IFLT:
                    return "IFLT";
                case Opcodes.IFGE:
                    return "IFGE";
                case Opcodes.IFGT:
                    return "IFGT";
                case Opcodes.IFLE:
                    return "IFLE";
                case Opcodes.IF_ICMPEQ:
                    return "IF_ICMPEQ";
                case Opcodes.IF_ICMPNE:
                    return "IF_ICMPNE";
                case Opcodes.IF_ICMPLT:
                    return "IF_ICMPLT";
                case Opcodes.IF_ICMPGE:
                    return "IF_ICMPGE";
                case Opcodes.IF_ICMPGT:
                    return "IF_ICMPGT";
                case Opcodes.IF_ICMPLE:
                    return "IF_ICMPLE";
                case Opcodes.IF_ACMPEQ:
                    return "IF_ACMPEQ";
                case Opcodes.IF_ACMPNE:
                    return "IF_ACMPNE";
                case Opcodes.IFNULL:
                    return "IFNULL";
                case Opcodes.IFNONNULL:
                    return "IFNONNULL";
                default:
                    return "UNKNOWN";
            }
        }
    }
}
