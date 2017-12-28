package jaa.internal.allocation;

import com.google.monitoring.runtime.instrumentation.Sampler;
import sun.reflect.Reflection; // TODO this does not work on Java 9, need to put behind conditional import

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Plugs into the google allocation tracker */
public class AllocationSampler implements Sampler
{
    private static final String THIS_PACKAGE_NAME = AllocationSampler.class.getPackage().getName();
    private static final String SAMPLER_PACKAGE_NAME = Sampler.class.getPackage().getName();

    private final AtomicBoolean profiling = new AtomicBoolean(false);
    private final ThreadLocal<List<String>> stackTraceCache = ThreadLocal.withInitial(() -> new ArrayList<>(10));

    private final File output;
    private final int stackDepth;

    private AllocationLedger ledger;

    public AllocationSampler(File output, int stackDepth) {
        this.output = output;
        this.stackDepth = stackDepth;
    }

    public synchronized void start()
    {
        ledger = new AllocationLedger();
        profiling.set(true);
    }

    public synchronized void stop() {
        profiling.set(false);
        try {
            ledger.write(output);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void sampleAllocation(int count, String desc, Object newObj, long size)
    {
        if(!profiling.get())
        {
            return;
        }

        List<String> stackTrace = stackTraceCache.get();
        fastStackTrace(stackTrace, 0, stackDepth);

        ledger.record(desc, size, stackTrace);
    }

    private void fastStackTrace(List<String> out, int startAtDepth, int depth)
    {
        out.clear();
        int outStackIndex = 0;
        int realStackIndex = startAtDepth + 1;
        String currentFrame;
        for (; outStackIndex < startAtDepth + depth; outStackIndex++)
        {
            while(true) {
                Class<?> callerClass = Reflection.getCallerClass(realStackIndex++);
                if(callerClass == null)
                {
                    // end of trace
                    for (; outStackIndex < startAtDepth + depth; outStackIndex++) {
                        out.add("");
                    }
                    return;
                }

                // Skip instrumentation
                if(callerClass == AllocationSampler.class || callerClass == AllocationLedger.class ||
                    callerClass.getName().startsWith(THIS_PACKAGE_NAME) ||
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

    private void detailedStackTrace(String[] out, int startAtDepth)
    {
        StackTraceElement[] source = Thread.currentThread().getStackTrace();

        for (int outIndex = 1, srcIndex=startAtDepth; outIndex < out.length; outIndex++) {
            while(true) {
                if(srcIndex >= source.length)
                {
                    // end of trace
                    for (; outIndex < out.length; outIndex++) {
                        out[outIndex] = "";
                    }
                    return;
                }

                StackTraceElement element = source[srcIndex];
                srcIndex++;

                // Skip instrumentation
                if(element.getClassName().startsWith(THIS_PACKAGE_NAME) ||
                   element.getClassName().startsWith(SAMPLER_PACKAGE_NAME))
                {
                    continue;
                }

                out[outIndex] = String.format("%s#%s L%d", element.getClassName(), element.getMethodName(), element.getLineNumber());
                break;
            }
        }
    }
}
