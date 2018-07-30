package org.aion.os;

import org.junit.Test;

import java.util.EmptyStackException;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

public class KernelControlExceptionTest {
    @Test
    public void test() {
        assertThat(new KernelControlException("someMessage").getMessage(), is("someMessage"));

        Throwable someThrowable = new EmptyStackException();
        assertThat(new KernelControlException("someMessage",
                someThrowable).getCause(), is(someThrowable));
    }
}