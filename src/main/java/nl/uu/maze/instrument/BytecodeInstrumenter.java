package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class BytecodeInstrumenter {

    public static void instrument(String classPath, String className) throws IOException {
        String classString = className.replace(".", "/");
        String classFile = classPath + '/' + classString + ".class";

        byte[] classBytes = Files.readAllBytes(Paths.get(classFile));

        // Read the existing class file
        ClassReader classReader = new ClassReader(classBytes);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        // Instrument the class
        ClassVisitor classVisitor = new SymbolicTraceClassVisitor(classWriter);
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

        // Write the instrumented class back to a file
        File outFile = new File(classPath + "/instrumented/" + classString + ".class");
        outFile.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            fos.write(classWriter.toByteArray());
        }
        System.out.println("Instrumentation complete. Instrumented class written to: " + outFile.getAbsolutePath());
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
            // Inject code at the start of the method to log the entry point
            mv.visitLdcInsn("Entering method: " + methodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "SymbolicTraceLogger", "log", "(Ljava/lang/String;)V", false);
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            // Inject code before conditional jumps to log branch conditions
            mv.visitLdcInsn("Branch condition in method: " + methodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "SymbolicTraceLogger", "log", "(Ljava/lang/String;)V", false);
            super.visitJumpInsn(opcode, label);
        }

        @Override
        protected void onMethodExit(int opcode) {
            // Inject code at the end of the method to log the exit point
            mv.visitLdcInsn("Exiting method: " + methodName);
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "SymbolicTraceLogger", "log", "(Ljava/lang/String;)V", false);
        }
    }

    // Logger utility to be used in the instrumented code
    public static class SymbolicTraceLogger {
        public static void log(String message) {
            System.out.println(message);
        }
    }
}
