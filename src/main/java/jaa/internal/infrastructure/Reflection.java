package jaa.internal.infrastructure;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.stream.Stream;

public class Reflection {
    public static Function<? super String, Class> findClass = name -> {
        try {
          return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    };

    public static Function<Class, Stream<? extends Method>> methodsWithAnnotation(Class annotation) {
        return cls -> {
            try {
                return Stream
                        .of(cls.getDeclaredMethods())
                        .filter(m -> m.getAnnotation(annotation) != null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }

    public static Object invoke(Object obj, Method method, Object ... arguments) {
        try {
            return method.invoke(obj, arguments);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }
}
