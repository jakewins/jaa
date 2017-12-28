package jaa.ea;

import jaa.allocation.AllocationLedger;
import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.spliteratorUnknownSize;

public class EliminationParser
{
    public Stream<EliminatedAllocation> parse(Stream<String> outputLines)
    {
        return outputLines
                .filter(l -> l.startsWith("Scalar  ") && l.contains("!jvms:"))
                .map(l -> {
                    try {
                        String objectName;
                        if(l.contains("rawptr:NotNull"))
                        {
                            objectName = "rawptr:NotNull";
                        }
                        else
                        {
                            objectName = l.split("#")[1].split(" ")[0];
                        }

                        String[] split = l.split("!jvms:");
                        String[] tokens = split[split.length - 1].trim().split(" ");
                        EliminatedAllocation.Position[] positions = new EliminatedAllocation.Position[tokens.length / 3];

                        for (int posIdx = 0; posIdx < positions.length; posIdx++) {
                            String[] classAndMethod = tokens[posIdx * 3].split("::");
                            positions[posIdx] = new EliminatedAllocation.Position(
                                    classAndMethod[0],
                                    classAndMethod[1],
                                    Integer.parseInt(tokens[posIdx * 3 + 2].split(":")[1])
                            );
                        }
                        return new EliminatedAllocation(objectName, positions);
                    } catch(Exception e)
                    {
                        throw new RuntimeException("Failed to parse line: '"+l+"'", e);
                    }
                });
    }

    public static Predicate<AllocationLedger.Record> predicateThatExcludes(Stream<EliminatedAllocation> source) {
        Map<String, List<EliminatedAllocation>> eliminated = new HashMap<>();
        eofAsStreamEnd(source).forEach(e -> {
            String justName = e.objectName().split(":")[0];
            List<EliminatedAllocation> allocations = eliminated.computeIfAbsent(justName, k -> new LinkedList<>());
            allocations.add(e);
        });

        return allocation -> {
            String obj = allocation.getObj();

            List<EliminatedAllocation> eliminations = eliminated.get(obj);
            if(eliminations == null) {
                // Check if there are rawptr allocations that match the stack trace instead..
                eliminations = eliminated.get("rawptr");
            }
            if (eliminations != null) {
                long count = eliminations
                        .stream()
                        .filter(stackTracesMatch(allocation.getStackTrace()))
                        .count();
                if (count >= 1) {
                    // Not sure that > is ok here; overall, need to swap to Java 9 so we can get stack
                    // traces with method names from the allocation tracker, class-names only is prone to error.
                    return false;
                }
            }
            return true;
        };
    }

    private static <T> Stream<T> eofAsStreamEnd(Stream<T> in) {
        Iterator<T> source = Spliterators.iterator(in.spliterator());
        return StreamSupport.stream(spliteratorUnknownSize(new Iterator<T>() {
            @Override
            public boolean hasNext() {
                try {
                    return source.hasNext();
                } catch(UncheckedIOException e) {
                    if(e.getMessage().contains("Stream closed")) {
                        return false;
                    } else {
                        throw e;
                    }
                }
            }

            @Override
            public T next() {
                return source.next();
            }
        }, Spliterator.ORDERED), false);
    }

    private static Predicate<EliminatedAllocation> stackTracesMatch(List<String> allocationStackTrace) {
        return elimination -> {
            for (int i = 0; i < elimination.allocationPoint().size(); i++) {
                String allocationStackFrame = allocationStackTrace.get(i);
                String eliminationStackFrame = elimination.allocationPoint().get(i).className;

                if(!allocationStackFrame.contains(eliminationStackFrame)) {
                    return false;
                }
            }

            return true;
        };
    }

    public static void main(String ... argv) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Stream<String> lines = Files.lines(Paths.get(argv[0]));

        Map<String, Object> elimination = new HashMap<>();
        new EliminationParser().parse(lines).forEach( e -> {
            try {
                elimination.put("objectName", e.objectName());
                elimination.put("allocationPoint", e.allocationPoint());
                System.out.println(mapper.writeValueAsString(elimination));
            } catch (IOException e1) {
                throw new RuntimeException(e1);
            }
        });
    }
}
