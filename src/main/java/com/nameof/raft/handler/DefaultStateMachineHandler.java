package com.nameof.raft.handler;

import cn.hutool.json.JSONUtil;
import com.nameof.raft.log.LogEntry;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;

@Slf4j
public class DefaultStateMachineHandler implements StateMachineHandler {
    @Override
    public void apply(int commitIndex, Collection<LogEntry> entries) {
        log.info("日志apply：{}", JSONUtil.toJsonStr(entries));
    }

    @Override
    public void failed(Collection<LogEntry> entries) {
        log.info("AppendEntry失败：{}", JSONUtil.toJsonStr(entries));
    }
}
