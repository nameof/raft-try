package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.MessageType;
import com.nameof.raft.rpc.Rpc;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class MessageHandler implements Runnable {

    private final Node context;
    private final BlockingQueue<Message> queue;
    private final Map<MessageType, Handler> map;

    public MessageHandler(Node context, Rpc rpc, BlockingQueue<Message> queue) {
        this.context = context;
        this.queue = queue;
        map = new HashMap<MessageType, Handler>() {{
            put(MessageType.ElectionTimeout, new ElectionTimeoutHandler());
            put(MessageType.Heartbeat, new HeartbeatHandler(rpc));
        }};
    }

    public void start() {
        new Thread(this).start();
    }

    @Override
    public void run() {
        while (true) {
            Message message = null;
            try {
                message = queue.take();
                map.get(message.getType()).handle(context, message);
            } catch (InterruptedException e) {
                return;
            } catch (Exception e) {
                log.error("消息处理失败：{}", message, e);
            }
        }
    }
}
