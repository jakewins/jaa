package jaa;

import jaa.internal.allocation.AllocationLedger;
import jaa.internal.ea.EliminationParser;
import jaa.internal.infrastructure.Reflection;
import jaa.internal.runner.EntryPoint;
import jaa.internal.runner.JaaResources;
import jaa.internal.runner.MethodAllocationAnalyzer;
import jaa.internal.runner.Proc;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.function.Predicate;

import static jaa.internal.ea.EliminationParser.predicateThatExcludes;
import static jaa.internal.runner.MethodAllocationAnalyzer.defaultAllocationInstrumenterJarPath;
import static jaa.internal.runner.MethodAllocationAnalyzer.defaultReportFolder;
import static jaa.internal.runner.Proc.exec;
import static java.nio.file.Files.createDirectories;
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
                            AllocationLedger fullLedger = new MethodAllocationAnalyzer()
                                    .analyze(classPath, javaExecutable, allocationInstrumenterJar, fullReportPath, m);

                            // 3. Combine the two into a report of allocations, sans eliminated ones
                            Path reportPath = reportFolder.resolve(methodDescription + ".json");
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

    private Path reportFolder() throws IOException {
        Path reportFolder = options.reportFolder();
        if(reportFolder != null) {
            createDirectories(reportFolder);
            return reportFolder;
        }

        return defaultReportFolder();
    }

    private Path allocationInstrumenterJarPath() {
        Path allocationInstrumenter = options.allocationInstrumenter();
        if(allocationInstrumenter != null) {
            return allocationInstrumenter;
        }

        return defaultAllocationInstrumenterJarPath();
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
