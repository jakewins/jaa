package jaa.examples;

import jaa.*;

import java.io.IOException;
import java.nio.file.Paths;

public class FixtureExample {

    private boolean wasSetUp = false;
    private boolean wasExecuted = false;

    // Fixture methods are executed outside of the
    // allocation sampling, so you can instantiate things you don't
    // want to see in the analysis report.

    @SetUp
    public void setup() {
        wasSetUp = true;
    }

    @AllocationAnalysis
    public void analyzeWithFixtures() {
        wasExecuted = true;
    }

    @TearDown
    public void tearDown() {
        System.out.println("[Teardown] Was set up: " + wasSetUp);
        System.out.println("[Teardown] Was executed: " + wasExecuted);
    }


    public static void main(String ... argv) throws IOException {
        new Jaa(new Options.Builder()
                .include(FixtureExample.class)
                .withReportFolder(Paths.get("./target/allocation-reports"))
                .build())
                .run();
    }
}
