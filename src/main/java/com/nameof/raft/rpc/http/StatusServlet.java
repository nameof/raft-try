package com.nameof.raft.rpc.http;


import cn.hutool.http.ContentType;
import cn.hutool.json.JSONUtil;
import com.nameof.raft.Node;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet(urlPatterns = "/status", name = "StatusServlet", asyncSupported = true)
public class StatusServlet extends HttpServlet {

    private final Node context;

    public StatusServlet(Node context) {
        this.context = context;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        StatusDto statusDto = new StatusDto(context.getConfig().getNodeInfo(), context.getRole().getRole()
                , context.getCurrentTerm(), context.getVotedFor(), context.getLeaderId(), context.getLogStorage().getLast());

        response.setContentType(ContentType.JSON.getValue());
        response.getWriter().println(JSONUtil.toJsonStr(statusDto));
        response.getWriter().flush();
    }
}
