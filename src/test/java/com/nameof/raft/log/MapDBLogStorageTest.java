package com.nameof.raft.log;

import cn.hutool.core.io.FileUtil;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import java.io.File;
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
    public void t() {
        DB db = DBMaker
                .fileDB(new File(FileUtil.getTmpDir(), "aaaa"))
                .make();
        HTreeMap<Integer, String> map = db
                .hashMap("collectionName", Serializer.INTEGER, Serializer.STRING)
                .createOrOpen();

        map.put(1,"one");
        map.put(2,"two");
        System.out.println(map.get(1));
        db.rollback();

        System.out.println(map.get(1));

        db.close();
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