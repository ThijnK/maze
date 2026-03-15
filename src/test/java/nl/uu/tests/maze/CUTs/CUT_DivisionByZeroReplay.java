package nl.uu.tests.maze.CUTs;

/**
 * CUT to test that division-by-zero checking in the replay mode work.
 * Replay happens in concrete driven SE.
 */
public class CUT_DivisionByZeroReplay {

	public int divisionByZeroReplay(int x) {
		int y = 0 ;
		x = x/y ;
		if (x==0) return 1 ; else return -1 ;
	}
	
	
	public int remByZeroReplay(int x) {
		int y = 0 ;
		x = x % y ;
		if (x==0) return 1 ; else return -1 ;
	}
	
	public int longDivisionByZeroReplay(long x) {
		long y = 0 ;
		x = x/y ;
		if (x==0) return 1 ; else return -1 ;
	}
	
	public int longRemByZeroReplay(long x) {
		long y = 0 ;
		x = x % y ;
		if (x==0) return 1 ; else return -1 ;
	}
	
}
