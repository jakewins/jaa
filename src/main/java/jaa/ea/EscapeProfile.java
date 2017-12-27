package jaa.ea;

import jaa.allocation.AllocationLedger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.function.Predicate;

import static jaa.ea.EliminationParser.predicateThatExcludes;

public class EscapeProfile
{
    public static void main(String ... argv) throws IOException {
        String escapeAnalysisStdout = argv[0];
        String allocationProfile = argv[1];
        String output = argv[2];

        Predicate<AllocationLedger.Record> excludeEliminated = predicateThatExcludes(new EliminationParser()
                .parse(Files.lines(Paths.get(escapeAnalysisStdout))));

        AllocationLedger
                .read(new File(allocationProfile))
                .filter(excludeEliminated).write(new File(output));
    }
}
