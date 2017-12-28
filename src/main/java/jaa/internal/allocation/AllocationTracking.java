package jaa.internal.allocation;

import com.google.monitoring.runtime.instrumentation.AllocationRecorder;

import java.io.File;

public class AllocationTracking
{
    private final AllocationSampler sampler;
    private final ControlServer controlServer;

    public AllocationTracking()
    {
        this(new File("./allocations.json"));
    }

    public AllocationTracking(File reportOutput) {
        this(reportOutput, 15);
    }

    public AllocationTracking(File reportOutput, int stackDepth)
    {
        this.sampler = new AllocationSampler(reportOutput, stackDepth);
        this.controlServer = new ControlServer(sampler::start, sampler::stop);
    }

    public void start() throws Throwable
    {
        AllocationRecorder.addSampler(sampler);
        Thread thread = new Thread(controlServer, "Allocation.ControlServer");
        thread.setDaemon(false);
        thread.start();
    }

    public void stop() throws Throwable
    {
        controlServer.stop();
        AllocationRecorder.removeSampler(sampler);
    }
}
