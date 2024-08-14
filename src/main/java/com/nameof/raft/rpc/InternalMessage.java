package com.nameof.raft.rpc;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@NoArgsConstructor
public class InternalMessage extends Message {

    @SuperBuilder
    @NoArgsConstructor
    public static class ElectionTimeoutMessage extends InternalMessage {
    }

    @SuperBuilder
    @NoArgsConstructor
    public static class HeartbeatTimeoutMessage extends InternalMessage {
    }

    @Setter
    @Getter
    @SuperBuilder
    @NoArgsConstructor
    public static class ClientAppendEntryMessage extends InternalMessage {
        private List<String> log;
        private String rawReqId;
    }
}