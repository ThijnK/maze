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
import nl.uu.tests.maze.CUTs.CUT_withConsolePrint;
import picocli.CommandLine;

/**
 * Test MAZE handling of IO writes like System.out.print.
 */
public class SkipIOWrites_Test {
	
	String binClassesDir = "./target/test-classes" ;
	String outputDir = "./tmp" ;	
	Class<CUT_withConsolePrint> CUT = CUT_withConsolePrint.class ;
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
	void test_no_skip() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--class-name=" + CUT.getName()
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true" // don't close z3 context, or else the next tests will crash
				      + sp + "--skip-IO-writes=false"
				      + sp + "--export-jimple=log"
				      + sp
				      ;
		
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("1"))) ;
	    
	    //var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
  	    
	}
	
	@Test
	void test_skip() throws IOException {

		String argz =   "--classpath=" + binClassesDir
				      + sp + "--class-name=" + CUT.getName()
				      + sp + "--output-path=" + outputDir 
				      + sp + "--do-not-close-z3-context=true" // don't close z3 context, or else the next tests will crash
				      + sp
				      ;
		
	    int exitCode = new CommandLine(new MazeCLI()).execute(argz.split(" ") );
	    
	    assertTrue(interceptor.anyMatch(msg -> msg.contains("#generated") && msg.contains("3"))) ;
	    
	    //var outputFile = new TxtFileContent(Path.of(outputDir, CUT.getSimpleName() + "Test.java")) ;
  	    
	}

}
