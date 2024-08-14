package com.nameof.raft.exception;

public class NotLeaderException extends RuntimeException {
    private final Integer leaderId;

    public NotLeaderException(int leaderId) {
        this.leaderId = leaderId;
    }

    public int getLeaderId() {
        return leaderId;
    }
}
