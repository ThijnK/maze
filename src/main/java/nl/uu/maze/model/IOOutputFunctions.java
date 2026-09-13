package nl.uu.maze.model;

import com.microsoft.z3.Context;

import nl.uu.maze.execution.symbolic.SymbolicState;

import nl.uu.maze.transform.JimpleToZ3Transformer;
import nl.uu.maze.util.Z3ContextProvider;
import sootup.core.jimple.basic.Immediate;
import sootup.core.jimple.basic.Local;
import sootup.core.jimple.common.expr.AbstractInvokeExpr;
import sootup.core.signatures.MethodSignature;
import sootup.core.types.PrimitiveType;

/**
 * Models of I/O methods that write to the "environment" (e.g. the Console, or even
 * the file system), such as System.out.println. Currently MAZE does 
 * not model the "environment" though, so such a method would appear as
 * changing nothing in the current symbolic state. So, here we model
 * them as having no effect (a skip). This is of course incorrect, but
 * will at least allow the symbolic execution to continue. 
 * 
 * <p>Accurately model I/O methods is challenging, and would require a model
 * of the environment to be taken into account. At this moment this is not
 * done in MAZE.
 */
public class IOOutputFunctions {
	
	static private final JimpleToZ3Transformer jimpleToZ3 = new JimpleToZ3Transformer();

	private static final Context ctx() { return Z3ContextProvider.getContext(); }
	
	public static ModelOfMethod MODELof_PrintLn = new PrintLn();

	public static class PrintLn extends ModelOfMethod {

		String methodname  = "println";
		String methodname2 = "print";
		String classname = "java.io.PrintStream";
		
		PrintLn() { }
		
		@Override
		public boolean match(MethodSignature sootSignature) {
			
			String mname = sootSignature.getName() ;
			//System.out.println(">>> method to check: " + mname + ", " + sootSignature.getDeclClassType().getFullyQualifiedName()) ;
			if (sootSignature.getDeclClassType().getFullyQualifiedName().equals(classname)
					   && (mname.equals(methodname) || mname.equals(methodname2))) {
				return true ;
			}
			return false ;
		}

		@SuppressWarnings("unchecked")
		@Override
		public SymbolicState executeModel(SymbolicState state, Local base, AbstractInvokeExpr expr) {
			MethodSignature methodSig = expr.getMethodSignature() ;
			if (! match(methodSig)) return null ;
			// PrintStream.print does not change the sym-state, so we return the sym-state:
			// Note that this may be unsound! (e.g. some implementation of print may throw an
			// exception, which this model would be oblivious).
			return state ;
		}
		
	}
	

}
