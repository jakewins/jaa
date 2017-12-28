package jaa.internal.allocation;

import org.codehaus.jackson.JsonFactory;
import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.JsonToken;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

public class ReportSummarizer {

    // Implementation note: This is a very silly class.
    // It uses streaming deserialization because I once generated a multi-gigabyte
    // report due to a bug, and wasn't able to parse it regularly. It seemed useful
    // to retain the streaming parser since that handles very large allocations well.
    // However, the parser is duplicated in two methods here, doing almost the exact same
    // work, so at least that needs cleaning up.

    enum State
    {
        START,
        IN_ALLOC_MAP,
        IN_TOP_ALLOC_MAP,
        IN_STACK,
        IN_TOP_STACK
    }

    static class Allocation
    {
        final int index;
        final String objectDescription;
        final long bytesAllocated;
        final String[] stack;

        Allocation(int index, String objectDescription, long bytesAllocated, String[] stack) {
            this.index = index;
            this.objectDescription = objectDescription;
            this.bytesAllocated = bytesAllocated;
            this.stack = stack;
        }
    }

    static class TotalAndTopIndexes
    {
        final long totalBytes;
        final Set<Integer> indexesOfTopAllocations;

        TotalAndTopIndexes(long totalBytes, Set<Integer> indexesOfTopAllocations) {
            this.totalBytes = totalBytes;
            this.indexesOfTopAllocations = indexesOfTopAllocations;
        }
    }

    public static void main(String ... argv) throws IOException {
        File allocationReport = new File("/home/jake/Downloads/alex/neo4j-enterprise-3.3.1/allocations-filter-ea.json");

        JsonFactory jsonFactory = new JsonFactory();
        jsonFactory.setCodec(new ObjectMapper());

        System.out.println("Finding top allocations..");
        TotalAndTopIndexes res = getAllocationsSortedByBytes(jsonFactory.createJsonParser(allocationReport), 0, 10);
        long total = res.totalBytes;
        Set<Integer> topAllocators = res.indexesOfTopAllocations;

        System.out.println("Collecting report..");
        LinkedList<Allocation> allocs = getTopAllocs(jsonFactory.createJsonParser(allocationReport), topAllocators);


        System.out.println("Total: " + total / 1024 + "Kb");
        allocs.stream()
                .sorted(Comparator.comparingLong(a -> -a.bytesAllocated))
                .forEach(n -> {
                    System.out.printf("%dKb of %s at:\n",
                            n.bytesAllocated / 1024,
                            n.objectDescription);
                    Stream.of(n.stack)
                            .limit(10)
                            .forEach(s -> System.out.printf("  %s\n", s));
                });
    }

    private static LinkedList<Allocation> getTopAllocs(JsonParser parser, Set<Integer> topAllocators) throws IOException {
        LinkedList<Allocation> allocs = new LinkedList<>();
        State state = State.START;

        String obj = null;
        long bytesAllocated = 0;
        String[] stack = null;
        int stackFrame = 0;

        int index = 0;
        for(JsonToken token = parser.nextToken(); token != null; token = parser.nextToken())
        {
            switch(state) {
                case START:
                    switch(token) {
                        case START_OBJECT:
                            if(topAllocators.contains(index))
                            {
                               state = State.IN_TOP_ALLOC_MAP;
                            } else
                            {
                                state = State.IN_ALLOC_MAP;
                            }
                            index++;
                            break;
                        case START_ARRAY:
                            break;
                        case END_ARRAY:
                            break;
                        default:
                            throw new RuntimeException("Unexpected " + token);
                    }
                    break;
                case IN_ALLOC_MAP:
                    switch(token) {
                        case FIELD_NAME:
                            break;
                        case VALUE_NUMBER_INT:
                            break;
                        case VALUE_STRING:
                            break;
                        case START_ARRAY:
                            state = State.IN_STACK;
                            break;
                        case END_OBJECT:
                            state = State.START;
                            break;
                        default:
                            throw new RuntimeException("Unexpected " + token);
                    }
                    break;
                case IN_TOP_ALLOC_MAP:
                    switch(token) {
                        case FIELD_NAME:
                            break;
                        case VALUE_NUMBER_INT:
                            if(parser.getCurrentName().equals("totalBytes")) {
                                bytesAllocated = parser.getLongValue();
                            }
                            break;
                        case VALUE_STRING:
                            if(parser.getCurrentName().equals("obj")) {
                                obj = parser.getText();
                            }
                            break;
                        case START_ARRAY:
                            stack = new String[10];
                            state = State.IN_TOP_STACK;
                            break;
                        case END_OBJECT:
                            allocs.add(new Allocation(index, obj, bytesAllocated, stack));
                            obj = null;
                            bytesAllocated = 0;
                            stack = null;
                            stackFrame = 0;
                            state = State.START;
                            break;
                        default:
                            throw new RuntimeException("Unexpected " + token + ": " + parser.getCurrentName());
                    }
                    break;
                case IN_STACK:
                    switch(token) {
                        case END_ARRAY:
                            state = State.IN_ALLOC_MAP;
                            break;
                        case VALUE_STRING:
                            break;
                        default:
                            throw new RuntimeException("Unexpected " + token + ": " + parser.getCurrentName());
                    }
                    break;

                case IN_TOP_STACK:
                    switch(token) {
                        case END_ARRAY:
                            state = State.IN_TOP_ALLOC_MAP;
                            break;
                        case VALUE_STRING:
                            if(stackFrame >= stack.length) {
                                break;
                            }
                            stack[stackFrame] = parser.getText();
                            stackFrame++;
                            break;
                        default:
                            throw new RuntimeException("Unexpected " + token + ": " + parser.getCurrentName());
                    }
                    break;

            }
        }
        return allocs;
    }

    private static TotalAndTopIndexes getAllocationsSortedByBytes(JsonParser parser, int skip, int n) throws IOException {
        long totalBytes = 0;
        ArrayList<Allocation> counts = new ArrayList<>();
        State state = State.START;
        for(JsonToken token = parser.nextToken(); token != null; token = parser.nextToken())
        {
            switch(state) {
                case START:
                    switch(token) {
                        case START_OBJECT:
                            state = State.IN_ALLOC_MAP;
                            break;
                        case START_ARRAY:
                            break;
                        case END_ARRAY:
                            break;
                        default:
                            throw new RuntimeException("Unexpected " + token);
                    }
                    break;
                case IN_ALLOC_MAP:
                    switch(token) {
                        case FIELD_NAME:
                            break;
                        case VALUE_NUMBER_INT:
                            if(parser.getCurrentName().equals("totalBytes")) {
                                long bytes = parser.getLongValue();
                                totalBytes += bytes;
                                counts.add(new Allocation(counts.size(), null, bytes, null));
                            }
                            break;
                        case VALUE_STRING:
                            break;
                        case START_ARRAY:
                            state = State.IN_STACK;
                            break;
                        case END_OBJECT:
                            state = State.START;
                            break;
                        default:
                            throw new RuntimeException("Unexpected " + token);
                    }
                    break;
                case IN_STACK:
                    switch(token) {
                        case END_ARRAY:
                            state = State.IN_ALLOC_MAP;
                            break;
                        case VALUE_STRING:
                            break;
                        default:
                            throw new RuntimeException("Unexpected " + token);
                    }
                    break;
            }
        }

        Set<Integer> out = new HashSet<>();
        counts.sort(Comparator.comparingLong(a -> -a.bytesAllocated));
        for (int i = Math.min(skip, counts.size() - 1); i < Math.min(n, counts.size()); i++) {
            out.add(counts.get(i).index);
        }
        return new TotalAndTopIndexes(totalBytes, out);
    }
}
