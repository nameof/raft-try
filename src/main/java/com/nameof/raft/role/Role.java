package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

public interface Role {
    void init(Node context);
    Reply.RequestVoteReply onRequestVote(Node context, Message.RequestVoteMessage message);
    Reply.AppendEntryReply onAppendEntry(Node context, Message.AppendEntryMessage message);
    RoleType getRole();
}