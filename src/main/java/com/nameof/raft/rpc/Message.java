package com.nameof.raft.rpc;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.json.JSONUtil;
import com.nameof.raft.log.LogEntry;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@Setter
@SuperBuilder
public abstract class Message {
    private MessageType type;
    private int term;
    private int id;

    @Getter
    @Setter
    @SuperBuilder
    public static class RequestVoteMessage extends Message {
        private int candidateId;
        private int lastLogIndex;  // 候选人的最后日志条目的索引值
        private int lastLogTerm;  // 候选人最后日志条目的任期号
    }

    @Getter
    @Setter
    @SuperBuilder
    public static class AppendEntryMessage extends Message {
        private int leaderId;
        private int prevLogIndex;  // 新日志条目之前的那个日志条目的索引
        private int prevLogTerm;  // 新日志条目之前的那个日志条目的任期
        private List<LogEntry> entries;
        private int leaderCommit;  // leader已知已提交的最高的日志条目的索引
    }
}
