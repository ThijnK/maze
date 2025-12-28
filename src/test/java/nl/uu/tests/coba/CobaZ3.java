package nl.uu.tests.coba;

import org.junit.jupiter.api.Test;

import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.transform.Z3ToJavaTransformer;
import nl.uu.maze.util.Z3Sorts; 
import com.microsoft.z3.*;
import sootup.core.types.PrimitiveType.DoubleType;
import sootup.core.types.PrimitiveType.IntType;

public class CobaZ3 {
	
	//@Test
	// For trying out the treatment of special FP values, namely NaN and infinity.
	//
	void coba_FP_specialvalues() {
		
		 System.out.println(">> COBA FP special values") ;
		 Context ctx = new Context();
		 Z3ToJavaTransformer toJ = new Z3ToJavaTransformer() ;
         /* do something with the context */
		 FPSort doubleSort = ctx.mkFPSort64() ;
		 
		 // +infinity created directly in z3, and through conversion from java Double infinity
		 FPNum posInf = ctx.mkFPInf(doubleSort, false) ;
		 FPNum posInf2 = ctx.mkFP(Double.POSITIVE_INFINITY, doubleSort) ;
		 
		 // same for - infinity
		 FPNum negInf = ctx.mkFPInf(doubleSort, true) ;
		 FPNum negInf2 = ctx.mkFP(Double.NEGATIVE_INFINITY, doubleSort) ;
		 
		 // same for NaN:
		 FPNum nan = ctx.mkFPNaN(doubleSort) ;
		 FPNum nan2 = ctx.mkFP(Double.NaN, doubleSort) ;
		 
		 // just a float valiue 10.0:
		 FPNum ten = ctx.mkFP(10D, doubleSort) ;
		 
		 // just a variable x in z3:
		 Expr x = ctx.mkConst("x", doubleSort) ;
		 
		 
		 System.out.println(">>> z3 +inf : " + posInf + ", " + toJ.transformExpr(posInf,DoubleType.getInstance())) ;
		 System.out.println(">>> z3 +inf2: " + posInf2 + ", " + toJ.transformExpr(posInf2,DoubleType.getInstance())) ;
		 System.out.println(">>> z3 -inf : " + negInf + ", " + toJ.transformExpr(negInf,DoubleType.getInstance())) ;
		 System.out.println(">>> z3 -inf2: " + negInf2 + ", " + toJ.transformExpr(negInf2,DoubleType.getInstance())) ;
		 System.out.println(">>> z3 nan : " + nan + ", " + toJ.transformExpr(nan,DoubleType.getInstance())) ;
		 System.out.println(">>> z3 nan2: " + nan2 + ", " + toJ.transformExpr(nan2,DoubleType.getInstance())) ;
		 
		 System.out.println(">>> +inf = +inf2 --> " + (posInf.equals(posInf2))) ;
		 System.out.println(">>> -inf = -inf2 --> " + (negInf.equals(negInf2))) ;
		 System.out.println(">>> nan = nan2   --> " + (nan.equals(nan2))) ;
		 
		 
		 Solver solver = ctx.mkSolver();
		
		 solver.add(ctx.mkAnd(
				      ctx.mkNot(ctx.mkFPLt(x, posInf)),
				      ctx.mkNot(ctx.mkFPIsNaN(x))) 
				 ) ;
		 System.out.println("** !(x < +inf) && not (x is NaN)") ;
		 Status st = solver.check() ;
		 System.out.println("   " + st) ;
		 Model m = null ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkAnd(
			      ctx.mkFPLEq(x, posInf2),
			      ctx.mkFPIsNormal(x))
			 ) ;
		 System.out.println("** x <= +inf2 && x is normal-number") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkFPGt(x, negInf2));
		 System.out.println("** x > -inf2") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkFPEq(x, posInf));
		 System.out.println("** x = +inf") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkFPEq(x, posInf2));
		 System.out.println("** x = +inf2") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkFPGt(x, posInf2));
		 System.out.println("** x > +inf2") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkFPEq(x, negInf2));
		 System.out.println("** x = -inf2") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkFPEq(x, nan));
		 System.out.println("** x = nan") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }

		 solver.reset();
		 solver.add(ctx.mkFPEq(x, nan2));
		 System.out.println("** x = nan2") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
	        	
         /* be kind to dispose manually and not wait for the GC. */
         ctx.close();
		
	}
	
	
	// Original Maze function to convert a bitvector expr, that represents an integral number
	// like an int, to an FP expr.
	// The function seems to be wrong as the intsance of mkFPToFP below seems to expect,
	// according to its doc, that the bitvec expr represents a floating point.
	// We''ll try it
    /** Coerce a bit vector to the given sort. */
    private FPExpr maze_orig_coerceToSort(Context ctx, BitVecExpr expr, FPSort sort) {
        int sizeSort = sort.getEBits() + sort.getSBits();
        return ctx.mkFPToFP(maze_orig_coerceToSize(ctx, expr, sizeSort), sort);
    }
	
    

    //original Maze function for changing the size of bitvec, usec by the coarce-function
    // above.
    /**
     * Coerce a bit vector to the given size by either sign extending or extracting
     * bits.
     */
    private BitVecExpr maze_orig_coerceToSize(Context ctx, BitVecExpr expr, int size) {
        if (size > expr.getSortSize()) {
            return ctx.mkSignExt(size - expr.getSortSize(), expr);
        } else if (size < expr.getSortSize()) {
            return ctx.mkExtract(size - 1, 0, expr);
        }
        return expr;
    }
    
	@Test
    // For trying out Maze conversion of bitvec representing integral number to float
    // number
	void coba_FB_BV_casting() {
		
		 System.out.println(">> COBA java-int, z3 BV, z3 FP casting") ;
		 Z3Sorts sorts = Z3Sorts.getInstance() ;
		 Z3ToJavaTransformer toJ = new Z3ToJavaTransformer() ;
		 
		 Context ctx = new Context();
		 
         /* do something with the context */
		 
		 BitVecSort bvsort = sorts.getIntSort() ;
		 IntSort intsort   = ctx.mkIntSort() ;
		 FPSort doubleSort = ctx.mkFPSort64() ;

		 // a variable i of type int, and its bitvec representation:
		 Expr i = ctx.mkConst("i",intsort) ;
		 BitVecExpr i_bv = ctx.mkBVConst("i_bv", bvsort.getSize()) ;
		 
		 // (double) i_bv using maze original conversion, and using z3 own conversion:
	     FPExpr i_fp1 = maze_orig_coerceToSort(ctx,i_bv, doubleSort) ;
		 boolean signed = true ; 
	     FPExpr i_fp2 = ctx.mkFPToFP(ctx.mkFPRoundTowardZero(), i_bv, doubleSort, signed) ;
	     
	     // just a number 10.0:
	     FPNum ten_fp = ctx.mkFP(10D, doubleSort) ;
	     
	     // an integer 10, its bitvec, and casted (double) 10_bv using maze and using native z3:
	     IntNum ten_i = ctx.mkInt(10) ;
	     BitVecExpr ten_bv = ctx.mkInt2BV(bvsort.getSize(), ten_i) ;
	     FPExpr ten_fp1 = maze_orig_coerceToSort(ctx,ten_bv, doubleSort) ;
	     FPExpr ten_fp2 = ctx.mkFPToFP(ctx.mkFPRoundTowardZero(), ten_bv, doubleSort, signed) ;
	     
	     System.out.println(">>> ten-i:"   + ten_i) ;
	     System.out.println(">>> ten-bv:"  + ten_bv) ;
	     System.out.println(">>> ten-fp1:" + ten_fp1) ;
	     System.out.println(">>> ten-fp2:" + ten_fp2) ;
	     
	     // just a variable x of type double:
	     Expr x = ctx.mkConst("x", doubleSort) ;
	     
	     Solver solver = ctx.mkSolver();
			
		 solver.add(ctx.mkFPLt(i_fp1, ten_fp)) ;
		 System.out.println("** (float) i_bv < 10, via maze conversion") ;
		 Status st = solver.check() ;
		 System.out.println("   " + st) ;
		 Model m = null ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(i_bv,false),IntType.getInstance())) ;
		 }
		 
		 // this give unsat! (wrong)
		 solver.reset();
		 solver.add(ctx.mkFPGt(i_fp1, ten_fp)) ;
		 System.out.println("** (float) i_bv > 10, via maze conversion") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 m = null ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(i_bv,false),IntType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkFPLt(i_fp2, ten_fp)) ;
		 System.out.println("** (float) i_bv < 10, via z3 native conversion") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 m = null ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(i_bv,false),IntType.getInstance())) ;
		 }
		 
		 solver.reset();
		 solver.add(ctx.mkFPGt(i_fp2, ten_fp)) ;
		 System.out.println("** (float) i_bv > 10, via z3 native conversion") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 m = null ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(i_bv,false),IntType.getInstance())) ;
		 }
		 
		 // this gives (very) wrong answer... 
		 solver.reset();
		 solver.add(ctx.mkFPEq(x, ten_fp1)) ;
		 System.out.println("** x = ten_fp1") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }

		 // this one gives correct answer
		 solver.reset();
		 solver.add(ctx.mkFPEq(x, ten_fp2)) ;
		 System.out.println("** x = ten_fp2") ;
		 st = solver.check() ;
		 System.out.println("   " + st) ;
		 if (st == Status.SATISFIABLE){
			 m = solver.getModel();
			 System.out.println("   model: " + toJ.transformExpr(m.eval(x,false),DoubleType.getInstance())) ;
		 }
		 
         ctx.close();
	}
	
	
}
