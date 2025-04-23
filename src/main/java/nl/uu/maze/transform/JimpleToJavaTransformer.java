package nl.uu.maze.transform;

import java.util.Optional;

import javax.annotation.Nonnull;

import sootup.core.jimple.common.constant.*;
import sootup.core.jimple.visitor.AbstractConstantVisitor;

/**
 * Transforms a Jimple constant ({@link Constant}) to a Java value.
 */
public class JimpleToJavaTransformer extends AbstractConstantVisitor<Optional<Object>> {
    @Override
    public void caseBooleanConstant(@Nonnull BooleanConstant constant) {
        setResult(Optional.of(constant.toString().equals("1")));
    }

    @Override
    public void caseDoubleConstant(@Nonnull DoubleConstant constant) {
        setResult(Optional.of(constant.getValue()));
    }

    @Override
    public void caseFloatConstant(@Nonnull FloatConstant constant) {
        setResult(Optional.of(constant.getValue()));
    }

    @Override
    public void caseIntConstant(@Nonnull IntConstant constant) {
        setResult(Optional.of(constant.getValue()));
    }

    @Override
    public void caseLongConstant(@Nonnull LongConstant constant) {
        setResult(Optional.of(constant.getValue()));
    }

    @Override
    public void caseNullConstant(@Nonnull NullConstant constant) {
        setResult(Optional.of(null));
    }

    @Override
    public void caseStringConstant(@Nonnull StringConstant constant) {
        setResult(Optional.of(constant.getValue()));
    }

    // Below constants are not supported in the current implementation
    @Override
    public void caseClassConstant(@Nonnull ClassConstant constant) {
        setResult(Optional.empty());
    }

    @Override
    public void caseEnumConstant(@Nonnull EnumConstant constant) {
        setResult(Optional.empty());
    }

    @Override
    public void caseMethodHandle(@Nonnull MethodHandle handle) {
        setResult(Optional.empty());
    }

    @Override
    public void caseMethodType(@Nonnull MethodType methodType) {
        setResult(Optional.empty());
    }
}
