package com.nameof.raft.rpc;

import lombok.*;

@Data
public class Reply {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RequestVoteReply extends Reply {
        private int term;
        private boolean voteGranted;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AppendEntryReply extends Reply {
        private int term;
        private boolean success;
        private Integer matchIndex;

        public AppendEntryReply(int term, boolean success) {
            this.term = term;
            this.success = success;
        }
    }
}