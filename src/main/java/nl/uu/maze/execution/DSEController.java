package nl.uu.maze.execution;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.z3.Model;

import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.concrete.ConcreteExecutor;
import nl.uu.maze.execution.symbolic.*;
import nl.uu.maze.generation.JUnitTestGenerator;
import nl.uu.maze.instrument.*;
import nl.uu.maze.search.SearchExecutionException;
import nl.uu.maze.search.strategy.ConcreteSearchStrategy;
import nl.uu.maze.search.strategy.DFS;
import nl.uu.maze.search.strategy.SearchStrategy;
import nl.uu.maze.search.strategy.SymbolicSearchStrategy;
import nl.uu.maze.util.BranchStmtUtil;
import nl.uu.maze.util.HCFG;
import nl.uu.maze.util.IOUtils;
import nl.uu.maze.util.Pair;
import sootup.core.graph.StmtGraph;
import sootup.core.jimple.common.stmt.Stmt;
import sootup.java.core.JavaSootClass;
import sootup.java.core.JavaSootMethod;
import sootup.java.core.types.JavaClassType;

/**
 * Controls the dynamic symbolic execution using various search strategies, both
 * concrete or symbolic-driven.
 */
public class DSEController {
    private static final Logger logger = LoggerFactory.getLogger(DSEController.class);

    /** Max path length for symbolic execution */
    private final int maxDepth;
    private final boolean concreteDriven;
    private final Path outPath;
    private final String methodName;
    private final SearchStrategy<?> searchStrategy;
    /** Search strategy used for symbolic replay of a trace (DFS). */
    private final SymbolicSearchStrategy replayStrategy;
    private final JavaAnalyzer analyzer;
    private final BytecodeInstrumenter instrumenter;
    private JavaSootClass sootClass;
    private Class<?> clazz;
    private Class<?> instrumented;

    private final SymbolicExecutor symbolic;
    private final SymbolicStateValidator validator;
    private final ConcreteExecutor concrete;
    private final JUnitTestGenerator generator;

    private List<JavaSootMethod> staticMuts = new ArrayList<>();
    private List<JavaSootMethod> nonStaticMuts = new ArrayList<>();
    private Constructor<?> ctor;
    private JavaSootMethod ctorSoot;
    private StmtGraph<?> ctorCfg;
    private long timeBudget;
    private long overallDeadline = Long.MAX_VALUE;
    private final Map<String, Integer> replayAborts = new java.util.LinkedHashMap<>();

    public Map<String, Integer> getReplayAborts() {
        return Map.copyOf(replayAborts);
    }
    private long executionDeadline;
    /**
     * Map of init states used in concrete-driven execution, indexed by the hash
     * code of the path encoding.
     * That is, they are the final states of the constructor execution, which can
     * then be reused when a concrete execution traversed the constructor the same
     * as a previous execution.
     */
    private Map<Integer, SymbolicState> initStates;
   

    /**
     * Create a new execution controller.
     * 
     * @param classPath      The class path to the target class
     * @param concreteDriven Whether to use concrete-driven DSE (otherwise symbolic)
     * @param searchStrategy The search strategy to use
     * @param outPath        The output path for the generated test cases
     * @param methodName     The name of the method to generate tests for (or "all"
     *                       for all methods)
     * @param maxDepth       The maximum depth for symbolic execution
     * @param testTimeout    The timeout to apply to generated test cases in ms
     * @param packageName    The package name for the generated test files
     */
    public DSEController(String classPath, boolean concreteDriven, SearchStrategy<?> searchStrategy,
            String methodName, int maxDepth, long testTimeout, String packageName, boolean targetJUnit4)
            throws Exception {
        instrumenter = new BytecodeInstrumenter(classPath);
        ClassLoader classLoader;
        /*
         WP: changing the logic below to choose which classloader to use. Since in the new
         setup we always use the instrumented CUT for stmt-history reconstruction, we will
         always use the class-loader from the instrumenter:
         
        if (concreteDriven) {
            classLoader = instrumenter.getClassLoader();
        } else {
            String[] paths = classPath.split(File.pathSeparator);
            URL[] urls = new URL[paths.length];
            for (int i = 0; i < paths.length; i++) {
                urls[i] = Paths.get(paths[i]).toUri().toURL();
            }
            classLoader = new URLClassLoader(urls);
        }
        */
        
        // WP: as mentioned above, we'll change the logic to always use the instrumenter's 
        // class-loader:
        classLoader = instrumenter.getClassLoader();
        
        this.outPath = Path.of(EngineConfiguration.getInstance().outPath);
        this.methodName = methodName;
        this.maxDepth = maxDepth;
        this.concreteDriven = concreteDriven;
        this.searchStrategy = searchStrategy;
        this.replayStrategy = new SymbolicSearchStrategy(new DFS<SymbolicState>());

        this.analyzer = JavaAnalyzer.initialize(classPath, classLoader);

        this.concrete = new ConcreteExecutor();
        this.validator = new SymbolicStateValidator();
        
        this.symbolic = new SymbolicExecutor(concrete, validator, analyzer);
        
        this.generator = new JUnitTestGenerator(targetJUnit4, analyzer, concrete, testTimeout, packageName);
    }

    /**
     * Run the dynamic symbolic execution engine on the given class.
     * 
     * @param className  The name of the class to execute on
     * @param classToTrack If not null, the name of the class whose coverage will 
     *                   also be tracked. Note that coverage over className is 
     *                   always tracked.
     * @param timeBudget The time budget for the search in ms (0 for no timeout)
     * @throws Exception If an error occurs during execution, or if the class cannot
     *                   be found in the class path
     * @implNote This method prepares the class for execution, while the
     *           {@link #run()} method actually runs the execution.
     */
    public void run(String className, String classToTrack, long timeBudget) throws Exception {
    	Long mystartTime = System.currentTimeMillis();
        this.timeBudget = timeBudget;
        // Instrument the class if concrete-driven
        // If this class was instrumented before, it will reuse previous results
        //this.instrumented = concreteDriven
        //        ? instrumenter.instrument(className)
        //       : null;
        
        // WP: changing the above instrumentation logic.
        // Setting MAZE to always instrument, as we also need it for stmt-history reconstruction:
        
        if (this.instrumented == null)
        	  //this.instrumented = instrumenter.instrument(className) ;
  	  		  this.instrumented = instrumenter.instrument2(className) ;
        	
        JavaClassType classType = analyzer.getClassType(className);
        this.sootClass = analyzer.getSootClass(classType);
        this.clazz = analyzer.getJavaClass(classType);
        generator.initializeForClass(clazz);

        Set<JavaSootMethod> methods = sootClass.getMethods(); 

        // Organize mehtods under test into static and non-static, and filter out any
        // non-standard methods
        Pattern pattern = Pattern.compile("<[^>]+>");
        for (JavaSootMethod method : methods) {
            if (!method.isPublic() || pattern.matcher(method.getName()).matches()
                    || (!methodName.equals("all") && !method.getName().equals(methodName))) {
            	// we won't target these methods
            	
            	if (!method.isPublic()) {
            		// but coverage over non-public methods are still tracked
            		//CoverageTracker.getInstance().addTarget(method) ;
            	}
            	
                continue;
            }
           

            if (method.isStatic()) {
                staticMuts.add(method);
            } else {
                nonStaticMuts.add(method);
            }
            //CoverageTracker.getInstance().addTarget(method) ;
        }

        if (staticMuts.isEmpty() && nonStaticMuts.isEmpty()) {
            if (!methodName.equals("all")) {
                logger.info("No public testable methods found with name: {}", methodName);
            } else {
                logger.info("No public testable methods found in class: {}", clazz.getName());
            }
            return;
        }

        // If class includes non-static methods, need to execute constructor first
        if (!nonStaticMuts.isEmpty()) {
            ctor = analyzer.getJavaConstructor(instrumented != null ? instrumented : clazz);
            if (ctor == null) {
                throw new Exception("No constructor found for class: " + clazz.getName());
            }

            // Get corresponding CFG
            ctorSoot = analyzer.getSootConstructor(methods, ctor);
            ctorCfg = analyzer.getCFG(ctorSoot);
            initStates = new HashMap<>();
            //CoverageTracker.getInstance().addTarget(ctorSoot) ;
            logger.info("Using constructor: {}, #stmts:{}", 
            		ctorSoot.getSignature(),
            		ctorSoot.getBody().getStmts().size());
            //System.out.println(">>> " + ctorSoot.getName() + "\n" + ctorSoot.getBody()) ;
        }
        
        // registering coverage targets:
        CoverageTracker.getInstance().addTarget(
        		sootClass,
        		methodName.equals("all") ? null : methodName,
        		false // track non-public methods too
        		) ;
        // if classToTrack is not null, add that too as coverage
        // target:
        if (classToTrack != null) {
        	JavaSootClass TC = analyzer.getSootClass(analyzer.getClassType(classToTrack));
        	CoverageTracker.getInstance().addTarget(TC,null,false) ;
        }
        
        
        logger.info("Running {} DSE on class: {}", concreteDriven ? "concrete-driven" : "symbolic-driven",
                clazz.getSimpleName());

        logger.debug("Max depth: {}", maxDepth);
        logger.debug("Output path: {}", outPath);
        logger.debug("Time budget: {}", timeBudget > 0 ? timeBudget : "unlimited");

        // Write test cases regardless of whether the execution was successful or not,
        // so that intermediate results are not lost
        try {
            logger.info("Using search strategy: {}", searchStrategy.getName());
            run();
            // Resolve the final callback before publishing any success output.
            int exploredCount = searchStrategy.getTotalExploredCount();
        	generator.writeToFile(outPath); 
            logger.info("#generated test-cases: {}", generator.getNumberOfGeneratedTestCases()) ;
            
            // ok... well, runtime sampled at this point, NOT the same as the one sampled
            // from CLI
            Long runtime_ = System.currentTimeMillis() - mystartTime ;
            
            
            logger.info("#items explored: " + exploredCount) ;
        	int stmtTargets = CoverageTracker.getInstance().numberOfTargetStmts() ;
        	int stmtCovered = stmtTargets - CoverageTracker.getInstance().numberOfStillUnCoveredStmts() ;
        	int branchTargets = CoverageTracker.getInstance().numberOfTargetBranches() ;
        	int branchCovered = branchTargets - CoverageTracker.getInstance().numberOfStillUnCoveredBranches() ;
        	int untargetdBranchCovered = CoverageTracker.getInstance().numberOfCoveredUntargetedBrances() ;
        	int pathTargets = CoverageTracker.getInstance().numberOfTargetPaths() ;
        	int pathCovered = pathTargets 
        			- CoverageTracker.getInstance().numberOfStillUncoveredTargetPaths() 
        			- CoverageTracker.getInstance().numberOfDroppedTargetPaths() ;
        	logger.info("statement-coverage (by test): " + stmtCovered + "/" + stmtTargets) ;
        	logger.info("branch-coverage    (by test): " + branchCovered + "/" + branchTargets
        			+ ", #untargeted-branches covered: " + untargetdBranchCovered) ;
        	if (EngineConfiguration.getInstance().pathLengthCoverage == -1
        			|| EngineConfiguration.getInstance().pathLengthCoverage >= 1)
        		logger.info("k-path-converage    (by test): " + pathCovered + "/" + pathTargets
        				+ ", dropped: " + CoverageTracker.getInstance().numberOfDroppedTargetPaths()
        				) ;
        	
        	if (generator.getNumberOfViolationFound() > 0) {
        		if (EngineConfiguration.getInstance().verificationMode != 0) {
                   	logger.info("Verification: there were {} test/s that give ERROR (threw an unexpected exception).",  generator.getNumberOfViolationFound()) ;
        		}
        		else {
                   	logger.info("There were {} test/s that threw an unexpected exception. They may be ERRORs.",  generator.getNumberOfViolationFound()) ;       			
        		}
            }
        	else {
        		if (EngineConfiguration.getInstance().verificationMode != 0) {
                   	logger.info("Verification: PASS (no violation found") ;
        		}
        	}
            
            switch(EngineConfiguration.getInstance().exportPathCovInfo) {
                case -1 : logger.info("Path-coverage info:\n" + CoverageTracker.getInstance().showPathCoverageInfo()) ;
            	   	      break ;
                case 1: String classname = clazz.getName() ;
                        String file = Paths.get(EngineConfiguration.getInstance().outPath, classname + "-pathcov.txt").toString() ;
                        try {
                        	IOUtils.saveTxtToFile(file, CoverageTracker.getInstance().showPathCoverageInfo()) ;
                        }
                        catch(Exception e) {
                            throw new IllegalStateException("Failed to save the path-coverage info of " + classname, e);
                        } ;
                        break ;
            }
            
            if (EngineConfiguration.getInstance().exportSummary) {
            	String[] summaryHeader = {
               		 "CUT",
               		 "#items-explored",
               		 "#instruction",
               		 "#instr-cov",
               		 "instr-cov-ratio",
               		 "#branch",
               		 "#branch-cov",
               		 "branch-cov-ratio",
               		 "#path-targets",
               		 "#path-cov",
               		 "path-cov-ratio",
               		 "#path-dropped",
               		 "#test",
               		 "#errors",
               		 "runtime" 
               } ;
               
               float stmtCovRatio = 0f;
               if (stmtTargets > 0) {
               	stmtCovRatio = (float) stmtCovered / (float) stmtTargets ;
               }
               float branchCovRatio = 0f ;
               if (branchTargets > 0) {
               	branchCovRatio = (float) branchCovered / (float) branchTargets ;
               }
               float pathCovRatio = 0f ;
               if (pathTargets > 0) {
               	pathCovRatio = (float) pathCovered / (float) pathTargets ;
               }
               String[] summaryContent = {
               		clazz.getName(),
                     "" + exploredCount,
               		 "" + stmtTargets,
               		 "" + stmtCovered,
               		 "" + stmtCovRatio,
               		 "" + branchTargets,
               		 "" + branchCovered,
               		 "" + branchCovRatio,
               		 "" + pathTargets,
               		 "" + pathCovered,
               		 "" + pathCovRatio,
               		 "" + CoverageTracker.getInstance().numberOfDroppedTargetPaths(),
               		 "" + generator.getNumberOfGeneratedTestCases(),
               		 "" + generator.getNumberOfViolationFound(),
               		 "" + runtime_
               } ;
               
               String summary = Stream.of(summaryHeader).collect(Collectors.joining(",")).toString() ;
               summary += "\n" + Stream.of(summaryContent).collect(Collectors.joining(",")).toString() ;
               String file =Paths.get(EngineConfiguration.getInstance().outPath, clazz.getName() + "-test-summary.csv").toString() ;
               try {
            	   IOUtils.saveTxtToFile(file, summary) ;
               }
               catch (Exception e) {
                   throw new IllegalStateException("Failed to save test summary of " + clazz.getName(), e);
               }
            }
        } catch (Exception | LinkageError | AssertionError failure) {
            logger.error("Run failed; generated tests are partial and must not be scored as a completed run.");
            try {
                generator.writeToFile(outPath);
            } catch (Exception | LinkageError | AssertionError cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * Run the dynamic symbolic execution engine on the current class.
     */
    private void run() throws Exception {
        overallDeadline = timeBudget > 0 ? System.currentTimeMillis() + timeBudget : Long.MAX_VALUE;

        // Concrete-driven is run one method at a time, while symbolic-driven is run on
        // all methods at once
        if (concreteDriven) {
            // Sort methods by name to ensure consistent ordering
            JavaSootMethod[] muts = new JavaSootMethod[staticMuts.size() + nonStaticMuts.size()];
            int i = 0;
            for (JavaSootMethod method : staticMuts) {
                muts[i++] = method;
            }
            for (JavaSootMethod method : nonStaticMuts) {
                muts[i++] = method;
            }

            executionDeadline = overallDeadline;
            ConcreteSearchStrategy strategy = searchStrategy.toConcrete();
            Arrays.sort(muts, (m1, m2) -> m1.getName().compareTo(m2.getName()));
            for (i = 0; i < muts.length; i++) {
                JavaSootMethod method = muts[i];
                if (System.currentTimeMillis() >= overallDeadline) {
                    logger.info("Time budget exceeded while iterating methods under test, stopping...");
                    break;
                }

                // Set execution deadline for this method
                if (timeBudget > 0) {
                    // Divide remaining time budget by the number of remaining methods
                    long remainingBudget = overallDeadline - System.currentTimeMillis();
                    // If not last method, can use a little more time than just dividing
                    // by the number of remaining methods, because following methods may be
                    // easier to execute
                    double factor = muts.length - i > 1 ? 1.1 : 1.0;
                    long methodBudget = (long) (remainingBudget * factor / (muts.length - i));
                    executionDeadline = System.currentTimeMillis() + methodBudget;
                    logger.info("Time budget for method {}: {}", method.getName(), methodBudget);
                }

                try {
                    strategy.reset();
                    logger.info("Processing method: {}", method.getName());
                    runConcreteDriven(method, strategy);
                } catch (SearchExecutionException e) {
                    throw e;
                } catch (Exception e) {
                    logger.error("Error processing method {}: {}", method.getName(), e.getMessage());
                    logger.debug("Error stack trace: ", e);
                    // Continue with the next method even if an error occurs
                }
            }
        } else {
            // Reserve 30% of the time budget for generating test cases for unfinished paths
            long reservedTime = (long) (timeBudget * 0.3);
            executionDeadline = timeBudget > 0 ? System.currentTimeMillis() + (timeBudget - reservedTime)
                    : Long.MAX_VALUE;

            SymbolicSearchStrategy strategy = searchStrategy.toSymbolic();
            initializeSymbolic(strategy);
            runSymbolicDriven(strategy, ctorSoot);

            // If any unfinished states are still in the strategy, generate test cases
            Collection<SymbolicState> states = strategy.getAll();
            if (states.isEmpty()) {
                return;
            }
            // there may be some symbolic states left in search strategy, from which
            // we can try to generate tests from. However if the engine is configured 
            // to add only coverage-contributing tests, and all coverage targets are already 
            // covered, then we are done. 
            if (EngineConfiguration.getInstance().minimalisticTestSuite
            	&& CoverageTracker.getInstance().numberOfStillUnCoveredBranches() 
            		+ CoverageTracker.getInstance().numberOfStillUnCoveredStmts() == 0) {
            	return ;
            }
            // generating extra tests from left over symbolic states:
            logger.info("Generating test cases for remaining states in search strategy");
            for (SymbolicState state : states) {
                // Check if we are over the time budget
                if (System.currentTimeMillis() >= overallDeadline) {
                    logger.info("Time budget exceeded while evaluating unifinished paths, stopping...");
                    break;
                }
                // also, stop if the engine is configured to do the verificagion-mode, 
                // with k>0, and it has generated k test-cases with violations:
                if (EngineConfiguration.getInstance().verificationMode > 0 &&
                		generator.getNumberOfViolationFound() >= EngineConfiguration.getInstance().verificationMode) {
                	break;
                }
                if (!state.isInfeasible()) {
                	generateTestCase(state.returnToRootCaller());
                }
            }
        }
    }
    
    //int j = 1 ;
    int k = 1 ;
    
    /**
     * Generate a test case for the given method and symbolic state.
     */
    private void generateTestCase(SymbolicState state) {
        try {
        	//System.out.println("### test " + j) ; j++ ;
        	Optional<ArgMap> argMap = validator.evaluate(state);
            if (argMap.isPresent()) {
            	//System.out.println("--- test " + k) ; k++ ;
            	//System.out.println(">>> " + state.getMethod().getName()) ;
            	//System.out.println(">>> argMap: " + argMap) ;
            	
            	InstructionHistory history = rerunToGetHistory(state.getMethod(), argMap.get()) ;
            	//InstructionHistory history = new InstructionHistory() ;
            	//System.out.println("history: " + history.getHistory()) ;
            	var hasNewCov = CoverageTracker.getInstance().registerCoveregeByTesting(state, history) ;
            	if (hasNewCov || state.isExceptionThrown() || ! EngineConfiguration.getInstance().minimalisticTestSuite) {
                	/*
                	System.out.println("    argmap: " + argMap.get()) ;
                	for (var name : argMap.get().getArgsNames()) {
                		var o = argMap.get().get(name) ;
                		if (o != null && o.getClass().isArray()) {
                			var o_ = (Object[]) o ;
                    		System.out.println("    " + name + " -> " + o_.length) ; 
                		}
                	}
                	*/  
                	generator.addMethodTestCase(state.getMethod(), ctorSoot, argMap.get());           	
                }
            }
        } catch (ReplayBudget.Exceeded e) {
            recordReplayAbort(state.getMethod(), e);
        } catch (Exception e) {
            logger.error("Error generating test case for method {}: {}, {}", state.getMethod().getName(), e.getClass().getName(), e.getMessage());
            logger.info("Error stack trace: ", e);
            
        }
    }

    /**
     * Initialize the symbolic search strategy with the right initial states.
     */ 
    public void initializeSymbolic(SymbolicSearchStrategy searchStrategy) {
        // If methods under test include non-static methods, need to execute constructor
        // as well
        if (!nonStaticMuts.isEmpty()) {
        	SymbolicState state = new SymbolicState(ctorSoot, ctorCfg) ;
            searchStrategy.add(state);
        }

        // For static methods, we can start directly with the target method
        for (JavaSootMethod method : staticMuts) {
            // If the method is static, we can start directly with the target method
            SymbolicState state = new SymbolicState(method, analyzer.getCFG(method));
            state.switchToMethodState();
            searchStrategy.add(state);
        }
    }
    

    /**
     * Run symbolic-driven DSE on the given method.
     * Returns the final state if this is a symbolic replay for concrete-driven DSE.
     * 
     * @param searchStrategy The search strategy to use
     * @param targetMethod   The method to switch to after the constructor (only
     *                       used if running concrete-driven)
     * @return The (first) final state found if running concrete-driven, emtpy
     *         optional otherwise
     */
    private Optional<SymbolicState> runSymbolicDriven(SymbolicSearchStrategy searchStrategy,
            JavaSootMethod targetMethod) {
    	
        SymbolicState current;
              
        while ((current = searchStrategy.next()) != null) {
        	
            // stop the search if the engine is configured to add only coverage-
            // contributing tests, and all coverage targets are already covered.
            if (EngineConfiguration.getInstance().minimalisticTestSuite
            	&&  CoverageTracker.getInstance().allCoverageTargetsCompleted()) {
            	return concreteDriven ? Optional.of(current) : Optional.empty();
            }
            // stop the search if the engine is configured to do the verificagion-mode, 
            // with k>0, and it has generated k test-cases with violations:
            if (EngineConfiguration.getInstance().verificationMode > 0 &&
            		generator.getNumberOfViolationFound() >= EngineConfiguration.getInstance().verificationMode) {
            	return concreteDriven ? Optional.of(current) : Optional.empty();
            }
            	
        	
            // Check if we are over the time budget
            if (System.currentTimeMillis() >= executionDeadline) {
                if (concreteDriven) {
                    return Optional.of(current);
                }

                // Check if number of states in search strategy is small compared to the time
                // left before overall deadline
                // If so, we can keep going for a bit longer
                long remainingTime = overallDeadline - System.currentTimeMillis();
                if (searchStrategy.size() * 8L > remainingTime) {
                    logger.info("Time budget exceeded during symbolic-driven execution, stopping...");
                    return concreteDriven ? Optional.of(current) : Optional.empty();
                }

                logger.info("Extending time budget for symbolic-driven execution...");
                executionDeadline = System.currentTimeMillis() + remainingTime / 2;
            }
            

            logger.debug("Current state: {}", current);
            if (!current.isCtorState() && current.isFinalState() || current.getDepth() >= maxDepth) {
                // For concrete-driven, we only care about one final state, so we can stop
                if (concreteDriven) {
                    return Optional.of(current);
                } else if (!current.isInfeasible()) {
                    // For symblic-driven, generate test case
                	generateTestCase(current.returnToRootCaller());
                }
                continue;
            }
            
            //System.out.println(">>> about to exec: " + current.getStmt()) ;
            //System.out.println("** STATE: " + current) ;
            
            // Symbolically execute the statement of the current symbolic state
            List<SymbolicState> newStates = symbolic.step(current, concreteDriven);   
            
            //System.out.println("** AFTER, #next: " + newStates.size()) ;
            //System.out.println("** AFTER: " + current) ;
            
            
            // For ctor states, check for final states from which we can switch to the
            // target method(s)
            if (current.isCtorState()) {
                for (SymbolicState state : newStates) {
                    if (state.isFinalState()) {
                        // If the state is an exception-throwing state, generate test case and stop
                        // exploring (i.e., don't go into the target method)
                        if (state.isExceptionThrown() || state.isInfeasible()) {
                            if (concreteDriven) {
                                return Optional.of(state);
                            } else if (!clazz.isEnum() && !state.isInfeasible()) {
                                generateTestCase(state);
                            }
                            continue;
                        }

                        // Since ctor is now done, we can switch to the target method
                        state.switchToMethodState();

                        // For concrete-driven, store this final ctor state for reuse and switch to the
                        // single target method being replayed right now
                        if (concreteDriven) {
                            initStates.put(TraceManager.hashCode(state.getMethodSignature()), state.clone());
                            state.setMethod(targetMethod, analyzer.getCFG(targetMethod));
                        }
                        // For symbolic-driven, we can switch to any of the target methods
                        else {
                            for (int i = 0; i < nonStaticMuts.size(); i++) {
                                JavaSootMethod target = nonStaticMuts.get(i);
                                target.getExceptionSignatures() ;
                                // Clone state, except for the last one
                                SymbolicState newState = i == nonStaticMuts.size() - 1 ? state : state.clone();
                                
                                // add the branch-hist of the constructor to indirect branch-hist of the new state:
                                HCFG hcfg = CoverageTracker.getInstance().getHCFG(state.getMethod()) ;
                                if (hcfg != null) {
                                	newState.getIndirectBranchHistories().add(new Pair<>(hcfg,state.getBranchHistory())) ;
                                	newState.getBranchHistory().clear() ;
                                }
                                
                                newState.setMethod(target, analyzer.getCFG(target));
                                searchStrategy.add(newState);

                            }
                            continue;
                        }
                    }

                    searchStrategy.add(state);
                }
            }
            // For non-ctor states, we can simply add the new states to the search strategy
            else {
            	searchStrategy.add(newStates);
            }
        }

        return Optional.empty();
    }
    

    /** Run symbolic-driven DSE to replay a concrete execution. */
    public Optional<SymbolicState> runSymbolicReplay(JavaSootMethod method) {
        
    	SymbolicState initState;

        // For static methods, start at the target method
        if (method.isStatic()) {
            initState = new SymbolicState(method, analyzer.getCFG(method));
            initState.switchToMethodState();
        }
        // Otherwise, start with constructor
        else {
            // If the ctor has been explored along this path before, we can reuse, otherwise
            // we start from scratch
            int pathHash = TraceManager.hashCode(ctorSoot.getSignature());
            if (initStates.containsKey(pathHash)) {
                initState = initStates.get(pathHash).clone();
                initState.setMethod(method, analyzer.getCFG(method));
            } else {
                initState = new SymbolicState(ctorSoot, ctorCfg);
            }
        }

        replayStrategy.add(initState);
        // Run symbolic execution
        Optional<SymbolicState> finalState = runSymbolicDriven(replayStrategy, method);
        replayStrategy.reset();
        return finalState;
    }

    /** Run concrete-driven DSE on the given method. */
    private void runConcreteDriven(JavaSootMethod method, ConcreteSearchStrategy searchStrategy) throws Exception {
        Method javaMethod = analyzer.getJavaMethod(method.getSignature(), instrumented);
        ArgMap argMap = new ArgMap();
        boolean deadlineReached = false;

        while (true) {
            // Check time budget
            if (System.currentTimeMillis() >= executionDeadline) {
                logger.info("Time budget exceeded during concrete-driven execution, stopping...");
                deadlineReached = true;
                break;
            }

            // Concrete execution followed by symbolic replay
            TraceManager.clearEntries();
            //System.out.println(">>> concrete exec " + javaMethod.getName() + ": " + argMap.getArgsNames()) ;
            concrete.execute(ctor, javaMethod, argMap);
            Optional<SymbolicState> finalState = runSymbolicReplay(method);
            logger.debug("Replayed state: {}", finalState.isPresent() ? finalState.get() : "none");

            if (finalState.isPresent()) {
                boolean isNew = searchStrategy.add(finalState.get());
                // Only add a new test case if this path has not been explored before
                // Note: this particular check will catch only certain edge cases that are not
                // caught by the search strategy
                
                if (isNew) {
                    try {
                        var history = rerunToGetHistory(method, argMap);
                        boolean hasNewCov = CoverageTracker.getInstance().registerCoveregeByTesting(finalState.get(), history);
                        if (hasNewCov || !EngineConfiguration.getInstance().minimalisticTestSuite) {
                            // The concrete executor has already populated argMap.
                            generator.addMethodTestCase(method, ctorSoot, argMap);
                        }
                    } catch (ReplayBudget.Exceeded e) {
                        recordReplayAbort(method, e);
                    }
                }
            }

            if (deadlineReached) {
                break;
            }

            Optional<Pair<Model, SymbolicState>> candidate = searchStrategy.next(validator, executionDeadline);
            // If we cannot find a new path condition, we are done
            if (candidate.isEmpty()) {
                break;
            }

            // If a new path condition is found, evaluate it to get the next set of
            // arguments which will be used in the next iteration for concrete execution
            Pair<Model, SymbolicState> pair = candidate.get();
            argMap = validator.evaluate(pair.getFirst(), pair.getSecond().returnToRootCaller(), false);
            
        }
    }

    private void recordReplayAbort(JavaSootMethod method, ReplayBudget.Exceeded limit) {
        replayAborts.merge(limit.reason(), 1, Integer::sum);
        logger.debug("Discarding bounded candidate for {}: {}", method.getName(), limit.reason());
    }
    
    /**
     * Re-ren a test to obtain the history of instructions passed during the execution.
     * 
     * The given ArgMap should be fully specifying the concrete values of the parameters
     * for the method-under-test.
     * @throws NoSuchMethodException 
     * @throws ClassNotFoundException 
     */
    public InstructionHistory rerunToGetHistory(JavaSootMethod method, ArgMap argMap) throws ClassNotFoundException, NoSuchMethodException {
    	
    	// need the instrumented version of the method to produce the trace, to be able
    	// to replay:
    	Method instrumentedJavaMethod = null ;
    	
    	//System.out.println(">>> method: " + method.getSignature()) ;
        
    	if (! method.getName().equals("<init>")) {
    		instrumentedJavaMethod = analyzer.getJavaMethod(method.getSignature(), instrumented);
    	}    	
    	//System.out.println(">>> instrumented.") ;
    	TraceManager.clearEntries();
        //System.out.println(">>> concrete exec " + javaMethod.getName() + ": " + argMap.getArgsNames()) ;
    	// run concretely to obtain the trace:
    	
        try (ReplayBudget budget = ReplayBudget.open(EngineConfiguration.getInstance().maxReplaySteps, overallDeadline)) {
            var result = concrete.execute(ctor, instrumentedJavaMethod, argMap);
            // Unwrap only the reflection call. The CUT owns any further exception causes,
            // which can legitimately form a cycle or describe an error it already handled.
            Throwable failure = result.exception();
            if (failure instanceof InvocationTargetException invocation) failure = invocation.getTargetException();
            if (failure instanceof ReplayBudget.Exceeded limit) throw limit;
            if (failure instanceof StackOverflowError) throw new ReplayBudget.Exceeded("stack_overflow");

            // System.out.println(">>> trace to REPLAY: " + TraceManager.traceEntries) ;
            // now run symbolically to obtain the sequence of instructions

            // construct the initial symbolic state:
            SymbolicState initState ;
            // For static methods, start at the target method
            if (method.isStatic()) {
                initState = new SymbolicState(method, analyzer.getCFG(method));
                initState.switchToMethodState();
            }
            else {
                initState = new SymbolicState(ctorSoot,ctorCfg);
            }

            InstructionHistory history = new InstructionHistory() ;
            JavaSootMethod M = null ;
            //StmtGraph cfg = null ;
            SymbolicState currentSymbolicState = initState ;
            boolean replayModeOn = true ;
            // replay the execution step by step, until we get to a final state,
            // which is not a constructor state:
            while (currentSymbolicState.isCtorState() || !currentSymbolicState.isFinalState()) {
                budget.replayStep();
                JavaSootMethod M_ = currentSymbolicState.getMethod() ;
                if (M==null || M_ != M) {
                    history.addMethodSwitch(M_);
                    M = M_ ;
                    //cfg = currentSymbolicState.getCFG() ;
                }
                history.addInstruction(currentSymbolicState.getStmt());

                // Symbolically execute the statement of the current symbolic state
                // System.out.println("### cur stmt: " + currentSymbolicState.getStmt()) ;
                List<SymbolicState> newStates = symbolic.step(currentSymbolicState,replayModeOn) ;
                if (newStates.isEmpty()) {
                    break ;
                }
                if (newStates.size() > 1) {
                    logger.warn("Replaying a concrete execution leads to a symbolic state with multiple successors! Executed instr: " + currentSymbolicState.getStmt());
                }
                SymbolicState nextState = newStates.getFirst() ;
                if (currentSymbolicState.isCtorState()) {
                    if (nextState.isFinalState()) {
                        if (nextState.isExceptionThrown())
                            break ;
                        nextState.switchToMethodState();
                        nextState.setMethod(method, analyzer.getCFG(method));
                    }
                }
                //if (nextState == currentSymbolicState)
                //	break ;
                currentSymbolicState = nextState ;
            }
            return history ;
        }
    }
    
    /**
     * Just for debugging. "Printing" the cfg of a method. That is,
     * for every stmt in the method's body, the will print its successors.
     * It will also indicate if the stmt is the start or exit node of the
     * cfg.
     */
    void debugPrintCFGs(JavaSootMethod method) {
    	StmtGraph cfg = method.getBody().getStmtGraph() ;
    	System.out.println(">>> CFG of " + method.getName()) ;
    	int k = 1 ;
    	for (Stmt stmt : method.getBody().getStmts()) {
    		var sucs = cfg.successors(stmt) ;
    		var esucs = cfg.exceptionalSuccessors(stmt) ;
    		System.out.print("  " + k + ": " + stmt 
    				+ ", #sucs=" + sucs.size() 
    				+ ", #exceptional-sucs=" + esucs.size()) ;
    		if (stmt == cfg.getStartingStmt()) 
    			System.out.print(" [START]") ;
    		boolean handlersHead = stmt != cfg.getStartingStmt() 
    				&& cfg.getEntrypoints().contains(stmt) ;
    		if (handlersHead)
    			System.out.println(" [HANDLER HEAD]") ;
    		if (sucs.size() == 0)
    			System.out.println(" [EXIT]") ;
    		System.out.println("") ;
    		
    		for (var z : sucs) {
    			System.out.println("     --> " + z) ;
    		}
    		for (var z : esucs.values()) {
    			System.out.println("     xx> " + z) ;
    		}
    		k++ ;
    	}
    }
 }
