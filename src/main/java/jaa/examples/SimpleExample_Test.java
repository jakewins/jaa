package jaa.examples;

import jaa.infrastructure.OutputCapture;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
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
        // Exactly what gets allocated is slightly non-deterministic,
        // since JVM processes can be up to adventures, allocating a few bytes.
        // We are asserting that the escape analysis is picked up on, and that
        // the top allocations match as expected.
        String captured = stdout.captured();
        String allocated = captured.contains("80000648") ?
                "Allocates 80000648b total, JVM eliminates 80000000b, 648b remaining" :
                "Allocates 80000776b total, JVM eliminates 80000000b, 776b remaining";
        assertThat(captured, equalTo(String.format(
                "jaa.examples.SimpleExample#simple\n" +
                "  %s\n" +
                "  Complete reports in ./target/allocation-reports/jaa.examples.SimpleExample#simple.json\n" +
                "== Top 5 allocation points: ==\n" +
                "  80b of java/lang/reflect/Constructor at:\n" +
                "\tjava.lang.reflect.Constructor\n" +
                "\tjava.lang.reflect.ReflectAccess\n" +
                "\tsun.reflect.ReflectionFactory\n" +
                "\tjava.lang.Class\n" +
                "\tjava.lang.Class\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "  72b of char at:\n" +
                "\tjava.lang.String\n" +
                "\tjava.lang.Class\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "  72b of char at:\n" +
                "\tjava.lang.String\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "  48b of java/nio/HeapCharBuffer at:\n" +
                "\tjava.nio.CharBuffer\n" +
                "\tsun.nio.cs.StreamEncoder\n" +
                "\tsun.nio.cs.StreamEncoder\n" +
                "\tjava.io.OutputStreamWriter\n" +
                "\tjava.io.BufferedWriter\n" +
                "\tjava.io.PrintStream\n" +
                "\tjava.io.PrintStream\n" +
                "\tjaa.examples.SimpleExample\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "  48b of java/nio/HeapCharBuffer at:\n" +
                "\tjava.nio.CharBuffer\n" +
                "\tsun.nio.cs.StreamEncoder\n" +
                "\tsun.nio.cs.StreamEncoder\n" +
                "\tjava.io.OutputStreamWriter\n" +
                "\tjava.io.BufferedWriter\n" +
                "\tjava.io.PrintStream\n" +
                "\tjava.io.PrintStream\n" +
                "\tjava.io.PrintStream\n" +
                "\tjaa.examples.SimpleExample\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\tjaa.runner.EntryPoint\n" +
                "\n" +
                "\n", allocated)
        ));
    }
}
