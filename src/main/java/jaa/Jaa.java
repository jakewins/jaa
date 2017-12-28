package jaa;

import com.google.monitoring.runtime.instrumentation.Sampler;
import jaa.internal.allocation.AllocationLedger;
import jaa.internal.ea.EliminationParser;
import jaa.internal.runner.EntryPoint;
import jaa.internal.runner.JaaResources;
import jaa.internal.runner.Proc;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static jaa.internal.ea.EliminationParser.predicateThatExcludes;
import static jaa.internal.runner.Proc.exec;
import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.joining;

public class Jaa
{
    private final Options options;

    public Jaa(Options options) {
        this.options = options;
    }

    public void run() {
        try {
            final String classPath = System.getProperty("java.class.path");
            final Path javaExecutable = javaExecutable();
            final Path allocationInstrumenterJar = allocationInstrumenterJarPath();
            final Path reportFolder = reportFolder();

            options
                    .includes()
                    .flatMap(getAnalysisDecoratedMethods())
                    .forEach((Method m) -> {
                        try {
                            String methodDescription = m.getDeclaringClass().getName() + "#" + m.getName();

                            // 1. One run to analyze what gets eliminated by escape analysis
                            Proc proc = exec(
                                    javaExecutable.toAbsolutePath().toString(),
                                    "-classpath", classPath,
                                    "-XX:+UnlockDiagnosticVMOptions",
                                    "-XX:+PrintEliminateAllocations",
                                    EntryPoint.class.getName(),
                                    "analyze-escapes",
                                    methodDescription,
                                    "1");
                            Predicate<AllocationLedger.Record> excludeEliminatedAllocations = predicateThatExcludes(
                                    new EliminationParser().parse(proc.stdout()));
                            proc.awaitSuccessfulExit();

                            // 2. One run to get an allocation profile
                            Path fullReportPath = reportFolder.resolve(methodDescription + ".full.json");
                            Path reportPath = reportFolder.resolve(methodDescription + ".json");

                            exec(javaExecutable.toAbsolutePath().toString(),
                                    "-classpath", classPath,
                                    "-javaagent:" + allocationInstrumenterJar.toAbsolutePath().toString(),
                                    EntryPoint.class.getName(),
                                    "analyze-allocation",
                                    methodDescription,
                                    fullReportPath.toAbsolutePath().toString())
                                    .awaitSuccessfulExit();

                            // 3. Combine the two into a report of allocations, sans eliminated ones
                            AllocationLedger fullLedger = AllocationLedger.read(fullReportPath);
                            AllocationLedger filteredLedger = fullLedger
                                    .filter(excludeEliminatedAllocations);
                            filteredLedger.write(reportPath);

                            int n = 5;

                            System.out.println(methodDescription);
                            System.out.printf("  Allocates %db total, JVM eliminates %db, %db remaining\n",
                                    fullLedger.totalBytes(),
                                    fullLedger.totalBytes() - filteredLedger.totalBytes(),
                                    filteredLedger.totalBytes());
                            System.out.printf("  Complete reports in %s\n", reportPath);
                            System.out.printf("== Top %d allocation points: ==\n", n);
                            filteredLedger.records().sorted(comparingLong(r -> -r.getTotalBytes())).limit(n).forEach(r -> {
                                System.out.printf("  %db of %s at:\n\t%s\n",
                                        r.getTotalBytes(),
                                        r.getObj(),
                                        r.getStackTrace()
                                                .stream()
                                                .filter(s -> s.length() > 1)
                                                .collect(joining("\n\t")));
                            });
                            System.out.println();
                            System.out.println();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch(Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Path reportFolder() throws IOException {
        Path reportFolder = options.reportFolder();
        if(reportFolder != null) {
            Files.createDirectories(reportFolder);
            return reportFolder;
        }

        String directoryName = String.format("allocations-%s",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date()));
        reportFolder = Paths.get(".",directoryName);
        Files.createDirectories(reportFolder);
        return reportFolder;
    }

    private Path allocationInstrumenterJarPath() {
        Path allocationInstrumenter = options.allocationInstrumenter();
        if(allocationInstrumenter != null) {
            return allocationInstrumenter;
        }

        Class klass = Sampler.class;
        URL location = klass.getResource('/' + klass.getName().replace('.', '/') + ".class");
        if(!location.getPath().startsWith("file:")) {
            throw new RuntimeException("Unable to determine location of the allocation instrumenter jar, please ensureDownloadedTo the allocation jar from https://github.com/google/allocation-instrumenter and manually specify the location in the options to the Jaa runner.");
        }
        return Paths.get(location.getPath().substring("file:".length()).split("!")[0]);
    }

    private Path javaExecutable() throws IOException, InterruptedException {
        Path javaExecutable = options.javaExecutable();
        if(javaExecutable == null) {
            JaaResources jvmFetcher = new JaaResources();
            javaExecutable = jvmFetcher.javaExecutable(jvmFetcher.javaHome());
        }
        return javaExecutable;
    }

    private Function<String, Stream<? extends Method>> getAnalysisDecoratedMethods() {
        return className -> {
            try {
                return Stream
                        .of(Class.forName(className).getDeclaredMethods())
                        .filter(m -> m.getAnnotation(AllocationAnalysis.class) != null);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
    }
}
