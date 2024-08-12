package com.nameof.raft.rpc.http;


import cn.hutool.extra.servlet.ServletUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nameof.raft.rpc.InternalMessage;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.MessageType;
import lombok.SneakyThrows;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

@WebServlet(urlPatterns = "/", name = "CoreServlet", asyncSupported = true)
public class CoreServlet extends HttpServlet {

    private final BlockingQueue<Message> queue;
    private final Map<MessageType, Class<?>> typeMap;

    public CoreServlet(BlockingQueue<Message> queue) {
        this.queue = queue;
        typeMap = new HashMap<MessageType, Class<?>>() {{
            put(MessageType.ElectionTimeout, InternalMessage.ElectionTimeoutMessage.class);
            put(MessageType.Heartbeat, InternalMessage.HeartbeatTimeoutMessage.class);
            put(MessageType.ClientAppendEntry, InternalMessage.ClientAppendEntryMessage.class);
            put(MessageType.AppendEntry, Message.AppendEntryMessage.class);
            put(MessageType.RequestVote, Message.RequestVoteMessage.class);
        }};
    }

    @SneakyThrows
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        AsyncContext asyncContext = request.startAsync();
        asyncContext.setTimeout(60000);

        Map<String, Object> map = new HashMap<String, Object>() {{
            put("request", request);
            put("response", response);
            put("asyncContext", asyncContext);
        }};

        JSONObject obj = JSONUtil.parseObj(ServletUtil.getBody(request));
        MessageType type = MessageType.valueOf(obj.getStr("type"));
        Message message = (Message) JSONUtil.toBean(obj, this.typeMap.get(type));
        message.setClientExtra(map);
        queue.put(message);
    }
}

