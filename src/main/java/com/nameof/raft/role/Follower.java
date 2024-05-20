package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.log.LogStorage;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

public class Follower implements State {

    private LogStorage logStorage;

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
    public Reply.AppendEntryReply onAppendEntry(Node context, Message.AppendEntryMessage message) {
        if (message.getTerm() < context.getCurrentTerm()) {
            return new Reply.AppendEntryReply(context.getCurrentTerm(), false);
        }

        // 重置选举超时定时器
        context.resetElectionTimeoutTimer();

        // 更新任期并重置投票给的候选者（如果请求任期更大）
        context.setCurrentTerm(message.getTerm());
        context.setVotedFor(null);

        if (message.getEntries().isEmpty()) {
            return new Reply.AppendEntryReply(context.getCurrentTerm(), true);
        }

        /**
         * Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm ($5.3)
         * If an existing entry conflicts with a new one (same index but different terms), delete the existing entry and all thatfollow it (§5.3)
         * Append any new entries not already in the log
         * If leaderCommit>commitIndex,set commitIndex=min(leaderCommit, index of last new entry)
         */
        if (!isLogConsistent(message.getPrevLogIndex(), message.getPrevLogTerm())) {
            return new Reply.AppendEntryReply(context.getCurrentTerm(), false);
        }

        int leaderNextIndex = message.getPrevLogIndex() + 1;
        LogEntry first = message.getEntries().get(0);
        if (logConflict(leaderNextIndex, first.getTerm())) {
            logStorage.deleteAfter(leaderNextIndex);
        }

        // 追加新日志
        int newestLogIndex = appendEntriesFromRequest(message);

        // 如果Leader的commitIndex大于当前的，更新本地的commitIndex
        if (message.getLeaderCommit() > context.getCommitIndex()) {
            context.setCommitIndex(Math.min(message.getLeaderCommit(), newestLogIndex));
        }

        // 响应成功
        return new Reply.AppendEntryReply(context.getCurrentTerm(), true);
    }

    private boolean logConflict(int index, int term) {
        LogEntry entry = logStorage.findByIndex(index);
        return entry != null && entry.getTerm() != term;
    }

    private boolean isLogConsistent(int prevLogIndex, int prevLogTerm) {
        return logStorage.findByTermAndIndex(prevLogTerm, prevLogIndex) != null;
    }

    private int appendEntriesFromRequest(Message.AppendEntryMessage message) {
        return logStorage.append(message.getEntries());
    }
}