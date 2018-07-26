package org.aion.gui.model.dto;

import org.aion.gui.model.KernelConnection;
import org.junit.Test;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

public class AbstractDtoTest {

    @Test
    public void testLoadFromApi() {
        class TestImpl extends AbstractDto {
            public boolean loadCalled = false;

            public TestImpl() {
                super(mock(KernelConnection.class), null);
            }

            @Override
            protected void loadFromApiInternal() {
                loadCalled = true;
            }
        }

        AbstractDto unit = new TestImpl();
        unit.loadFromApi();
        assertThat(((TestImpl) unit).loadCalled, is(true));
    }
}