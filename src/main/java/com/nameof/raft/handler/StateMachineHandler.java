package com.nameof.raft.handler;

import com.nameof.raft.log.LogEntry;

import java.util.Collection;

public interface StateMachineHandler {
    void apply(int commitIndex, Collection<LogEntry>entries);
}
