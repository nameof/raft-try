package com.nameof.raft;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.nameof.raft.config.Configuration;

import java.io.File;
import java.nio.charset.StandardCharsets;

public class StateStorage {
    private final Configuration config = Configuration.get();
    private final File parent;

    public StateStorage() {
        this.parent = new File(FileUtil.getTmpDir(), "raft-try" + File.separator + config.getId());
        this.parent.mkdirs();
    }

    public int getCurrentTerm() {
        File currentTerm = new File(this.parent, "currentTerm");
        if (!currentTerm.exists()) {
            return 0;
        }
        String str = FileUtil.readString(currentTerm, StandardCharsets.UTF_8);
        if (StrUtil.isEmpty(str)) {
            return 0;
        }
        return Integer.parseInt(str);
    }

    public void setCurrentTerm(int currentTerm) {
        File f = new File(this.parent, "currentTerm");
        FileUtil.writeString(currentTerm + "", f, StandardCharsets.UTF_8);
    }

    public Integer getVotedFor() {
        File f = new File(this.parent, "votedFor");
        if (!f.exists()) {
            return null;
        }
        String str = FileUtil.readString(f, StandardCharsets.UTF_8);
        if (StrUtil.isEmpty(str)) {
            return null;
        }
        return Integer.valueOf(str);
    }

    public void setVotedFor(Integer votedFor) {
        File f = new File(this.parent, "votedFor");
        FileUtil.writeString(votedFor + "", f, StandardCharsets.UTF_8);
    }
}