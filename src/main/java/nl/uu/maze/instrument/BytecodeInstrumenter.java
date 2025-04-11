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
public class BytecodeInstrumenter {
    private String[] classPaths;
    private BytecodeClassLoader classLoader = new BytecodeClassLoader();
    private Set<String> processedClasses = new HashSet<>();

    /**
     * Constructor for the BytecodeInstrumenter.
     * 
     * @param classPath The classpath (or multiple separated by path separators) to
     *                  search for class files to instrument.
     */
    public BytecodeInstrumenter(String classPath) {
        classPaths = classPath.split(File.pathSeparator);
    }

    /**
     * Instrument a class file to record symbolic traces.
     * This will also instrument all nested classes and classes which the main class
     * depends on (as long as they can be found in the classpath).
     * 
     * @param className The name of the class
     * @return The instrumented class
     * @throws IOException            If an I/O error occurs while reading class
     *                                files
     * @throws ClassNotFoundException If the main class cannot be found
     */
    public Class<?> instrument(String className) throws IOException, ClassNotFoundException {
        Queue<String> classesToProcess = new LinkedList<>();
        classesToProcess.add(className);

        while (!classesToProcess.isEmpty()) {
            String currentClass = classesToProcess.poll();
            // Skip if already processed
            if (!processedClasses.add(currentClass)) {
                continue;
            }

            // Instrument current class and any nested classes
            Set<String> dependencies = instrumentClassAndGetDependencies(currentClass);

            // Add new dependencies to the queue
            for (String dependency : dependencies) {
                if (!processedClasses.contains(dependency)) {
                    classesToProcess.add(dependency);
                }
            }
        }

        // Find the main class in the class loader and return it
        return classLoader.findClass(className);
    }

    /**
     * Get the class loader used by this instrumentation to store instrumented
     * classes.
     */
    public BytecodeClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Instrument a single class and its nested classes, and return their
     * dependencies.
     */
    private Set<String> instrumentClassAndGetDependencies(String className) throws IOException, ClassNotFoundException {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String packageName = className.substring(0, className.lastIndexOf('.'));
        String resourcePath = packageName.replace(".", "/");

        // Find the first classpath that contains the class file
        String classPath = null;
        for (String path : classPaths) {
            File file = new File(path, resourcePath);
            if (file.exists()) {
                classPath = path;
                break;
            }
        }
        if (classPath == null) {
            throw new ClassNotFoundException("Class " + className + " not found in classpath");
        }

        // Collect class files (main + nested)
        File classPathDir = new File(classPath, resourcePath);
        if (!classPathDir.exists()) {
            return Set.of(); // Class not found
        }
        List<File> classFiles = new ArrayList<>();
        File[] files = classPathDir.listFiles();
        if (files == null) {
            return Set.of();
        }
        for (File file : files) {
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
