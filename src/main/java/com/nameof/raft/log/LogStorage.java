package com.nameof.raft.log;

import java.util.List;

public interface LogStorage {
    LogEntry findByIndex(int index);

    List<LogEntry> findByIndexAndAfter(int index);

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

    /**
     * 从指定位置插入日志，如已存在日志，则删除index之后所有旧日志再插入
     * @return 最新的日志索引
     */
    int append(int index, List<LogEntry> logs);

    LogEntry getLast();

    int lastIndex();
}
