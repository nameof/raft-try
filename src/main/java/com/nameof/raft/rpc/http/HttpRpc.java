package com.nameof.raft.rpc.http;

import cn.hutool.http.ContentType;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONUtil;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.config.NodeInfo;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;
import com.nameof.raft.rpc.Rpc;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

public class HttpRpc implements Rpc {

    private final int port;
    private final BlockingQueue<Message> queue;

    public HttpRpc(BlockingQueue<Message> queue) {
        this.port = Configuration.get().getNodeInfo().getPort();
        this.queue = queue;
    }

    @Override
    public void startServer() {
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        ServletHolder servletHolder = new ServletHolder(new CoreServlet(queue));
        context.addServlet(servletHolder, "/");

        try {
            server.start();
            server.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Reply.AppendEntryReply appendEntry(NodeInfo info, Message.AppendEntryMessage message) {
        try {
            return JSONUtil.toBean(request(info, message), Reply.AppendEntryReply.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public Reply.RequestVoteReply requestVote(NodeInfo info, Message.RequestVoteMessage message) {
        try {
            return JSONUtil.toBean(request(info, message), Reply.RequestVoteReply.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void sendReply(Reply reply) {
        Map<String, Object> extra = reply.getExtra();
        AsyncContext asyncContext = (AsyncContext) extra.get("asyncContext");
        HttpServletResponse response = (HttpServletResponse) extra.get("response");
        response.setStatus(200);
        try {
            response.setContentType(ContentType.JSON.getValue());
            response.getWriter().println(JSONUtil.toJsonStr(reply));
            asyncContext.complete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String request(NodeInfo info, Message message) {
        String url = String.format("http://%s:%d", info.getIp(), info.getPort());
        return HttpUtil.post(url, JSONUtil.toJsonStr(message));
    }
}
