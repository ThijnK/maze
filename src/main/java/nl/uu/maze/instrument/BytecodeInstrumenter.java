package nl.uu.maze.instrument;

import org.objectweb.asm.*;
import org.objectweb.asm.util.TraceClassVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nl.uu.maze.util.Pair;

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

    private final String[] classPaths;
    private final BytecodeClassLoader classLoader = new BytecodeClassLoader();
    private final Set<String> processedClasses = new HashSet<>();

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
     * The original MAZE instrumenter. It does not load the dependencies
     * in the correct order with respect to inheritence. This is now
     * replaced by {@link #instrument2(String)}.
     * 
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
    public Class<?> instrumentOrig(String className) throws IOException, ClassNotFoundException {
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
        // get the bin of the target class + its nested classes;
    	// Note: only classes from classPaths will be collected:
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
    

   /* Instrument a class file to record symbolic traces.
    * This will also instrument all nested classes and classes which the main class
    * depends on (as long as they can be found in the classpath).
    * 
    * @param className The name of the class
    * @return The instrumented class
    * @throws IOException            If an I/O error occurs while reading class
    *                                files
    * @throws ClassNotFoundException If the main class cannot be found
    * 
    * <p>NOTE: when a class D is instrumented, this is done on its byte-code. After that,
    * it is converted to an instance of a Class, effectively "loading" it to the runtime.
    * This conversion requires that all D's ancestor classes/interfaces to have already been
    * loaded. This is not an issue for ancestor E is a library class that was already 
    * pre-loaded. However, if E is managed by MAZE, so another class that needs to be instrumented
    * as well, this means that the order of instrumentation matters: E should be instrumented (and
    * hence loaded) before D. The original MAZE instrumenter does not take this into account.
    * 
    * <p>The new instrumenter2 below isn't fully correct either. (1) It instruments (and loads) classes
    * simply in the order of combined inheritence-and-use-dependency, rather than just inheritence 
    * dependency, and (2) inner classes are instrumented in no particular order, which can be a problem
    * if there are inheritance relations among them.
    * 
    * <p>For now this works in most cases. TODO: fix this.
    */ 

    public Class<?> instrument2(String className) throws IOException, ClassNotFoundException {
    	List<ClassFileEntry> classesToInstrument = collectBinAndDependencies2(className) ;
    	// instrument the classes in reverse order:
    	while (! classesToInstrument.isEmpty()) {
    		ClassFileEntry cf = classesToInstrument.removeLast() ;
    		// Then instrument
            ClassReader classReader = new ClassReader(cf.bytes);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            ClassVisitor classVisitor = new SymbolicTraceClassVisitor(classWriter, cf.resourcePath);
            classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES);

            byte[] instrumentedBytes = classWriter.toByteArray();
            classLoader.addClass(cf.fullName, instrumentedBytes);
    	}
    	// Find the main class in the class loader and return it
        return classLoader.findClass(className);
    }

    	
    
    /**
     * Collect the bytecode of the given class C,  the bytecode of C's inner classes,
     * and that of other classes on which C depends on. Only bytecodes
     * from classes in {@link #classPaths} will be collected though.
     * 
     * <p>The dependencies consist of all ancestor classes/interfaces of C and all 
     * classes/interfaces that are transitively used by C. So, if C uses D, which in
     * turns uses E, both D and E are considered as dependencies.
     * 
     * <p>The bytecodes are returned in a list, in the order of the dependencies,
     * such that a class C appear before all classes it depends on. 
     * NOTE: the ordering does not necerssarily respect inheritence ordering; TODO.
     * 
     * @param className   full class name
     * @return Bytecode of the given class, its inner classes, and its dependencies.
     */
    private List<ClassFileEntry> collectBinAndDependencies2(String className) {
    	List<ClassFileEntry> done = new LinkedList<>();
    	List<String> done_ =  new LinkedList<>();
    	List<String> toDo = new LinkedList<>() ;
    	toDo.add(className) ;
    	while (! toDo.isEmpty()) {
    		String Cname = toDo.removeFirst() ;
    		if (done_.contains(Cname))
    			continue ;
    		// get the binary of Cname and all its inner classes:
    		List<ClassFileEntry> C_andInners = collectClassFiles(Cname) ;
    		// calculate the dependencies of those classes:
    		DependencyCollectingVisitor dependencyCollector = new DependencyCollectingVisitor();
            for (var cf : C_andInners) {
    			done.add(cf) ;
    			done_.add(cf.fullName) ;
    			ClassReader depReader = new ClassReader(cf.bytes);
                depReader.accept(dependencyCollector, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    		}
            // adding the dependencies to the to-do list:
            Set<String> newDeps = dependencyCollector.getDependencies() ;
            for (var D : newDeps) {
            	if (! toDo.contains(D) && ! done_.contains(D))
            		toDo.add(D) ;
            }
    	}
    	return done ; 	
    }
    
    

    /** Collect class files from classpath. */
    private List<ClassFileEntry> collectClassFiles(String className) {
        int packageEnd = className.lastIndexOf('.');
        String simpleName = className.substring(packageEnd + 1);
        String packageName = packageEnd < 0 ? "" : className.substring(0, packageEnd);
        String resourcePath = packageName.replace('.', '/');
        String packagePrefix = packageName.isEmpty() ? "" : packageName + '.';
        String resourcePrefix = resourcePath.isEmpty() ? "" : resourcePath + '/';
        
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
                        String fileName = entryName.substring(entryName.lastIndexOf('/') + 1);
                        if (entryName.equals(resourcePrefix + fileName) &&
                                isClassOrNestedClass(fileName, simpleName)) {
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
                                classFiles.add(new ClassFileEntry(name, packagePrefix + name,
                                        resourcePrefix + name, classBytes));
                            } catch (IOException e) {
                                logger.error("Error reading class from JAR: {}", entryName, e);
                            }
                        }
                    }
                } catch (IOException e) {
                    logger.error("Error opening JAR file: {}", path, e);
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
                        	//System.out.println(">>> listing file : " + f + ", just-name: " + f.getName()) ;
                            if (isClassOrNestedClass(f.getName(), simpleName)) {
                                found = true;
                                try {
                                    byte[] classBytes = Files.readAllBytes(f.toPath());
                                    //System.out.println("   grabbing bytes of " + f) ;
                                    String name = f.getName().substring(0, f.getName().length() - 6);
                                    classFiles.add(new ClassFileEntry(name, packagePrefix + name,
                                            resourcePrefix + name, classBytes));
                                    //System.out.println("   adding class-file of " + f) ;
                                    // Found class file
                                } catch (IOException e) {
                                    logger.error("Error reading class file: {}", f.getAbsolutePath(), e);
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

    private static boolean isClassOrNestedClass(String fileName, String simpleName) {
        return fileName.equals(simpleName + ".class")
                || (fileName.startsWith(simpleName + '$') && fileName.endsWith(".class"));
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
    private record ClassFileEntry(String simpleName, String fullName, String resourcePath, byte[] bytes) {
    }
}
