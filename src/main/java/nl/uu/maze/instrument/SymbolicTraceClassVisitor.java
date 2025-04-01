package nl.uu.maze.instrument;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM class visitor that instruments the class to record symbolic traces.
 */
public class SymbolicTraceClassVisitor extends ClassVisitor {
    private final String resourcePath;

    public SymbolicTraceClassVisitor(ClassVisitor classVisitor, String resourcePath) {
        super(Opcodes.ASM9, classVisitor);
        this.resourcePath = resourcePath;
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
            String[] exceptions) {
        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        String methodSignature = TraceManager.buildMethodSignature(resourcePath, name, descriptor);
        return new SymbolicTraceMethodVisitor(Opcodes.ASM9, methodVisitor, access, name, descriptor,
                methodSignature);
    }
}
