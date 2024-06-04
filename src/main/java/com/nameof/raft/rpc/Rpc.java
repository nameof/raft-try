package com.nameof.raft.rpc;

import com.nameof.raft.config.NodeInfo;

public interface Rpc {
    void startServer();
    Reply.AppendEntryReply appendEntry(NodeInfo info, Message.AppendEntryMessage message);
}