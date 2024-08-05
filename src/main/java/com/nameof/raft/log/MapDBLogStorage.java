package com.nameof.raft.log;

import cn.hutool.core.io.FileUtil;
import cn.hutool.json.JSONUtil;
import com.nameof.raft.config.Configuration;
import lombok.extern.slf4j.Slf4j;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.IndexTreeList;
import org.mapdb.Serializer;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class MapDBLogStorage implements LogStorage {
    private final Configuration config = Configuration.get();
    private final DB db;
    private final IndexTreeList<String> data;

    public MapDBLogStorage() {
        File parentDir = FileUtil.getTmpDir();
        parentDir = new File(parentDir, "raft-try" + File.separator + config.getId());
        parentDir.mkdirs();
        this.db = DBMaker
                .fileDB(new File(parentDir, "data"))
                .checksumHeaderBypass()
                .make();
        this.data = db.indexTreeList("logs", Serializer.STRING).createOrOpen();

        Runtime.getRuntime().addShutdownHook(new Thread(db::close));
    }

    @Override
    public LogEntry findByIndex(int index) {
        if (index >= data.size()) {
            return null;
        }
        return obj(data.get(index));
    }

    @Override
    public List<LogEntry> findByIndexAndAfter(int index) {
        if (index >= data.size()) {
            log.error("index is greater than data size");
            return new ArrayList<>();
        }
        return subList(index, data.size());
    }

    private List<LogEntry> subList(int index, int end) {
        List<String> result = data.subList(index, end);
        return result.stream().map(this::obj).collect(Collectors.toList());
    }

    @Override
    public int deleteAfter(int index) {
        int size = data.size();
        if (index >= size) {
            return 0;
        }
        int result = 0;
        while (index < data.size()) {
            data.removeAt(index);
            result++;
        }
        return result;
    }

    @Override
    public int append(List<LogEntry> logs) {
        this.data.addAll(logs.stream().map(this::string).collect(Collectors.toList()));
        return this.data.size() - 1;
    }

    @Override
    public int append(int index, List<LogEntry> logs) {
        int size = this.data.size();
        if (index < size) {
            deleteAfter(index);
        } else if (index > size) {
            log.error("append refuse, index: {}, current size: {}", index, size);
            return -1;
        }
        append(logs);
        return data.size() - 1;
    }

    @Override
    public LogEntry getLast() {
        int size = data.size();
        if (size == 0) {
            return null;
        }
        return findByIndex(size - 1);
    }

    @Override
    public int lastIndex() {
        return data.size() - 1;
    }

    private LogEntry obj(String s) {
        return JSONUtil.toBean(s, LogEntry.class);
    }

    private String string(LogEntry log) {
        return JSONUtil.toJsonStr(log);
    }

    public void clear() {
        data.clear();
    }

    public void close() {
        this.db.close();
    }
}