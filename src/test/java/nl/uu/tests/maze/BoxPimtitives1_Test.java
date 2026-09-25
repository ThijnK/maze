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
import nl.uu.tests.maze.CUTs.CUT_BoxedPrimitiveUnconstrained;
import nl.uu.tests.maze.CUTs.CUT_IntegerLong;
import picocli.CommandLine;

/**
 * Test MAZE handling of Boxed-primitive types like Integer and Long parameters
 * in the case where they are unconstrained.
 */
public class BoxPimtitives1_Test {
	
	String binClassesDir = "./target/test-classes" ;
	String outputDir = "./tmp" ;	
	Class CUT     = CUT_BoxedPrimitiveUnconstrained.class ;
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
	
	
	@Test
	void test0() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--class-name=" + CUT.getName()
				      + sp + "--output-path=" + outputDir
				      + sp + "--do-not-close-z3-context=true" // don't close z3 context, or else the next tests will crash
				      + sp + "--constrain-fp-params-to-normal-numbers=true"
				      + sp
				      ;
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("11"))) ;
	    
	    var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg0 = 2;"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("expected = 14;"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg0 = 2L;"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("expected = 14L"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg0 = 2.0F;"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("expected = 14.1F"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("marg0 = 2.0;"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("expected = 14.5;"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("expected = true"))) ;
	    
	    assertEquals(1, outputFile.countMatchingLines(z -> ! Preds.isCommentLine(z) 
	    		&& z.contains("expected = false"))) ;
	    
	}

}
