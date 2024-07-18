package com.nameof.raft.rpc.http;

import com.nameof.raft.config.NodeInfo;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.role.RoleType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StatusDto {
    private NodeInfo nodeInfo;
    private RoleType currentRole;
    private int currentTerm;
    private Integer voteFor;
    private Integer leaderId;
    private LogEntry lastLogEntry;
}
