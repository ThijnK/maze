package nl.uu.tests.maze;

/**
 * Provide a bunch of handy predicates.
 */
public class Preds {
	
	static public boolean isCommentLine(String z) { return z.strip().startsWith("//") ; }
	
	static public boolean isAssertThrowsLine(Class<? extends Throwable> exceptionType, String z) {
		return z.contains("assertThrows") && z.contains(exceptionType.getSimpleName() + ".class") ;
	}

}
