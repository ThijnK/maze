package nl.uu.maze.instrument;

import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * ASM visitor that collects class dependencies.
 */
public class DependencyCollectingVisitor extends ClassVisitor {
    private final Set<String> dependencies = new HashSet<>();

    public DependencyCollectingVisitor() {
        super(Opcodes.ASM9);
    }

    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        // Superclass and interfaces count as dependencies
        if (superName != null) {
            addDependency(superName);
        }
        if (interfaces != null) {
            for (String iface : interfaces) {
                addDependency(iface);
            }
        }
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        // Extract method references
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                    String descriptor, boolean isInterface) {
                // Add the owner of any method call as a dependency
                addDependency(owner);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                // Add the owner of any field access as a dependency
                addDependency(owner);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                // Any type reference is a dependency
                if (type.startsWith("[")) {
                    // Handle array types (we want the element type)
                    String elementType = extractElementType(type);
                    if (elementType != null) {
                        addDependency(elementType);
                    }
                } else {
                    addDependency(type);
                }
            }
        };
    }

    /**
     * Add a dependency if it is not a Java standard library type.
     */
    private void addDependency(String type) {
        if (!isJavaLangType(type)) {
            dependencies.add(type.replace('/', '.'));
        }
    }

    /**
     * Check if the type is part of the Java standard library.
     *
     * @param type The type to check
     * @return True if it is a Java standard library type, false otherwise
     */
    private boolean isJavaLangType(String type) {
        return type.startsWith("java/lang/") ||
                type.startsWith("java/util/") ||
                type.startsWith("java/io/");
    }

    /**
     * Extracts the element type from an array descriptor.
     *
     * @param arrayType The array descriptor (e.g., "[I" for int[],
     *                  "[Lcom/example/MyClass;")
     * @return The element type (e.g., "I" for int, "Lcom/example/MyClass;")
     */
    private String extractElementType(String arrayType) {
        // Extract element type from array descriptor
        if (arrayType.startsWith("[L") && arrayType.endsWith(";")) {
            return arrayType.substring(2, arrayType.length() - 1);
        }
        return null;
    }

    /**
     * Returns the set of dependencies collected by this visitor.
     *
     * @return The set of dependencies
     */
    public Set<String> getDependencies() {
        return dependencies;
    }
}