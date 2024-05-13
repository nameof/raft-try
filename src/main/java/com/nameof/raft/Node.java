package com.nameof.raft;

import com.nameof.raft.role.State;
import lombok.Getter;

import java.util.Map;

@Getter
public class Node {
    // 持久状态
    private int id;
    private int currentTerm;
    private Integer votedFor;

    // 易失性状态
    private int commitIndex;  // 已知已提交的最高的日志条目的索引（初始值为0，单调递增）
    private int lastApplied;  // 已经被应用到状态机的最高的日志条目的索引（初始值为0，单调递增）

    // leader易失性状态
    private Map<Integer, Integer> nextIndex;  // 对于每一台服务器，发送到该服务器的下一个日志条目的索引（初始值为领导者最后的日志条目的索引+1）
    private Map<Integer, Integer> matchIndex; // 对于每一台服务器，已知的已经复制到该服务器的最高日志条目的索引（初始值为0，单调递增）

    private State state;

    public void setState(State state) {
        // TODO
        this.state = state;
    }

    public void setCurrentTerm(int currentTerm) {
        this.currentTerm = currentTerm;
        // TODO 持久化
    }

    public void setVotedFor(Integer votedFor) {
        this.votedFor = votedFor;
        // TODO 持久化
    }

    public void setCommitIndex(int commitIndex) {
        this.commitIndex = commitIndex;
    }

    public int getLastLogTerm() {
        return 0;
    }

    public int getLastLogIndex() {
        return 0;
    }
}