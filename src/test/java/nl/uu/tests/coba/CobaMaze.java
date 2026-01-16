package nl.uu.tests.coba;

import nl.uu.maze.main.Application;
import nl.uu.maze.main.cli.MazeCLI;
import picocli.CommandLine;

import org.junit.jupiter.api.Test;

// Just for trying out Maze-application, for convenience, invoked from here
public class CobaMaze {
	
	@Test
	void coba_Maze() {
		
		String[] args_ = { "--help" } ;
		
		String cobabenchPath = "../my_simple_bench" ;
		String CUT = "cobabench.TriangleClassifier2" ;
		//String CUT = "cobabench.TriangleClassifier3" ;
		//String CUT = "cobabench.CobaBranches" ;
		String sp = " " ;

		String argz =   "--classpath=" + cobabenchPath + "/target/classes"
				      + sp + "--classname=" + CUT 
				      + sp + "--output-path=" + cobabenchPath + "/src/test/java/"
				     // + sp + "-j=JUnit4"
				      //+ sp + "-s=RPS -u=UH " 
				      + sp + "-s=BFS"
				      + sp + "-b=240"
				      + sp + "--constrain-FP-params-to-normal-numbers=true"
				      + sp + "--surpress-regression-oracles=false"
				      + sp + "--propagate-unexpected-exceptions=true"
				      + sp
				      ;

		args_ = argz.split(" ") ;
		
		// Application.main(args_);  --> this call System.exit() which causes Maven test runner to crash
		
		// we'll do this instead, which is what the main() above does:
        int exitCode = new CommandLine(new MazeCLI()).execute(args_);
    }

}
