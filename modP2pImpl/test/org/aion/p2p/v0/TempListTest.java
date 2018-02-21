package org.aion.p2p.v0;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

import org.junit.Test;

public class TempListTest {
    
    int cap = 10;
    
    private Map<Integer, Object> tempList = Collections.synchronizedMap(new LinkedHashMap<Integer, Object>(){
        private static final long serialVersionUID = 1L;
        @Override
        protected boolean removeEldestEntry(final Map.Entry<Integer, Object> eldest) {
            return size() > cap;
        } 
    });
    
    @Test
    public void testParseFromP2p(){
        ExecutorService executor = Executors.newFixedThreadPool(8);
        for(int t = 0; t < 8; t++) {
            executor.submit(() -> {
                Random r = new Random();
                for(int i = 0; i < 10000; i ++) {
                    int rnd = r.nextInt();
                    tempList.put(rnd, new Object());
                }
            });
        }
        
        try {
            Thread.sleep(5000);
            
            assertEquals(cap, this.tempList.size());
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
