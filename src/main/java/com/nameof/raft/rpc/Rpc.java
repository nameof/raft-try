package com.nameof.raft.rpc;

public interface Rpc {
    void startServer();
    Reply.AppendEntryReply appendEntry(Message.AppendEntryMessage message);
}