package com.nameof.raft.log;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class MemoryLogStorageTest {
    MemoryLogStorage memoryLogStorage;

    @Before
    public void setUp() {
        memoryLogStorage = new MemoryLogStorage();
    }

    @Test
    public void testFindByIndexAndAfter() throws Exception {
        memoryLogStorage.append(Collections.singletonList(new LogEntry()));

        List<LogEntry> result = memoryLogStorage.findByIndexAndAfter(1);
        Assert.assertEquals(1, result.size());

        result = memoryLogStorage.findByIndexAndAfter(2);
        Assert.assertEquals(0, result.size());
    }

    @Test
    public void testDeleteAfter() throws Exception {
        memoryLogStorage.append(Collections.singletonList(new LogEntry()));

        int result = memoryLogStorage.deleteAfter(1);

        Assert.assertEquals(1, result);
        Assert.assertNull(memoryLogStorage.getLast());
    }

    @Test
    public void testAppend() throws Exception {
        memoryLogStorage.append(Collections.singletonList(new LogEntry()));

        int result = memoryLogStorage.append(1, Collections.singletonList(new LogEntry(1, null, "")));
        Assert.assertEquals(1, result);
        Assert.assertEquals(1, memoryLogStorage.findByIndexAndAfter(1).size());

        Assert.assertEquals(new LogEntry(1, null, ""), memoryLogStorage.findByIndex(1));
    }
}