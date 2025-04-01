package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;

/**
 * Instruments Java bytecode files to record symbolic traces during (concrete)
 * execution.
 * Uses the ASM library.
 */
public class BytecodeInstrumentation {
    /**
     * Instrument a class file to record symbolic traces.
     * This will also instrument all nested classes and classes which the main class
     * depends on (as long as they can be found in the classpath).
     * 
     * @param classPath The path to the class file
     * @param className The name of the class
     * @return The instrumented class
     * @throws IOException            If an I/O error occurs while reading class
     *                                files
     * @throws ClassNotFoundException If the main class cannot be found
     */
    public static Class<?> instrument(String classPath, String className) throws IOException, ClassNotFoundException {
        BytecodeClassLoader classLoader = new BytecodeClassLoader();
        Set<String> processedClasses = new HashSet<>();
        Queue<String> classesToProcess = new LinkedList<>();

        // Start with the main class to instrument
        classesToProcess.add(className);

        while (!classesToProcess.isEmpty()) {
            String currentClass = classesToProcess.poll();
            if (!processedClasses.add(currentClass)) {
                continue;
            }

            // Instrument current class and any nested classes
            Set<String> dependencies = instrumentClassAndGetDependencies(classPath, currentClass, classLoader);

            // Add new dependencies to the queue
            for (String dependency : dependencies) {
                if (!processedClasses.contains(dependency)) {
                    classesToProcess.add(dependency);
                }
            }
        }

        // Find the main class in the class loader and return it
        Class<?> mainClass = classLoader.findClass(className);
        if (mainClass == null) {
            throw new ClassNotFoundException("Class " + className + " not found in class loader.");
        }
        return mainClass;
    }

    /**
     * Instrument a single class and its nested classes, and return their
     * dependencies.
     */
    private static Set<String> instrumentClassAndGetDependencies(String classPath, String className,
            BytecodeClassLoader classLoader) throws IOException {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String packageName = className.substring(0, className.lastIndexOf('.'));
        String resourcePath = packageName.replace(".", "/");

        // Collect class files (main + nested)
        File classPathDir = new File(classPath, resourcePath);
        if (!classPathDir.exists()) {
            return Set.of(); // Class not found
        }
        List<File> classFiles = new ArrayList<>();
        for (File file : classPathDir.listFiles()) {
            // Filter for class files of the main class and nested classes
            // (e.g., Foo$Bar.class for nested class Foo.Bar)
            if (file.getName().startsWith(simpleName) && file.getName().endsWith(".class")) {
                classFiles.add(file);
            }
        }

        // Process each file
        DependencyCollectingVisitor dependencyCollector = new DependencyCollectingVisitor();
        for (File file : classFiles) {
            String name = file.getName().substring(0, file.getName().length() - 6);
            byte[] classBytes = Files.readAllBytes(file.toPath());

            // First scan for dependencies
            ClassReader depReader = new ClassReader(classBytes);
            depReader.accept(dependencyCollector, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            // Then instrument
            ClassReader classReader = new ClassReader(classBytes);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor classVisitor = new SymbolicTraceClassVisitor(classWriter, resourcePath + '/' + name);
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

            byte[] instrumentedBytes = classWriter.toByteArray();
            classLoader.addClass(packageName + '.' + name, instrumentedBytes);
        }

        return dependencyCollector.getDependencies();
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
