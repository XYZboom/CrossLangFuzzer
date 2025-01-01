import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
public final class SimpleClassWithSimpleFunction {
    public final /*@NotNull*/ PType getSimple() {
        throw new RuntimeException();
    }
}
