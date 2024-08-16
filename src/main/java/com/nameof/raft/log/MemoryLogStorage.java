package com.nameof.raft.log;


import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class MemoryLogStorage implements LogStorage {

    private ArrayList<LogEntry> data = new ArrayList<>();

    @Override
    public LogEntry findByIndex(int index) {
        index -= 1;
        if (index >= data.size() || index < 0) {
            log.warn("invalid index {}", index);
            return null;
        }
        return data.get(index);
    }

    @Override
    public List<LogEntry> findByIndexAndAfter(int index) {
        index -= 1;
        if (index >= data.size() || index < 0) {
            log.warn("index is greater than data size");
            return Collections.emptyList();
        }
        return data.subList(index, data.size());
    }

    @Override
    public int deleteAfter(int index) {
        index -= 1;
        int size = data.size();
        if (index >= size) {
            return 0;
        }
        List<LogEntry> subList = data.subList(index, size);
        int count = subList.size();
        subList.clear();
        return count;
    }

    @Override
    public int append(List<LogEntry> logs) {
        data.addAll(logs);
        return data.size() - 1;
    }

    @Override
    public int append(int index, List<LogEntry> logs) {
        int realIndex = index - 1;
        int size = data.size();
        if (realIndex < size) {
            deleteAfter(index);
        } else if (realIndex > size) {
            return -1;
        }
        append(logs);
        return data.size();
    }

    @Override
    public LogEntry getLast() {
        int size = data.size();
        if (size == 0) {
            return null;
        }
        return data.get(size - 1);
    }

    @Override
    public int lastIndex() {
        return data.size();
    }
}