package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Rpc;

import java.util.Collections;

public class HeartbeatHandler extends ClientAppendEntryHandler {

    public HeartbeatHandler(Rpc rpc) {
        super(rpc);
    }

    @Override
    public void handle(Node context, Message message) {
        appendEntry(context, Collections.emptyList());
    }
}
