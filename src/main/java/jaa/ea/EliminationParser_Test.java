package jaa.ea;

import jaa.allocation.AllocationLedger;
import org.junit.Test;
import jaa.ea.EliminatedAllocation.Position;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toCollection;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;

public class EliminationParser_Test
{
    @org.junit.Test
    public void shouldParseSimpleElimination() throws Exception
    {
        // Given
        String simpleElimination =
                "Scalar  102\tCheckCastPP\t===  99  97  [[ 266  202  190  148  148 ]]  #org/neo4j/qp/TheThings$Timestamp:NotNull:exact *,iid=85  Oop:org/neo4j/qp/TheThings$Timestamp:NotNull:exact *,iid=85 !jvms: TheThings::test6 @ bci:18\n" +
                "++++ Eliminated: 85 Allocate\n";

        // When
        ArrayList<EliminatedAllocation> found = new EliminationParser().parse(Stream.of(simpleElimination.split("\n"))).collect(toCollection(ArrayList::new));

        // Then
        assertThat(found, equalTo(singletonList(new EliminatedAllocation(
                "org/neo4j/qp/TheThings$Timestamp:NotNull:exact",
                new Position("TheThings", "test6", 18)))));
    }

    @Test
    public void shouldParseMultiPathElimination() throws Exception
    {
        // Given
        String twoHopElimination = "Scalar  697\tCheckCastPP\t===  694  692  [[ 1280  1484  970  917  917  831  831  845  845  1361  1377  1077  1077  954  873  1280  1157  1173  873 ]]  #java/util/ArrayList$Itr:NotNull:exact *,iid=680  Oop:java/util/ArrayList$Itr:NotNull:exact *,iid=680 !orig=[772] !jvms: ArrayList::iterator @ bci:0 TheThings::test4 @ bci:54\n" +
                "++++ Eliminated: 680 Alloca";

        // When
        ArrayList<EliminatedAllocation> found = new EliminationParser()
                .parse(Stream.of(twoHopElimination.split("\n")))
                .collect(toCollection(ArrayList::new));

        // Then
        assertThat(found, equalTo(singletonList(new EliminatedAllocation(
                "java/util/ArrayList$Itr:NotNull:exact",
                new Position("ArrayList", "iterator", 0),
                new Position("TheThings", "test4", 54)))));
    }

    @Test
    public void shouldParseLineWithMultiple_jvms() throws Exception
    {
        // Given
        String line = "Scalar  220\tCheckCastPP\t===  217  215  [[ 2215  2126  2098  2079  2038  300  232  232  2023  2016  1989  1956  1943  1888  1719  1701  1671  1616  1601  1573  1568  485  917  972  1016  1541  1515  1044 Scalar  271\tCheckCastPP\t===  268  266  [[ 1790  1700  1678  1650  1636  351  283  283  1616  1600  1579  1531  1518  1447  1447  1431  1431  1234  1479  1408  1408  1392  1089  1102  1392  1348  1348  1234 ]]  #org/neo4j/cypher/internal/frontend/v3_3/symbols/TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$contains$1:NotNull:exact *,iid=203  Oop: 1387  1387  1273  1273  536  958  1011  1055  1141  1128  1083 ]]  #org/neo4j/cypher/internal/frontend/v3_3/symbols/TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$contains$1org/neo4j/cypher/internal/frontend/v3_3/symbols/TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$contains$1:NotNull:exact *,iid=203 !jvms::NotNull:exact *,iid=254  Oop:org/neo4j/cypher/internal/frontend/v3_3/symbols/TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$contains$1:NotNull:exact *,iid=254 !jvms: TypeSpec::org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$contains @ bci:1 TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$toStream$1::apply @ bci:17 TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$toStream$1::apply @ bci:5 TraversableLike$$anonfun$filterImpl$1::apply @ bci:5\n";

        // When
        ArrayList<EliminatedAllocation> found = new EliminationParser()
                .parse(Stream.of(line.split("\n")))
                .collect(toCollection(ArrayList::new));

        // Then
        assertThat(found, equalTo(singletonList(new EliminatedAllocation(
                "org/neo4j/cypher/internal/frontend/v3_3/symbols/TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$contains$1:NotNull:exact",
                new Position("TypeSpec", "org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$contains", 1),
                new Position("TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$toStream$1", "apply", 17),
                new Position("TypeSpec$$anonfun$org$neo4j$cypher$internal$frontend$v3_3$symbols$TypeSpec$$toStream$1", "apply", 5),
                new Position("TraversableLike$$anonfun$filterImpl$1", "apply", 5)))));
    }

    @Test
    public void shouldParseOtherThing() throws Exception
    {
        // Given
        String line = "Scalar  256\tAllocate\t===  198  79  93  8  1 ( 66  65  26  1  10  11  1  1  1  1  11  1 ) [[ 257  258  259  266  267  268 ]]  rawptr:NotNull ( int:>=0, java/lang/Object:NotNull *, bool, top ) Double::valueOf @ bci:0 Predef$::double2Double @ bci:1 Selectivity$::of @ bci:17 !jvms: Double::valueOf @ bci:0 Predef$::double2Double @ bci:1 Selectivity$::of @ bci:17\n";

        // When
        ArrayList<EliminatedAllocation> found = new EliminationParser()
                .parse(Stream.of(line.split("\n")))
                .collect(toCollection(ArrayList::new));

        // Then
        assertThat(found, equalTo(singletonList(new EliminatedAllocation(
                // Note that this is rather unhelpful; need to read up on how to interpret the spec that follows
                // this in the output; eg. in this test it looks like it's saying it's a pointer to something that
                // is an integer >= 0? Or maybe that's the input requirements for this elimination to apply?
                "rawptr:NotNull",

                new Position("Double", "valueOf", 0),
                new Position("Predef$", "double2Double", 1),
                new Position("Selectivity$", "of", 17)))));
    }

    @Test
    public void shouldConsiderRawPtrEliminations() throws Exception
    {
        // Note; I'm not entirely on the level with rawptr; I think it shows up when there is an
        // allocation that doesn't actually end up shipping the reference around at all, or something?
        // In any case, matching on these as-is is likely to lead to false positives; what needs to
        // happen is to use Java 9 APIs instead to get better stack traces, and couple that with
        // pulling out the line numbers via the byte code indexes we get in the allocation outputs.
        // That way we can match the exact allocation point and avoid errors.

        // Given
        String line = "Scalar  256\tAllocate\t===  198  79  93  8  1 ( 66  65  26  1  10  11  1  1  1  1  11  1 ) [[ 257  258  259  266  267  268 ]]  rawptr:NotNull ( int:>=0, java/lang/Object:NotNull *, bool, top ) Double::valueOf @ bci:0 Predef$::double2Double @ bci:1 Selectivity$::of @ bci:17 !jvms: Double::valueOf @ bci:0 Predef$::double2Double @ bci:1 Selectivity$::of @ bci:17\n";

        // When
        Predicate<AllocationLedger.Record> pred = EliminationParser.predicateThatExcludes(new EliminationParser()
                .parse(Stream.of(line.split("\n"))));

        // Then
        assertFalse(pred.test(new AllocationLedger.Record(
                "java/lang/Object",
                1337,
                "Double", "Predef$", "Selectivity$")));
        assertTrue(pred.test(new AllocationLedger.Record(
                "java/lang/Object",
                1337,
                "Unrelated", "Predef$", "Selectivity$")));
    }

}
