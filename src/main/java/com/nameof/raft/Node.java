package com.nameof.raft;

import com.nameof.raft.config.Configuration;
import com.nameof.raft.config.NodeInfo;
import com.nameof.raft.exception.StateChangeException;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.log.LogStorage;
import com.nameof.raft.log.MemoryLogStorage;
import com.nameof.raft.role.Follower;
import com.nameof.raft.role.State;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.MessageType;
import com.nameof.raft.rpc.Reply;
import com.nameof.raft.rpc.Rpc;
import com.nameof.raft.rpc.http.HttpRpc;
import lombok.Getter;
import lombok.Setter;

import java.util.*;

@Getter
public class Node {
    // 持久状态
    private final int id;
    private int currentTerm;
    private Integer votedFor;

    // 易失性状态
    private int commitIndex;  // 已知已提交的最高的日志条目的索引（初始值为0，单调递增）
    private int lastApplied;  // 已经被应用到状态机的最高的日志条目的索引（初始值为0，单调递增）

    // leader易失性状态
    @Setter
    private Map<Integer, Integer> nextIndex;  // 对于每一台服务器，发送到该服务器的下一个日志条目的索引（初始值为领导者最后的日志条目的索引+1）
    @Setter
    private Map<Integer, Integer> matchIndex; // 对于每一台服务器，已知的已经复制到该服务器的最高日志条目的索引（初始值为0，单调递增）

    private State state;
    private final Configuration config;
    private final LogStorage logStorage = new MemoryLogStorage();
    private final StateStorage stateStorage = new StateStorage();

    private final Rpc rpc;

    public Node() {
        config = Configuration.get();
        id = config.getId();

        commitIndex = 0;
        lastApplied = 0;

        currentTerm = stateStorage.getCurrentTerm();
        votedFor = stateStorage.getVotedFor();

        rpc = new HttpRpc(config);
    }

    public void start() {
        // 初始化并启动选举超时定时器
        setState(new Follower());

        // 监听网络请求
        rpc.startServer();

        // 启动事件处理器
    }

    public void setState(State state) {
        this.state = state;
        this.state.init(this);
    }

    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
        this.stateStorage.setCurrentTerm(currentTerm);
    }

    public void setVotedFor(Integer votedFor) {
        this.votedFor = votedFor;
        this.stateStorage.setVotedFor(votedFor);
    }

    public void setCommitIndex(int commitIndex) {
        this.commitIndex = commitIndex;
        // TODO commit log
    }

    public int getLastLogTerm() {
        LogEntry last = logStorage.getLast();
        return last == null ? -1 : last.getTerm();
    }

    public int getLastLogIndex() {
        return logStorage.lastIndex();
    }

    public void stopElectionTimeoutTimer() {
        // TODO
    }

    public void resetElectionTimeoutTimer() {
        // TODO
    }

    public void startHeartbeatTimer() {
        // TODO
    }

    public void stopHeartbeatTimer() {
        // TODO
    }

    private void sendHeartbeat() {
        appendEntry(Collections.emptyList());
    }

    private void appendEntry(List<LogEntry> entries) {
        int leaderLastLogIndex = getLastLogIndex();
        int leaderLastLogTerm = getLastLogTerm();

        Set<Integer> successFollower = new HashSet<>();
        for (Map.Entry<Integer, NodeInfo> entry : config.getNodeMap().entrySet()) {
            Integer followerId = entry.getKey();
            // 日志不一致，首先同步
            int matchIndex = this.getMatchIndex().get(followerId);
            try {
                if (leaderLastLogIndex != matchIndex) {
                    if (!syncLog(followerId)) {
                        continue;
                    }
                }

                Message.AppendEntryMessage message = buildMessage(entries, leaderLastLogIndex, leaderLastLogTerm);
                Reply.AppendEntryReply reply = rpc.appendEntry(config.getNodeInfo(followerId), message);
                if (appendEntryReply(followerId, reply)) {
                    successFollower.add(followerId);
                }
            } catch (StateChangeException e) {
                return;
            }
        }

        // FIXME 考虑大多数节点失联时（仅考虑失联，部分节点日志不一致不代表集群出现问题，只是需要同步，例如部分节点崩溃后恢复），可以启动选举超时定时器，以便重试几次无果后主动重新选举，这里暂时依靠其它follower来触发选举

        int success = successFollower.size() + 1;
        if (success < config.getMajority() || entries.isEmpty()) {
            return;
        }

        logStorage.append(entries);
        // 根据各节点响应的matchIndex，更新commitIndex
        refreshCommitIndex(successFollower);
    }

    private void refreshCommitIndex(Set<Integer> successFollower) {
        // TODO
    }

    private boolean appendEntryReply(Integer followerId, Reply.AppendEntryReply reply) {
        if (reply == null) {
            return false;
        }
        // 同步成功
        if (reply.isSuccess()) {
            this.getMatchIndex().put(followerId, reply.getMatchIndex());
            this.getNextIndex().put(followerId, reply.getMatchIndex() + 1);
            return true;
        }
        // 同步失败
        // 任期落后，转为follower
        if (reply.getTerm() > getCurrentTerm()) {
            setState(new Follower());
            throw new StateChangeException();
        }
        // 等待下次回溯重试
        this.getNextIndex().put(followerId, this.getNextIndex().get(followerId) - 1);
        this.getMatchIndex().put(followerId, this.getMatchIndex().get(followerId) - 1);
        return false;
    }

    private boolean syncLog(Integer followerId) {
        int nextIndex = this.getNextIndex().get(followerId);
        int matchIndex = this.getMatchIndex().get(followerId);
        if (matchIndex == getLastLogIndex()) {
            return true;
        }

        List<LogEntry> entries = logStorage.findByIndexAndAfter(nextIndex);
        int prevLogTerm = logStorage.findByIndex(matchIndex).getTerm();

        Message.AppendEntryMessage message = buildMessage(entries, matchIndex, prevLogTerm);
        Reply.AppendEntryReply reply = rpc.appendEntry(config.getNodeInfo(followerId), message);
        return appendEntryReply(followerId, reply);
    }

    private Message.AppendEntryMessage buildMessage(List<LogEntry> entries, int prevLogIndex, int prevLogTerm) {
        return Message.AppendEntryMessage.builder()
                .type(MessageType.AppendEntry)
                .id(this.id)
                .leaderId(this.id)
                .term(this.currentTerm)
                .leaderCommit(this.commitIndex)
                .prevLogIndex(prevLogIndex)
                .prevLogTerm(prevLogTerm)
                .entries(entries)
                .build();
    }
}