package com.nameof.raft.rpc;

public enum MessageType {
    RequestVote,
    AppendEntry,

    // 内部消息
    ElectionTimeout,
    Heartbeat,
    ClientAppendEntry
}
