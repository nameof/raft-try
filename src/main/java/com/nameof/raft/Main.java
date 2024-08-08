package com.nameof.raft;

import com.nameof.raft.handler.DefaultStateMachineHandler;

public class Main {
    public static void main(String[] args) {
        new Node(new DefaultStateMachineHandler()).start();
    }
}
