package com.nameof.raft.rpc.http;

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
            String url = String.format("http://%s:%d", info.getIp(), info.getPort());
            String response = HttpUtil.post(url, JSONUtil.toJsonStr(message));
            return JSONUtil.toBean(response, Reply.AppendEntryReply.class);
        } catch (Exception e) {
            return null;
        }
    }
}
