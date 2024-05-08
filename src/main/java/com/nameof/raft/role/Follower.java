package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

public class Follower implements State {
    @Override
    public Reply onRequestVote(Node context, Message.RequestVoteMessage message) {
        return null;
    }

    @Override
    public Reply onAppendEntry(Node context, Message.RequestVoteMessage message) {
        return null;
    }
}