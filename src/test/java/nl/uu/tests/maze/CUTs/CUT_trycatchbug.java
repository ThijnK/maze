package nl.uu.tests.maze.CUTs;

import nl.uu.tests.maze.CUTs.SomePckg.CUT_B;

/**
 * For testing a bug when handling try-catch. There was a case where
 * MAZE could not find class dependency inside a try-body.
 */
public class CUT_trycatchbug {
	
	public static int foo(int x) {
		try {
			CUT_B b = new CUT_B() ;
			//CUT_B b = new CUT_B(x) ;
			if (x == 0) 
				throw new Exception() ; 
			return b.v ;
		}
		catch(Exception e) {
			return -1 ;
		}
	}

}
