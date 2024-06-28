package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Follower implements State {

    @Override
    public void init(Node context) {
        context.setNextIndex(null);
        context.setMatchIndex(null);
        context.setVotedFor(null);
        context.stopHeartbeatTimer();
        context.resetElectionTimeoutTimer();
    }

    @Override
    public Reply.RequestVoteReply onRequestVote(Node context, Message.RequestVoteMessage message) {
        log.info("onRequestVote 请求任期{}，当前任期{}", message.getTerm(), context.getCurrentTerm());

        // 请求任期小于当前任期，拒绝投票
        if (message.getTerm() < context.getCurrentTerm()) {
            log.info("拒绝投票");
            return new Reply.RequestVoteReply(context.getCurrentTerm(), false);
        }

        // TODO 任期内只投票一次

        // 更新任期（请求任期大于当前任期），已是follower无需切换状态
        context.setCurrentTerm(message.getTerm());

        // FIXME 投票后，是否有必要重置选举超时定时器？
        // 未投票或曾投票给该候选者，且候选者的日志至少和本地一样新
        if ((context.getVotedFor() == null || context.getVotedFor().equals(message.getCandidateId()))
                && candidateLogIsNewerOrEqual(context, message)) {
            context.setVotedFor(message.getCandidateId());
            log.info("日志符合，赞成投票");
            return new Reply.RequestVoteReply(context.getCurrentTerm(), true);
        } else {
            log.info("日志较旧，拒绝投票");
            return new Reply.RequestVoteReply(context.getCurrentTerm(), false);
        }
    }

    /**
     * 候选人最后一条Log条目的任期号大于本地最后一条Log条目的任期号；
     * 或候选人最后一条Log条目的任期号等于本地最后一条Log条目的任期号，且候选人的Log记录长度大于等于本地Log记录的长度
     * @param context
     * @param message
     * @return
     */
    private boolean candidateLogIsNewerOrEqual(Node context, Message.RequestVoteMessage message) {
        log.info("LastLogTerm：{} {}， LastLogIndex：{} {}", message.getLastLogTerm(), context.getLastLogTerm(), message.getLastLogIndex(), context.getLastLogIndex());
        if (message.getLastLogTerm() > context.getLastLogTerm()) {
            return true;
        }
        if (message.getLastLogTerm() == context.getLastLogTerm()) {
            return message.getLastLogIndex() >= context.getLastLogIndex();
        }
        return false;
    }

    /**
     * 响应给leader的matchIndex，也就是本地最新的日志索引
     */
    @Override
    public Reply.AppendEntryReply onAppendEntry(Node context, Message.AppendEntryMessage message) {

        log.info("onAppendEntry 请求任期{}，当前任期{}", message.getTerm(), context.getCurrentTerm());
        if (message.getTerm() < context.getCurrentTerm()) {
            log.info("拒绝追加日志");
            return new Reply.AppendEntryReply(context.getCurrentTerm(), false);
        }

        // 更新任期并重置投票给的候选者（如果请求任期更大）
        context.setCurrentTerm(message.getTerm());
        context.setVotedFor(null);

        // 重置选举超时定时器
        context.resetElectionTimeoutTimer();

        if (message.getEntries().isEmpty()) {
            int newestLogIndex = context.getLastLogIndex();

            // 心跳响应也需要更新commitIndex
            if (message.getLeaderCommit() > context.getCommitIndex()) {
                context.setCommitIndex(Math.min(message.getLeaderCommit(), newestLogIndex));
            }

            return new Reply.AppendEntryReply(context.getCurrentTerm(), true, newestLogIndex);
        }

        /**
         * Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm ($5.3)
         * If an existing entry conflicts with a new one (same index but different terms), delete the existing entry and all thatfollow it (§5.3)
         * Append any new entries not already in the log
         * If leaderCommit>commitIndex,set commitIndex=min(leaderCommit, index of last new entry)
         */
        if (!isLogConsistent(context, message.getPrevLogIndex(), message.getPrevLogTerm())) {
            log.info("日志不一致，拒绝追加日志");
            return new Reply.AppendEntryReply(context.getCurrentTerm(), false);
        }

        int newLogStartIndex = message.getPrevLogIndex() + 1;
        LogEntry first = message.getEntries().get(0);
        if (logConflict(context, newLogStartIndex, first.getTerm())) {
            log.info("删除冲突位置及之后的日志{}", newLogStartIndex);
            context.getLogStorage().deleteAfter(newLogStartIndex);
        }

        // 追加新日志
        // 保证幂等，不直接追加，而是在newLogStartIndex处开始追加
        int newestLogIndex = appendEntriesFromRequest(context, newLogStartIndex, message);
        if (newestLogIndex == -1) {
            log.info("日志不一致，拒绝追加日志");
            return new Reply.AppendEntryReply(context.getCurrentTerm(), false);
        }

        // 更新commitIndex
        if (message.getLeaderCommit() > context.getCommitIndex()) {
            context.setCommitIndex(Math.min(message.getLeaderCommit(), newestLogIndex));
        }

        log.info("AppendEntry执行完成");
        return new Reply.AppendEntryReply(context.getCurrentTerm(), true, newestLogIndex);
    }

    @Override
    public RoleType getRole() {
        return RoleType.Follower;
    }

    private boolean logConflict(Node context, int index, int term) {
        LogEntry entry = context.getLogStorage().findByIndex(index);
        return entry != null && entry.getTerm() != term;
    }

    private boolean isLogConsistent(Node context, int prevLogIndex, int prevLogTerm) {
        int lastLogIndex = context.getLastLogIndex();
        if (lastLogIndex == -1 || prevLogIndex == -1) {
            return lastLogIndex == prevLogIndex;
        }
        LogEntry log = context.getLogStorage().findByIndex(prevLogIndex);
        int myPrevLogTerm = log == null ? -1 : log.getTerm();
        return myPrevLogTerm == prevLogTerm;
    }

    private int appendEntriesFromRequest(Node context, int index, Message.AppendEntryMessage message) {
        return context.getLogStorage().append(index, message.getEntries());
    }
}