package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Candidate implements State {
    @Override
    public void init(Node context) {
        context.setNextIndex(null);
        context.setMatchIndex(null);

        context.setCurrentTerm(context.getCurrentTerm() + 1);
        context.setVotedFor(context.getId());

        context.stopHeartbeatTimer();
        context.resetElectionTimeoutTimer();
    }

    @Override
    public Reply.RequestVoteReply onRequestVote(Node context, Message.RequestVoteMessage message) {
        log.info("onRequestVote 请求任期{}，当前任期{}", message.getTerm(), context.getCurrentTerm());
        // 请求任期大于当前任期，转为follower
        if (message.getTerm() > context.getCurrentTerm()) {
            context.setCurrentTerm(message.getTerm());

            State newState = new Follower();
            context.setState(newState);
            return newState.onRequestVote(context, message);
        }

        // 同任期选举，进行退避
        if (message.getTerm() == context.getCurrentTerm()) {
            context.resetElectionTimeoutTimer();
            return new Reply.RequestVoteReply(context.getCurrentTerm(), false);
        }

        return new Reply.RequestVoteReply(context.getCurrentTerm(), false);
    }

    @Override
    public Reply.AppendEntryReply onAppendEntry(Node context, Message.AppendEntryMessage message) {
        log.info("onAppendEntry 请求任期{}，当前任期{}", message.getTerm(), context.getCurrentTerm());
        if (message.getTerm() >= context.getCurrentTerm()) {
            context.setCurrentTerm(message.getTerm());

            State newState = new Follower();
            context.setState(newState);
            return newState.onAppendEntry(context, message);
        }

        return new Reply.AppendEntryReply(context.getCurrentTerm(), false);
    }

    @Override
    public RoleType getRole() {
        return RoleType.Candidate;
    }
}