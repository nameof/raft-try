package com.nameof.raft;

import cn.hutool.core.io.FileUtil;
import com.nameof.raft.config.Configuration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class StateStorageTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    private StateStorage stateStorage;
    private MockedStatic<Configuration> mockedConfig;

    @Before
    public void setUp() {
        mockedConfig = Mockito.mockStatic(Configuration.class);
        Configuration config = Mockito.mock(Configuration.class);
        Mockito.when(config.getId()).thenReturn(1);
        mockedConfig.when(Configuration::get).thenReturn(config);

        stateStorage = new StateStorage(tempFolder.getRoot());
    }

    @After
    public void tearDown() {
        mockedConfig.close();
    }

    @Test
    public void testGetCurrentTerm_FileDoesNotExist() {
        int term = stateStorage.getCurrentTerm();
        assertEquals(0, term);
    }

    @Test
    public void testGetCurrentTerm_FileIsEmpty() {
        File file = new File(stateStorage.getParent(), "currentTerm");
        FileUtil.writeString("", file, StandardCharsets.UTF_8);

        int term = stateStorage.getCurrentTerm();
        assertEquals(0, term);
    }

    @Test
    public void testGetCurrentTerm_FileHasValidTerm() {
        File file = new File(stateStorage.getParent(), "currentTerm");
        FileUtil.writeString("5", file, StandardCharsets.UTF_8);

        int term = stateStorage.getCurrentTerm();
        assertEquals(5, term);
    }

    @Test
    public void testSetCurrentTerm() {
        stateStorage.setCurrentTerm(7);

        File file = new File(stateStorage.getParent(), "currentTerm");
        String content = FileUtil.readString(file, StandardCharsets.UTF_8);
        assertEquals("7", content);
    }

    @Test
    public void testGetVotedFor_FileDoesNotExist() {
        Integer votedFor = stateStorage.getVotedFor();
        assertNull(votedFor);
    }

    @Test
    public void testGetVotedFor_FileIsEmpty() {
        File file = new File(stateStorage.getParent(), "votedFor");
        FileUtil.writeString("", file, StandardCharsets.UTF_8);

        Integer votedFor = stateStorage.getVotedFor();
        assertNull(votedFor);
    }

    @Test
    public void testGetVotedFor_FileHasValidVotedFor() {
        File file = new File(stateStorage.getParent(), "votedFor");
        FileUtil.writeString("3", file, StandardCharsets.UTF_8);

        Integer votedFor = stateStorage.getVotedFor();
        assertEquals(Integer.valueOf(3), votedFor);
    }

    @Test
    public void testSetVotedFor() {
        stateStorage.setVotedFor(4);

        File file = new File(stateStorage.getParent(), "votedFor");
        String content = FileUtil.readString(file, StandardCharsets.UTF_8);
        assertEquals("4", content);
    }

    @Test
    public void testSetVotedFor_NullValue() {
        stateStorage.setVotedFor(null);

        File file = new File(stateStorage.getParent(), "votedFor");
        assertFalse(file.exists());
    }

    @Test
    public void testClearState() {
        File file = new File(stateStorage.getParent(), "currentTerm");
        FileUtil.writeString("5", file, StandardCharsets.UTF_8);

        stateStorage.clearState();

        assertFalse(file.exists());
    }
}