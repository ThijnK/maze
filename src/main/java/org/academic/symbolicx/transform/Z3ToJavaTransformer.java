package org.academic.symbolicx.transform;

import com.microsoft.z3.BoolExpr;

public class Z3ToJavaTransformer {

    public String transform(BoolExpr expr) {
        // TODO: implement this (if applicable), maybe make it a visitor pattern
        return expr.toString();
    }

}
