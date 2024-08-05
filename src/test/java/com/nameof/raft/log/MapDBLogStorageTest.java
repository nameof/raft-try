package com.nameof.raft.log;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

public class MapDBLogStorageTest {
    MapDBLogStorage logStorage;

    @Before
    public void setUp() {
        logStorage = new MapDBLogStorage();
    }

    @After
    public void after() {
        logStorage.clear();
        logStorage.close();
    }

    @Test
    public void testFindByIndexAndAfter() throws Exception {
        logStorage.append(Collections.singletonList(new LogEntry()));

        List<LogEntry> result = logStorage.findByIndexAndAfter(0);
        Assert.assertEquals(1, result.size());

        result = logStorage.findByIndexAndAfter(1);
        Assert.assertEquals(0, result.size());
    }

    @Test
    public void testDeleteAfter() throws Exception {
        logStorage.append(Collections.singletonList(new LogEntry()));

        int result = logStorage.deleteAfter(0);

        Assert.assertEquals(1, result);
        Assert.assertNull(logStorage.getLast());
    }

    @Test
    public void testAppend() throws Exception {
        logStorage.append(Collections.singletonList(new LogEntry()));

        int result = logStorage.append(0, Collections.singletonList(new LogEntry(1, null)));
        Assert.assertEquals(0, result);
        Assert.assertEquals(1, logStorage.findByIndexAndAfter(0).size());

        Assert.assertEquals(new LogEntry(1, null), logStorage.findByIndex(0));
    }
}