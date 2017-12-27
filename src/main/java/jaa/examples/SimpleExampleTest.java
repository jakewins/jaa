package jaa.examples;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@interface AllocationAnalysis {

}

class Options
{
    private final Set<String> includes;
    private final String javaExecutable;

    public static class Builder {

        private final Options state;

        public Builder() {
            this(new Options(Collections.emptySet(), null));
        }

        private Builder(Options state) {
            this.state = state;
        }

        /**
         * Class to include to execute analysis methods in.
         * @param classWithCodeToAnalyze
         * @return
         */
        public Builder include(Class<?> classWithCodeToAnalyze) {
            HashSet<String> newIncludes = new HashSet<>(state.includes);
            newIncludes.add(classWithCodeToAnalyze.getName());
            return new Builder(new Options(newIncludes, state.javaExecutable));
        }

        /**
         * Path to the `java` exetuable to use to run the tests; this *must* be the an OpenJDK-based
         * JVM built with the debug flag on
         *
         * @param pathToJavaExecutable
         * @return a new builder
         */
        public Builder withJavaExecutable(String pathToJavaExecutable)
        {
            return new Builder(new Options(state.includes, pathToJavaExecutable));
        }

        public Options build()
        {
            return state;
        }
    }

    private Options(Set<String> includes, String javaExecutable) {
        this.includes = includes;
        this.javaExecutable = javaExecutable;
    }

    public Stream<String> includes() {
        return includes.stream();
    }

    public String javaExecutable() {
        return javaExecutable;
    }
}

class Jaa
{
    private final Options options;

    Jaa(Options options) {
        this.options = options;
    }

    void run()
    {
        final String classPath = System.getProperty("java.class.path");
        final String javaExecutable = javaExecutable();

        options
            .includes()
            .flatMap(getAnalysisDecoratedMethods())
            .forEach(m -> {
                try {

                    ProcessBuilder builder = new ProcessBuilder(
                            javaExecutable,
                            "-classpath", classPath,
                            EntryPoint.class.getName(),
                            "ea",
                            m.getDeclaringClass().getName() + "#" + m.getName(),
                            "1");
                    builder.inheritIO().redirectOutput(ProcessBuilder.Redirect.PIPE);
                    Process process = builder.start();

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()))) {

                        reader.lines().forEach(line -> System.out.println("R: " + line));
                    }

                    process.waitFor();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
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

public class SimpleExampleTest {

    @AllocationAnalysis
    public void simple() {
        System.out.println("Hello, world!");
    }

    public static void main(String ... argv)
    {
        new Jaa(new Options.Builder()
                .include(SimpleExampleTest.class)
                .build())
                .run();
    }


    @Test
    public void thisExampleShouldWork() throws Exception
    {
        main();
    }
}
