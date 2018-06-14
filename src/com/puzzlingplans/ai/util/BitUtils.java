package com.puzzlingplans.ai.util;

import java.util.BitSet;
import java.util.Iterator;
import java.util.Random;

public class BitUtils
{
	private static final byte[] lbits;
	private static final byte[] nbits;
	private static final byte[] hbits;
	static {
		// TODO: table
		lbits = new byte[256];
		nbits = new byte[256];
		hbits = new byte[256];
		for (int i=0; i<256; i++)
		{
			lbits[i] = (byte) _nextBit(i, 0);
			nbits[i] = (byte) _countBits(i);
			hbits[i] = (byte) _highSetBit(i);
		}
	}

	public static int countBits(long mask)
	{
		int n = 0;
		while (mask != 0)
		{
			n += nbits[(int)mask & 0xff];
			mask >>>= 8;
		}
		return n;
	}

	public static int countBitsInt(int mask)
	{
		int n = 0;
		while (mask != 0)
		{
			n += nbits[mask & 0xff];
			mask >>>= 8;
		}
		return n;
	}

	public static int _countBits(long mask)
	{
		int n = 0;
		while (mask != 0)
		{
			if ((mask & 1) != 0)
			{
				n++;
			}
			mask >>>= 1;
		}
		return n;
	}

	public static int choose_bit(long mask, Random rnd)
	{
		int n = BitUtils.countBits(mask);
		assert(n != 0);
		int i = rnd.nextInt(n);
		return getNthBitPosition(mask, i);
	}

	public static int getNthBitPosition(long mask, int n)
	{
		int i = 0;
		int l;
		
		l = nbits[(int)mask & 0xff];
		if (n >= l)
		{
			n -= l;
			i += 8;
			mask >>>= 8;
			l = nbits[(int)mask & 0xff];
			if (n >= l)
			{
				n -= l;
				i += 8;
				mask >>>= 8;
				l = nbits[(int)mask & 0xff];
				if (n >= l)
				{
					n -= l;
					i += 8;
					mask >>>= 8;
				}
			}
		}

		while (mask != 0)
		{
			if ((mask & 1) != 0 && n-- == 0)
				return i;
			
			mask >>>= 1;
			i++;
		}
		return -1;
	}

	public static int nextBit(long n, int i)
	{
		// TODO: speedup?
		if (i >= 64)
			return -1;
		
		n >>>= i;
		while (n != 0)
		{
			int l = lbits[(int)n & 0xff];
			if (l >= 0)
				return i + l;
			n >>>= 8;
			i += 8;
		}
		return -1;
	}

	public static int _nextBit(long n, int i)
	{
		if (i >= 64)
			return -1;
		
		n >>>= i;
		while (n != 0)
		{
			if ((n & 1) != 0)
				return i;
			i++;
			n >>>= 1;
		}
		return -1;
	}

	public static final long rotl(long x, int i)
	{
		return (x<<i) | (x>>>(i-64));
	}

	public static final long rotr(long x, int i)
	{
		return (x>>>i) | (x<<(i-64));
	}

	public static int _highSetBit(long x)
	{
		int n = -1;
		while (x != 0)
		{
			x >>>= 1;
			n++;
		}
		return n;
	}

	public static int highSetBit(long x)
	{
		if (x == 0)
			return -1;
		
		int b = 0;
		if ((x >>> 32) != 0)
		{
			b += 32;
			x >>>= 32;
		}
		if ((x >>> 16) != 0)
		{
			b += 16;
			x >>>= 16;
		}
		if ((x >>> 8) != 0)
		{
			b += 8;
			x >>>= 8;
		}
		return hbits[(int)x & 0xff] + b;
	}

    /**
     * Returns the smallest number n such that (1<<n) >= x
     * x must be > 0, or returns negative
     */
	public static int log2fast(int v)
	{
		assert(v > 0);
		int fv = Float.floatToIntBits(v);
		return (fv >> 23) - 127;
	}

	public static BitSet toBitSet(long mask)
	{
		return BitSet.valueOf(new long[] { mask });
	}
	
	public static class BitIterator implements Iterator<Integer>, Iterable<Integer>
	{
		long mask;
		int i;

		public BitIterator(long mask)
		{
			this.mask = mask;
			this.i = BitUtils.nextBit(mask, 0);
		}

		@Override
		public boolean hasNext()
		{
			return i >= 0;
		}

		@Override
		public Integer next()
		{
			assert(i >= 0);
			int j = i;
			i = BitUtils.nextBit(mask, i+1);
			return j;
		}

		@Override
		public void remove()
		{
			throw new RuntimeException();
		}

		@Override
		public Iterator<Integer> iterator()
		{
			return this;
		}
	}

	public static BitIterator bitsSet(final long mask)
	{
		return new BitIterator(mask);
	}

	public static long mix64(long x)
	{
		x ^= x >> 12; // a
		x ^= x << 25; // b
		x ^= x >> 27; // c
		return x * 2685821657736338717L;
	}
	
	public static long mix128(long s0, long s1)
	{
		s1 ^= s1 << 23;
		return ( s1 ^ s0 ^ ( s1 >> 17 ) ^ ( s0 >> 26 ) ) + s0;
	}

}
