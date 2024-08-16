package com.nameof.raft.log;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.List;

public class MapDBLogStorageTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    MapDBLogStorage logStorage;

    @Before
    public void setUp() {
        logStorage = new MapDBLogStorage(tempFolder.getRoot());
    }

    @After
    public void after() {
        logStorage.clear();
        logStorage.close();
    }

    @Test
    public void testFindByIndexAndAfter() throws Exception {
        logStorage.append(Collections.singletonList(new LogEntry()));

        List<LogEntry> result = logStorage.findByIndexAndAfter(1);
        Assert.assertEquals(1, result.size());

        result = logStorage.findByIndexAndAfter(2);
        Assert.assertEquals(0, result.size());
    }

    @Test
    public void testDeleteAfter() throws Exception {
        logStorage.append(Collections.singletonList(new LogEntry()));

        int result = logStorage.deleteAfter(1);

        Assert.assertEquals(1, result);
        Assert.assertNull(logStorage.getLast());
    }

    @Test
    public void testAppend() throws Exception {
        logStorage.append(Collections.singletonList(new LogEntry()));

        int result = logStorage.append(1, Collections.singletonList(new LogEntry(1, null, "")));
        Assert.assertEquals(1, result);
        Assert.assertEquals(1, logStorage.findByIndexAndAfter(1).size());

        Assert.assertEquals(new LogEntry(1, null, ""), logStorage.findByIndex(1));
    }
}