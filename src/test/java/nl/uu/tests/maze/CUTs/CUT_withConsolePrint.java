package nl.uu.tests.maze.CUTs;

public class CUT_withConsolePrint {
	
	public static int foo(int x) {
		// The code below will translate to DynamicInvoke in the bytecode (java 9 or above), which
		// currently MAZE cannot handle. Such an invoke handles string concat (as below),
		// and lambda expr. This requires runtime method linkage, which  requires
		// qiute some effort. Handling this is TODO.
		//
		//System.out.println(">> input x: " + x + " === oh btw 2x=" + (2*x)) ;
		
		System.out.println("START foo") ;
		
		if (x>0) {
			System.out.println("x is POSITIVE: ") ;
			if (x>10)
				return 10 ;
			else
				return x ;
		}
		return 0 ;
	}

}
