package com.nameof.raft.config;


import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.SystemPropsUtil;
import cn.hutool.json.JSONUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Data
public class Configuration {
    private static final String NODE_ID_CONFIG_KEY = "RAFT_NODE_ID";

    private static volatile Configuration instance;

    private int id;
    private NodeInfo nodeInfo;
    private List<NodeInfo> nodes;
    private Map<Integer, NodeInfo> nodeMap;
    private int majority;
    private int heartbeatInterval;
    private int electionTimeOut;
    private int minElectionTimeOut;
    private int maxElectionTimeOut;

    private Configuration() {
    }

    public static Configuration get() {
        if (instance == null) {
            synchronized (Configuration.class) {
                if (instance == null) {
                    try {
                        instance = loadConfig();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        return instance;
    }

    private static Configuration loadConfig() throws IOException {
        try (InputStream inputStream = Configuration.class.getResourceAsStream("/config.json")) {
            Configuration config = JSONUtil.toBean(IoUtil.read(inputStream, StandardCharsets.UTF_8), Configuration.class);

            int id = SystemPropsUtil.getInt(NODE_ID_CONFIG_KEY, config.getId());
            config.setId(id);

            config.setMajority(config.getNodes().size() / 2 + 1);
            config.setNodeMap(config.getNodes().stream().filter(n -> n.getId() != config.getId()).collect(Collectors.toMap(NodeInfo::getId, n -> n)));
            config.setNodeInfo(config.getNodes().stream().filter(n -> n.getId() == config.getId()).findFirst().get());
            log.info("current node: {}", config.getNodeInfo());

            int timeout = calculateTimeout(config.getMinElectionTimeOut(), config.getMaxElectionTimeOut(), config.getNodes().size(), config.getId());
            config.setElectionTimeOut(timeout);
            log.info("current ElectionTimeOut: {}", timeout);
            return config;
        }
    }

    public NodeInfo getNodeInfo(int id) {
        return nodeMap.get(id);
    }

    public static int calculateTimeout(int minTimeout, int maxTimeout, int totalNodes, int nodeId) {
        if (nodeId < 1 || nodeId > totalNodes) {
            throw new IllegalArgumentException("Invalid Node ID");
        }
        if (minTimeout >= maxTimeout) {
            throw new IllegalArgumentException("Invalid Timeout Range");
        }
        if (totalNodes == 1) {
            return RandomUtil.randomInt(minTimeout, maxTimeout);
        }

        // 每个节点的时间间隔
        int interval = (maxTimeout - minTimeout) / (totalNodes - 1);

        return minTimeout + (nodeId - 1) * interval;
    }
}
