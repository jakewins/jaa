package jaa.examples;

import jaa.internal.infrastructure.OutputCapture;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class SimpleExample_Test {
    @Test
    public void theExampleShouldWork() throws Exception
    {
        OutputCapture stdout = new OutputCapture();

        try(AutoCloseable ignored = stdout.capture()) {
            // When
            SimpleExample.main();
        }

        // Then
        String captured = stdout.captured();
        assertThat(captured, containsString("jaa.examples.SimpleExample#simple"));
        assertThat(captured, containsString("JVM eliminates 80000000b"));
        assertThat(captured, containsString("Complete reports in ./target/allocation-reports/jaa.examples.SimpleExample#simple.json"));
    }
}
