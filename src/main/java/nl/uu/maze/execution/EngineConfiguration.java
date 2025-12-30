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
