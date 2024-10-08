package com.nameof.raft;

import cn.hutool.core.lang.Validator;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.exception.NotLeaderException;
import com.nameof.raft.handler.MessageHandler;
import com.nameof.raft.handler.StateMachineHandler;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.log.LogStorage;
import com.nameof.raft.log.MapDBLogStorage;
import com.nameof.raft.role.Follower;
import com.nameof.raft.role.Role;
import com.nameof.raft.role.RoleType;
import com.nameof.raft.rpc.InternalMessage;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.MessageType;
import com.nameof.raft.rpc.Rpc;
import com.nameof.raft.rpc.http.HttpRpc;
import com.nameof.raft.timer.ElectionTimeoutTimer;
import com.nameof.raft.timer.HeartbeatTimer;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

@Slf4j
@Getter
public class Node {
    // 持久状态
    private final int id;
    private int currentTerm;
    private Integer votedFor;

    // 易失性状态
    private int commitIndex;  // 已知已提交的最高的日志条目的索引（初始值为0，单调递增）
    private int lastApplied;  // 已经被应用到状态机的最高的日志条目的索引（初始值为0，单调递增）

    @Setter
    private Integer leaderId;

    // leader易失性状态
    @Setter
    private Map<Integer, Integer> nextIndex;  // 对于每一台服务器，发送到该服务器的下一个日志条目的索引（初始值为领导者最后的日志条目的索引+1）
    @Setter
    private Map<Integer, Integer> matchIndex; // 对于每一台服务器，已知的已经复制到该服务器的最高日志条目的索引（初始值为0，单调递增）

    private Role role;
    private final Configuration config;
    private final LogStorage logStorage;
    private final StateStorage stateStorage;

    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();
    private final ElectionTimeoutTimer electionTimeoutTimer = new ElectionTimeoutTimer(queue);
    private final HeartbeatTimer heartbeatTimer = new HeartbeatTimer(queue);
    private final MessageHandler handler;

    private final Rpc rpc;
    private final StateMachineHandler stateMachineHandler;

    public Node(File dataDir, StateMachineHandler stateMachineHandler) {
        Validator.validateNotNull(dataDir, "dataDir is null");
        Validator.validateNotNull(stateMachineHandler, "stateMachineHandler is null");

        this.stateMachineHandler = stateMachineHandler;
        this.logStorage = new MapDBLogStorage(dataDir);
        this.stateStorage = new StateStorage(dataDir);

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
        setRole(new Follower());

        // 启动事件处理器
        handler.start();

        // 监听网络请求
        rpc.startServer(this);
    }

    @SneakyThrows
    public void appendEntry(List<String> log, String rawReqId) {
        if (this.getRole().getRole() != RoleType.Leader) {
            throw new NotLeaderException(this.leaderId);
        }
        InternalMessage.ClientAppendEntryMessage msg = InternalMessage.ClientAppendEntryMessage.builder()
                .type(MessageType.ClientAppendEntry)
                .log(log)
                .rawReqId(rawReqId).build();
        this.queue.put(msg);
    }

    public void successAppendEntry(int newCommitIndex) {
        List<LogEntry> entries = new ArrayList<>();
        for (int i = (this.lastApplied + 1); i <= newCommitIndex; i++) {
            entries.add(logStorage.findByIndex(i));
        }
        if (entries.isEmpty()) {
            return;
        }
        try {
            stateMachineHandler.apply(newCommitIndex, entries);
        } catch (Exception e) {
            log.error("apply log error", e);
            System.exit(1);
        }
    }

    public void failedAppendEntry(List<LogEntry> entries) {
        try {
            this.stateMachineHandler.failed(entries);
        } catch (Exception e) {
            log.error("stateMachineHandler.failed error", e);
        }
    }

    public void setRole(Role role) {
        this.role = role;
        MDC.put("nodeRole", role.getRole().name());
        log.info("状态初始化：{}", role.getRole().name());
        this.role.init(this);
    }

    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
        this.stateStorage.setCurrentTerm(currentTerm);
        log.info("currentTerm更新：{}", currentTerm);

        // 任期更新，voteFor更新
        this.setVotedFor(null);
    }

    public void setVotedFor(Integer votedFor) {
        this.votedFor = votedFor;
        this.stateStorage.setVotedFor(votedFor);
        log.info("votedFor更新：{}", votedFor);
    }

    public void setCommitIndex(int newCommitIndex) {
        successAppendEntry(newCommitIndex);

        log.info("commitIndex更新：{}", newCommitIndex);

        this.lastApplied = newCommitIndex;
        this.commitIndex = newCommitIndex;
    }

    public int getLastLogTerm() {
        LogEntry last = logStorage.getLast();
        return last == null ? 0 : last.getTerm();
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
        log.info("重新计算commitIndex，当前值：{}", this.commitIndex);
        int newCommitIndex = this.getLastLogIndex();
        for (Integer follower : successFollower) {
            Integer matchIndex = this.getMatchIndex().get(follower);
            newCommitIndex = Math.min(newCommitIndex, matchIndex);
        }
        if (newCommitIndex <= this.commitIndex) {
            log.info("无需更新commitIndex");
            return;
        }
        setCommitIndex(newCommitIndex);
    }
}