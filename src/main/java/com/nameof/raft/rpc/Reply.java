package com.nameof.raft.rpc;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
public class Reply {
    private ReplyType type;
    private String payload;

    @Getter
    @Setter
    public static class RequestVoteReply {
        private int term;
        private boolean voteGranted;
    }

    @Getter
    @Setter
    public static class AppendEntryReply {
        private int term;
        private boolean success;
    }
}