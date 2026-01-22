package nl.uu.tests.maze;



import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Path;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.Level;
import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.DSEController;
import nl.uu.maze.main.cli.MazeCLI;
import nl.uu.maze.util.Z3ContextProvider;
import picocli.CommandLine;

/**
 * Testing different oracles generation mode of MAZE.
 */
public class OraclesGenerationTest {
	
	public static class CUT_oracleGeneration {
		
		public int methodWithNormalTermination(int x) {
			return x ;
		}
		
		public int methodThrowsExpectException(int x) throws ArithmeticException {
			x++ ;
			throw new ArithmeticException("I always throws ArithException.") ;
		}
		
		public int methodThrowsIllegalArgumentExcpetion(int x) {
			x++ ;
			throw new IllegalArgumentException("I refuse any x!") ;
		}
		
		public int methodThrowsUnexpectedException(int x) {
			x++ ;
			throw new Error("I always throws Error!") ;
		}
		
	}
	
	String binClassesDir = Path.of("","target","test-classes").toString() ;
	String outputDir = Path.of(".","tmp").toString() ;	
	@SuppressWarnings("rawtypes")
	Class CUT     = CUT_oracleGeneration.class ;
	String sp = " " ;
	
	LoggerInterceptor interceptor ;
	
	@BeforeEach
	void setup() {
		// make the JavaAnalyzer to drop its current instance, to force a fresh one
		// to be created:
		JavaAnalyzer.dropInstance();
		
		// setting logger interceptor:
		Logger logger = (Logger) LoggerFactory.getLogger(DSEController.class);
		this.interceptor = new LoggerInterceptor() ;
		interceptor.start(); 
		logger.addAppender(interceptor);
		logger.setLevel(Level.INFO);	
		
		// remove the output-test-file produced by MAZE:
		TestUtils.removeFile(Path.of(outputDir, CUT.getSimpleName() +  "Test.java"));
	}
	
	// @AfterAll  
	static void cleanup() {
		// ... does not work, will cause other test classes invoking MAZE to crash
		Z3ContextProvider.close(); 
	}
		
	
	@Test
	void test_oraclesEnabled_unexpectedExceptionNotPropagated() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--classname=" + CUT.getName() 
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true" // don't close z3 context, or else the next tests will crash
				      + sp
				      ;
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("4"))) ;
	    
	    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) && z.contains("assertEquals"))) ;
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(ArithmeticException.class,z))) ;
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(IllegalArgumentException.class,z))) ;
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(Error.class,z))) ;
	    
	}
	
	@Test
	void test_oraclesDisabled_unexpectedExceptionNotPropagated() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--classname=" + CUT.getName() 
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true"
				      + sp + "--surpress-regression-oracles=true"
				      + sp
				      ;
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("4"))) ;
	    
	    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
	    
	    // ordinary oracle is not commented out:
	    assertEquals(0, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) && z.contains("assertEquals"))) ;
	    assertEquals(1, outputFile.countMatchingLines(z -> Preds.isCommentLine(z) && z.contains("assertEquals"))) ;
	    
	    // expected exceptions are still asserted:
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(ArithmeticException.class,z))) ;
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(IllegalArgumentException.class,z))) ;
	    
	    // unexpected exception is propagated (not asserted), though noted in a comment
	    assertEquals(0, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(Error.class,z))) ;
	    assertEquals(1, outputFile.countMatchingLines(z -> Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(Error.class,z))) ;   
	}
	
	@Test
	void test_oraclesEnabled_unexpectedExceptionPropagated() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--classname=" + CUT.getName() 
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true"
				      + sp + "--propagate-unexpected-exceptions=true"
				      + sp
				      ;
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("4"))) ;
	    
	    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
	    
	    // ordinary oracle:
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) && z.contains("assertEquals"))) ;
	    
	    // expected exceptions are asserted:
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(ArithmeticException.class,z))) ;
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(IllegalArgumentException.class,z))) ;
	    
	    // unexpected exception is propagated (not asserted), though noted in a comment
	    assertEquals(0, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(Error.class,z))) ;
	    assertEquals(1, outputFile.countMatchingLines(z -> Preds.isCommentLine(z)
	    		&& Preds.isAssertThrowsLine(Error.class,z))) ;   
	}
	
	@Test
	void test_oraclesDisabled_unexpectedExceptionPropagated() throws IOException {

			String argz =   "--classpath=" + binClassesDir
					      + sp + "--classname=" + CUT.getName() 
					      + sp + "--output-path=" + outputDir 
					      + sp + "--do-not-close-z3-context=true"
					      + sp + "--surpress-regression-oracles=true"
					      + sp + "--propagate-unexpected-exceptions=true"
					      + sp
					      ;
		    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
		    
		    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("4"))) ;
		    
		    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
		    
		    // ordinary oracle is not commented out:
		    assertEquals(0, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) && z.contains("assertEquals"))) ;
		    assertEquals(1, outputFile.countMatchingLines(z -> Preds.isCommentLine(z) && z.contains("assertEquals"))) ;
		    
		    // expected exceptions are still asserted:
		    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
		    		&& Preds.isAssertThrowsLine(ArithmeticException.class,z))) ;
		    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
		    		&& Preds.isAssertThrowsLine(IllegalArgumentException.class,z))) ;
		    
		    // unexpected exception is propagated (not asserted), though noted in a comment
		    assertEquals(0, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z)
		    		&& Preds.isAssertThrowsLine(Error.class,z))) ;
		    assertEquals(1, outputFile.countMatchingLines(z -> Preds.isCommentLine(z)
		    		&& Preds.isAssertThrowsLine(Error.class,z))) ;   
		}
}
