package nl.uu.tests.maze;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.DSEController;
import nl.uu.maze.main.cli.MazeCLI;
import nl.uu.maze.util.Z3ContextProvider;
import nl.uu.tests.maze.FloatNormalAndSpecialValuesGenerationTest.CUT_FloatValuesGeneration;
import picocli.CommandLine;

/**
 * To test MAZE ability to find exceptional execution flow, such as an execution
 * that leads to null-dereference exception being thrown.
 */
public class ExceptionalFlowFindingTest {
	
	static public class CUT_ExceptionalFlowFinding {
		
		public int arrayIndexOutOfBound(int k) {
			int[] a = {0,1,2} ;
			if (k<a.length) {
				return a[k] ;
			}
			else
				return -1 ;
		}
		
		public float divByZero(float x, float y) {
			// MAZE does not detect division by zero
			// TODO
			return x/(y+1) ;
		}
		
		public int nullDerefInteger(Integer x) {
			// Maze has an issue to generate a non-null Integer, possibly
			// because it calls a wrong constructor Integr(str).
			// TODO
			return x+1 ;
		}

		
		public int nullDerefString(String x) {
			// MAZE cannot generate null string
			// TODO
			return x.length() ;
		}
		
		public String nullDerefObject(Object x) {
			return x.toString() ;
		}
		
	}
	
	String binClassesDir = "./target/test-classes" ;
	String outputDir = "./tmp" ;	
	Class CUT     = CUT_ExceptionalFlowFinding.class ;
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
		TestUtils.removeFile(Path.of(outputDir, CUT.getSimpleName() + "Test.java"));
	}
	
	//@AfterAll  
	static void cleanup() {
		// ... does not work, will cause other test classes invoking MAZE to crash
		Z3ContextProvider.close();
	}
	
	@Test
	void test_finding_exceptional_flow() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--classname=" + CUT.getName()
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true" // don't close z3 context, or else the next tests will crash
				      + sp
				      ;
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("8"))) ;
	    
	    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
	    
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& Preds.isAssertThrowsLine(ArrayIndexOutOfBoundsException.class,z))) ;

	    // division by zero detection does not work, yet:
	    //assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    //		&& Preds.isAssertThrowsLine(ArithmeticException.class,z))) ;
	    
	    assertEquals(2, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& Preds.isAssertThrowsLine(NullPointerException.class,z))) ;

	    
	}

}
