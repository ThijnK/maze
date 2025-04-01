package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

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
}
