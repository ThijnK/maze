package nl.uu.maze.transform;

import com.microsoft.z3.BoolExpr;

/**
 * Transforms a Z3 expression ({@link Expr}) to a Java expression.
 */
public class Z3ToJavaTransformer {

    public String transform(BoolExpr expr) {
        // TODO: implement this (if applicable), maybe make it a visitor pattern
        return expr.toString();

    }

}
