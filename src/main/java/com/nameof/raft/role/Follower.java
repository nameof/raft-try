package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Follower implements Role {

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
        Reply.RequestVoteReply reply = doRequestVote(context, message);
        // 投赞成票后，重置选举超时定时器进行退避
        if (reply.isVoteGranted()) {
            context.setVotedFor(message.getCandidateId());
            context.resetElectionTimeoutTimer();
        }
        return reply;
    }

    private Reply.RequestVoteReply doRequestVote(Node context, Message.RequestVoteMessage message) {
        // 请求任期小于当前任期，直接拒绝
        if (message.getTerm() < context.getCurrentTerm()) {
            log.info("拒绝投票");
            return voteReply(context, false);
        }

        // 相同任期内只投票一次
        if (message.getTerm() == context.getCurrentTerm()) {
            if (context.getVotedFor() != null) {
                return voteReply(context, context.getVotedFor().equals(message.getCandidateId()));
            } else {
                return voteReply(context, voteByLog(context, message));
            }
        } else {
            // 请求任期大于当前任期，更新任期，已是follower无需切换状态
            context.setCurrentTerm(message.getTerm());
            return voteReply(context, voteByLog(context, message));
        }
    }

    private boolean voteByLog(Node context, Message.RequestVoteMessage message) {
        boolean shouldVote = candidateLogIsNewerOrEqual(context, message);
        if (shouldVote) {
            log.info("日志符合，赞成投票");
            return true;
        } else {
            log.info("日志较旧，拒绝投票");
            return false;
        }
    }

    private Reply.RequestVoteReply voteReply(Node context, boolean vote) {
        return new Reply.RequestVoteReply(context.getCurrentTerm(), vote);
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
        Reply.AppendEntryReply reply = doAppendEntry(context, message);
        // 日志通过时，才是有效的心跳消息，重置选举超时定时器
        if (reply.isSuccess()) {
            context.resetElectionTimeoutTimer();
        }
        return reply;
    }

    private Reply.AppendEntryReply doAppendEntry(Node context, Message.AppendEntryMessage message) {
        if (message.getTerm() < context.getCurrentTerm()) {
            log.info("拒绝追加日志");
            return new Reply.AppendEntryReply(context.getCurrentTerm(), false);
        }

        // 更新任期并重置投票给的候选者（如果请求任期更大）
        if (message.getTerm() > context.getCurrentTerm()) {
            context.setCurrentTerm(message.getTerm());
        }

        /**
         * 验证日志一致性
         * Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm ($5.3)
         * If an existing entry conflicts with a new one (same index but different terms), delete the existing entry and all thatfollow it (§5.3)
         * Append any new entries not already in the log
         * If leaderCommit>commitIndex,set commitIndex=min(leaderCommit, index of last new entry)
         */
        if (!isLogConsistent(context, message.getPrevLogIndex(), message.getPrevLogTerm())) {
            log.info("日志不一致，拒绝追加日志");
            // 日志不一致时返回LastLogIndex，供Leader参考
            return new Reply.AppendEntryReply(context.getCurrentTerm(), false, context.getLastLogIndex());
        }

        boolean empty = message.getEntries().isEmpty(); // empty表明是心跳请求，或新leader上任首次同步日志
        int lastLogIndex = context.getLastLogIndex();
        if (!empty) {
            // 开始尝试追加日志
            int newLogStartIndex = message.getPrevLogIndex() + 1;
            LogEntry first = message.getEntries().get(0);
            if (logConflict(context, newLogStartIndex, first.getTerm())) {
                log.info("删除冲突位置及之后的日志{}", newLogStartIndex);
                context.getLogStorage().deleteAfter(newLogStartIndex);
            }

            // 追加新日志，保证幂等，不直接追加，而是在newLogStartIndex处开始追加
            lastLogIndex = appendEntriesFromRequest(context, newLogStartIndex, message);
            if (lastLogIndex == -1) {
                log.info("日志不一致，拒绝追加日志");
                // 日志不一致时返回LastLogIndex，供Leader参考
                return new Reply.AppendEntryReply(context.getCurrentTerm(), false, context.getLastLogIndex());
            }
        }

        // 更新commitIndex
        if (message.getLeaderCommit() > context.getCommitIndex()) {
            context.setCommitIndex(Math.min(message.getLeaderCommit(), lastLogIndex));
        }

        log.info("AppendEntry执行完成");
        return appendEntryReplySuccess(context, lastLogIndex, message);
    }

    private Reply.AppendEntryReply appendEntryReplySuccess(Node context, int lastLogIndex, Message.AppendEntryMessage message) {
        context.setLeaderId(message.getLeaderId());
        return new Reply.AppendEntryReply(context.getCurrentTerm(), true, lastLogIndex);
    }

    @Override
    public RoleType getRole() {
        return RoleType.Follower;
    }

    private boolean logConflict(Node context, int index, int term) {
        LogEntry entry = context.getLogStorage().findByIndex(index);
        return entry != null && entry.getTerm() != term;
    }

    private boolean isLogConsistent(Node context, int leaderPrevLogIndex, int leaderPrevLogTerm) {
        int myPrevLogIndex = context.getLastLogIndex();
        log.info("leaderPrevLogIndex: {}, leaderPrevLogTerm: {}, myPrevLogIndex: {}", leaderPrevLogIndex, leaderPrevLogTerm, myPrevLogIndex);
        if (myPrevLogIndex == 0 || leaderPrevLogIndex == 0) {
            return myPrevLogIndex == leaderPrevLogIndex;
        }
        LogEntry entry = context.getLogStorage().findByIndex(leaderPrevLogIndex);
        int myPrevLogTerm = entry == null ? 0 : entry.getTerm();
        log.info("myPrevLogTerm: {}", myPrevLogTerm);
        return myPrevLogTerm == leaderPrevLogTerm;
    }

    private int appendEntriesFromRequest(Node context, int index, Message.AppendEntryMessage message) {
        return context.getLogStorage().append(index, message.getEntries());
    }
}