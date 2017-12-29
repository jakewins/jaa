package jaa.internal.runner;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;
import jaa.SetUp;
import jaa.TearDown;
import jaa.internal.allocation.AllocationSampler;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jaa.internal.infrastructure.Reflection.invoke;
import static java.util.Arrays.asList;

public class EntryPoint {
    public static void main(String ... argv) {
        try {
            switch (argv[0]) {
                case "analyze-escapes":
                    new EntryPoint().execute(argv[1], Long.parseLong(argv[2]));
                    break;
                case "analyze-allocation":
                    new EntryPoint().allocationTrackingExecute(argv[1], argv[2]);
                    break;
                default:
                    stdout(asList("error", String.format("Unknown command %s", argv[0])));
            }
        }
        catch(Throwable e)
        {
            stdout(asList("error", e.getMessage()));
            e.printStackTrace();
        }
    }

    private static void stdout(List<Object> message) {
        try {
            System.out.println("__jaa " + new ObjectMapper().writeValueAsString(message));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void executeFixture(Object instance, Class fixtureAnnotation) {
        for (Method method : instance.getClass().getDeclaredMethods()) {
            if(method.getAnnotation(fixtureAnnotation) != null) {
                invoke(instance, method);
            }
        }
    }

    private void allocationTrackingExecute(String methodDescription, String outputPath) throws Exception
    {
        Method method = findMethod(methodDescription);
        BlackHole hole = new BlackHole();
        Object instance = newInstance(method);
        AllocationSampler sampler = new AllocationSampler(new File(outputPath), 15);

        AllocationRecorder.addSampler(sampler);

        try
        {
            executeFixture(instance, SetUp.class);
            sampler.start();
            hole.consume(invoke(instance, method));
        }
        finally
        {
            sampler.stop();
            executeFixture(instance, TearDown.class);
        }
    }

    private void execute(String methodDescription, long iterations) throws ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException {
        Method method = findMethod(methodDescription);
        execute(new BlackHole(), newInstance(method), method, iterations);
    }

    private void execute(BlackHole hole, Object instance, Method method, long iterations) throws ClassNotFoundException, IllegalAccessException, InstantiationException, InvocationTargetException {
        executeFixture(instance, SetUp.class);
        try {
            for (int i = 0; i < iterations; i++) {
                hole.consume(method.invoke(instance));
            }
        }
        finally {
            executeFixture(instance, TearDown.class);
        }
    }

    private Method findMethod(String methodDescription) throws ClassNotFoundException {
        String[] parts = methodDescription.split("#");
        String className = parts[0];
        String methodName = parts[1];

        Class<?> cls = Class.forName(className);
        List<Method> methods = Stream.of(cls.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .collect(Collectors.toList());
        if(methods.size() == 0)
        {
            throw new RuntimeException(String.format("No method with the name %s found in %s", methodName, className));
        }

        if(methods.size() > 1)
        {
            throw new RuntimeException(String.format("More than one method named %s in %s, please give your tests unique names.", methodName, className));
        }

        return methods.get(0);
    }

    private Object newInstance(Method method) throws InstantiationException, IllegalAccessException {
        return method.getDeclaringClass().newInstance();
    }
}

/**
 * Prevent JVM optimizations from eradicating code under test by ensuring the code has side
 * effects.
 *
 * This is a mini-version of the Black Hole in JMH (see LICENSES); this does not do
 * cache line padding, so is not suitable for performance testing. Please see the extensive
 * commentary in the BlackHole implementation in JMH for details.
 */
class BlackHole
{
    public volatile Object obj1;
    public int tlr;
    public volatile int tlrMask;

    public BlackHole() {
        tlr = new Random(System.nanoTime()).nextInt();
        tlrMask = 1;
        obj1 = new Object();
    }

    /**
     * Consume object. This call provides a side effect preventing JIT to eliminate dependent computations.
     *
     * @param obj object to consume.
     */
    public final void consume(Object obj) {
        int tlrMask = this.tlrMask; // volatile read
        int tlr = (this.tlr = (this.tlr * 1664525 + 1013904223));
        if ((tlr & tlrMask) == 0) {
            // SHOULD ALMOST NEVER HAPPEN IN MEASUREMENT
            this.obj1 = new WeakReference<>(obj);
            this.tlrMask = (tlrMask << 1) + 1;
        }
    }

}