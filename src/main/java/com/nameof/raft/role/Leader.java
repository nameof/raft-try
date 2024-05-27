package com.nameof.raft.role;


import com.nameof.raft.Node;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;

import java.util.stream.Collectors;

public class Leader implements State {
    @Override
    public void init(Node context) {
        Configuration config = context.getConfig();

        int nextIndex = context.getLogStorage().lastIndex() + 1;
        context.setNextIndex(config.getNodeMap().keySet()
                .stream().collect(Collectors.toMap(id -> id, id -> nextIndex)));
        context.setMatchIndex(config.getNodeMap().keySet()
                .stream().collect(Collectors.toMap(id -> id, id -> 0)));

        // 停止选举超时定时器
        context.stopElectionTimeoutTimer();
        context.startHeartbeatTimer();
    }

    @Override
    public Reply.RequestVoteReply onRequestVote(Node context, Message.RequestVoteMessage message) {
        // 请求任期小于等于当前任期，拒绝投票，并发送心跳
        if (message.getTerm() <= context.getCurrentTerm()) {
            // TODO 发送心跳
            return new Reply.RequestVoteReply(context.getCurrentTerm(), false);
        }

        // 更新任期（请求任期大于当前任期），切换状态
        context.setCurrentTerm(message.getTerm());
        State newState = new Follower();
        context.setState(newState);

        return newState.onRequestVote(context, message);
    }

    @Override
    public Reply.AppendEntryReply onAppendEntry(Node context, Message.AppendEntryMessage message) {
        if (message.getTerm() > context.getCurrentTerm()) {
            // 降级为Follower
            State newState = new Follower();
            context.setState(newState);
            return newState.onAppendEntry(context, message);
        } else {
            // FIXME 发送心跳/发送error/忽略
            return null;
        }
    }
}