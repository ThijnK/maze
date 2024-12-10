package org.academic.symbolicx.generator;

import com.microsoft.z3.Expr;
import com.microsoft.z3.FuncDecl;
import com.microsoft.z3.Model;

import java.util.ArrayList;
import java.util.List;

import org.academic.symbolicx.executor.SymbolicState;
import org.academic.symbolicx.util.Tuple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JUnitTestCaseGenerator {
    private static final Logger logger = LoggerFactory.getLogger(JUnitTestCaseGenerator.class);

    public void generateTestCases(List<Tuple<SymbolicState, Model>> models, String className, String methodName) {
        logger.info("Generating JUnit test cases...");
        for (Tuple<SymbolicState, Model> tuple : models) {
            SymbolicState state = tuple.getX();
            Model model = tuple.getY();
            StringBuilder testCase = new StringBuilder("public void test() {\n");

            List<String> parameters = new ArrayList<>();
            for (FuncDecl<?> decl : model.getConstDecls()) {
                String param = state.getParameterValue(decl.getName().toString());
                Expr<?> value = model.getConstInterp(decl);
                testCase.append(String.format("    %s %s = %s;\n", getJavaType(value), param, value));
                parameters.add(param);
            }

            // FIXME: better way to get class name and method name
            testCase.append(String.format("   %s cut = new %s();\n", className, className));
            testCase.append(String.format("   cut.%s(", methodName));
            // FIXME: determine order of parameters, should be able to get that from method
            // signature
            testCase.append(String.join(", ", parameters));
            testCase.append(");\n}\n");

            logger.info(testCase.toString());
        }
    }

    /** Z3 Sort to java type as a String */
    private String getJavaType(Expr<?> value) {
        if (value.isInt())
            return "int";
        if (value.isBool())
            return "boolean";
        if (value.isReal())
            return "double";
        if (value.isString())
            return "String";
        return "Object";
    }
}