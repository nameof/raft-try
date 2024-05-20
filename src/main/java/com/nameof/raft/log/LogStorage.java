package com.nameof.raft.log;

import java.util.List;

public interface LogStorage {
    LogEntry findByTermAndIndex(int term, int index);

    LogEntry findByIndex(int index);

    /**
     * 删除指定位置及以后的所有日志
     * @param index
     * @return
     */
    int deleteAfter(int index);

    /**
     * @return 最新的日志索引
     */
    int append(List<LogEntry> logs);

    LogEntry getLast();

    int lastIndex();
}
