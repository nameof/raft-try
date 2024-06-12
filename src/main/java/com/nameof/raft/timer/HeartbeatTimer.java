package com.nameof.raft.timer;

import com.nameof.raft.config.Configuration;
import com.nameof.raft.rpc.InternalMessage;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.MessageType;

import java.util.concurrent.*;

public class HeartbeatTimer {
    private final BlockingQueue<Message> queue;
    private final Configuration config = Configuration.get();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> schedule = null;

    public HeartbeatTimer(BlockingQueue<Message> queue) {
        this.queue = queue;
    }

    public void stop() {
        if (schedule != null && !schedule.isDone()) {
            schedule.cancel(true);
        }
        this.schedule = null;

        removeExists();
    }

    private void removeExists() {
        queue.removeIf(element -> element.getType() == MessageType.Heartbeat);
    }

    public void start() {
        this.schedule = executor.schedule(() -> {
            try {
                queue.put(InternalMessage.HeartbeatTimeoutMessage.builder().type(MessageType.Heartbeat).build());
            } catch (InterruptedException ignored) {
            }
        }, config.getHeartbeatInterval(), TimeUnit.MILLISECONDS);
    }
}
