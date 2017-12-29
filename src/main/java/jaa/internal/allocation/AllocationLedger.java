package jaa.internal.allocation;

import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.StreamSupport.stream;

/** Data structure for storing allocations */
public class AllocationLedger
{
    /**
     * Create a new ledger with the contents of this ledger, sans the contents
     * of the provided ledger.
     * @param other
     * @return
     */
    public AllocationLedger subtract(AllocationLedger other) {
        Stream<Record> result = records()
                .map(r -> {
                    Map<List<String>, Record> otherRecords = other.records.get(r.getObj());
                    if (otherRecords == null) {
                        return r;
                    }

                    Record otherRecord = otherRecords.get(r.getStackTrace());
                    if (otherRecord == null) {
                        return r;
                    }

                    return new Record(r.getObj(),
                            r.getTotalBytes() - otherRecord.getTotalBytes(),
                            r.getObjectsAllocated() - otherRecord.getObjectsAllocated(),
                            r.getStackTrace());
                })
                .filter(r -> r.getObjectsAllocated() > 0 && r.getTotalBytes() > 0);
        return new AllocationLedger(result);
    }

    public static class Record
    {
        private final List<String> stackTrace;
        private final String objectDescription;

        AtomicLong totalBytes = new AtomicLong();
        AtomicLong allocs = new AtomicLong();

        public Record(String objectDescription, long totalBytes, long allocs, String ... stackTrace) {
            this(objectDescription, totalBytes, allocs, Arrays.asList(stackTrace));
        }

        public Record(String objectDescription, long totalBytes, long allocs, List<String> stackTrace) {
            this.stackTrace = stackTrace;
            this.objectDescription = objectDescription;
            this.totalBytes.set(totalBytes);
            this.allocs.set(allocs);
        }

        public void increment(int addAllocs, long addBytes) {
            allocs.addAndGet(addAllocs);
            totalBytes.addAndGet(addBytes);
        }

        public List<String> getStackTrace() {
            return stackTrace;
        }

        public String getObj() {
            return objectDescription;
        }

        public long getTotalBytes() {
            return totalBytes.get();
        }

        public long getObjectsAllocated() {
            return allocs.get();
        }

        @Override
        public String toString() {
            return "Record{" +
                    "objectDescription='" + objectDescription + '\'' +
                    ", stackTrace=" + stackTrace +
                    ", totalBytes=" + totalBytes +
                    ", allocs=" + allocs +
                    '}';
        }
    }

    private final Map<String, Map<List<String>, Record>> records = new ConcurrentHashMap<>();

    public AllocationLedger() {

    }

    public AllocationLedger(AllocationLedger ... sources) {
        for (AllocationLedger source : sources) {
            records.putAll(source.records);
        }
    }

    public AllocationLedger(Stream<Record> records) {
        records.forEach(r -> {
            record(r.objectDescription, r.totalBytes.get(), r.stackTrace);
        });
    }

    public void record(String objectDescription, long bytes, List<String> stackTrace)
    {
        Map<List<String>, Record> stackTraces = records.computeIfAbsent(objectDescription, k -> new ConcurrentHashMap<>());

        Record record = stackTraces.get(stackTrace);
        if(record == null) {
            List<String> storedTrace = new ArrayList<>(stackTrace);
            record = stackTraces.computeIfAbsent(storedTrace, k -> new Record(objectDescription, 0, 0, storedTrace));
        }
        record.increment(1, bytes);
    }

    public AllocationLedger filter(Predicate<Record> include)
    {
        return new AllocationLedger(records().filter(include));
    }

    public Stream<Record> records() {
        return records.values().stream().flatMap(m -> m.values().stream());
    }

    public long totalBytes() {
        return records().mapToLong(Record::getTotalBytes).sum();
    }

    public void write(Path path) throws IOException {
        write(path.toFile());
    }

    public void write(File path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer();
        writer.writeValue(path, records()
                .collect(toCollection(LinkedList::new)));
    }

    public static AllocationLedger read(Path path) throws IOException {
        return read(path.toFile());
    }

    public static AllocationLedger read(File file) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return read(mapper.readTree(file));
    }

    public static AllocationLedger read(JsonNode value) {
        Stream<JsonNode> allocations = stream(value.spliterator(), false);
        return allocations.reduce(new AllocationLedger(), (l, allocation) -> {
            LinkedList<String> stack = new LinkedList<>();
            allocation.get("stackTrace").forEach(s -> stack.add(s.asText()));
            l.record(
                    allocation.get("obj").asText(),
                    allocation.get("totalBytes").asInt(),
                    stack);
            return l;
        }, AllocationLedger::new);
    }
}
