package nl.uu.tests.coba;

import nl.uu.maze.main.Application;
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
				      //+ sp + "-s=RPS -u=UH " 
				      + sp + "-s=BFS"
				      + sp + "-b=240"
				      + sp + "--constrain-FP-params-to-normal-numbers=true"
				      + sp + "--surpress-regression-oracles=false"
				      + sp + "--propagate-unexpected-exceptions=true"
				      + sp
				      ;

		args_ = argz.split(" ") ;
		
		Application.main(args_);
    }

}
