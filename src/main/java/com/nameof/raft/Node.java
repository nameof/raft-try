package com.nameof.raft;

import com.nameof.raft.config.Configuration;
import com.nameof.raft.handler.MessageHandler;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.log.LogStorage;
import com.nameof.raft.log.MemoryLogStorage;
import com.nameof.raft.role.Follower;
import com.nameof.raft.role.State;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Rpc;
import com.nameof.raft.rpc.http.HttpRpc;
import com.nameof.raft.timer.ElectionTimeoutTimer;
import com.nameof.raft.timer.HeartbeatTimer;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

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

    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
    private final ElectionTimeoutTimer electionTimeoutTimer = new ElectionTimeoutTimer(queue);
    private final HeartbeatTimer heartbeatTimer = new HeartbeatTimer(queue);
    private final MessageHandler handler;

    private final Rpc rpc;

    public Node() {
        config = Configuration.get();
        id = config.getId();

        commitIndex = 0;
        lastApplied = 0;

        currentTerm = stateStorage.getCurrentTerm();
        votedFor = stateStorage.getVotedFor();

        rpc = new HttpRpc(queue);
        handler = new MessageHandler(this, rpc, queue);
    }

    public void start() {
        // 初始化并启动选举超时定时器
        setState(new Follower());

        // 启动事件处理器
        handler.start();

        // 监听网络请求
        rpc.startServer();
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
        electionTimeoutTimer.stop();
    }

    public void resetElectionTimeoutTimer() {
        electionTimeoutTimer.reset();
    }

    public void startHeartbeatTimer() {
        heartbeatTimer.start();
    }

    public void stopHeartbeatTimer() {
        heartbeatTimer.stop();
    }

    public void refreshCommitIndex(Set<Integer> successFollower) {
        // TODO
    }
}