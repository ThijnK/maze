package nl.uu.maze.execution;

import picocli.CommandLine.Option;

/**
 * Contains configuration that influences how the DSE engine works.
 * We will maintain the configuration globally with a Singleton pattern.
 * <p>
 */
public class EngineConfiguration {
	
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
     * When true, when a test throws an exception that is not declared as expected 
     * exception by the method under test will be propagated. So, it will not be asserted as 
     * an expected exception by the test oracle. Note that this means the test will 
     * then fail (a potential bug is found by Maze).
     * 
     * <p>default: false.
     */
    public boolean propagateUnexpectedExceptions = false ;
    
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
