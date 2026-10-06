package nl.uu.tests.maze.CUTs;

/**
 * CUT to test MAZE handling of number casting.
 */
public class CUT_NumericCasting {
	
	
	public String intToFloat(int x) {
		float x_ = (float) x ;
		if (-4.33 < x_ && x_ < -3.33)
			return "xLTGT intToFloatSuccess" ;
		return "else-branch" ;
	}
	
	public String intToFloat_xy(int x, float y) {
		float x_ = (float) x ;
		if (y < x_ && x_ < y+1)
			return"xy_LTGT intToFloatSuccess"  ;
		return "else-branch" ;
	}
	
	

	// MAZE can't solve the cond x_ < 4 below. TODO
	public String floatToInt_LT(float x) {
		int x_ = (int) x ;
		if (x_ < -4 )
			return "xLT floatToIntSuccess" ;
		return "else-branch" ;
	}
	
	// MAZE can solve x_ > -4, BUT can't solve the else part. TODO
	public String floatToInt_GT(float x) {
		int x_ = (int) x ;
		if (x_ > -4)
			return "xGT floatToIntSuccess"  ;
		return "xGT else-branch" ;
	}
	
	// MAZE can't solve the then-condition. TODO
	public String floatToInt_LTGT(float x) {
		int x_ = (int) x ;
		if (-6 < x_ && x_ < -4 )
			return "xLTGT floatToIntSuccess"  ;
		return "else-branch" ;
	}
	
	public String floatToInt_xy_LT(float x, int y) {
		int x_ = (int) x ;
		if (x_ < y+4)
			return "xy_LT floatToIntSuccess" ;
		return "else-branch" ;
	}
	
	public String floatToInt_xy_GT(float x, int y) {
		int x_ = (int) x ;
		if (x_ > y)
			return "xy_GT floatToIntSuccess"  ;
		return "else-branch" ;
	}
	
	// MAZE cant always solve the then-cond. Depends on other formulas currently in z3 
	// context, e.g. if this is the only target method, it is solvable, but if we include
	// all the other methods above z3 can solve it.
	public String floatToInt_xy_LTGT(float x, int y) {
		int x_ = (int) x ;
		if (y < x_ && x_ < y+2)
			return "xy_LTGT floatToIntSuccess" ;
		return "else-branch" ;
	}
	

}