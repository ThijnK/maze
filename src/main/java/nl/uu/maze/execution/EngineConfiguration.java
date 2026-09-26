package nl.uu.maze.execution;

import java.util.Random;

import picocli.CommandLine.Option;

/**
 * Contains configuration that influences how the DSE engine works.
 * We will maintain the configuration globally with a Singleton pattern.
 * <p>
 */
public class EngineConfiguration {
    /** Maximum trace entries and symbolic steps per test-candidate history replay. */
    public int maxReplaySteps = 10000;

	
	/**
	 * If true, the symbolic solver will add a constraint to every method
	 * parameter x of floating-number type, that it should be a normal number
	 * (so, not infinity nor NaN).
	 * 
	 * <p>Default: false.
	 */
	public boolean constrainFPNumberParametersToNormalNumbers = false ;
	
	/**
	 * When true generated regression oracles in the test-cases will be commented out.
	 * 
	 * <p>default: false.
	 */
    public boolean surpressRegressionOracles = false ;

    /** 
     * This allows MAZE to changed the state/fields of the CUT by using reflection.
     * Normally MAZE only sets the CUT fields when its constructor was invoked. So,
     * if the constructor does not set a field x, it will have its Java default value,
     * and MAZE will leave it like that. While the CUT may have setters, MAZE does
     * not currently use them (to limit multiplying paths that have to be explored).
     * <p>This flag allows MAZE to set the value of CUT's fields through reflection.
     * Note that this is done by setting the initial value of CUT's fields to symbolic
     * variables, rather than setting them to the their default values. This may lead
     * to a test being generated that is actually not a feasible scenario on the CUT's
     * actual use.
     * 
     * <p>Default: false.
     */
    public boolean allowCUTfieldschangeByReflection = false ;
    
    /**
     * When true, when a test throws an exception that is not declared as expected 
     * exception by the method under test, the exception will be propagated. So, it 
     * will not be asserted as an expected exception by the test oracle. Note that this means the test will 
     * then fail (a potential bug is found by Maze).
     * 
     * <p>default: false.
     */
    public boolean propagateUnexpectedExceptions = false ;
    
    /**
     * If the value is k != 0, MAZE will be in the verification mode. In this mode, when a test
     * throws an exception that is not declared as expected exception by the method under test,
     * the exception will be propagated. So, when the test is ran by Junit, it will result in 
     * a violation. This is achieved by forcing the flag propagateUnexpectedExceptions to true.
     * 
     * <p>Additionally, only test-cases that will cause violations (as defined above) will
     * be generated, and only up it k test cases. So, setting k=1 will cause MAZE to stop after
     * the first found violation. If k<0, then all violating test cases will be generated 
     * (bound by given time budget of course).
     * 
     * <p>default: 0.
     */
    public int verificationMode = 0 ;
    
    /**
     * When true, MAZE will actively check expressions of the form x/y and x%y, whether a
     * division or remainder by zero error is possible. 
     * 
     * <p>Note that such an error can only happen on
     * types int or long. In Java, x/0 in float results in Infinity or NaN (when x is 0)
     * when the types are float-like.
     * For other integral-type like short, there is no separate / or % operator in Java bytecode.
     * The arguments will be up-casted to e.g. int (so, the / or % will be of int type).
     * 
     * <p>Default: false.
     */
    public boolean enableDivisionByZeroChecking = false ;
    
    /**
     * When true, MAZE will generate random values for parameters of the constructor
     * and method under tests. This is only applicable in the test generation through
     * concrete-driven symbolic execution, where some parameters may be left unconstrained
     * by the symbolic constrain solving part.
     * 
     * <p>Default: false. In which case unconstrained parameters are always instantiated
     * to their default value. E.g. int-type parameter to 0. 
     * 
     */
    public boolean randomSeedingInConcreteDriven = false ;
    
    /**
     * Global random seed. All random generators in MAZE should use this seed, if it is not
     * null.
     * <p>Default: null.
     */
    public Long globalRandomSeed = null ;
    
    /**
     * Normally MAZE will explore all possible execution paths (within the given depth bounding).
     * However, if this flag is true, only tests that contribute to new coverage will be generated.
     * A test generates "new coverage" if the test executes a statement/instruction or a branch
     * that was not executed by all the tests before it. Only coverage over target methods is counted.
     * E.g. coverage over private methods is not counted.
     * <p>Default: false.
     */
    public boolean minimalisticTestSuite = false ;
    
    /**
     * The maximum size of an array to avoid memory issues trying to 
     * reconstruct really large arrays. Smaller arrays are also less stressful
     * for the back-end theorem prover. <p>Default: 20.
     */
     public int max_array_size = 20;
     
     
     /**
      * if the value k >= 1, the engine will track the coverage over elementary 
      * paths of length k as coverage targets. The length of a path is  here defined
      * as the number of edges in the path. Only intra-method paths are considered
      * as targets. That is, paths that do not cross between method-boundary.
      * The considered paths are paths at the high-level CFG of the method under
      * tests in the CUT. So, these are paths at the node-level of the HCFG, and 
      * not paths at the instruction/stmt-level.
      * 
      * <p>If k=0, no tracking is done.
      * <p>With k=2 the engine will track the coverage over edge-pairs. With
      * k=1 it will track coverage over HCFG edges, which more or less correspond
      * to the conditional branches in the program. Note that by default MAZE
      * separately always track coverage over instruction level branches.
      * 
      * <p>When k=-1, prime paths will be targeted.
      * 
      * <p>When the option {@link #minimalisticTestSuite} is enabled, and
      * the value of enablePathCoverage is k non-zero, whenever a test is found
      * that covers target path of length k, which has not been covered before,
      * the test will be actually be generated as aJUnit test-method. As there are
      * usually more paths of length k than the number of branches, this option 
      * may thus cause more tests to be generated (though on the other hand, it is
      * the also more thorough). (if k=0, then only tests that cover new instruction
      * or instruction-level branch will be generated).
      * 
      * <p>If {@link #minimalisticTestSuite} is not set, the engine will convert any 
      * completed path to a test, regardless whether it gives new coverage or not.
      * 
      * <p>Default: 0.
      */
     public int pathLengthCoverage = 0 ;
     
     
     /**
      * Some strategy e.g. PCS may remove a target path after attempts to cover them
      * seem to be failing. This is determined by the path age. In PCS this means
      * the number of engine iterations since the last best attempt to cover the path
      * (the one that gives the best partial coverage). This parameter sets the maximum
      * age before it becomes a candidate to be dropped as a target.
      * 
      * <p>-1 means there is no aging, so the target will never be dropped.
      * 
      * <p>0 means that the total number of instructions in the CUT is used to estimate
      * the age setting, which is set to 2x #instructions.
      * 
      * <p>Default: -1.
      */
     public int targetPathAging = -1 ;
     
     /**
      * If 1, the Jimple-code of every target class will be exported to a file. 
      * If -1 it will be printed to log.info. If 0 it will not be saved nor printed.
      * <p>Default: 0.
      */
     public int exportJimple = 0 ;
     
     /**
      * If 1, the high-level CFG of every target method will be exported to a dot-file.
      * If -1 it will be printed to log.info. If 0 it will not be saved nor printed.
      * <p>Default: 0.
      */
     public int exportHCFG = 0 ;
     
     /**
      * If 1, registered target paths will be exported to a file. 
      * If -1 it will be printed to log.info. If 0 it will not be saved nor printed.
      * <p>Default: 0.
      */
     public int exportTargetPaths = 0 ;
     
     /**
      * If 1, path coverage information will be exported to a file. 
      * If -1 it will be printed to log.info. If 0 it will not be saved nor printed.
      * <p>Default: 0.
      */
     public int exportPathCovInfo = 0 ;
     
     /**
      * If true will export testing statistics (coverage, if error was found etc) to a csv file.
      * <p>Default: false.
      */
     public boolean exportSummary = false ;
     
     
     public String outPath = null ;
     
    
    /**
     * Get a fresh random generator, using {@link #globalRandomSeed} as the seed, if it is
     * defined. Else unseeded random generator is returned.
     */
    public Random mkNewRandomGenerator() {
    	if (globalRandomSeed == null)
    		return new Random() ;
    	else 
    		return new Random(globalRandomSeed) ;
    }
	
	private EngineConfiguration() {
		
	}
	
	static private EngineConfiguration theEngineConfiguration ;
	
	static public synchronized EngineConfiguration getInstance() {
		if (theEngineConfiguration == null) {
			theEngineConfiguration = new EngineConfiguration() ;
		}
 		return theEngineConfiguration ;
	}

}
