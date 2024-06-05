package com.nameof.raft.log;

import lombok.*;

@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class LogEntry {
    private int term;
    private String data;
}