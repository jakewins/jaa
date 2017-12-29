package jaa.internal.runner;

import com.google.monitoring.runtime.instrumentation.Sampler;
import jaa.internal.allocation.AllocationLedger;

import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Consumer;

import static jaa.internal.runner.Proc.exec;

public class MethodAllocationAnalyzer {
    private AllocationLedger allocationsByHarness;

    public AllocationLedger analyze(String classPath, Path javaExecutable,
                                    Path allocationInstrumenterJar, Path reportPath,
                                    Method method) throws IOException, InterruptedException {
        AllocationLedger ledger = analyze0(classPath, javaExecutable, allocationInstrumenterJar, reportPath, method);
        return ledger.subtract(allocationsDoneByHarness());
    }

    public AllocationLedger analyze(Method method) throws IOException, InterruptedException {
        Path reportPath = Files.createTempFile("jaa", getClass().getSimpleName());
        try {
            JaaResources jvmFetcher = new JaaResources();
            return analyze(System.getProperty("java.class.path"),
                    jvmFetcher.javaExecutable(jvmFetcher.javaHome()),
                    defaultAllocationInstrumenterJarPath(),
                    reportPath, method);
        } finally {
            Files.delete(reportPath);
        }
    }

    /**
     * Determine allocations made by the test harness itself by analyzing a method known to
     * make no allocations; this is used to remove these allocations from the report.
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private AllocationLedger allocationsDoneByHarness() throws IOException, InterruptedException {
        if(allocationsByHarness != null) {
            return allocationsByHarness;
        }

        Path reportPath = Files.createTempFile("jaa.calibration", getClass().getSimpleName());
        try {
            JaaResources jvmFetcher = new JaaResources();
            return allocationsByHarness = analyze0(System.getProperty("java.class.path"),
                    jvmFetcher.javaExecutable(jvmFetcher.javaHome()),
                    defaultAllocationInstrumenterJarPath(),
                    reportPath, getClass().getMethod("noop"));
        } catch (NoSuchMethodException e) {
            throw new RuntimeException("Programming error, unable to find noop method, " +
                    "can't calibrate allocation test. Please ensure you are using the latest " +
                    "version of JAA. If you are, please help the community by " +
                    "filing a ticket for this.", e);
        } finally {
            Files.delete(reportPath);
        }
    }

    // Used to calibrate the allocation, to remove any allocation made by the test infrastructure.
    public void noop() {

    }

    private AllocationLedger analyze0(String classPath, Path javaExecutable,
                                    Path allocationInstrumenterJar, Path reportPath,
                                    Method method) throws IOException, InterruptedException {
        String methodDescription = method.getDeclaringClass().getName() + "#" + method.getName();
        Proc allocProc = exec(javaExecutable.toAbsolutePath().toString(),
                "-classpath", classPath,
                "-javaagent:" + allocationInstrumenterJar.toAbsolutePath().toString(),
                EntryPoint.class.getName(),
                "analyze-allocation",
                methodDescription,
                reportPath.toAbsolutePath().toString());
        allocProc.stdout().forEach(forwardUserOutputTo(System.out));
        allocProc.awaitSuccessfulExit();
        return AllocationLedger.read(reportPath);
    }

    public static Path defaultReportFolder() throws IOException {
        String directoryName = String.format("allocations-%s",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
        Path reportFolder = Paths.get(".",directoryName);
        Files.createDirectories(reportFolder);
        return reportFolder;
    }

    public static Path defaultAllocationInstrumenterJarPath() {
        Class klass = Sampler.class;
        URL location = klass.getResource('/' + klass.getName().replace('.', '/') + ".class");
        if(!location.getPath().startsWith("file:")) {
            throw new RuntimeException("Unable to determine location of the allocation instrumenter jar, please ensureDownloadedTo the allocation jar from https://github.com/google/allocation-instrumenter and manually specify the location in the options to the Jaa runner.");
        }
        return Paths.get(location.getPath().substring("file:".length()).split("!")[0]);
    }

    private Consumer<String> forwardUserOutputTo(PrintStream out) {
        return line -> {
            if(!line.startsWith("__jaa")) {
                out.println(line);
            }
        };
    }
}
