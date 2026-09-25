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
import nl.uu.tests.maze.CUTs.CUT_NumericCasting;
import picocli.CommandLine;

/**
 * To test how MAZE handles casting between numeric types.
 */
public class NumericCastingTest {
	
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
				      + sp + "--class-name=" + CUT.getName()
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
