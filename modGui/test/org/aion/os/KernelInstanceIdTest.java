package org.aion.os;

import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

public class KernelInstanceIdTest {
    @Test
    public void test() {
        long pid = 12358492L;
        KernelInstanceId unit = new KernelInstanceId(pid);
        assertThat(unit.getPid(), is(pid));
    }
}