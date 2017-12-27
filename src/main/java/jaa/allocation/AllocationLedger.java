package jaa.allocation;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.ObjectWriter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static java.util.stream.Collectors.toCollection;

/** Data structure for storing allocations */
public class AllocationLedger
{
    static class Record
    {
        private final List<String> stackTrace;
        private final String objectDescription;

        AtomicLong totalBytes = new AtomicLong();
        AtomicLong allocs = new AtomicLong();

        Record(List<String> stackTrace, String objectDescription) {
            this.stackTrace = stackTrace;
            this.objectDescription = objectDescription;
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
    }

    private final Map<String, Map<List<String>, Record>> records = new ConcurrentHashMap<>();

    public AllocationLedger() {

    }

    public AllocationLedger(AllocationLedger ... sources) {
        for (AllocationLedger source : sources) {
            records.putAll(source.records);
        }
    }

    public void record(String objectDescription, long bytes, List<String> stackTrace)
    {
        Map<List<String>, Record> stackTraces = records.computeIfAbsent(objectDescription, k -> new ConcurrentHashMap<>());

        Record record = stackTraces.get(stackTrace);
        if(record == null) {
            List<String> storedTrace = new ArrayList<>(stackTrace);
            record = stackTraces.computeIfAbsent(storedTrace, k -> new Record(storedTrace, objectDescription));
        }
        record.increment(1, bytes);
    }

    public void write(File path) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer();
        writer.writeValue(path, records
                .values()
                .stream()
                .flatMap(r -> r.values().stream())
                .collect(toCollection(LinkedList::new)));
    }
}
