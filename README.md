# raft-try
Java实现的raft协议层，采用单线程控制模型，不包含StateMachine层。 仅供学习和参考
- 节点角色：Leader、Follower和Candidate
- 日志管理：日志数据结构（索引、任期号和数据）、存储、重启后恢复
- 网络通信：日志同步、心跳、投票

### 使用方式
修改`config.json`配置集群节点、心跳、超时相关参数

打包`mvn clean package -DskipTests=true`

执行`java -DRAFT_NODE_ID=id -jar raft-try-1.0-SNAPSHOT.jar`启动每个节点

验证方式：
- 根据控制台或日志文件查看各节点运行日志，例如选举过程
- 访问各节点`http://ip:port/status`查看节点状态
- 对Leader节点发送`AppendEntry`请求观察集群状态变化
- 日志通过MapDB持久化存储，参考`MapDBLogStorage`读取其数据文件观察日志数据； 也可通过接口`http://ip:port/logs`快捷查看
- 直接执行单元测试`com.nameof.raft.RaftTest.test`，包含自动化启动集群、随机发起请求、杀死节点、集群状态验证

### StateMachine层
`Node`类创建时可指定`StateMachineHandler`回调实现，在回调处理中执行自定义的日志解析和状态变更，例如KV数据库
- StateMachine层 -> raft层：调用`Node.appendEntry`发起AppendEntry请求。
- raft层 -> StateMachine层：通过`StateMachineHandler`回调传递AppendEntry结果。StateMachine层再执行日志apply或失败处理，需自行保证幂等性

### 相关实现
- 日志持久化存储：MapDB
- 网络通信：基于Jetty的REST调用，也可以实现`com.nameof.raft.rpc.Rpc`接口，扩展其它的RPC通信方式

### 高级优化，尚未实现
- 并行处理
- 成员变更
- 日志压缩