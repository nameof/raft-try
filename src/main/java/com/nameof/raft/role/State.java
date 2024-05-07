package com.nameof.raft.role;


import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

public interface State {
    Reply onRequestVote(Message message);
    Reply onAppendEntry(Message message);
}