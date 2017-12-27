package jaa;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class Options
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
