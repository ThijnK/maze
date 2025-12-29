package nl.uu.maze.execution;

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
