package jaa;

import com.google.monitoring.runtime.instrumentation.Sampler;
import jaa.allocation.AllocationLedger;
import jaa.ea.EliminationParser;
import jaa.runner.EntryPoint;
import jaa.runner.JaaResources;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static jaa.ea.EliminationParser.predicateThatExcludes;
import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.joining;

public class Jaa
{
    private final Options options;

    public Jaa(Options options) {
        this.options = options;
    }

    public void run()
    {
        final String classPath = System.getProperty("java.class.path");
        final File javaExecutable = javaExecutable();
        final File allocationInstrumenterJar = allocationInstrumenterJarPath();
        final File reportFolder = reportFolder();

        options
            .includes()
            .flatMap(getAnalysisDecoratedMethods())
            .forEach(m -> {
                try {
                    String methodDescription = m.getDeclaringClass().getName() + "#" + m.getName();

                    // 1. One run to analyze what gets eliminated by escape analysis
                    String[] args = {
                            javaExecutable.getAbsolutePath(),
                            "-classpath", classPath,
                            "-XX:+UnlockDiagnosticVMOptions",
                            "-XX:+PrintEliminateAllocations",
                            EntryPoint.class.getName(),
                            "analyze-escapes",
                            methodDescription,
                            "1"};
                    Process process = exec(args);
                    Stream<String> outputLines = standardOutputFrom(process);

                    Predicate<AllocationLedger.Record> excludeEliminatedAllocations = predicateThatExcludes(
                            new EliminationParser().parse(outputLines));
                    process.waitFor();
                    if(process.exitValue() != 0) {
                        throw new RuntimeException(String.format("Test exited with error code %d", process.exitValue()));
                    }

                    // 2. One run to get an allocation profile
                    File fullReportPath = new File(reportFolder, methodDescription + ".full.json");
                    File reportPath = new File(reportFolder, methodDescription + ".json");

                    String[] args2 = {
                            javaExecutable.getAbsolutePath(),
                            "-classpath", classPath,
                            "-javaagent:" + allocationInstrumenterJar.getAbsolutePath(),
                            EntryPoint.class.getName(),
                            "analyze-allocation",
                            methodDescription,
                            fullReportPath.getAbsolutePath()};
                    Process exec = exec(args2);
                    exec.waitFor();
                    if(exec.exitValue() != 0) {
                        throw new RuntimeException(String.format("Test exited with error code %d", exec.exitValue()));
                    }

                    // 3. Combine the two into a report of allocations, sans eliminated ones
                    AllocationLedger ledger = AllocationLedger.read(fullReportPath)
                            .filter(excludeEliminatedAllocations);
                    ledger.write( reportPath );

                    System.out.println(methodDescription);
                    ledger.records().sorted(comparingLong(r -> -r.getTotalBytes())).limit(5).forEach(r -> {
                        System.out.printf("%db of %s at:\n\t%s\n",
                                r.getTotalBytes(),
                                r.getObj(),
                                r.getStackTrace()
                                        .stream()
                                        .filter(s -> s.length() > 1)
                                        .collect(joining("\n\t")));
                    });

                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private File reportFolder() {
        File reportPathTemplate = options.reportFolder();
        if(reportPathTemplate != null) {
            return reportPathTemplate;
        }

        File file = new File(String.format("./allocations-%s",
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(new Date())));
        if(!file.mkdirs()) {
            throw new IllegalStateException(String.format("Unable to create folder for reports at %s",
                    file.getAbsolutePath()));
        }
        return file;
    }

    private File allocationInstrumenterJarPath() {
        File allocationInstrumenter = options.allocationInstrumenter();
        if(allocationInstrumenter != null) {
            return allocationInstrumenter;
        }

        Class klass = Sampler.class;
        URL location = klass.getResource('/' + klass.getName().replace('.', '/') + ".class");
        if(!location.getPath().startsWith("file:")) {
            throw new RuntimeException("Unable to determine location of the allocation instrumenter jar, please download the allocation jar from https://github.com/google/allocation-instrumenter and manually specify the location in the options to the Jaa runner.");
        }
        return new File(location.getPath().substring("file:".length()).split("!")[0]);
    }

    private Process exec(String[] args) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.inheritIO().redirectOutput(ProcessBuilder.Redirect.PIPE);
        return builder.start();
    }

    private Stream<String> standardOutputFrom(Process process) throws IOException {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
        Stream<String> lines = reader.lines();
        return lines.onClose(() -> {
            try {
                reader.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    private File javaExecutable() {
        File javaExecutable = options.javaExecutable();
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
