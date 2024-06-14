package com.nameof.raft.rpc;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@SuperBuilder
public class InternalMessage extends Message {

    @Setter
    @Getter
    private Map<String, Object> extra;

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
