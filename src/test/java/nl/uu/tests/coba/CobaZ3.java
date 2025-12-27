package nl.uu.tests.coba;

import org.junit.jupiter.api.Test;
import nl.uu.maze.transform.Z3ToJavaTransformer;

import com.microsoft.z3.*;
import sootup.core.types.PrimitiveType.DoubleType;;

public class CobaZ3 {
	
	@Test
	void test1() {
		
		 Context ctx = new Context();
		 Z3ToJavaTransformer toJ = new Z3ToJavaTransformer() ;
         /* do something with the context */
		 FPSort doubleSort = ctx.mkFPSort64() ;
		 Expr x = ctx.mkConst("x", doubleSort) ;
		 
		 FPNum posInf = ctx.mkFPInf(doubleSort, false) ;
		 FPNum posInf2 = ctx.mkFP(Double.POSITIVE_INFINITY, doubleSort) ;
		 
		 FPNum negInf = ctx.mkFPInf(doubleSort, true) ;
		 FPNum negInf2 = ctx.mkFP(Double.NEGATIVE_INFINITY, doubleSort) ;
		 
		 FPNum nan = ctx.mkFPNaN(doubleSort) ;
		 FPNum nan2 = ctx.mkFP(Double.NaN, doubleSort) ;
		 
		 FPNum ten = ctx.mkFP(10D, doubleSort) ;
		 
		 
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
	

}
