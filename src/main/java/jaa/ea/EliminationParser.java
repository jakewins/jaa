package jaa.ea;

import org.codehaus.jackson.map.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

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

    public static Map<String, List<EliminatedAllocation>> toMapOfEliminations(Stream<EliminatedAllocation> eliminations) {
        Map<String, List<EliminatedAllocation>> eliminated = new HashMap<>();
        eliminations.forEach(e -> {
            String justName = e.objectName().split(":")[0];

            List<EliminatedAllocation> allocations = eliminated.computeIfAbsent(justName, k -> new LinkedList<>());
            allocations.add(e);
        });
        return eliminated;
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
