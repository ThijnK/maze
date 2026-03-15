package nl.uu.tests.coba;

import nl.uu.maze.main.Application;
import nl.uu.maze.main.cli.MazeCLI;
import picocli.CommandLine;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

// Just for trying out Maze concrete driven SE
public class CobaConcreteDriven {
	
	@Test
	void coba_Maze() {
				
		String[] args_ = { "--help" } ;
		
		String cobabenchPath = "../my_simple_bench" ;
		//String CUT = "cobabench.TriangleClassifier2" ;
		//String CUT = "cobabench.FloatStatisticsX" ;
		//String CUT = "cobabench.SinglyLinkedListX" ;
		
		//String CUT = "cobabench.TriangleClassifier3" ;
		//String CUT = "cobabench.CobaBranches" ;
		
		// maze bm classes:
		//String CUT = "cobabench.mazebm.TriangleClassifier" ;
		//String CUT = "cobabench.mazebm.AckermannPeter" ;
		//String CUT = "cobabench.mazebm.BinarySearch" ;
		//String CUT = "cobabench.mazebm.BinaryTree" ;
		//String CUT = "cobabench.mazebm.BitwiseManipulator" ;
		//String CUT = "cobabench.mazebm.IntUtils" ;
		//String CUT = "cobabench.mazebm.StringUtils" ;
		//String CUT = "cobabench.mazebm.BracketBalancer" ;
		//String CUT = "cobabench.mazebm.ConnectedComponents" ;
		//String CUT = "cobabench.mazebm.ConvergingPaths" ;
		//String CUT = "cobabench.mazebm.Dijkstra" ;
		//String CUT = "cobabench.mazebm.ExprEvaluator" ;
		//String CUT = "cobabench.mazebm.FloatStatistics" ;
		//String CUT = "cobabench.mazebm.GraphTraversal" ;
		//String CUT = "cobabench.mazebm.HeapSort" ;
		//String CUT = "cobabench.mazebm.MatrixAnalyzer" ;
		//String CUT = "cobabench.mazebm.NestedLoops" ;
		//String CUT = "cobabench.mazebm.QuickSort" ;
		//String CUT = "cobabench.mazebm.StringPatternMatcher" ;
		String CUT = "cobabench.mazebm.SinglyLinkedList" ;

		
		String sp = " " ;

		String argz =   "--classpath=" + cobabenchPath + "/target/classes"
				      + sp + "--classname=" + CUT 
				      + sp + "--output-path=" + cobabenchPath + "/src/test/java/"
				     // + sp + "-j=JUnit4"
				      //+ sp + "-s=RPS -u=UH " 
				      + sp + "-s=BFS"
				      + sp + "--concrete-driven=true"
				      + sp + "-b=60"
				      //+ sp + "--max-depth=50"
				      + sp + "--constrain-FP-params-to-normal-numbers=true"
				      //+ sp + "--surpress-regression-oracles=false"
				      //+ sp + "--propagate-unexpected-exceptions=true"
				      + sp
				      ;

		args_ = argz.split(" ") ;
		
		// Application.main(args_);  --> this call System.exit() which causes Maven test runner to crash
		
		// we'll do this instead, which is what the main() above does:
        int exitCode = new CommandLine(new MazeCLI()).execute(args_);
    }

}
