package com.nameof.raft;

import cn.hutool.core.io.FileUtil;
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
import org.junit.*;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

import java.io.File;
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
        FileUtil.del(Configuration.getDefaultDataDir());

        processes = new HashMap<>();
        for (int i = 0; i < config.getNodes().size(); i++) {
            start(config.getNodes().get(i).getId());
        }
        Thread.sleep(config.getMaxElectionTimeOut());
    }

    @SneakyThrows
    public void clean() {
        for (Map.Entry<Integer, Process> entry : processes.entrySet()) {
            entry.getValue().destroy();
            entry.getValue().waitFor();
        }
        FileUtil.del(Configuration.getDefaultDataDir());
    }

    @Rule
    public TestWatcher watcher = new TestWatcher() {
        @Override
        protected void succeeded(Description description) {
            clean();
        }

        @Override
        protected void failed(Throwable e, Description description) {
            clean();
        }
    };

    @Test
    public void test() {
        Map<String, Runnable> functions = new HashMap<String, Runnable>(){{
            put("appendEntry", RaftTest.this::appendEntry);
            put("concurrentAppendEntry", RaftTest.this::concurrentAppendEntry);
            put("killOneAndStart", RaftTest.this::killOneAndStart);
            put("hackElection", RaftTest.this::hackElection);
            put("killLeaderAndStart", RaftTest.this::killLeaderAndStart);
            put("killMultipleAndStart", RaftTest.this::killMultipleAndStart);
            put("networkPartition", RaftTest.this::networkPartition);
            put("testUnstableLeader", RaftTest.this::testUnstableLeader);
            put("killOneAndClearData", RaftTest.this::killOneAndClearData);
        }};

        int totalIterations = 10;
        ArrayList<Map.Entry<String, Runnable>> list = new ArrayList<>(functions.entrySet());
        for (int i = 0; i < totalIterations; i++) {
            Collections.shuffle(list);
            for (Map.Entry<String, Runnable> entry : list) {
                log.info("execute: {}", entry.getKey());
                entry.getValue().run();
            }
        }
    }

    @SneakyThrows
    private void start(int id) {
        String cmd = String.format("java -DRAFT_NODE_ID=%d -DLOG_DIR=%s -jar %s", id, logDir + id, jarPath);
        Process process = RuntimeUtil.exec(cmd);
        process.getErrorStream().close();
        process.getInputStream().close();
        log.info("启动节点{}", id);
        processes.put(id, process);
    }

    @SneakyThrows
    private NodeInfo findLeader() {
        for (int i = 0; i < 3; i++) {
            for (NodeInfo node : config.getNodes()) {
                StatusDto status = getStatus(node);
                if (status != null && status.getCurrentRole() == RoleType.Leader) {
                    log.info("当前leader: {}", status.getNodeInfo().getId());
                    return node;
                }
            }
            Thread.sleep(config.getMaxElectionTimeOut() + 5000);
        }
        return null;
    }

    public void appendEntry() {
        NodeInfo leader = findLeader();
        Assert.assertNotNull(leader);

        JSONObject obj = new JSONObject();
        obj.set("type", "ClientAppendEntry");
        obj.set("rawReqId", IdUtil.simpleUUID());
        obj.set("log", Arrays.asList(IdUtil.simpleUUID(), IdUtil.simpleUUID()));

        String result = HttpUtil.post(String.format("http://%s:%d", leader.getIp(), leader.getPort()), JSONUtil.toJsonStr(obj));
        Assert.assertNotNull(result);
        log.info("appendEntry result: {}", result);
        Assert.assertTrue(JSONUtil.parseObj(result).getBool("success"));

        checkStatus();
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
                    log.error("appendEntry", e);
                    return false;
                }
            }));
        }
        for (CompletableFuture<Boolean> future : all) {
            Assert.assertTrue(future.get());
        }
        checkStatus();
    }

    @SneakyThrows
    public void killOneAndStart() {
        int i = RandomUtil.randomInt(3) + 1;
        kill(i);

        Thread.sleep(config.getMaxElectionTimeOut());

        start(i);

        Thread.sleep(config.getHeartbeatInterval() * 2L);
        checkStatus();
    }

    @SneakyThrows
    public void killOneAndClearData() {
        int i = RandomUtil.randomInt(3) + 1;
        kill(i);

        Thread.sleep(config.getMaxElectionTimeOut());

        FileUtil.del(new File(Configuration.getDefaultDataDir(), "" + i));

        start(i);

        // 等待足够长时间，确保节点日志重新同步
        Thread.sleep(config.getMaxElectionTimeOut() * 5L);
        checkStatus();
    }

    @SneakyThrows
    public void killMultipleAndStart() {
        List<Integer> crashedNodes = new ArrayList<>();
        for (int i = 0; i < config.getMajority(); i++) {
            int nodeId = RandomUtil.randomInt(config.getNodes().size());
            while (crashedNodes.contains(nodeId)) {
                nodeId = RandomUtil.randomInt(config.getNodes().size());
            }
            crashedNodes.add(nodeId);
            kill(nodeId);
        }

        Thread.sleep(config.getMaxElectionTimeOut() * 2L);

        for (int nodeId : crashedNodes) {
            start(nodeId);
        }

        Thread.sleep(config.getMaxElectionTimeOut() * 2L);
        checkStatus();
    }

    @SneakyThrows
    public void killLeaderAndStart() {
        NodeInfo initialLeader = findLeader();
        Assert.assertNotNull(initialLeader);
        kill(initialLeader.getId());

        Thread.sleep(config.getMaxElectionTimeOut() * 2L);

        NodeInfo newLeader = findLeader();
        Assert.assertNotNull(newLeader);
        Assert.assertNotEquals(initialLeader.getId(), newLeader.getId());
        log.info("new leader: {}", newLeader.getId());

        start(initialLeader.getId());
        Thread.sleep(config.getHeartbeatInterval() * 2L);

        checkStatus();
    }

    private void kill(int id) throws InterruptedException {
        log.info("kill: {}", id);
        Process process = processes.remove(id);
        if (process != null) {
            process.destroy();
            process.waitFor();
        }
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

        Thread.sleep(config.getMaxElectionTimeOut() * 2L);

        checkStatus();
    }

    @SneakyThrows
    public void networkPartition() {
        NodeInfo leader = findLeader();
        Assert.assertNotNull(leader);

        // Simulate network partition
        for (NodeInfo node : config.getNodes()) {
            if (node.getId() != leader.getId()) {
                kill(node.getId());
            }
        }

        Thread.sleep(config.getMaxElectionTimeOut());

        // Check if the leader still holds the leadership
        NodeInfo currentLeader = findLeader();
        Assert.assertNotNull(leader);
        Assert.assertEquals(leader.getId(), currentLeader.getId());

        // Reconnect the partitioned nodes
        for (NodeInfo node : config.getNodes()) {
            if (node.getId() != leader.getId()) {
                start(node.getId());
            }
        }

        Thread.sleep(config.getMaxElectionTimeOut() * 2L);
        checkStatus();
    }

    @SneakyThrows
    public void testUnstableLeader() {
        NodeInfo initialLeader = findLeader();
        Assert.assertNotNull(initialLeader);

        for (int i = 0; i < 5; i++) {
            // Simulate network instability by killing and restarting the leader
            kill(initialLeader.getId());
            Thread.sleep(config.getMaxElectionTimeOut());
            start(initialLeader.getId());
            Thread.sleep(config.getElectionTimeOut() * 2L);

            NodeInfo currentLeader = findLeader();
            Assert.assertNotNull(currentLeader);
            initialLeader = currentLeader;
        }

        checkStatus();
    }

    @SneakyThrows
    public void checkStatus() {
        boolean isConsistent = true;
        List<LogEntry> data = null;
        for (int i = 0; i < 3; i++) {
            data = new ArrayList<>();
            for (NodeInfo nodeInfo : config.getNodes()) {
                StatusDto status = getStatus(nodeInfo);
                data.add(status == null ? null : status.getLastLogEntry());
            }

            isConsistent = true;
            LogEntry first = data.get(0);
            for (LogEntry entry : data) {
                if (!Objects.equals(first, entry)) {
                    isConsistent = false;
                    break;
                }
            }
            if (isConsistent) {
                break;
            }
            Thread.sleep(config.getMaxElectionTimeOut());
        }
        log.info("各节点最后日志：{}", JSONUtil.toJsonStr(data));
        Assert.assertTrue("集群状态不一致", isConsistent);
        log.info("集群状态一致");
    }

    private StatusDto getStatus(NodeInfo nodeInfo) {
        try {
            String result = HttpUtil.get(String.format("http://%s:%d/status", nodeInfo.getIp(), nodeInfo.getPort()));
            return JSONUtil.toBean(result, StatusDto.class);
        } catch (Exception e) {
            log.error("Failed to get status from node {}:{}：{}", nodeInfo.getIp(), nodeInfo.getPort(), e.getMessage());
        }
        return null;
    }
}
