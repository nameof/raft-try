package com.nameof.raft.role;


import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

public class Candidate implements State {
    @Override
    public Reply onRequestVote(Message message) {
        return null;
    }

    @Override
    public Reply onAppendEntry(Message message) {
        return null;
    }
}