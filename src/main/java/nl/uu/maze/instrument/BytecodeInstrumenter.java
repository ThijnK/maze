package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class BytecodeInstrumenter {
    private static final String LOGGER_CLASS = TraceLogger.class.getName();
    private static final String LOGGER_PATH = LOGGER_CLASS.replace('.', '/');

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

        // Regsiter the TraceLogger class to this class loader, so the instrumented
        // code will have access to the logging functionality
        BytecodeClassLoader classLoader = new BytecodeClassLoader();
        classLoader.registerClass(TraceLogger.class);
        byte[] instrumentedBytes = classWriter.toByteArray();
        return classLoader.defineClass(className, instrumentedBytes);
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
        private final String methodName;

        protected SymbolicTraceMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name,
                String descriptor) {
            super(api, methodVisitor, access, name, descriptor);
            this.methodName = name;
        }

        @Override
        protected void onMethodEnter() {
            addTraceLog("Entering method: " + methodName);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            String branchMessage = "";

            // Check if the opcode is one of the conditional jump instructions (IF_*
            // opcodes)
            switch (opcode) {
                case Opcodes.IFEQ:
                    branchMessage = "If equal branch taken";
                    break;
                case Opcodes.IFNE:
                    branchMessage = "If not equal branch taken";
                    break;
                case Opcodes.IFLT:
                    branchMessage = "If less than branch taken";
                    break;
                case Opcodes.IFGE:
                    branchMessage = "If greater than or equal branch taken";
                    break;
                case Opcodes.IFGT:
                    branchMessage = "If greater than branch taken";
                    break;
                case Opcodes.IFLE:
                    branchMessage = "If less than or equal branch taken";
                    break;
                case Opcodes.IFNULL:
                    branchMessage = "If null branch taken";
                    break;
                case Opcodes.IFNONNULL:
                    branchMessage = "If non-null branch taken";
                    break;
                default:
                    branchMessage = "Jumping to label: " + label;
                    break;
            }

            // Log the branch taken based on the jump instruction
            addTraceLog(branchMessage);

            // Continue with the original jump instruction
            super.visitJumpInsn(opcode, label);
        }

        @Override
        protected void onMethodExit(int opcode) {
            addTraceLog("Exiting method: " + methodName);
        }

        /** Insert code to log a symbolic trace message */
        protected void addTraceLog(String message) {
            mv.visitLdcInsn(message);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, LOGGER_PATH, "log", "(Ljava/lang/String;)V", false);
        }
    }
}
