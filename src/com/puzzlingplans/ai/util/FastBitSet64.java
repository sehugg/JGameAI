package com.puzzlingplans.ai.util;

import java.util.BitSet;

public class FastBitSet64 extends FastBitSet<FastBitSet64>
{
	private static final int MAXBITS = 64;

	private long value;

	//
	
	public FastBitSet64(int nbits)
	{
		super(nbits);
		if (nbits <= 0 || nbits > MAXBITS)
			throw new IllegalArgumentException("Number of bits must be between 1 and " + MAXBITS);
	}

	public FastBitSet64(int nbits, long value2)
	{
		super(nbits);
		this.value = value2;
	}

	@Override
	public boolean get(int index)
	{
		assert(index >= 0 && index < nbits);
		return ((1L << index) & value) != 0;
	}

	@Override
	public void set(int index)
	{
		long m = (1L << (index & (MAXBITS-1)));
		value |= m;
	}

	@Override
	public void clear(int index)
	{
		long m = (1L << (index & (MAXBITS-1)));
		value &= ~m;
	}

	@Override
	public void or(FastBitSet64 a)
	{
		assert(nbits == a.nbits);
		value |= a.value;
	}

	@Override
	public void and(FastBitSet64 a)
	{
		assert(nbits == a.nbits);
		value &= a.value;
	}

	@Override
	public void xor(FastBitSet64 a)
	{
		assert(nbits == a.nbits);
		value ^= a.value;
	}

	@Override
	public void invert()
	{
		value ^= getMask64();
	}

	@Override
	public long getRange64(int a, int b)
	{
		assert (a >= 0) && (b >= a) && (b - a <= MAXBITS);
		return (value >>> a) & ((1L << (b - a)) - 1);
	}

	@Override
	public boolean isEmpty()
	{
		return (value == 0);
	}

	@Override
	public String toString()
	{
		return toBitSet() + "";
	}

	@Override
	public BitSet toBitSet()
	{
		return BitSet.valueOf(new long[] { value });
	}

	@Override
	public void clear()
	{
		value = 0;
	}

	@Override
	public int cardinality()
	{
		return BitUtils.countBits(value);
	}

	@Override
	public int nextSetBit(int i)
	{
		return BitUtils.nextBit(value, i);
	}

	@Override
	public long longValue()
	{
		return value;
	}

	public long getMask64()
	{
		if (nbits == MAXBITS)
			return -1;
		else
			return (1L << nbits) - 1;
	}

}
