package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.exception.StateChangeException;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Rpc;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;

@Slf4j
public class HeartbeatHandler extends ClientAppendEntryHandler {

    public HeartbeatHandler(Rpc rpc) {
        super(rpc);
    }

    @Override
    public void handle(Node context, Message message) {
        log.info("开始发送心跳");
        try {
            appendEntry(context, Collections.emptyList());
            log.info("心跳完成");
        } catch (StateChangeException ignored) {
            log.warn("状态变更");
        }

        // 再次启动
        context.startHeartbeatTimer();
    }
}
