package com.nameof.raft;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class StateStorageTest {
    StateStorage stateStorage = new StateStorage();

    @Before
    public void setUp() {
        stateStorage.clearState();
    }

    @After
    public void after() {
        stateStorage.clearState();
    }

    @Test
    public void testCurrentTerm() throws Exception {
        int result = stateStorage.getCurrentTerm();
        Assert.assertEquals(0, result);

        stateStorage.setCurrentTerm(1);
        Assert.assertEquals(1, stateStorage.getCurrentTerm());
    }

    @Test
    public void testVotedFor() throws Exception {
        Integer result = stateStorage.getVotedFor();
        Assert.assertNull(result);

        stateStorage.setVotedFor(1);
        Assert.assertEquals(1, stateStorage.getVotedFor().intValue());

        stateStorage.setVotedFor(null);
        Assert.assertNull(null, stateStorage.getVotedFor());
    }
}