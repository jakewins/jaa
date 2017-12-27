package jaa;

import jaa.ea.EliminatedAllocation;
import jaa.ea.EliminationParser;
import jaa.examples.DebugJVMFetcher;
import jaa.examples.EntryPoint;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.function.Function;
import java.util.stream.Stream;

public class Jaa
{
    private final Options options;

    public Jaa(Options options) {
        this.options = options;
    }

    public void run()
    {
        final String classPath = System.getProperty("java.class.path");
        final String javaExecutable = javaExecutable();

        options
            .includes()
            .flatMap(getAnalysisDecoratedMethods())
            .forEach(m -> {
                try {
                    String[] args = {
                            javaExecutable,
                            "-classpath", classPath,
                            "-XX:+UnlockDiagnosticVMOptions",
                            "-XX:+PrintEliminateAllocations",
                            EntryPoint.class.getName(),
                            "ea",
                            m.getDeclaringClass().getName() + "#" + m.getName(),
                            "1"};
                    Process process = exec(args);
                    Stream<EliminatedAllocation> eliminations = new EliminationParser()
                            .parse(standardOutputFrom(process));

                    process.waitFor();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private Process exec(String[] args) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.inheritIO().redirectOutput(ProcessBuilder.Redirect.PIPE);
        return builder.start();
    }

    private Stream<String> standardOutputFrom(Process process) throws IOException {
        Stream<String> output;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            output = reader.lines();
        }
        return output;
    }

    private String javaExecutable() {
        String javaExecutable = options.javaExecutable();
        if(javaExecutable == null) {
            DebugJVMFetcher jvmFetcher = new DebugJVMFetcher();
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
