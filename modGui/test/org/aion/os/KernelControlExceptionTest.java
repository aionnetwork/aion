package org.aion.os;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.util.EmptyStackException;
import org.junit.Test;

public class KernelControlExceptionTest {
    @Test
    public void test() {
        assertThat(new KernelControlException("someMessage").getMessage(), is("someMessage"));

        Throwable someThrowable = new EmptyStackException();
        assertThat(
                new KernelControlException("someMessage", someThrowable).getCause(),
                is(someThrowable));
    }
}
