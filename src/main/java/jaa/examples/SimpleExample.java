package jaa.examples;

import jaa.AllocationAnalysis;
import jaa.Jaa;
import jaa.Options;

import java.io.IOException;
import java.nio.file.Paths;

public class SimpleExample {

    @AllocationAnalysis
    public void simple() {
        // This loop allocates 5,000,000 objects - except
        // in production, hotspot will get rid of it, because
        // it has no side effects.
        for (int i = 0; i < 5000; i++) {
            allocateABunchOfObjectsForNoGoodReason();
        }

        // This has a side effect - the string needs to
        // go via the heap to stdout. There's some complexities around
        // this being a constant, but the general idea works.
        System.out.println("Hello, world!");
    }

    private void allocateABunchOfObjectsForNoGoodReason()
    {
        for (int i = 0; i < 1000; i++) {
            new Object();
        }
    }

    public static void main(String ... argv) throws IOException {
        new Jaa(new Options.Builder()
                .include(SimpleExample.class)
                .withReportFolder(Paths.get("./target/allocation-reports"))
                .build())
                .run();
    }
}
