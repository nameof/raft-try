package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;
import com.nameof.raft.rpc.Rpc;

public class RequestVoteHandler implements Handler {

    private final Rpc rpc;

    public RequestVoteHandler(Rpc rpc) {
        this.rpc = rpc;
    }

    @Override
    public void handle(Node context, Message message) {
        Reply.RequestVoteReply reply = context.getState().onRequestVote(context, (Message.RequestVoteMessage) message);
        reply.setExtra(message.getExtra());
        rpc.sendReply(reply);
    }
}
