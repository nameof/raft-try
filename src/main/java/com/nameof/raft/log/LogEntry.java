package com.nameof.raft.log;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LogEntry {
    private int term;
    private String data;
}
