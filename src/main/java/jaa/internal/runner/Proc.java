package jaa.internal.runner;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.stream.Stream;

public class Proc {
    public final Process process;

    public static Proc exec(String ... command) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.inheritIO().redirectOutput(ProcessBuilder.Redirect.PIPE);
        return new Proc(builder.start());
    }

    public Proc(Process process) {
        this.process = process;
    }

    public Stream<String> stdout() {
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

    public void awaitSuccessfulExit() throws InterruptedException {
        process.waitFor();
        if(process.exitValue() != 0) {
            // TODO include captured as a help here
            throw new AssertionError(String.format("Subprocess exited with error code %d", process.exitValue()));
        }
    }
}
