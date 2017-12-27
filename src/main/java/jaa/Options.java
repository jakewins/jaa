package jaa;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class Options
{
    private final Set<String> includes;
    private final File javaExecutable;
    private final File allocationInstrumenter;
    private final File reportFolder;

    public static class Builder {

        private final Options state;

        public Builder() {
            this(new Options(Collections.emptySet(), null, null, null));
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
            return new Builder(new Options(newIncludes, state.javaExecutable, state.allocationInstrumenter, state.reportFolder));
        }

        /**
         * Path to the `java` exetuable to use to run the tests; this *must* be the an OpenJDK-based
         * JVM built with the debug flag on
         *
         * @param pathToJavaExecutable
         * @return a new builder
         */
        public Builder withJavaExecutable(File pathToJavaExecutable)
        {
            return new Builder(new Options(state.includes, pathToJavaExecutable, state.allocationInstrumenter, state.reportFolder));
        }

        /**
         * Path to the allocation instrumenter jar, from https://github.com/google/allocation-instrumenter,
         * to use. This jarfile should ideally just be the built jar from there - if not, you need to make sure the
         * jar file has the correct manifest set up to have it act as an agent jar and correctly instrument the
         * bytecode as it's loaded.
         *
         * @param pathToAllocationInstrumenterJar
         * @return a new builder
         */
        public Builder withAllocationInstrumenter(File pathToAllocationInstrumenterJar)
        {
            return new Builder(new Options(state.includes, state.javaExecutable, pathToAllocationInstrumenterJar, state.reportFolder));
        }

        /**
         * Folder to store allocation reports.
         *
         * @param reportFolder
         * @return a new builder
         */
        public Builder withReportPathTemplate(File reportFolder)
        {
            return new Builder(new Options(state.includes, state.javaExecutable, state.allocationInstrumenter, reportFolder));
        }

        public Options build()
        {
            return state;
        }
    }

    private Options(Set<String> includes, File javaExecutable, File allocationInstrumenter, File reportFolder) {
        this.includes = includes;
        this.javaExecutable = javaExecutable;
        this.allocationInstrumenter = allocationInstrumenter;
        this.reportFolder = reportFolder;
    }

    public Stream<String> includes() {
        return includes.stream();
    }

    public File javaExecutable() {
        return javaExecutable;
    }

    public File allocationInstrumenter() {
        return allocationInstrumenter;
    }

    public File reportFolder() {
        return reportFolder;
    }
}
