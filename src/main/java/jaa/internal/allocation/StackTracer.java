package jaa.internal.allocation;

import com.google.monitoring.runtime.instrumentation.Sampler;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;

public interface StackTracer {

    String THIS_PACKAGE_NAME = AllocationSampler.class.getPackage().getName();
    String SAMPLER_PACKAGE_NAME = Sampler.class.getPackage().getName();

    void getStackTrace(List<String> into, int depth);

    static StackTracer load() {
        if(SunReflectStackTracer.available()) {
            return new SunReflectStackTracer();
        }
        return new OldSchoolStackTrace();
    }
}

class SunReflectStackTracer implements StackTracer {
    private static final MethodHandle getCallerClass = loadMethod();

    private static MethodHandle loadMethod() {
        try {
            Class<?> aClass = Class.forName("sun.reflect.Reflection");
            Method method = aClass.getDeclaredMethod("getCallerClass", int.class);
            MethodHandle getCallerClass = MethodHandles.lookup().unreflect(method);
            // Make sure it works..
            getCallerClass.invoke(0);
            return getCallerClass;
        } catch(Throwable e) {
            return null;
        }
    }

    static boolean available() {
        return getCallerClass != null;
    }

    private static Class getCallerClass(int stackDepth) {
        try {
            return (Class) getCallerClass.invoke(stackDepth);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void getStackTrace(List<String> out, int depth) {
        out.clear();
        int outStackIndex = 0;
        int realStackIndex = 3;
        String currentFrame;
        for (; outStackIndex < depth; outStackIndex++)
        {
            while(true) {
                Class<?> callerClass = getCallerClass(realStackIndex++);
                if(callerClass == null)
                {
                    // end of trace
                    for (; outStackIndex < depth; outStackIndex++) {
                        out.add("");
                    }
                    return;
                }

                // Skip instrumentation
                if(callerClass.getName().startsWith(THIS_PACKAGE_NAME) ||
                        callerClass.getName().startsWith(SAMPLER_PACKAGE_NAME))
                {
                    continue;
                }

                currentFrame = callerClass.getName();
                break;
            }

            out.add(currentFrame);
        }
    }
}

class OldSchoolStackTrace implements StackTracer {

    @Override
    public void getStackTrace(List<String> out, int depth) {
        out.clear();
        int outStackIndex = 0;
        int realStackIndex = 3;
        StackTraceElement[] source = Thread.currentThread().getStackTrace();
        String currentFrame;
        for (; outStackIndex < depth; outStackIndex++)
        {
            while(true) {
                if(source.length <= realStackIndex)
                {
                    // end of trace
                    for (; outStackIndex < depth; outStackIndex++) {
                        out.add("");
                    }
                    return;
                }
                StackTraceElement element = source[realStackIndex++];

                // Skip instrumentation
                String callerClass = element.getClassName();
                if(callerClass.startsWith(THIS_PACKAGE_NAME) ||
                        callerClass.startsWith(SAMPLER_PACKAGE_NAME))
                {
                    continue;
                }

                currentFrame = callerClass;
                break;
            }

            out.add(currentFrame);
        }
    }
}