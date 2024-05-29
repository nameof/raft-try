package com.nameof.raft.rpc.http;

import com.nameof.raft.config.Configuration;
import com.nameof.raft.rpc.Message;
import com.nameof.raft.rpc.Reply;
import com.nameof.raft.rpc.Rpc;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

public class HttpRpc implements Rpc {

    private final int port;

    public HttpRpc(Configuration config) {
        this.port = config.getNodeInfo().getPort();
    }

    @Override
    public void startServer() {
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        ServletHolder servletHolder = new ServletHolder(CoreServlet.class);
        context.addServlet(servletHolder, "/");

        try {
            server.start();
            server.join();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Reply.AppendEntryReply appendEntry(Message.AppendEntryMessage message) {
        return null;
    }
}
