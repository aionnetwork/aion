package org.aion.crypto.hash;

import static org.junit.Assert.*;

import org.aion.crypto.hash.Blake2b.Engine;
import org.junit.Test;

public class Blake2bTest {

	@Test
	public void test() {
		byte[] in = new byte[16 * 8 +3];
//		for (int i = 0; i < 64; i++) {
//			in[i] = (byte) i;
//		}
		Engine e = new Engine();

		long ts0 = 0;
		long ts1 = 0;
		long tt0, tt1;

		for (int i = 0; i < 100000; i++) {
			tt0 = System.nanoTime();
			e.USE_BB_BS2LONG_CONVERT = true;
			tt1 = System.nanoTime();
			e.compress(in, 3);
			ts0 += tt1 - tt0;
			e.USE_BB_BS2LONG_CONVERT = false;
			e.compress(in, 3);
			tt0 = System.nanoTime();
			ts1 += tt0 - tt1;
		}

		//  T0:4693174 T1:152919871 T0:T1_0.030690413   ,  use BB convert byte[] to long will be 30x faster.
		System.out.println(" T0:" + ts0 + " T1:" + ts1 + " T0:T1_" + (float)ts0 / ts1);
	}

}
