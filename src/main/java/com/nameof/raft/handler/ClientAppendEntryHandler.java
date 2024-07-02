package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.config.NodeInfo;
import com.nameof.raft.exception.StateChangeException;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.role.Follower;
import com.nameof.raft.role.RoleType;
import com.nameof.raft.rpc.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class ClientAppendEntryHandler implements Handler {

    protected final Configuration config;
    protected final Rpc rpc;

    public ClientAppendEntryHandler(Rpc rpc) {
        this.config = Configuration.get();
        this.rpc = rpc;
    }

    @SneakyThrows
    @Override
    public void handle(Node context, Message message) {
        InternalMessage.ClientAppendEntryMessage m = (InternalMessage.ClientAppendEntryMessage) message;
        boolean success = false;
        if (context.getState().getRole() == RoleType.Leader) {
            LogEntry logEntry = new LogEntry(context.getCurrentTerm(), m.getData());
            try {
                success = appendEntry(context, Collections.singletonList(logEntry));
            } catch (StateChangeException ignore) {
            }
        }
        // TODO 重定向请求
        rpc.sendReply(new Reply.ClientAppendEntryReply(message.getExtra(), success));
    }

    protected boolean appendEntry(Node context, List<LogEntry> entries) {
        int leaderLastLogIndex = context.getLastLogIndex();
        int leaderLastLogTerm = context.getLastLogTerm();

        // TODO 并发执行
        Set<Integer> successFollower = new HashSet<>();
        for (Map.Entry<Integer, NodeInfo> entry : config.getNodeMap().entrySet()) {
            Integer followerId = entry.getKey();
            // 日志不一致，首先同步
            int matchIndex = context.getMatchIndex().get(followerId);
            if (leaderLastLogIndex != matchIndex) {
                if (!syncLog(context, followerId)) {
                    log.info("followerId {}同步日志失败", followerId);
                    continue;
                }
                log.info("followerId {}同步日志成功", followerId);
            }

            Message.AppendEntryMessage message = buildMessage(context, entries, leaderLastLogIndex, leaderLastLogTerm);
            Reply.AppendEntryReply reply = rpc.appendEntry(config.getNodeInfo(followerId), message);
            if (appendEntryReply(context, followerId, reply)) {
                successFollower.add(followerId);
            }
        }

        // FIXME 考虑大多数节点失联时（仅考虑网络分区，部分节点日志不一致不代表集群出现问题，只是需要同步，例如部分节点崩溃后恢复），可以启动选举超时定时器，以便重试几次无果后主动重新选举，这里暂时依靠其它follower来触发选举

        int success = successFollower.size() + 1;
        log.info("appendEntry 成功{}个节点", success);
        if (success < config.getMajority()) {
            return false;
        }

        if (!entries.isEmpty()) {
            context.getLogStorage().append(entries);
            // 根据各节点响应的matchIndex，更新commitIndex
            context.refreshCommitIndex(successFollower);
        }
        return true;
    }

    private boolean appendEntryReply(Node context, Integer followerId, Reply.AppendEntryReply reply) {
        if (reply == null) {
            return false;
        }
        // 同步成功
        if (reply.isSuccess()) {
            log.info("followerId {} appendEntry成功", followerId);
            updateMatchIndex(context, followerId, reply.getMatchIndex());
            updateNextIndex(context, followerId, reply.getMatchIndex() + 1);
            return true;
        }
        // 同步失败
        // 任期落后，转为follower
        if (reply.getTerm() > context.getCurrentTerm()) {
            log.info("followerId {} appendEntry失败，任期落后", followerId);
            context.setState(new Follower());
            throw new StateChangeException();
        }
        log.info("followerId {} appendEntry失败，日志未匹配", followerId);
        // 等待下次回溯重试
        updateNextIndex(context, followerId, context.getNextIndex().get(followerId) - 1);
        updateMatchIndex(context, followerId, context.getMatchIndex().get(followerId) - 1);
        return false;
    }

    private void updateNextIndex(Node context, Integer followerId, Integer value) {
        log.info("followerId {} NextIndex 更新至{}", followerId, value);
        context.getNextIndex().put(followerId, value);
    }

    private void updateMatchIndex(Node context, Integer followerId, Integer value) {
        log.info("followerId {} MatchIndex 更新至{}", followerId, value);
        context.getMatchIndex().put(followerId, value);
    }

    private boolean syncLog(Node context, Integer followerId) {
        int nextIndex = context.getNextIndex().get(followerId);
        int matchIndex = context.getMatchIndex().get(followerId);
        if (matchIndex == context.getLastLogIndex()) {
            return true;
        }

        List<LogEntry> entries = context.getLogStorage().findByIndexAndAfter(nextIndex);
        LogEntry log = context.getLogStorage().findByIndex(matchIndex);
        int prevLogTerm = log == null ? -1 : log.getTerm();

        Message.AppendEntryMessage message = buildMessage(context, entries, matchIndex, prevLogTerm);
        Reply.AppendEntryReply reply = rpc.appendEntry(config.getNodeInfo(followerId), message);
        return appendEntryReply(context, followerId, reply);
    }

    private Message.AppendEntryMessage buildMessage(Node context, List<LogEntry> entries, int prevLogIndex, int prevLogTerm) {
        return Message.AppendEntryMessage.builder()
                .type(MessageType.AppendEntry)
                .id(context.getId())
                .leaderId(context.getId())
                .term(context.getCurrentTerm())
                .leaderCommit(context.getCommitIndex())
                .prevLogIndex(prevLogIndex)
                .prevLogTerm(prevLogTerm)
                .entries(entries)
                .build();
    }
}
