package com.nameof.raft.rpc;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
public class InternalMessage extends Message {

    @SuperBuilder
    public static class ElectionTimeoutMessage extends InternalMessage {
    }

    @SuperBuilder
    public static class HeartbeatTimeoutMessage extends InternalMessage {
    }

    @Setter
    @Getter
    @SuperBuilder
    public static class ClientAppendEntryMessage extends InternalMessage {
        private String data;
    }
}
