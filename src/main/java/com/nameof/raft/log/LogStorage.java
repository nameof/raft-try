package com.nameof.raft.log;

import java.util.List;

public interface LogStorage {
    LogEntry findByTermAndIndex(int term, int index);

    /**
     * 删除指定位置及以后的所有日志
     * @param index
     * @return
     */
    int deleteAfter(int index);

    void append(List<LogEntry> logs);

    LogEntry getLast();

    int lastIndex();
}
