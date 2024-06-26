package com.nameof.raft.rpc;

import lombok.*;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Reply {

    @Setter
    @Getter
    private Map<String, Object> extra;

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

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientAppendEntryReply extends Reply {
        private boolean success;

        public ClientAppendEntryReply(Map<String, Object> extra, boolean success) {
            super(extra);
            this.success = success;
        }
    }
}