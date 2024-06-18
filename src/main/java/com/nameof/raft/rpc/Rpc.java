package com.nameof.raft.rpc;

import com.nameof.raft.config.NodeInfo;

public interface Rpc {
    void startServer();
    Reply.AppendEntryReply appendEntry(NodeInfo info, Message.AppendEntryMessage message);
    Reply.RequestVoteReply requestVote(NodeInfo info, Message.RequestVoteMessage message);
    void sendReply(Reply reply);
}