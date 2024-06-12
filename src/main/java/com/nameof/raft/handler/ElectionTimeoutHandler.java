package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.role.Candidate;
import com.nameof.raft.rpc.Message;

public class ElectionTimeoutHandler implements Handler {

    @Override
    public void handle(Node context, Message message) {
        context.setState(new Candidate());

        requestVote(context);
    }

    private void requestVote(Node context) {

    }
}