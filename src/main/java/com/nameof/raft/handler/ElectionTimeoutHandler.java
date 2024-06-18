package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.config.NodeInfo;
import com.nameof.raft.exception.StateChangeException;
import com.nameof.raft.role.Candidate;
import com.nameof.raft.role.Follower;
import com.nameof.raft.role.Leader;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.MessageType;
import com.nameof.raft.rpc.Reply;
import com.nameof.raft.rpc.Rpc;

import java.util.Map;

public class ElectionTimeoutHandler implements Handler {

    protected final Configuration config = Configuration.get();
    private final Rpc rpc;

    public ElectionTimeoutHandler(Rpc rpc) {
        this.rpc = rpc;
    }

    @Override
    public void handle(Node context, Message message) {
        context.setState(new Candidate());

        try {
            requestVote(context);
        } catch (StateChangeException ignore) {
        }
    }

    private void requestVote(Node context) {
        Message.RequestVoteMessage message = buildMessage(context);
        int vote = 1;
        for (Map.Entry<Integer, NodeInfo> entry : config.getNodeMap().entrySet()) {
            Integer followerId = entry.getKey();
            Reply.RequestVoteReply reply = rpc.requestVote(config.getNodeInfo(followerId), message);
            if (reply == null) {
                continue;
            }
            if (reply.isVoteGranted()) {
                vote++;
            } else {
                if (reply.getTerm() > context.getCurrentTerm()) {
                    context.setState(new Follower());
                    throw new StateChangeException();
                }
            }
        }
        if (vote >= config.getMajority()) {
            context.setState(new Leader());
        }
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