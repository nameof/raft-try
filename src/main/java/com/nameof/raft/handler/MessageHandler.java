package com.nameof.raft.handler;

import com.nameof.raft.Node;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.MessageType;
import com.nameof.raft.rpc.Rpc;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

@Slf4j
public class MessageHandler implements Runnable {

    private final Node context;
    private final BlockingQueue<Message> queue;
    private final Map<MessageType, Handler> map;
    private Map<String, String> mdcContextMap;

    public MessageHandler(Node context, Rpc rpc, BlockingQueue<Message> queue) {
        this.context = context;
        this.queue = queue;
        map = new HashMap<MessageType, Handler>() {{
            put(MessageType.ElectionTimeout, new ElectionTimeoutHandler(rpc));
            put(MessageType.Heartbeat, new HeartbeatHandler(rpc));
            put(MessageType.ClientAppendEntry, new ClientAppendEntryHandler(rpc));
            put(MessageType.AppendEntry, new AppendEntryHandler(rpc));
            put(MessageType.RequestVote, new RequestVoteHandler(rpc));
        }};
    }

    public void start() {
        mdcContextMap = MDC.getCopyOfContextMap();

        Thread thread = new Thread(this);
        thread.setName("HandlerThread");
        thread.start();
    }

    @Override
    public void run() {
        MDC.setContextMap(mdcContextMap);
        while (true) {
            Message message = null;
            try {
                message = queue.take();
                log.info("处理事件：{}", message.getType());
                map.get(message.getType()).handle(context, message);
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                log.error("消息处理失败：" + message, e);
            }
        }
    }
}
