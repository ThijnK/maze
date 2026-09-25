package nl.uu.tests.coba;

import nl.uu.maze.main.Application;
import nl.uu.maze.main.cli.MazeCLI;
import picocli.CommandLine;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;

// Just for trying out Maze-application, for convenience, invoked from here
public class CobaMaze_Junit4 {
	
	@Test
	void coba_Maze() {
				
		String[] args_ = { "--help" } ;
		
		String cobabenchPath = "../my_simple_bench" ;
		//String CUT = "cobabench.TriangleClassifier2" ;
		//String CUT = "cobabench.CobaUnexpectedExceptionDetection" ;
		//String CUT = "cobabench.TriangleClassifier3" ;
		//String CUT = "cobabench.CobaBranches" ;
		//String CUT = "cobabench.CobaSimple" ;
		//String CUT = "cobabench.CobaNullDeref" ;
		//String CUT = "cobabench.CobaVarDefaultInit" ;
		//String CUT = "cobabench.BinaryTreeY" ;
		// String CUT = "cobabench.CobaParamOtherClasses" ;
		//String CUT = "cobabench.RBTY" ;
		//String CUT = "cobabench.CobbaInnerClasses" ;
		//String CUT = "cobabench.CobaBinaryTree" ;
		// String CUT = "cobabench.CobaIntegerParam" ;
		//String CUT = "cobabench.CobaArrayLength" ;
		//String CUT = "cobabench.CobaSettingCUTfields" ;
		//String CUT = "cobabench.CobaExceptionHandlers" ;
		//String CUT = "cobabench.CobaGraphTraversal" ;
		//String CUT = "cobabench.CobaSimplePaths" ;
		
		// classes from Maze-bm:
		//String CUT = "cobabench.mazebm.TriangleClassifier" ;
		//String CUT = "cobabench.mazebm.AckermannPeter" ;
		//String CUT = "cobabench.mazebm.BinarySearch" ;
		//String CUT = "cobabench.mazebm.BinaryTree" ; // challenge
		//String CUT = "cobabench.mazebm.BitwiseManipulator" ;
		//String CUT = "cobabench.mazebm.IntUtils" ;
		//String CUT = "cobabench.mazebm.StringUtils" ;// lots of prime paths 3000+ !
		//String CUT = "cobabench.mazebm.BracketBalancer" ; // 500 pps
		//String CUT = "cobabench.mazebm.ConnectedComponents" ;		
		//String CUT = "cobabench.mazebm.ConvergingPaths" ;
		 //String CUT = "cobabench.mazebm.Dijkstra" ; // challenge
		//String CUT = "cobabench.mazebm.ExprEvaluator" ; 
		//String CUT = "cobabench.mazebm.FloatStatistics" ;
		//String CUT = "cobabench.mazebm.GraphTraversal" ;
		String CUT = "cobabench.mazebm.HeapSort" ;
		//String CUT = "cobabench.mazebm.MatrixAnalyzer" ;
		//String CUT = "cobabench.mazebm.NestedLoops" ;// 56598 pps???
		//String CUT = "cobabench.mazebm.QuickSort" ; 
		//String CUT = "cobabench.mazebm.StringPatternMatcher" ; 
		//String CUT = "cobabench.mazebm.SinglyLinkedList" ;
		
			
		String sp = " " ;

		String argz =   "--classpath=" + cobabenchPath + "/target/classes"
				      + sp + "--class-name=" + CUT
				      + sp + "--output-path=" + cobabenchPath + "/src/test/java/"
				      //+ sp + "-C"
				      + sp + "-j=JUnit4"
				      //+ sp + "-s=RPS" 
				      //+ sp + "-s=COS"
				      //+ sp + "-s=PS" + sp + "-u=DTUH"
				      //+ sp + "-m=isComplexValidTreeHelper"
				      //+ sp + "-s=BFS"
				      + sp + "-s=PCS"
				      
				      
				      + sp + "--minimization=true"
				      + sp + "--path-length-coverage=1"
				      + sp + "--target-path-aging=0"
				      + sp + "-b=60"
				      //+ sp + "-l=DEBUG" // log level
				      //+ sp + "--max-depth=100"
				      + sp + "--max-array-size=10"
				      + sp + "--constrain-fp-params-to-normal-numbers=true"
				      + sp + "--check-division-by-zero"
				      //+ sp + "--suppress-regression-oracles=false"
				      //+ sp + "--propagate-unexpected-exceptions=true"
				      + sp + "--export-jimple=log"
				      + sp + "--export-hcfg=log"
				      //+ sp + "--export-target-paths=log"
				      //+ sp + "--export-path-coverage=log"
				      //+ sp + "--export-summary=true"
				      
				      + sp
				      ;

		args_ = argz.split(" ") ;
		
		// Application.main(args_);  --> this call System.exit() which causes Maven test runner to crash
		
		// we'll do this instead, which is what the main() above does:
        int exitCode = new CommandLine(new MazeCLI()).execute(args_);
    }

}
