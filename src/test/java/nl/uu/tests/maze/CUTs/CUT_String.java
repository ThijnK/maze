package nl.uu.tests.maze.CUTs;

public class CUT_String {
	
	// currently MAZE treat String as a Z3 string, wich can't be null. So, the
	// null-case for this method is not generated. TODO.
	// MAZE also don't symbolically execute s.length() TODO.
	// So.. only one test is generated for this method. TODO
	public static int StrLen(String s) {
		if (s == null)
			return -1 ;
		if (s.length() > 2)
			return 2 ;
		return s.length() ;
	}

}
