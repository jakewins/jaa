package jaa.internal.runner;

import jaa.internal.allocation.AllocationLedger;
import org.junit.Test;

import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

public class MethodAllocationAnalyzer_Test {
    @Test
    public void allocationSampleIsEmptyIfUserDoesNotAllocateAnything() throws Exception
    {
        // When
        AllocationLedger ledger = new MethodAllocationAnalyzer()
                .analyze(getClass().getMethod("methodThatDoesNothing"));

        // Then
        assertEquals("",
                ledger.records()
                .map(AllocationLedger.Record::toString)
                .collect(Collectors.joining("\n")));
    }

    public void methodThatDoesNothing() {

    }
}
