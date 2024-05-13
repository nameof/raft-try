package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

public class Candidate implements State {
    @Override
    public Reply.RequestVoteReply onRequestVote(Node context, Message.RequestVoteMessage message) {
        // 请求任期大于当前任期，转为follower
        if (message.getTerm() > context.getCurrentTerm()) {
            context.setCurrentTerm(message.getTerm());

            State newState = new Follower();
            context.setState(newState);
            return newState.onRequestVote(context, message);
        }

        return new Reply.RequestVoteReply(context.getCurrentTerm(), false);
    }

    @Override
    public Reply.AppendEntryReply onAppendEntry(Node context, Message.AppendEntryMessage message) {
        if (message.getTerm() > context.getCurrentTerm()) {
            context.setCurrentTerm(message.getTerm());

            State newState = new Follower();
            context.setState(newState);
            return newState.onAppendEntry(context, message);
        }

        return new Reply.AppendEntryReply(context.getCurrentTerm(), false);
    }
}