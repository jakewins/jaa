package jaa.examples;

import jaa.internal.infrastructure.OutputCapture;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThat;

public class FixtureExample_Test {
    @Test
    public void theExampleShouldWork() throws Exception
    {
        OutputCapture stdout = new OutputCapture();

        try(AutoCloseable ignored = stdout.capture()) {
            // When
            FixtureExample.main();
        }

        // Then
        String captured = stdout.captured();
        assertThat(captured, containsString("[Teardown] Was set up: true"));
        assertThat(captured, containsString("[Teardown] Was executed: true"));
    }
}
