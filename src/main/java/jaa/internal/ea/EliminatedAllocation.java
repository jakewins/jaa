package jaa.internal.ea;

import java.util.Arrays;
import java.util.List;

/**
 * Describes a stack path to a byte code position.
 *
 * The path is a sequence of class/method/bci positions. Sometimes it's just a
 * direct position, indicating a position has been globally eliminated. More commonly,
 * it's a sequence of positions indicating that a call stack pattern has been inlined,
 * and that for that pattern an allocation has been eliminated.
 *
 * For instance, you'd likely not see just `ArrayList#iterator` eliminated - globally
 * converting that iterator instance to stack-local variables would not be possible -
 * but you are very likely to see `ArrayList#iterator YourClass#methodThatCallsIterator`,
 * meaning the iterator call was inlined into your method, and then the allocation was
 * eliminated.
 */
public class EliminatedAllocation
{
    public static class Position
    {
        public final String className;
        public final String methodName;
        public final int byteCodeIndex;

        Position(String className, String methodName, int byteCodeIndex) {
            this.className = className;
            this.methodName = methodName;
            this.byteCodeIndex = byteCodeIndex;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Position position = (Position) o;

            if (byteCodeIndex != position.byteCodeIndex) return false;
            if (className != null ? !className.equals(position.className) : position.className != null) return false;
            return methodName != null ? methodName.equals(position.methodName) : position.methodName == null;
        }

        @Override
        public int hashCode() {
            int result = className != null ? className.hashCode() : 0;
            result = 31 * result + (methodName != null ? methodName.hashCode() : 0);
            result = 31 * result + byteCodeIndex;
            return result;
        }

        @Override
        public String toString() {
            return "Position{" +
                    "className='" + className + '\'' +
                    ", methodName='" + methodName + '\'' +
                    ", byteCodeIndex=" + byteCodeIndex +
                    '}';
        }
    }

    private final String objectName;
    private final Position[] path;

    public EliminatedAllocation(String objectName, Position ... path) {
        this.objectName = objectName;
        this.path = path;
    }

    public String objectName() {
        return objectName;
    }

    public List<Position> allocationPoint()
    {
        return Arrays.asList(path);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EliminatedAllocation that = (EliminatedAllocation) o;

        if (objectName != null ? !objectName.equals(that.objectName) : that.objectName != null) return false;
        return Arrays.equals(path, that.path);
    }

    @Override
    public int hashCode() {
        int result = objectName != null ? objectName.hashCode() : 0;
        result = 31 * result + Arrays.hashCode(path);
        return result;
    }

    @Override
    public String toString() {
        return "EliminatedAllocation{" +
                "objectName='" + objectName + '\'' +
                ", path=" + Arrays.toString(path) +
                '}';
    }
}
