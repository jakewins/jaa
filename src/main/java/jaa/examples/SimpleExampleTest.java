package jaa.examples;

import jaa.AllocationAnalysis;
import jaa.Jaa;
import jaa.Options;
import org.junit.Test;

import java.nio.file.Paths;

public class SimpleExampleTest {

    @AllocationAnalysis
    public void simple() {
        for (int i = 0; i < 5000; i++) {
            allocateABunchOfObjectsForNoGoodReason();
        }
        System.out.println("Hello, world!");
    }

    private void allocateABunchOfObjectsForNoGoodReason()
    {
        for (int i = 0; i < 1000; i++) {
            new Object();
        }
    }

    public static void main(String ... argv)
    {
        new Jaa(new Options.Builder()
                .include(SimpleExampleTest.class)
                .withReportFolder(Paths.get("./target/allocation-reports"))
                .build())
                .run();
    }

    @Test
    public void thisExampleShouldWork() throws Exception
    {
        main();
    }
}
