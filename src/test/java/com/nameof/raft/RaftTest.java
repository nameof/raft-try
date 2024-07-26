package com.nameof.raft;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.nameof.raft.config.Configuration;
import com.nameof.raft.config.NodeInfo;
import com.nameof.raft.log.LogEntry;
import com.nameof.raft.role.RoleType;
import com.nameof.raft.rpc.http.StatusDto;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class RaftTest {
    private String jarPath = "D:\\workspace\\raft-try\\target\\raft-try-0.0.1.jar";
    private String logDir = "C:\\Users\\at\\Desktop\\logs\\";
    Configuration config = Configuration.get();
    Map<Integer, Process> processes;

    @SneakyThrows
    @Before
    public void init() {
        processes = new HashMap<>();
        for (int i = 0; i < config.getNodes().size(); i++) {
            start(config.getNodes().get(i).getId());
        }
        Thread.sleep(config.getMaxElectionTimeOut());
    }

    @After
    public void clean() {
        for (Map.Entry<Integer, Process> entry : processes.entrySet()) {
            entry.getValue().destroy();
        }
    }

    @SneakyThrows
    private void start(int id) {
        String cmd = String.format("java -DRAFT_NODE_ID=%d -DLOG_DIR=%s -jar %s", id, logDir + id, jarPath);
        if (false) {
            cmd = "cmd /c start cmd /k " + cmd;
        }
        Process process = RuntimeUtil.exec(cmd);
        process.getErrorStream().close();
        process.getInputStream().close();
        log.info("启动节点{}", id);
        processes.put(id, process);
    }

    @Test
    public void test() throws Exception {
        Map<String, Runnable> functions = new HashMap<String, Runnable>(){{
                put("appendEntry", RaftTest.this::appendEntry);
                put("concurrentAppendEntry", RaftTest.this::concurrentAppendEntry);
                put("killOneAndStart", RaftTest.this::killOneAndStart);
                put("hackElection", RaftTest.this::hackElection);
                put("killLeaderAndStart", RaftTest.this::killLeaderAndStart);
        }};
        for (int i = 0; i < 20; i++) {
            Map.Entry<String, Runnable> entry = functions.entrySet().stream().skip(RandomUtil.randomInt(functions.size())).findFirst().get();

            log.info(entry.getKey());

            entry.getValue().run();

            Thread.sleep(config.getMaxElectionTimeOut());

            checkStatus();
        }
    }

    @SneakyThrows
    private NodeInfo findLeader() {
        for (int i = 0; i < 3; i++) {
            for (NodeInfo node : config.getNodes()) {
                StatusDto status = getStatus(node);
                if (status != null && status.getCurrentRole() == RoleType.Leader) {
                    return node;
                }
            }
            Thread.sleep(config.getMaxElectionTimeOut() + 3000);
        }
        return null;
    }

    public void appendEntry() {
        NodeInfo leader = findLeader();
        Assert.assertNotNull(leader);

        JSONObject obj = new JSONObject();
        obj.set("type", "ClientAppendEntry");
        obj.set("data", IdUtil.simpleUUID());

        String result = HttpUtil.post(String.format("http://%s:%d", leader.getIp(), leader.getPort()), JSONUtil.toJsonStr(obj));
        Assert.assertNotNull(result);
        System.out.println(result);
        Assert.assertTrue(JSONUtil.parseObj(result).getBool("success"));
    }

    @SneakyThrows
    public void concurrentAppendEntry() {
        List<CompletableFuture<Boolean>> all = new ArrayList<>();
        for (int i = 0; i < RandomUtil.randomInt(10); i++) {
            all.add(CompletableFuture.supplyAsync(() -> {
                try {
                    this.appendEntry();
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }));
        }
        for (CompletableFuture<Boolean> future : all) {
            Assert.assertTrue(future.get());
        }
    }

    @SneakyThrows
    public void killOneAndStart() {
        int i = RandomUtil.randomInt(3) + 1;
        Process process = processes.get(i);
        process.destroy();
        process.waitFor();

        Thread.sleep(config.getMaxElectionTimeOut());

        start(i);

        Thread.sleep(config.getHeartbeatInterval() * 2L);

        checkStatus();
    }

    @SneakyThrows
    public void killLeaderAndStart() {
        NodeInfo initialLeader = findLeader();
        Process process = processes.get(initialLeader.getId());
        process.destroy();
        process.waitFor();

        Thread.sleep(config.getMaxElectionTimeOut());

        NodeInfo newLeader = findLeader();
        Assert.assertNotNull(newLeader);
        Assert.assertNotEquals(initialLeader.getId(), newLeader.getId());

        start(initialLeader.getId());
        Thread.sleep(config.getMaxElectionTimeOut());

        checkStatus();
    }

    @SneakyThrows
    private void hackElection() {
        NodeInfo nodeInfo = config.getNodes().get(RandomUtil.randomInt(3));
        StatusDto status = getStatus(nodeInfo);
        Assert.assertNotNull(status);

        JSONObject obj = new JSONObject();
        obj.set("type", "RequestVote");
        obj.set("term", status.getCurrentTerm() + 1);
        obj.set("candidateId", 0);
        obj.set("lastLogIndex", 1);
        obj.set("lastLogTerm", status.getCurrentTerm());
        HttpUtil.post(String.format("http://%s:%d", nodeInfo.getIp(), nodeInfo.getPort()), JSONUtil.toJsonStr(obj));
    }

    public void checkStatus() throws InterruptedException {
        for (int i = 0; i < 3; i++) {
            List<LogEntry> data = new ArrayList<>();
            List<NodeInfo> nodes = config.getNodes();
            for (int j = 0; j < nodes.size(); j++) {
                NodeInfo nodeInfo = nodes.get(j);
                StatusDto status = getStatus(nodeInfo);
                data.add(status == null ? null : status.getLastLogEntry());
            }

            if (Objects.equals(data.get(0), data.get(1)) && Objects.equals(data.get(2), data.get(1))) {
                return;
            }
            Thread.sleep(3000);
        }
        throw new IllegalStateException("状态不一致");
    }

    private StatusDto getStatus(NodeInfo nodeInfo) {
        try {
            String result = HttpUtil.get(String.format("http://%s:%d/status", nodeInfo.getIp(), nodeInfo.getPort()));
            StatusDto statusDto = JSONUtil.toBean(result, StatusDto.class);
            return statusDto;
        } catch (Exception e) {
            log.error("Failed to get status from node {}:{}", nodeInfo.getIp(), nodeInfo.getPort(), e);
        }
        return null;
    }
}
