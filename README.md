[![Build Status](https://travis-ci.org/jakewins/jaa.svg?branch=master)](https://travis-ci.org/jakewins/jaa)

# JVM Allocation Analyzer, JAA

Simple JVM memory analysis with awareness of allocations removed by HotSpot.

## Minimum viable snippet

Add dependency:

    <dependency>
      <groupId>com.jakewins</groupId>
      <artifactId>jaa</artifactId>
      <version>1.1.0</version>
    </dependency>
    
Write analysis case:

    public class SimpleExample {
    
        public static void main(String ... argv) throws IOException {
            new Jaa(new Options.Builder()
                    .include(SimpleExample.class)
                    .withReportFolder(Paths.get("./target/allocation-reports"))
                    .build())
                    .run();
        }
    
        @AllocationAnalysis
        public void simple() {
            System.out.println("Hello, world!");
        }
    }

See [examples](src/main/java/jaa/examples) for more running code.

## Rationale

There are many ways to analyze allocations on the JVM - object counting, TLAB acquisition tracking, etc. 
If you want an exact account of what was allocated and where, instrumenting bytecode is the way to go.

However, instrumenting byte code to track each allocated object makes it impossible for the JVM to perform standard optimizations.
The reports become filled with allocations that the JVM would optimize into stack allocations or remove altogether under normal operation.

The end result being that the reports are not actionable - if you fix the worst allocation points from an instrumentation-based report,
you're likely to make no difference to your programs performance, since you're just manually doing HotSpots job.

JAA analyses memory in three steps:

1. Execute the test without instrumentation with `-XX:+PrintEliminateAllocations`. Use the output from this to build an index of allocations the JVM removes already.
2. Execute the test with instrumentation
3. Filter the report from (2) with the data from (1)

This gives you a report of allocation points the JVM does not move to the stack or remove, substantially improving the usefulness of the analysis.

## Limitations

- Java 8 only, for now
- OS X users need to manually acquire a `fastdebug` build of OpenJDK (consider +1 [here](https://github.com/AdoptOpenJDK/openjdk-build/issues/146) to solve this)
- Ambiguity in OpenJDK debug output could lead to reports excluding the wrong thing. OpenJDK only outputs the class names, not the fully qualified names, so two allocation points allocating the exact same object could end up tricking JAA to eliminate the wrong allocation. 

## License

GPLv3
