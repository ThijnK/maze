package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Instruments Java bytecode files to record symbolic traces during (concrete)
 * execution.
 * Uses the ASM library.
 */
public class BytecodeInstrumenter {
    private static final Logger logger = LoggerFactory.getLogger(BytecodeInstrumenter.class);

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
     * Get the class loader used by this instrumentation to store instrumented
     * classes.
     */
    public BytecodeClassLoader getClassLoader() {
        return classLoader;
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
            Set<String> dependencies = instrumentAndCollectDependencies(currentClass);

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
     * Instrument a single class and its nested classes, and return their
     * dependencies.
     */
    private Set<String> instrumentAndCollectDependencies(String className) throws IOException, ClassNotFoundException {
        List<ClassFileEntry> classFiles = collectClassFiles(className);

        // Process each file
        DependencyCollectingVisitor dependencyCollector = new DependencyCollectingVisitor();
        for (ClassFileEntry file : classFiles) {
            // First scan for dependencies
            ClassReader depReader = new ClassReader(file.bytes);
            depReader.accept(dependencyCollector, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

            // Then instrument
            ClassReader classReader = new ClassReader(file.bytes);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor classVisitor = new SymbolicTraceClassVisitor(classWriter, file.resourcePath);
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

            byte[] instrumentedBytes = classWriter.toByteArray();
            classLoader.addClass(file.fullName, instrumentedBytes);
        }

        return dependencyCollector.getDependencies();
    }

    /** Collect class files from classpath. */
    private List<ClassFileEntry> collectClassFiles(String className) {
        String simpleName = className.substring(className.lastIndexOf('.') + 1);
        String packageName = className.substring(0, className.lastIndexOf('.'));
        String resourcePath = packageName.replace(".", "/");

        List<ClassFileEntry> classFiles = new ArrayList<>();
        boolean found = false;
        for (String path : classPaths) {
            File pathFile = new File(path);

            // Handle JAR files
            if (path.toLowerCase().endsWith(".jar")) {
                try (JarFile jarFile = new JarFile(pathFile)) {
                    // Look for class files in the JAR
                    for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements();) {
                        JarEntry entry = entries.nextElement();
                        String entryName = entry.getName();

                        // Check if it's in the right package and has the right name pattern
                        if (entryName.startsWith(resourcePath + "/") &&
                                entryName.endsWith(".class") &&
                                entryName.substring(entryName.lastIndexOf('/') + 1).startsWith(simpleName)) {
                            found = true;
                            try {
                                // Read the class bytes from the JAR entry
                                byte[] classBytes;
                                try (InputStream is = jarFile.getInputStream(entry)) {
                                    classBytes = is.readAllBytes();
                                }

                                // Extract the class name without '.class' extension
                                String name = entryName.substring(entryName.lastIndexOf('/') + 1,
                                        entryName.length() - 6);
                                classFiles.add(new ClassFileEntry(name, packageName + '.' + name,
                                        resourcePath + '/' + name, classBytes));
                            } catch (IOException e) {
                                logger.error("Error reading class from JAR: " + entryName, e);
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error opening JAR file: " + path, e);
                }
            }
            // Handle directories (existing code)
            else {
                File file = new File(path, resourcePath);
                if (file.exists()) {
                    // Collect class files (main + nested)
                    File[] files = file.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            // Filter for class files of the main class and nested classes
                            if (f.getName().startsWith(simpleName) && f.getName().endsWith(".class")) {
                                found = true;
                                try {
                                    byte[] classBytes = Files.readAllBytes(f.toPath());
                                    String name = f.getName().substring(0, f.getName().length() - 6);
                                    classFiles.add(new ClassFileEntry(name, packageName + '.' + name,
                                            resourcePath + '/' + name, classBytes));
                                    // Found class file
                                } catch (IOException e) {
                                    logger.error("Error reading class file: " + f.getAbsolutePath(), e);
                                }
                            }
                        }
                    }
                }
            }

            // If we found the class, stop searching in other class paths
            if (found)
                return classFiles;
        }
        return classFiles;
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
     * Helper class to store class names and bytecode together.
     */
    private static record ClassFileEntry(String simpleName, String fullName, String resourcePath, byte[] bytes) {
    }
}
