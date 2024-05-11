package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

public class Follower implements State {
    @Override
    public Reply.RequestVoteReply onRequestVote(Node context, Message.RequestVoteMessage message) {
        // 请求任期小于当前任期，拒绝投票
        if (message.getTerm() < context.getCurrentTerm()) {
            return new Reply.RequestVoteReply(context.getCurrentTerm(), false);
        }

        // 更新任期（请求任期大于当前任期），已是follower无需切换状态
        context.setCurrentTerm(message.getTerm());

        // FIXME 投票后，是否有必要重置选举超时定时器？
        // 未投票或曾投票给该候选者，且候选者的日志至少和本地一样新
        if ((context.getVotedFor() == null || context.getVotedFor().equals(message.getCandidateId()))
                && candidateLogIsNewerOrEqual(context, message)) {
            context.setVotedFor(message.getCandidateId());
            return new Reply.RequestVoteReply(context.getCurrentTerm(), true);
        } else {
            return new Reply.RequestVoteReply(context.getCurrentTerm(), false);
        }
    }

    private boolean candidateLogIsNewerOrEqual(Node context, Message.RequestVoteMessage message) {
        return message.getLastLogTerm() >= context.getLastLogTerm() && message.getLastLogIndex() >= context.getLastLogIndex();
    }

    @Override
    public Reply.AppendEntryReply onAppendEntry(Node context, Message.RequestVoteMessage message) {
        return null;
    }
}