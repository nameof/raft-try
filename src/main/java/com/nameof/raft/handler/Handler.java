package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;

public interface Handler {
    void handle(Node context, Message message);
}