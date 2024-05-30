package com.nameof.raft.rpc;

import lombok.*;

@Data
public class Reply {
    private ReplyType type;
    private String payload;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class RequestVoteReply {
        private int term;
        private boolean voteGranted;
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class AppendEntryReply {
        private int term;
        private boolean success;
        private Integer matchIndex;

        public AppendEntryReply(int term, boolean success) {
            this.term = term;
            this.success = success;
        }
    }
}