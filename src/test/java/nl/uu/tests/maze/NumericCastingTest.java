package nl.uu.tests.maze;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
 * To test how MAZE handles casting between numeric types.
 */
public class NumericCastingTest {
	
	static public class CUT_NumericCasting {
		
		
		public String intToFloat(int x) {
			float x_ = (float) x ;
			if (-4.33 < x_ && x_ < -3.33)
				return "xLTGT intToFloatSuccess: " + x_ ;
			return "else-branch" ;
		}
		
		public String intToFloat_xy(int x, float y) {
			float x_ = (float) x ;
			if (y < x_ && x_ < y+1)
				return"xy_LTGT intToFloatSuccess: " + x_ ;
			return "else-branch" ;
		}
		
		
	
		// MAZE can't solve the cond x_ < 4 below. TODO
		public String floatToInt_LT(float x) {
			int x_ = (int) x ;
			if (x_ < -4 )
				return "xLT floatToIntSuccess: " + x + "-->" + x_ ;
			return "else-branch" ;
		}
		
		// MAZE can solve x_ > -4, BUT can't solve the else part. TODO
		public String floatToInt_GT(float x) {
			int x_ = (int) x ;
			if (x_ > -4)
				return "xGT floatToIntSuccess: " + x + "-->" + x_ ;
			return "xGT else-branch" ;
		}
		
		// MAZE can't solve the then-condition. TODO
		public String floatToInt_LTGT(float x) {
			int x_ = (int) x ;
			if (-6 < x_ && x_ < -4 )
				return "xLTGT floatToIntSuccess: " + x + "-->" + x_ ;
			return "else-branch" ;
		}
		
		public String floatToInt_xy_LT(float x, int y) {
			int x_ = (int) x ;
			if (x_ < y+4)
				return "xy_LT floatToIntSuccess: " + x + "-->" + x_ ;
			return "else-branch" ;
		}
		
		public String floatToInt_xy_GT(float x, int y) {
			int x_ = (int) x ;
			if (x_ > y)
				return "xy_GT floatToIntSuccess: " + x + "-->" + x_ ;
			return "else-branch" ;
		}
		
		// MAZE cant always solve the then-cond. Depends on other formulas currently in z3 
		// context, e.g. if this is the only target method, it is solvable, but if we include
		// all the other methods above z3 can solve it.
		public String floatToInt_xy_LTGT(float x, int y) {
			int x_ = (int) x ;
			if (y < x_ && x_ < y+2)
				return "xy_LTGT floatToIntSuccess: " + x + "-->" + x_ ;
			return "else-branch" ;
		}
		

	}
	
	
	String binClassesDir = "./target/test-classes" ;
	String outputDir = "./tmp" ;
	
	@SuppressWarnings("rawtypes")
	Class CUT     = CUT_NumericCasting.class ;
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
	void test_NumCasting1() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--classname=" + CUT.getName() 
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true" // don't close z3 context, or else the next tests will crash
				      + sp
				      ;
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    //assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("10"))) ;
	    
	    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
	    
	    
	    assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("xLTGT intToFloatSuccess"))) ;
	    
	    assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("xy_LTGT intToFloatSuccess"))) ;

	    //assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    //		&& z.contains("xLT floatToIntSuccess"))) ;
	    
	    assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("xGT floatToIntSuccess"))) ;
	    //assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    //		&& z.contains("xGT else-branch"))) ;
	    
	    //assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    //		&& z.contains("xLTGT floatToIntSuccess"))) ;
	    
	    assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("xy_LT floatToIntSuccess"))) ;
	    
	    assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("xy_GT floatToIntSuccess"))) ;

	    //assertTrue(outputFile.matchAnyLine(z -> ! Preds.isCommentLine(z) 
	    //		&& z.contains("xy_LTGT floatToIntSuccess"))) ;
	    
	}

}
