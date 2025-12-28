package nl.uu.maze.main;

public class CobaMaze {
	
	public static void main(String[] args) {
		
		String[] args_ = { "--help" } ;
		
		String cobabenchPath = "../my_simple_bench" ;
		//String CUT = "cobabench.TriangleClassifier2" ;
		String CUT = "cobabench.TriangleClassifier3" ;
		//String CUT = "cobabench.CobaBranches" ;
		String sp = " " ;

		String argz =   "--classpath=" + cobabenchPath + "/target/classes"
				      + sp + "--classname=" + CUT 
				      + sp + "--output-path=" + cobabenchPath + "/src/test/java/"
				      //+ sp + "-s=RPS -u=UH " 
				      + sp + "-s=BFS"
				      + sp + "-b=240 ";

		args_ = argz.split(" ") ;
		
		Application.main(args_);
    }

}
