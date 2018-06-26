package org.aion.gui.controller;

import javafx.concurrent.Task;
import javafx.event.Event;
import javafx.event.EventHandler;
import org.junit.Test;

import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.isNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AbstractControllerTest {
    @Test public void test() {}
//    private class AbstractControllerSubclass extends  AbstractController {
//        @Override
//        protected void internalInit(URL location, ResourceBundle resources) {
//        };
//    };
//
//    @Test
//    public void testGetApiTask() throws Exception {
//        List<Integer> list = new LinkedList<>();
//
//        AbstractControllerSubclass unit = new AbstractControllerSubclass();
//        Task<Boolean> t = unit.getApiTask(o -> list.add(5), null);
//        t.run();
//
//        assertThat(t.get(), is(true) /* List#add always returns true */);
//        assertThat(list.size(), is(1));
//        assertThat(list.get(0), is(5));
//
//    }
//
//    @Test
//    public void testRunApiTask() throws Exception {
//        List<Integer> list = new LinkedList<>();
//        ExecutorService es = Executors.newFixedThreadPool(1);
//        AbstractControllerSubclass unit = new AbstractControllerSubclass();
//
//        Task<Boolean> t = unit.getApiTask(o -> list.add(9), null);
//        EventHandler eh1 = mock(EventHandler.class);
//        EventHandler eh2 = mock(EventHandler.class);
//        EventHandler eh3 = mock(EventHandler.class);
//
//        unit.runApiTask(t, eh1, eh2, eh3);
//        try {
//            unit.getExecutor().awaitTermination(2, TimeUnit.SECONDS);
//        }
//        catch (InterruptedException ex) {
//            fail("Execution took too long");
//        }
//
//        assertThat(t.get(), is(true) /* List#add alwaays returns true */);
//        assertThat(list.size(), is(1));
//        assertThat(list.get(0), is(9));
//        assertThat(t.getOnSucceeded(), is(eh1));
//        assertThat(t.getOnFailed(), is(eh2));
//        assertThat(t.getOnCancelled(), is(eh3));
//    }
//
//    @Test
//    public void testGetEmptyEvent() {
//        assertThat(new AbstractControllerSubclass().getEmptyEvent(), isNotNull());
//    }
//
//    @Test
//    public void testGetErrorEvent() {
//        List<Integer> list = new LinkedList<>(); // will increase this list
//
//        AbstractControllerSubclass unit = new AbstractControllerSubclass();
//
//        // need exception in order for the method to do something
//        Throwable exception = new RuntimeException();
//        Task<Boolean> task = mock(Task.class);
//        when(task.getException()).thenReturn(exception);
//
//        // make a consumer that updates the list
//        EventHandler result = unit.getErrorEvent(t -> list.add(-4), task);
//        result.handle(mock(Event.class));
//        assertThat(list.size(), is(1)); // if the consumer ran, the list must have gotten updated
//        assertThat(list.get(0), is(-4));
//
//    }
}