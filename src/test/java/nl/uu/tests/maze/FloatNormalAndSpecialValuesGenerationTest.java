package nl.uu.tests.maze;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import nl.uu.maze.analysis.JavaAnalyzer;
import nl.uu.maze.execution.DSEController;
import nl.uu.maze.main.cli.MazeCLI;
import nl.uu.maze.util.Z3ContextProvider;
import picocli.CommandLine;

/**
 * For testing MAZE generation of normal float/double values for parameters,
 * and special values like NaN and infinity.
 */
public class FloatNormalAndSpecialValuesGenerationTest {
	
	static public class CUT_FloatValuesGeneration {
		
		public double return_x_when_not99(double x, double y, double z) {
			if (x+y>z && y+z>x && x+z>y && x==y && y==z)
				return 0 ;
			return x ;
		}
		
		public double return_posInfinity(double x) {
			if (x == Double.POSITIVE_INFINITY)
				// MAZE cannot solve this! TODO.
				return x ;
			return 0 ;
		}
		
		public float return_x_when_negInfinity(float x) {
			if (x == Float.NEGATIVE_INFINITY)
				// MAZE cannot solve this! TODO.
				return x ;
			return 0 ;
		}	
		
		public double return_NaN(double x) {
			if (x <= Double.POSITIVE_INFINITY)
				return 0 ;
			return x ;
		}
	}
	
	String binClassesDir = "./target/test-classes" ;
	String outputDir = "./tmp" ;
	
	@SuppressWarnings("rawtypes")
	Class CUT     = CUT_FloatValuesGeneration.class ;
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
	
	// Well unfortunately for now MAZE is unable to solve constraint of the form x==inf. 
	// TODO.
	@Test
	void test_generation_Infinity_NaN() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--classname=" + CUT.getName() 
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true" // don't close z3 context, or else the next tests will crash
				      + sp
				      ;
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("10"))) ;
	    
	    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
	    
	    
	    assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg")
	    		&& !z.contains("cut")
	    		&& z.contains("POSITIVE_INFINITY"))) ;
	    
	    assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg")
	    		&& !z.contains("cut")
	    		&& z.contains("NaN"))) ;
	    
	    assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("expected")
	    		&& !z.contains("cut")
	    		&& z.contains("NaN"))) ;
	    
	}
	
	@Test
	void test_supress_generation_of_Infinity_NaN() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--classname=" + CUT.getName()
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true" // don't close z3 context, or else the next tests will crash
				      + sp + "--constrain-FP-params-to-normal-numbers=true"
				      + sp
				      ;
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("9"))) ;
	    
	    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
	    
	    
	    assertFalse(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg")
	    		&& !z.contains("cut")
	    		&& z.contains("POSITIVE_INFINITY"))) ;

	    assertFalse(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg")
	    		&& !z.contains("cut")
	    		&& z.contains("NEGATIVE_INFINITY"))) ;

	    assertFalse(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg")
	    		&& !z.contains("cut")
	    		&& z.contains("NaN"))) ;
	    
	}


}
