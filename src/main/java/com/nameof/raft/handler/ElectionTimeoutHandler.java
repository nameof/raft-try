package com.nameof.raft.handler;

import cn.hutool.core.lang.Tuple;
import com.nameof.raft.Node;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.exception.StateChangeException;
import com.nameof.raft.role.Candidate;
import com.nameof.raft.role.Follower;
import com.nameof.raft.role.Leader;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.MessageType;
import com.nameof.raft.rpc.Reply;
import com.nameof.raft.rpc.Rpc;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class ElectionTimeoutHandler implements Handler {

    protected final Configuration config = Configuration.get();
    private final Rpc rpc;

    public ElectionTimeoutHandler(Rpc rpc) {
        this.rpc = rpc;
    }

    @Override
    public void handle(Node context, Message message) {
        context.setState(new Candidate());

        boolean success = false;
        try {
            success = requestVote(context);
        } catch (StateChangeException ignore) {
        }

        if (!success) {
            context.resetElectionTimeoutTimer();
        }
    }

    @SneakyThrows
    private boolean requestVote(Node context) {
        log.info("开始请求投票，CurrentTerm: {}", context.getCurrentTerm());
        Message.RequestVoteMessage message = buildMessage(context);
        int vote = 1;
        for (CompletableFuture<Tuple> future : concurrentRequestVote(message)) {
            Tuple tuple = future.get();
            Integer followerId = tuple.get(0);
            Reply.RequestVoteReply reply = tuple.get(1);
            if (reply == null) {
                log.info("请求投票未成功：{}", followerId);
                continue;
            }
            if (reply.isVoteGranted()) {
                vote++;
                log.info("获得选票：{}", followerId);
            } else {
                log.info("选票被拒绝：{}", followerId);
                if (reply.getTerm() > context.getCurrentTerm()) {
                    context.setState(new Follower());
                    throw new StateChangeException();
                }
            }
        }
        log.info("获得选票：{}个", vote);
        if (vote >= config.getMajority()) {
            context.setState(new Leader());
            return true;
        }
        return false;
    }

    private List<CompletableFuture<Tuple>> concurrentRequestVote(Message.RequestVoteMessage message) {
        return config.getNodeMap().keySet().stream()
                .map(followerId -> CompletableFuture.supplyAsync(() -> {
                    Reply.RequestVoteReply reply = rpc.requestVote(config.getNodeInfo(followerId), message);
                    return new Tuple(followerId, reply);
                })).collect(Collectors.toList());
    }

    private Message.RequestVoteMessage buildMessage(Node context) {
        return Message.RequestVoteMessage.builder()
                .type(MessageType.RequestVote)
                .candidateId(context.getId())
                .id(context.getId())
                .term(context.getCurrentTerm())
                .lastLogIndex(context.getLastLogIndex())
                .lastLogTerm(context.getLastLogTerm()).build();
    }
}