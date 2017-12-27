package jaa.ea;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import jaa.allocation.AllocationLedger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

public class EscapeProfile
{

    // -XX:+UnlockDiagnosticVMOptions
    // -XX:+PrintEliminateAllocations
    // -XX:+PrintEscapeAnalysis
    private final String debugJvm = "path/to/debug/jvm";

    public static void main(String ... argv) throws IOException {
        String escapeAnalysisStdout = argv[0];
        String allocationProfile = argv[1];
        String output = argv[2];

        Map<String, List<EliminatedAllocation>> eliminated = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();

        // 1. Build a map of object name -> places where that allocation was eliminated
        Stream<String> lines = Files.lines(Paths.get(escapeAnalysisStdout));
        new EliminationParser().parse(lines).forEach( e -> {
            String justName = e.objectName().split(":")[0];

            List<EliminatedAllocation> allocations = eliminated.computeIfAbsent(justName, k -> new LinkedList<>());
            allocations.add(e);
        });

        // 2. Apply that map as a filter on an allocation profile
        File file = new File(allocationProfile);
        Stream<JsonNode> allocations = stream(mapper.readTree(file).spliterator(), false);
        Stream<JsonNode> uneliminated = allocations
                .filter(allocation -> {
                    String obj = allocation.get("obj").asText();

                    List<EliminatedAllocation> eliminations = eliminated.get(obj);
                    if (eliminations != null) {
                        long count = eliminations
                                .stream()
                                .filter(stackTracesMatch(allocation.get("stackTrace")))
                                .count();
                        if (count >= 1) {
                            // Not sure that > is ok here; overall, need to swap to Java 9 so we can get stack
                            // traces with method names from the allocation tracker, class-names only is prone to error.
                            return false;
                        }
                    }
                    return true;
                });
        AllocationLedger ledger = uneliminated.reduce(new AllocationLedger(), (l, allocation) -> {
            LinkedList<String> stack = new LinkedList<>();
            allocation.get("stackTrace").forEach(s -> stack.add(s.toString()));
            l.record(
                    allocation.get("obj").asText(),
                    allocation.get("totalBytes").asInt(),
                    stack);
            return l;
        }, AllocationLedger::new);

        // 3. Store the result
        ledger.write(new File(output));
    }

    private static Predicate<EliminatedAllocation> stackTracesMatch(JsonNode allocationStackTrace) {
        return elimination -> {

            for (int i = 0; i < elimination.allocationPoint().size(); i++) {
                String allocationStackFrame = allocationStackTrace.get(i).asText();
                String eliminationStackFrame = elimination.allocationPoint().get(i).className;

                if(!allocationStackFrame.contains(eliminationStackFrame)) {
                    return false;
                }
            }

            return true;
        };
    }
}
