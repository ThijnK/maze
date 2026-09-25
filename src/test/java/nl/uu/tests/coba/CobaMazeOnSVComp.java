package nl.uu.tests.coba;

import nl.uu.maze.main.Application;
import nl.uu.maze.main.cli.MazeCLI;
import picocli.CommandLine;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

//  For trying out Maze on SV-comp bm-classes
public class CobaMazeOnSVComp {
	
	@Test
	void coba_Maze() {
				
		String[] args_ = { "--help" } ;
		
		String cobabenchPath = "../my_simple_bench" ;

		// classes from svcomp-bm:
		
		//String CUT = "cobabench.svcomp.float_nonlinear_calculation.Bessel" ;
		//String CUT = "cobabench.svcomp.float_nonlinear_calculation.Conflict" ;// challenge
		//String CUT = "cobabench.svcomp.float_nonlinear_calculation.Euler" ; // challenge
		//String CUT = "cobabench.svcomp.float_nonlinear_calculation.Optimization" ;	// challenge!
		//String CUT = "cobabench.svcomp.MinePump.spec1_5_product1.Actions" ;			
		//String CUT = "cobabench.svcomp.algorithms.BellmanFord_FunSat01" ;
		//String CUT = "cobabench.svcomp.algorithms.BellmanFord_FunUnsat02" ;
		String CUT = "cobabench.svcomp.algorithms.RedBlackTree_MemSat01.RedBlackTree_MemSat01" ; // challenging
		//String CUT = "cobabench.svcomp.algorithms.RedBlackTree_MemSat01.RedBlackTreeNode" ; 
		              
		String sp = " " ;

		String argz =   "--classpath=" + cobabenchPath + "/target/classes"
				      + sp + "--class-name=" + CUT
				      + sp + "--output-path=" + cobabenchPath + "/src/test/java/"
				      // + sp + "-j=JUnit4"
				      //+ sp + "-s=RPS -u=UH " 
				      //+ sp + "-m=treeDelete"
				      + sp + "-s=COS"
				      //+ sp + "-s=BFS"
				      //+ sp + "--concrete-driven=true"
				      + sp + "--minimization=true"
				      + sp + "-b=120"
				      // + sp + "-l=OFF" // logging
				      //+ sp + "--max-depth=2000"
				      + sp + "--constrain-fp-params-to-normal-numbers=true"
				      //+ sp + "--suppress-regression-oracles=false"
				      //+ sp + "--propagate-unexpected-exceptions=true"
				      + sp
				      ;

		args_ = argz.split(" ") ;
		
		// Application.main(args_);  --> this call System.exit() which causes Maven test runner to crash
		
		// we'll do this instead, which is what the main() above does:
        int exitCode = new CommandLine(new MazeCLI()).execute(args_);
    }

}
