package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;
import com.nameof.raft.rpc.Rpc;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AppendEntryHandler implements Handler {

    private final Rpc rpc;

    public AppendEntryHandler(Rpc rpc) {
        this.rpc = rpc;
    }

    @Override
    public void handle(Node context, Message message) {
        Reply.AppendEntryReply reply = context.getRole().onAppendEntry(context, (Message.AppendEntryMessage) message);
        reply.setClientExtra(message.getClientExtra());
        rpc.sendReply(reply);
    }
}
