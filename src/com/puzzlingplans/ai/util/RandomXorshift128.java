package com.puzzlingplans.ai.util;

import java.util.Random;

// http://en.wikipedia.org/wiki/Xorshift
public class RandomXorshift128 extends Random
{
	private static final long serialVersionUID = 1351739219823845720L;

	long x0,x1;
	
	//

	public RandomXorshift128()
	{
		this(MiscUtils.nanoTime(), MiscUtils.nanoTime());
	}

	public RandomXorshift128(long seed)
	{
		this(seed, 0);
	}

	public RandomXorshift128(long seed1, long seed2)
	{
		super(seed1 ^ seed2); // just in case we use the old methods...
		setSeed(seed1, seed2);
	}
	
	@Override
	public synchronized void setSeed(long seed)
	{
		setSeed(seed, seed);
	}

	public void setSeed(long seed1, long seed2)
	{
		this.x0 = seed1 ^ 8682522807148012L;
		this.x1 = seed2 ^ 181783497276652981L;
		nextLong();
		nextLong();
	}
	
	@Override
	public long nextLong()
	{
		long s1 = x0;
		long s0 = x1;
		x0 = s0;
		s1 ^= s1 << 23;
		return ( x1 = ( s1 ^ s0 ^ ( s1 >> 17 ) ^ ( s0 >> 26 ) ) ) + s0;
	}

	/*
	@Override
	public long nextLong()
	{
		long x = x0;
		x ^= x >> 12; // a
		x ^= x << 25; // b
		x ^= x >> 27; // c
		x0 = x;
		return x * 2685821657736338717L;
	}
	*/

	@Override
	protected int next(int nbits)
	{
		int x = (int) nextLong();
		if (nbits < 32)
			return x & ((1 << nbits) - 1);
		else
			return x;
	}

	@Override
    public int nextInt() 
    {
		return (int) nextLong();
    }
	
	// these are needed to emulate java.util.Random

	@Override
	public int nextInt(int n)
	{
		if (n <= 0)
		{
			throw new IllegalArgumentException("n <= 0: " + n);
		}
		if ((n & -n) == n)
		{
			return (int) ((n * (long) next(31)) >> 31);
		}
		int bits, val;
		do
		{
			bits = next(31);
			val = bits % n;
		} while (bits - val + (n - 1) < 0);
		return val;
	}

	@Override
	public boolean nextBoolean()
	{
		return next(1) != 0;
	}

	@Override
	public void nextBytes(byte[] buf)
	{
		int rand = 0, count = 0, loop = 0;
		while (count < buf.length)
		{
			if (loop == 0)
			{
				rand = nextInt();
				loop = 3;
			} else
			{
				loop--;
			}
			buf[count++] = (byte) rand;
			rand >>= 8;
		}
	}

}
