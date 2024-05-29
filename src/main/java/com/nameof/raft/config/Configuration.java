package com.nameof.raft.config;


import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
public class Configuration {
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

    public static Configuration loadConfig() throws IOException {
        try (InputStream inputStream = Configuration.class.getResourceAsStream("/config.json")) {
            Configuration config = JSONUtil.toBean(IoUtil.read(inputStream, StandardCharsets.UTF_8), Configuration.class);
            config.setMajority(config.getNodes().size() / 2 + 1);
            config.setNodeMap(config.getNodes().stream().filter(n -> n.getId() != config.getId()).collect(Collectors.toMap(NodeInfo::getId, n -> n)));
            config.setNodeInfo(config.getNodes().stream().filter(n -> n.getId() == config.getId()).findFirst().get());
            config.setElectionTimeOut(RandomUtil.randomInt(config.getMinElectionTimeOut(), config.getMaxElectionTimeOut()));
            return config;
        }
    }
}
