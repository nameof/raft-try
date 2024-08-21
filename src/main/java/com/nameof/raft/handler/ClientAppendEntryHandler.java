package com.nameof.raft.handler;

import cn.hutool.core.lang.Tuple;
import com.nameof.raft.Node;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.exception.RoleChangeException;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.role.Follower;
import com.nameof.raft.role.RoleType;
import com.nameof.raft.rpc.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
        Integer redirectTo = null;

        List<LogEntry> entries = m.getLog().stream().map(log -> new LogEntry(context.getCurrentTerm(), log, m.getRawReqId())).collect(Collectors.toList());
        if (context.getRole().getRole() == RoleType.Leader) {
            try {
                success = appendEntry(context, entries);
            } catch (RoleChangeException ignore) {
            }
        } else {
            redirectTo = context.getLeaderId();
        }

        Map<String, Object> clientExtra = message.getClientExtra();
        boolean responseNotCallback = clientExtra != null;
        if (responseNotCallback) {
            rpc.sendReply(new Reply.ClientAppendEntryReply(clientExtra, success, redirectTo));
        } else if (!success) {
            context.failedAppendEntry(entries);
        }
    }

    @SneakyThrows
    protected boolean appendEntry(Node context, List<LogEntry> entries) {
        int leaderLastLogIndex = context.getLastLogIndex();
        int leaderLastLogTerm = context.getLastLogTerm();
        List<CompletableFuture<Tuple>> futures = concurrentAppendEntry(context, entries, leaderLastLogIndex, leaderLastLogTerm);

        // TODO 大多数返回成功时，leader即刻返回客户端结果，不用等待所有follower
        Set<Integer> successFollower = new HashSet<>();
        for (CompletableFuture<Tuple> future : futures) {
            Tuple tuple = future.get();
            Integer followerId = tuple.get(0);
            if (appendEntryReply(context, followerId, tuple.get(1))) {
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

    private List<CompletableFuture<Tuple>> concurrentAppendEntry(Node context, List<LogEntry> entries, int leaderLastLogIndex, int leaderLastLogTerm) {
        return config.getNodeMap().keySet().stream()
                .map(followerId -> CompletableFuture.supplyAsync(() -> {
                    // 日志不一致，首先同步
                    int matchIndex = context.getMatchIndex().get(followerId);
                    int nextIndex = context.getNextIndex().get(followerId);
                    if (leaderLastLogIndex != matchIndex) {
                        log.info("followerId {}日志不一致（matchIndex: {}，nextIndex：{}，当前prevLogIndex：{}），首先同步", followerId, matchIndex, nextIndex, leaderLastLogIndex);
                        if (!syncLog(context, followerId)) {
                            log.info("followerId {}同步日志失败", followerId);
                            return new Tuple(followerId, null);
                        }
                        log.info("followerId {}同步日志成功", followerId);
                    }
                    Message.AppendEntryMessage message = buildMessage(context, entries, leaderLastLogIndex, leaderLastLogTerm);
                    Reply.AppendEntryReply reply = rpc.appendEntry(config.getNodeInfo(followerId), message);
                    return new Tuple(followerId, reply);
                })).collect(Collectors.toList());
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
            context.setRole(new Follower());
            throw new RoleChangeException();
        }
        log.info("followerId {} appendEntry失败，日志未匹配", followerId);
        // 尝试使用follower的lastLogIndex快速回溯日志同步点，避免follower日志落后过多回溯太慢
        // 原nextIndex - 1：逐个回溯日志
        // lastLogIndex + 1：直接对齐到follower的日志位置
        // 取二者中的最小值
        Integer lastLogIndex = reply.getMatchIndex();
        int nextIndex = Math.min(context.getNextIndex().get(followerId) - 1, lastLogIndex + 1);
        updateNextIndex(context, followerId, nextIndex);
        return false;
    }

    public int fastBacktrace(Node context, Integer followerId) {
        int nextIndex = context.getNextIndex().get(followerId);
        int times = 0;
        int step = (int) Math.pow(2, times);
        nextIndex -= step;
        // times++;
        return nextIndex > 0 ? nextIndex : 1;
    }

    private void updateNextIndex(Node context, Integer followerId, Integer value) {
        if (value < 1) {
            log.info("followerId {} NextIndex不变", followerId);
            return;
        }
        log.info("followerId {} NextIndex 更新至{}", followerId, value);
        context.getNextIndex().put(followerId, value);
    }

    private void updateMatchIndex(Node context, Integer followerId, Integer value) {
        if (value < 0) {
            log.info("followerId {} MatchIndex不变", followerId);
            return;
        }
        log.info("followerId {} MatchIndex 更新至{}", followerId, value);
        context.getMatchIndex().put(followerId, value);
    }

    private boolean syncLog(Node context, Integer followerId) {
        int lastLogIndex = context.getLastLogIndex();
        int nextIndex = context.getNextIndex().get(followerId);
        int matchIndex = context.getMatchIndex().get(followerId);
        if (matchIndex == lastLogIndex) {
            return true;
        }

        // TODO 对落后的follower分批同步，而不是一次性同步所有日志

        int prevLogIndex;
        int prevLogTerm;
        List<LogEntry> entries;
        if (lastLogIndex == 0) { // 无日志数据
            entries = Collections.emptyList();
            prevLogIndex = 0;
            prevLogTerm = 0;
        } else {
            if (nextIndex > lastLogIndex) {
                // nextIndex超过所有日志数据（可能是follower日志并未落后，只是matchIndex需要同步，例如新leader上线，令nextIndex = lastLogIndex + 1）
                entries = Collections.emptyList();
                prevLogIndex = lastLogIndex;
                prevLogTerm = context.getLastLogTerm();
            } else {
                entries = context.getLogStorage().findByIndexAndAfter(nextIndex);
                prevLogIndex = nextIndex - 1;
                if (prevLogIndex == 0) {
                    prevLogTerm = 0;
                } else {
                    LogEntry entry = context.getLogStorage().findByIndex(prevLogIndex);
                    prevLogTerm = entry == null ? 0 : entry.getTerm();
                }
            }
        }

        log.info("发送{}个日志", entries.size());
        Message.AppendEntryMessage message = buildMessage(context, entries, prevLogIndex, prevLogTerm);
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
