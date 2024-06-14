package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.config.NodeInfo;
import com.nameof.raft.exception.StateChangeException;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.role.Follower;
import com.nameof.raft.rpc.*;
import lombok.SneakyThrows;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

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
        // TODO 检查是否leader角色

        InternalMessage.ClientAppendEntryMessage m = (InternalMessage.ClientAppendEntryMessage) message;
        LogEntry logEntry = new LogEntry(context.getCurrentTerm(), m.getData());
        appendEntry(context, Collections.singletonList(logEntry));

        Map<String, Object> extra = m.getExtra();
        AsyncContext asyncContext = (AsyncContext) extra.get("asyncContext");
        HttpServletResponse response = (HttpServletResponse) extra.get("response");
        response.setStatus(200);
        response.getWriter().println("OK");
        asyncContext.complete();
    }

    protected void appendEntry(Node context, List<LogEntry> entries) {
        int leaderLastLogIndex = context.getLastLogIndex();
        int leaderLastLogTerm = context.getLastLogTerm();

        // TODO 并发执行
        Set<Integer> successFollower = new HashSet<>();
        for (Map.Entry<Integer, NodeInfo> entry : config.getNodeMap().entrySet()) {
            Integer followerId = entry.getKey();
            // 日志不一致，首先同步
            int matchIndex = context.getMatchIndex().get(followerId);
            try {
                if (leaderLastLogIndex != matchIndex) {
                    if (!syncLog(context, followerId)) {
                        continue;
                    }
                }

                Message.AppendEntryMessage message = buildMessage(context, entries, leaderLastLogIndex, leaderLastLogTerm);
                Reply.AppendEntryReply reply = rpc.appendEntry(config.getNodeInfo(followerId), message);
                if (appendEntryReply(context, followerId, reply)) {
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

        context.getLogStorage().append(entries);
        // 根据各节点响应的matchIndex，更新commitIndex
        context.refreshCommitIndex(successFollower);
    }

    private boolean appendEntryReply(Node context, Integer followerId, Reply.AppendEntryReply reply) {
        if (reply == null) {
            return false;
        }
        // 同步成功
        if (reply.isSuccess()) {
            context.getMatchIndex().put(followerId, reply.getMatchIndex());
            context.getNextIndex().put(followerId, reply.getMatchIndex() + 1);
            return true;
        }
        // 同步失败
        // 任期落后，转为follower
        if (reply.getTerm() > context.getCurrentTerm()) {
            context.setState(new Follower());
            throw new StateChangeException();
        }
        // 等待下次回溯重试
        context.getNextIndex().put(followerId, context.getNextIndex().get(followerId) - 1);
        context.getMatchIndex().put(followerId, context.getMatchIndex().get(followerId) - 1);
        return false;
    }

    private boolean syncLog(Node context, Integer followerId) {
        int nextIndex = context.getNextIndex().get(followerId);
        int matchIndex = context.getMatchIndex().get(followerId);
        if (matchIndex == context.getLastLogIndex()) {
            return true;
        }

        List<LogEntry> entries = context.getLogStorage().findByIndexAndAfter(nextIndex);
        int prevLogTerm = context.getLogStorage().findByIndex(matchIndex).getTerm();

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
