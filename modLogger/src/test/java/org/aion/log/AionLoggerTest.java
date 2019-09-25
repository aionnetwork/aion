package org.aion.log;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.Marker;

/**
 * Verifies that the method calls are correctly delegated to the wrapped objects and that checks for
 * enabled are made before attempts to loggit add  messages.
 *
 * @author Alexandra Roatis
 */
public class AionLoggerTest {

    @Mock Logger log;
    AionLogger wrapped;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        wrapped = AionLogger.wrap(log);
    }

    @Test
    public void test_getName() {
        // call wrapper
        wrapped.getName();

        // verify enclosed object call
        verify(log, times(1)).getName();
    }

    @Test
    public void test_isTraceEnabled() {
        // call wrapper
        wrapped.isTraceEnabled();

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
    }

    @Test
    public void test_isDebugEnabled() {
        // call wrapper
        wrapped.isDebugEnabled();

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
    }

    @Test
    public void test_isInfoEnabled() {
        // call wrapper
        wrapped.isInfoEnabled();

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
    }

    @Test
    public void test_isWarnEnabled() {
        // call wrapper
        wrapped.isWarnEnabled();

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
    }

    @Test
    public void test_isErrorEnabled() {
        // call wrapper
        wrapped.isErrorEnabled();

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
    }

    @Test
    public void test_isTraceEnabled_withMarker() {
        Marker marker = mock(Marker.class);

        // call wrapper
        wrapped.isTraceEnabled(marker);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
    }

    @Test
    public void test_isDebugEnabled_withMarker() {
        Marker marker = mock(Marker.class);

        // call wrapper
        wrapped.isDebugEnabled(marker);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
    }

    @Test
    public void test_isInfoEnabled_withMarker() {
        Marker marker = mock(Marker.class);

        // call wrapper
        wrapped.isInfoEnabled(marker);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
    }

    @Test
    public void test_isWarnEnabled_withMarker() {
        Marker marker = mock(Marker.class);

        // call wrapper
        wrapped.isWarnEnabled(marker);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
    }

    @Test
    public void test_isErrorEnabled_withMarker() {
        Marker marker = mock(Marker.class);

        // call wrapper
        wrapped.isErrorEnabled(marker);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
    }

    @Test
    public void test_trace_enable_withMessage() {
        when(log.isTraceEnabled()).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.trace(msg);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(1)).trace(msg);
    }

    @Test
    public void test_trace_disabled_withMessage() {
        when(log.isTraceEnabled()).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.trace(msg);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(0)).trace(msg);
    }

    @Test
    public void test_trace_enable_withFormat1() {
        when(log.isTraceEnabled()).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.trace(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(1)).trace(format, obj);
    }

    @Test
    public void test_trace_disabled_withFormat1() {
        when(log.isTraceEnabled()).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.trace(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(0)).trace(format, obj);
    }

    @Test
    public void test_trace_enable_withFormat2() {
        when(log.isTraceEnabled()).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.trace(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(1)).trace(format, obj1, obj2);
    }

    @Test
    public void test_trace_disabled_withFormat2() {
        when(log.isTraceEnabled()).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.trace(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(0)).trace(format, obj1, obj2);
    }

    @Test
    public void test_trace_enable_withFormat3() {
        when(log.isTraceEnabled()).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.trace(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(1)).trace(format, obj);
    }

    @Test
    public void test_trace_disabled_withFormat3() {
        when(log.isTraceEnabled()).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.trace(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(0)).trace(format, obj);
    }

    @Test
    public void test_trace_enable_withThrowable() {
        when(log.isTraceEnabled()).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.trace(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(1)).trace(msg, t);
    }

    @Test
    public void test_trace_disabled_withThrowable() {
        when(log.isTraceEnabled()).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.trace(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled();
        verify(log, times(0)).trace(msg, t);
    }

    @Test
    public void test_trace_withMarker_enable_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.trace(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(1)).trace(marker, msg);
    }

    @Test
    public void test_trace_withMarker_disabled_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.trace(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(0)).trace(marker, msg);
    }

    @Test
    public void test_trace_withMarker_enable_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.trace(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(1)).trace(marker, format, obj);
    }

    @Test
    public void test_trace_withMarker_disabled_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.trace(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(0)).trace(marker, format, obj);
    }

    @Test
    public void test_trace_withMarker_enable_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.trace(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(1)).trace(marker, format, obj1, obj2);
    }

    @Test
    public void test_trace_withMarker_disabled_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.trace(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(0)).trace(marker, format, obj1, obj2);
    }

    @Test
    public void test_trace_withMarker_enable_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.trace(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(1)).trace(marker, format, obj);
    }

    @Test
    public void test_trace_withMarker_disabled_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.trace(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(0)).trace(marker, format, obj);
    }

    @Test
    public void test_trace_withMarker_enable_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.trace(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(1)).trace(marker, msg, t);
    }

    @Test
    public void test_trace_withMarker_disabled_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isTraceEnabled(marker)).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.trace(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isTraceEnabled(marker);
        verify(log, times(0)).trace(marker, msg, t);
    }

    @Test
    public void test_debug_enable_withMessage() {
        when(log.isDebugEnabled()).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.debug(msg);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(1)).debug(msg);
    }

    @Test
    public void test_debug_disabled_withMessage() {
        when(log.isDebugEnabled()).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.debug(msg);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(0)).debug(msg);
    }

    @Test
    public void test_debug_enable_withFormat1() {
        when(log.isDebugEnabled()).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.debug(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(1)).debug(format, obj);
    }

    @Test
    public void test_debug_disabled_withFormat1() {
        when(log.isDebugEnabled()).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.debug(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(0)).debug(format, obj);
    }

    @Test
    public void test_debug_enable_withFormat2() {
        when(log.isDebugEnabled()).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.debug(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(1)).debug(format, obj1, obj2);
    }

    @Test
    public void test_debug_disabled_withFormat2() {
        when(log.isDebugEnabled()).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.debug(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(0)).debug(format, obj1, obj2);
    }

    @Test
    public void test_debug_enable_withFormat3() {
        when(log.isDebugEnabled()).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.debug(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(1)).debug(format, obj);
    }

    @Test
    public void test_debug_disabled_withFormat3() {
        when(log.isDebugEnabled()).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.debug(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(0)).debug(format, obj);
    }

    @Test
    public void test_debug_enable_withThrowable() {
        when(log.isDebugEnabled()).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.debug(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(1)).debug(msg, t);
    }

    @Test
    public void test_debug_disabled_withThrowable() {
        when(log.isDebugEnabled()).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.debug(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled();
        verify(log, times(0)).debug(msg, t);
    }

    @Test
    public void test_debug_withMarker_enable_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.debug(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(1)).debug(marker, msg);
    }

    @Test
    public void test_debug_withMarker_disabled_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.debug(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(0)).debug(marker, msg);
    }

    @Test
    public void test_debug_withMarker_enable_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.debug(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(1)).debug(marker, format, obj);
    }

    @Test
    public void test_debug_withMarker_disabled_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.debug(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(0)).debug(marker, format, obj);
    }

    @Test
    public void test_debug_withMarker_enable_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.debug(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(1)).debug(marker, format, obj1, obj2);
    }

    @Test
    public void test_debug_withMarker_disabled_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.debug(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(0)).debug(marker, format, obj1, obj2);
    }

    @Test
    public void test_debug_withMarker_enable_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.debug(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(1)).debug(marker, format, obj);
    }

    @Test
    public void test_debug_withMarker_disabled_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.debug(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(0)).debug(marker, format, obj);
    }

    @Test
    public void test_debug_withMarker_enable_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.debug(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(1)).debug(marker, msg, t);
    }

    @Test
    public void test_debug_withMarker_disabled_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isDebugEnabled(marker)).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.debug(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isDebugEnabled(marker);
        verify(log, times(0)).debug(marker, msg, t);
    }

    @Test
    public void test_info_enable_withMessage() {
        when(log.isInfoEnabled()).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.info(msg);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(1)).info(msg);
    }

    @Test
    public void test_info_disabled_withMessage() {
        when(log.isInfoEnabled()).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.info(msg);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(0)).info(msg);
    }

    @Test
    public void test_info_enable_withFormat1() {
        when(log.isInfoEnabled()).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.info(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(1)).info(format, obj);
    }

    @Test
    public void test_info_disabled_withFormat1() {
        when(log.isInfoEnabled()).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.info(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(0)).info(format, obj);
    }

    @Test
    public void test_info_enable_withFormat2() {
        when(log.isInfoEnabled()).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.info(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(1)).info(format, obj1, obj2);
    }

    @Test
    public void test_info_disabled_withFormat2() {
        when(log.isInfoEnabled()).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.info(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(0)).info(format, obj1, obj2);
    }

    @Test
    public void test_info_enable_withFormat3() {
        when(log.isInfoEnabled()).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.info(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(1)).info(format, obj);
    }

    @Test
    public void test_info_disabled_withFormat3() {
        when(log.isInfoEnabled()).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.info(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(0)).info(format, obj);
    }

    @Test
    public void test_info_enable_withThrowable() {
        when(log.isInfoEnabled()).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.info(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(1)).info(msg, t);
    }

    @Test
    public void test_info_disabled_withThrowable() {
        when(log.isInfoEnabled()).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.info(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled();
        verify(log, times(0)).info(msg, t);
    }

    @Test
    public void test_info_withMarker_enable_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.info(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(1)).info(marker, msg);
    }

    @Test
    public void test_info_withMarker_disabled_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.info(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(0)).info(marker, msg);
    }

    @Test
    public void test_info_withMarker_enable_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.info(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(1)).info(marker, format, obj);
    }

    @Test
    public void test_info_withMarker_disabled_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.info(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(0)).info(marker, format, obj);
    }

    @Test
    public void test_info_withMarker_enable_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.info(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(1)).info(marker, format, obj1, obj2);
    }

    @Test
    public void test_info_withMarker_disabled_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.info(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(0)).info(marker, format, obj1, obj2);
    }

    @Test
    public void test_info_withMarker_enable_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.info(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(1)).info(marker, format, obj);
    }

    @Test
    public void test_info_withMarker_disabled_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.info(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(0)).info(marker, format, obj);
    }

    @Test
    public void test_info_withMarker_enable_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.info(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(1)).info(marker, msg, t);
    }

    @Test
    public void test_info_withMarker_disabled_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isInfoEnabled(marker)).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.info(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isInfoEnabled(marker);
        verify(log, times(0)).info(marker, msg, t);
    }

    @Test
    public void test_warn_enable_withMessage() {
        when(log.isWarnEnabled()).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.warn(msg);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(1)).warn(msg);
    }

    @Test
    public void test_warn_disabled_withMessage() {
        when(log.isWarnEnabled()).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.warn(msg);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(0)).warn(msg);
    }

    @Test
    public void test_warn_enable_withFormat1() {
        when(log.isWarnEnabled()).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.warn(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(1)).warn(format, obj);
    }

    @Test
    public void test_warn_disabled_withFormat1() {
        when(log.isWarnEnabled()).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.warn(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(0)).warn(format, obj);
    }

    @Test
    public void test_warn_enable_withFormat2() {
        when(log.isWarnEnabled()).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.warn(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(1)).warn(format, obj1, obj2);
    }

    @Test
    public void test_warn_disabled_withFormat2() {
        when(log.isWarnEnabled()).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.warn(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(0)).warn(format, obj1, obj2);
    }

    @Test
    public void test_warn_enable_withFormat3() {
        when(log.isWarnEnabled()).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.warn(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(1)).warn(format, obj);
    }

    @Test
    public void test_warn_disabled_withFormat3() {
        when(log.isWarnEnabled()).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.warn(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(0)).warn(format, obj);
    }

    @Test
    public void test_warn_enable_withThrowable() {
        when(log.isWarnEnabled()).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.warn(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(1)).warn(msg, t);
    }

    @Test
    public void test_warn_disabled_withThrowable() {
        when(log.isWarnEnabled()).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.warn(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled();
        verify(log, times(0)).warn(msg, t);
    }

    @Test
    public void test_warn_withMarker_enable_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.warn(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(1)).warn(marker, msg);
    }

    @Test
    public void test_warn_withMarker_disabled_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.warn(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(0)).warn(marker, msg);
    }

    @Test
    public void test_warn_withMarker_enable_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.warn(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(1)).warn(marker, format, obj);
    }

    @Test
    public void test_warn_withMarker_disabled_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.warn(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(0)).warn(marker, format, obj);
    }

    @Test
    public void test_warn_withMarker_enable_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.warn(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(1)).warn(marker, format, obj1, obj2);
    }

    @Test
    public void test_warn_withMarker_disabled_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.warn(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(0)).warn(marker, format, obj1, obj2);
    }

    @Test
    public void test_warn_withMarker_enable_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.warn(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(1)).warn(marker, format, obj);
    }

    @Test
    public void test_warn_withMarker_disabled_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.warn(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(0)).warn(marker, format, obj);
    }

    @Test
    public void test_warn_withMarker_enable_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.warn(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(1)).warn(marker, msg, t);
    }

    @Test
    public void test_warn_withMarker_disabled_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isWarnEnabled(marker)).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.warn(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isWarnEnabled(marker);
        verify(log, times(0)).warn(marker, msg, t);
    }

    @Test
    public void test_error_enable_withMessage() {
        when(log.isErrorEnabled()).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.error(msg);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(1)).error(msg);
    }

    @Test
    public void test_error_disabled_withMessage() {
        when(log.isErrorEnabled()).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.error(msg);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(0)).error(msg);
    }

    @Test
    public void test_error_enable_withFormat1() {
        when(log.isErrorEnabled()).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.error(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(1)).error(format, obj);
    }

    @Test
    public void test_error_disabled_withFormat1() {
        when(log.isErrorEnabled()).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.error(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(0)).error(format, obj);
    }

    @Test
    public void test_error_enable_withFormat2() {
        when(log.isErrorEnabled()).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.error(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(1)).error(format, obj1, obj2);
    }

    @Test
    public void test_error_disabled_withFormat2() {
        when(log.isErrorEnabled()).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.error(format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(0)).error(format, obj1, obj2);
    }

    @Test
    public void test_error_enable_withFormat3() {
        when(log.isErrorEnabled()).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.error(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(1)).error(format, obj);
    }

    @Test
    public void test_error_disabled_withFormat3() {
        when(log.isErrorEnabled()).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.error(format, obj);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(0)).error(format, obj);
    }

    @Test
    public void test_error_enable_withThrowable() {
        when(log.isErrorEnabled()).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.error(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(1)).error(msg, t);
    }

    @Test
    public void test_error_disabled_withThrowable() {
        when(log.isErrorEnabled()).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.error(msg, t);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled();
        verify(log, times(0)).error(msg, t);
    }

    @Test
    public void test_error_withMarker_enable_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(true);

        String msg = "message";

        // call wrapper
        wrapped.error(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(1)).error(marker, msg);
    }

    @Test
    public void test_error_withMarker_disabled_withMessage() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(false);

        String msg = "message";

        // call wrapper
        wrapped.error(marker, msg);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(0)).error(marker, msg);
    }

    @Test
    public void test_error_withMarker_enable_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(true);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.error(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(1)).error(marker, format, obj);
    }

    @Test
    public void test_error_withMarker_disabled_withFormat1() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(false);

        String format = "message {}";
        Object obj = "data";

        // call wrapper
        wrapped.error(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(0)).error(marker, format, obj);
    }

    @Test
    public void test_error_withMarker_enable_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(true);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.error(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(1)).error(marker, format, obj1, obj2);
    }

    @Test
    public void test_error_withMarker_disabled_withFormat2() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(false);

        String format = "message {} {}";
        Object obj1 = "data1";
        Object obj2 = "data2";

        // call wrapper
        wrapped.error(marker, format, obj1, obj2);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(0)).error(marker, format, obj1, obj2);
    }

    @Test
    public void test_error_withMarker_enable_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(true);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.error(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(1)).error(marker, format, obj);
    }

    @Test
    public void test_error_withMarker_disabled_withFormat3() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(false);

        String format = "message {} {} {}";
        Object[] obj = new Object[] {"data1", "data2", "data2"};

        // call wrapper
        wrapped.error(marker, format, obj);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(0)).error(marker, format, obj);
    }

    @Test
    public void test_error_withMarker_enable_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(true);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.error(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(1)).error(marker, msg, t);
    }

    @Test
    public void test_error_withMarker_disabled_withThrowable() {
        Marker marker = mock(Marker.class);
        when(log.isErrorEnabled(marker)).thenReturn(false);

        String msg = "message";
        Throwable t = mock(Throwable.class);

        // call wrapper
        wrapped.error(marker, msg, t);

        // verify enclosed object call
        verify(log, times(1)).isErrorEnabled(marker);
        verify(log, times(0)).error(marker, msg, t);
    }
}
