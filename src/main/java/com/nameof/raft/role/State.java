package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

public interface State {
    Reply onRequestVote(Node context, Message.RequestVoteMessage message);
    Reply onAppendEntry(Node context, Message.RequestVoteMessage message);
}