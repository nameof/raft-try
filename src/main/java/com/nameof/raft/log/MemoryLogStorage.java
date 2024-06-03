package com.nameof.raft.log;


import java.util.ArrayList;
import java.util.List;

public class MemoryLogStorage implements LogStorage {

    private ArrayList<LogEntry> data = new ArrayList<>();

    @Override
    public LogEntry findByTermAndIndex(int term, int index) {
        if (index >= data.size()) {
            return null;
        }
        LogEntry logEntry = data.get(index);
        return logEntry.getTerm() == term ? logEntry : null;
    }

    @Override
    public LogEntry findByIndex(int index) {
        return data.get(index);
    }

    @Override
    public List<LogEntry> findByIndexAndAfter(int index) {
        return data.subList(index, data.size());
    }

    @Override
    public int deleteAfter(int index) {
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
    public LogEntry getLast() {
        int size = data.size();
        if (size == 0) {
            return null;
        }
        return data.get(size - 1);
    }

    @Override
    public int lastIndex() {
        return data.size() - 1;
    }
}