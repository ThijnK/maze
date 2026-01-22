package nl.uu.maze.util;

import com.microsoft.z3.Context;
import com.microsoft.z3.*;

public class z3FormulaLib {
	
     static public BoolExpr mkFPIsNormal(Context ctx, FPExpr expr, FPSort sort) {
    	 
    	 // expr < +inf
    	 BoolExpr t1 = ctx.mkFPLt(expr, ctx.mkFPInf(sort, false)) ;
    	 // -inf < expr
    	 BoolExpr t2 = ctx.mkFPLt(ctx.mkFPInf(sort, true), expr) ;
    	 
    	 return ctx.mkAnd(t1,t2) ;
    	 
     }

}
