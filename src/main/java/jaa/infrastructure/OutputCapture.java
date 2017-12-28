package jaa.infrastructure;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public class OutputCapture {

    private ByteArrayOutputStream stdOutRecording;

    public AutoCloseable capture() {
        PrintStream original = System.out;
        stdOutRecording = new ByteArrayOutputStream();
        System.setOut(new PrintStream(stdOutRecording));

        return () -> System.setOut(original);
    }

    public String captured() throws UnsupportedEncodingException {
        return stdOutRecording.toString("UTF8");
    }
}
