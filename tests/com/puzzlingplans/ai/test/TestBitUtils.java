package com.puzzlingplans.ai.test;

import java.util.Iterator;
import java.util.Random;

import junit.textui.TestRunner;

import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class TestBitUtils extends BaseTestCase
{
	public void testRNG()
	{
		RandomXorshift128 rnd = new RandomXorshift128(0);
		assertEquals(7049883165365186640L, rnd.nextLong());
		assertEquals(-1238009538, rnd.nextInt());
		assertEquals(5, rnd.nextInt(8));
		assertEquals(1, rnd.nextInt(4));
	}

	public void testCountBits()
	{
		assertEquals(0, BitUtils.countBits(0));
		assertEquals(64, BitUtils.countBits(-1L));
		assertEquals(4, BitUtils.countBits(0x12000021L));
	}
	
	public void testNextBit()
	{
		assertEquals(0, BitUtils.nextBit(1, 0));
		assertEquals(1, BitUtils.nextBit(2, 0));
		assertEquals(-1, BitUtils.nextBit(1, 1));
		assertEquals(-1, BitUtils.nextBit(0, 0));
		assertEquals(-1, BitUtils.nextBit(1, 2));
		assertEquals(8, BitUtils.nextBit(0x100, 0));
		assertEquals(8, BitUtils.nextBit(0x100, 4));
		assertEquals(8, BitUtils.nextBit(0x100, 8));
		assertEquals(-1, BitUtils.nextBit(0x100, 9));
	}

	public void testNextBitIterator()
	{
		{
			Iterator<Integer> it = BitUtils.bitsSet(0);
			assertFalse(it.hasNext());
		}
		{
			Iterator<Integer> it = BitUtils.bitsSet(1);
			assertTrue(it.hasNext());
			assertEquals(0, (int)it.next());
			assertFalse(it.hasNext());
		}
		{
			Iterator<Integer> it = BitUtils.bitsSet(0x80);
			assertTrue(it.hasNext());
			assertEquals(7, (int)it.next());
			assertFalse(it.hasNext());
		}
		{
			Iterator<Integer> it = BitUtils.bitsSet(2|8);
			assertTrue(it.hasNext());
			assertEquals(1, (int)it.next());
			assertTrue(it.hasNext());
			assertEquals(3, (int)it.next());
			assertFalse(it.hasNext());
		}
	}

	public void testHighSetBit()
	{
		assertEquals(-1, BitUtils.highSetBit(0));
		assertEquals(0, BitUtils.highSetBit(1));
		assertEquals(1, BitUtils.highSetBit(2));
		assertEquals(2, BitUtils.highSetBit(2|4));
		assertEquals(2, BitUtils.highSetBit(1|4));
		assertEquals(7, BitUtils.highSetBit(128));
		assertEquals(8, BitUtils.highSetBit(0x100));
		assertEquals(16, BitUtils.highSetBit(0x10000));
		assertEquals(24, BitUtils.highSetBit(0x1000000));
		assertEquals(32, BitUtils.highSetBit(0x100000000L));
		assertEquals(31, BitUtils.highSetBit(0x100000000L-1));
		assertEquals(63, BitUtils.highSetBit(-1));
	}

	public void testImplementations()
	{
		for (int i=-100000; i<1000000; i++)
		{
			int x = i*53;
			assertEquals(BitUtils.countBits(x), BitUtils._countBits(x));
			assertEquals(BitUtils.nextBit(x,0), BitUtils._nextBit(x,0));
			assertEquals(BitUtils.nextBit(x,i&63), BitUtils._nextBit(x,i&63));
			assertEquals(BitUtils.highSetBit(x), BitUtils._highSetBit(x));
		}
	}
	
	public void testRandom()
	{
		Random rnd = new RandomXorshift128(0);
		//Random rnd = new Random(0);
		
		int n = 100;
		int m = 1000000;
		int p = 13;
		int[] zeroes = new int[n];
		for (int i=0; i<m; i++)
		{
			int r2 = rnd.nextInt(31);
			assertTrue(r2 >= 0);
			long r = rnd.nextLong();
			for (int j=2; j<n; j++)
			{
				if ((r % (j * p)) == 0)
					zeroes[j]++;
			}
		}
		// make sure frequency of each factor is within 10%
		for (int j=2; j<n; j++)
		{
			double freq = zeroes[j]*j*p*1.0/m;
			//System.out.println((j*p) + "\t" + freq);
			assertTrue(freq > 0.9 && freq < 1.1);
		}
	}
	
	public void testNthBit()
	{
		assertEquals(-1, BitUtils.getNthBitPosition(0, 0));
		assertEquals(-1, BitUtils.getNthBitPosition(0, 1));
		assertEquals(0, BitUtils.getNthBitPosition(1, 0));
		assertEquals(-1, BitUtils.getNthBitPosition(1, 1));
		assertEquals(1, BitUtils.getNthBitPosition(2, 0));
		assertEquals(-1, BitUtils.getNthBitPosition(2, 1));
		assertEquals(0, BitUtils.getNthBitPosition(3, 0));
		assertEquals(1, BitUtils.getNthBitPosition(3, 1));
		assertEquals(-1, BitUtils.getNthBitPosition(3, 2));
		assertEquals(1, BitUtils.getNthBitPosition(2|8, 0));
		assertEquals(3, BitUtils.getNthBitPosition(2|8, 1));
		assertEquals(-1, BitUtils.getNthBitPosition(2|8, 2));
		assertEquals(0, BitUtils.getNthBitPosition(0x101, 0));
		assertEquals(0+8, BitUtils.getNthBitPosition(0x101, 1));
		assertEquals(-1, BitUtils.getNthBitPosition(0x101, 2));
		assertEquals(1, BitUtils.getNthBitPosition(0x202, 0));
		assertEquals(1+8, BitUtils.getNthBitPosition(0x202, 1));
		assertEquals(-1, BitUtils.getNthBitPosition(0x202, 2));
		assertEquals(1, BitUtils.getNthBitPosition(0x20202, 0));
		assertEquals(1+8, BitUtils.getNthBitPosition(0x20202, 1));
		assertEquals(1+8+8, BitUtils.getNthBitPosition(0x20202, 2));
		assertEquals(-1, BitUtils.getNthBitPosition(0x20202, 3));
		assertEquals(1, BitUtils.getNthBitPosition(0x2000002, 0));
		assertEquals(1+8+8+8, BitUtils.getNthBitPosition(0x2000002, 1));
		assertEquals(0, BitUtils.getNthBitPosition(0x81, 0));
		assertEquals(7, BitUtils.getNthBitPosition(0x81, 1));
		assertEquals(63, BitUtils.getNthBitPosition(-1, 63));
		assertEquals(-1, BitUtils.getNthBitPosition(-1, 64));
		assertEquals(-1, BitUtils.getNthBitPosition(0x2000002, 2));
		assertEquals(-1, BitUtils.getNthBitPosition(0x2000002, 3));
	}
	
	public void testChooseBit()
	{
		doChooseBit(0x1);
		doChooseBit(0x100f);
		doChooseBit(-1);
		doChooseBit(0x123456789abcdefL);
	}

	private void doChooseBit(final long mask)
	{
		/* OLD:
		chooseBit 1: 178 cpu msec, 5.6179776E7 ops/sec
		chooseBit 100f: 604 cpu msec, 1.6556291E7 ops/sec
		chooseBit ffffffffffffffff: 704 cpu msec, 1.4204545E7 ops/sec
		chooseBit 123456789abcdef: 800 cpu msec, 1.25E7 ops/sec
		 */
		final Random rnd = new RandomXorshift128();
		benchmark("chooseBit " + Long.toHexString(mask), 10000000, new Runnable()
		{
			@Override
			public void run()
			{
				int b = BitUtils.choose_bit(mask, rnd);
				assertTrue(((1L<<b) & mask) != 0);
			}
		});
	}

	public void testChooseBitDistribution()
	{
		Random rnd = new RandomXorshift128();
		int[] count = new int[64];
		int niters = 5000000;
		for (int i=0; i<niters; i++)
		{
			long mask = rnd.nextLong();
			if (mask == 0)
				continue;
			int b = BitUtils.choose_bit(mask, rnd);
			count[b]++;
		}
		for (int i=0; i<count.length; i++)
		{
			float error = Math.abs(count[i] - niters / 64) * 64.0f / niters;
			System.out.println(i + "\t" + count[i] + "\t" + error);
			assertTrue(error < 0.02f);
		}
	}

	public void testLog2()
	{
		assertEquals(0, BitUtils.log2fast(1));
		assertEquals(1, BitUtils.log2fast(2));
		assertEquals(1, BitUtils.log2fast(3));
		assertEquals(2, BitUtils.log2fast(4));
		assertEquals(8, BitUtils.log2fast(256));
		assertEquals(8, BitUtils.log2fast(257));
		assertEquals(8, BitUtils.log2fast(511));
		assertEquals(0x7fffffff, (-1>>>1));
		assertEquals("bf800000", Integer.toHexString(Float.floatToIntBits(-1)));
		assertEquals("4f000000", Integer.toHexString(Float.floatToIntBits(-1>>>1)));
		assertEquals("4f000000", Integer.toHexString(Float.floatToIntBits((int)(-1>>>1))));
		assertEquals("4f000000", Integer.toHexString(Float.floatToIntBits(0x7fffffff)));
		assertEquals("4e800000", Integer.toHexString(Float.floatToIntBits(-1>>>2)));
		assertEquals("4e800000", Integer.toHexString(Float.floatToIntBits((int)(-1>>>2))));
		assertEquals("4e800000", Integer.toHexString(Float.floatToIntBits(0x3fffffff)));
		assertEquals(31, BitUtils.log2fast(-1>>>1));
	}
	
	public void testBenchmarks()
	{
		assertTrue(150 > benchmark("countBits", new Benchmarkable()
		{
			@Override
			public int run()
			{
				int n = 10000000;
				for (int i=0; i<n; i++)
					BitUtils.countBits(i);
				return n;
			}
		}));
		assertTrue(100 > benchmark("nextBit", new Benchmarkable()
		{
			@Override
			public int run()
			{
				int n = 10000000;
				for (int i=0; i<n; i++)
					BitUtils.nextBit(i, 0);
				return n;
			}
		}));
		assertTrue(150 > benchmark("highSetBit", new Benchmarkable()
		{
			@Override
			public int run()
			{
				int n = 10000000;
				for (int i=0; i<n; i++)
					BitUtils.highSetBit(i);
				return n;
			}
		}));
		assertTrue(100 > benchmark("random", new Benchmarkable()
		{
			@Override
			public int run()
			{
				Random rnd = new RandomXorshift128();
				int n = 10000000;
				for (int i=0; i<n; i++)
					rnd.nextLong();
				return n;
			}
		}));
	}
	
	//
	
	public static void main(String[] args)
	{
		TestRunner.run(TestBitUtils.class);
	}
}
