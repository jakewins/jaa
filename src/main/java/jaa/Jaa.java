package jaa;

import com.google.monitoring.runtime.instrumentation.Sampler;
import jaa.internal.allocation.AllocationLedger;
import jaa.internal.ea.EliminationParser;
import jaa.internal.infrastructure.Reflection;
import jaa.internal.runner.EntryPoint;
import jaa.internal.runner.JaaResources;
import jaa.internal.runner.Proc;

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
import java.util.function.Predicate;

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
                    .map(Reflection.findClass)
                    .flatMap(Reflection.methodsWithAnnotation(AllocationAnalysis.class))
                    .forEach((Method m) -> {
                        try {
                            String methodDescription = m.getDeclaringClass().getName() + "#" + m.getName();
                            System.out.printf("%s running..\n", methodDescription);

                            // 1. One run to analyze what gets eliminated by escape analysis
                            Proc eaProc = exec(
                                    javaExecutable.toAbsolutePath().toString(),
                                    "-classpath", classPath,
                                    "-XX:+UnlockDiagnosticVMOptions",
                                    "-XX:+PrintEliminateAllocations",
                                    EntryPoint.class.getName(),
                                    "analyze-escapes",
                                    methodDescription,
                                    "1");
                            Predicate<AllocationLedger.Record> excludeEliminatedAllocations = predicateThatExcludes(
                                    new EliminationParser().parse(eaProc.stdout()));
                            eaProc.awaitSuccessfulExit();

                            // 2. One run to get an allocation profile
                            Path fullReportPath = reportFolder.resolve(methodDescription + ".full.json");
                            Path reportPath = reportFolder.resolve(methodDescription + ".json");

                            Proc allocProc = exec(javaExecutable.toAbsolutePath().toString(),
                                    "-classpath", classPath,
                                    "-javaagent:" + allocationInstrumenterJar.toAbsolutePath().toString(),
                                    EntryPoint.class.getName(),
                                    "analyze-allocation",
                                    methodDescription,
                                    fullReportPath.toAbsolutePath().toString());
                            allocProc.stdout().forEach(forwardUserOutputTo(System.out));
                            allocProc.awaitSuccessfulExit();

                            // 3. Combine the two into a report of allocations, sans eliminated ones
                            AllocationLedger fullLedger = AllocationLedger.read(fullReportPath);
                            AllocationLedger filteredLedger = fullLedger
                                    .filter(excludeEliminatedAllocations);
                            filteredLedger.write(reportPath);

                            int n = 5;
                            System.out.printf("\n");
                            System.out.printf("== %s, summary ==\n", methodDescription);
                            System.out.printf("  Allocates %db total, JVM eliminates %db, %db remaining\n",
                                    fullLedger.totalBytes(),
                                    fullLedger.totalBytes() - filteredLedger.totalBytes(),
                                    filteredLedger.totalBytes());
                            System.out.printf("  Complete reports in %s\n", reportPath);
                            System.out.printf("\n");
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

    private Consumer<String> forwardUserOutputTo(PrintStream out) {
        return line -> {
            if(!line.startsWith("__jaa")) {
                out.println(line);
            }
        };
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

}
