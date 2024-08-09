package com.nameof.raft.rpc.http;


import cn.hutool.http.ContentType;
import cn.hutool.json.JSONUtil;
import com.nameof.raft.Node;
import com.nameof.raft.log.LogEntry;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@WebServlet(urlPatterns = "/logs", name = "StatusServlet", asyncSupported = true)
public class LogServlet extends HttpServlet {

    private final Node context;

    public LogServlet(Node context) {
        this.context = context;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        List<LogEntry> all = context.getLogStorage().findByIndexAndAfter(0);
        response.setContentType(ContentType.JSON.getValue());
        response.getWriter().println(JSONUtil.toJsonStr(all));
        response.getWriter().flush();
    }
}
