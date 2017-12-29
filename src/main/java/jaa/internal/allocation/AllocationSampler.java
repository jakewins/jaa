package jaa.internal.allocation;

import com.google.monitoring.runtime.instrumentation.Sampler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Plugs into the google allocation tracker */
public class AllocationSampler implements Sampler
{
    private final AtomicBoolean profiling = new AtomicBoolean(false);
    private final ThreadLocal<List<String>> stackTraceCache = ThreadLocal.withInitial(() -> new ArrayList<>(10));

    private final File output;
    private final int stackDepth;
    private final StackTracer stackTracer;

    private AllocationLedger ledger;

    public AllocationSampler(File output, int stackDepth) {
        this.output = output;
        this.stackDepth = stackDepth;
        this.stackTracer = StackTracer.load();
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
        stackTracer.getStackTrace(stackTrace, stackDepth);

        ledger.record(desc, size, stackTrace);
    }
}
