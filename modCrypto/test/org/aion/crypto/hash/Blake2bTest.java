package org.aion.crypto.hash;

import org.aion.crypto.hash.Blake2b.Engine;
import org.junit.Test;

public class Blake2bTest {

	@Test
	public void test() {
		byte[] in = new byte[16 * 8 +3];

		Engine e = new Engine();

		long ts0 = 0;
		long ts1 = 0;
		long tt0, tt1;

		tt0 = System.nanoTime();
		
		for (int i = 0; i < 100000; i++) {
			
			e.USE_BB_BS2LONG_CONVERT = true;
			e.compress(in, 3);
			
			tt1 = System.nanoTime();
			ts0 += tt1 - tt0;
			
			e.USE_BB_BS2LONG_CONVERT = false;
			e.compress(in, 3);
			
			tt0 = System.nanoTime();
			ts1 += tt0 - tt1;
		}

		//   T0:77810133 T1:79152316 T0:T1_0.983043 ,  load M[i] from BB will increase little bit perf.  
		System.out.println(" T0:" + ts0 + " T1:" + ts1 + " T0:T1_" + (float)ts0 / ts1);
	}

}
