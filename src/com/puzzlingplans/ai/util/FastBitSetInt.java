package com.puzzlingplans.ai.util;

import java.util.Arrays;
import java.util.BitSet;

public class FastBitSetInt extends FastBitSet<FastBitSetInt>
{
	private static final int WORDBITS = 32;
	private static final int WORDMASK = 31;
	private static final int WORDSHIFT = 5;
	
	private int[] words;

	public FastBitSetInt(int nbits)
	{
		super(nbits);
		this.words = new int[(nbits + WORDMASK) >> WORDSHIFT];
	}

	public FastBitSetInt(int nbits, int[] words2)
	{
		super(nbits);
		this.words = Arrays.copyOf(words2, words2.length);
	}

	@Override
	public FastBitSetInt clone() throws CloneNotSupportedException
	{
		return new FastBitSetInt(nbits, words);
	}

	@Override
	public boolean get(int index)
	{
		assert(index >= 0 && index < nbits);
		int w = words[index >> WORDSHIFT];
		index &= WORDMASK;
		return ((1 << index) & w) != 0;
	}

	@Override
	public void set(int index)
	{
		int m = (1 << (index & WORDMASK));
		int i = index >> WORDSHIFT;
		words[i] |= m;
	}

	@Override
	public void clear(int index)
	{
		int m = (1 << (index & WORDMASK));
		int i = index >> WORDSHIFT;
		words[i] &= ~m;
	}

	@Override
	public void or(FastBitSetInt a)
	{
		assert(nbits == a.nbits);
		int l = words.length;
		for (int i = 0; i < l; i++)
			words[i] |= a.words[i];
	}

	@Override
	public void and(FastBitSetInt a)
	{
		assert(nbits == a.nbits);
		int l = words.length;
		for (int i = 0; i < l; i++)
			words[i] &= a.words[i];
	}

	@Override
	public void xor(FastBitSetInt a)
	{
		assert(nbits == a.nbits);
		int l = words.length;
		for (int i = 0; i < l; i++)
			words[i] ^= a.words[i];
	}

	@Override
	public void invert()
	{
		for (int i = 0; i < words.length - 1; i++)
			words[i] ^= -1;
		if ((nbits & WORDMASK) != 0)
			words[words.length-1] ^= (1 << (nbits & WORDMASK)) - 1;
		else
			words[words.length-1] ^= -1;
	}

	@Override
	public long getRange64(int a, int b)
	{
		assert (a >= 0) && (b >= a) && (b - a <= WORDBITS);
		// TODO: test
		int w1 = a >> WORDSHIFT;
		int w2 = (b - 1) >> WORDSHIFT;
		long v = words[w1] >>> (a & WORDMASK);
		if (w2 > w1)
			v |= words[w2] << (WORDBITS - (a & WORDMASK));
		v &= (1L << (b - a)) - 1;
		return v;
	}

	@Override
	public boolean isEmpty()
	{
		for (int i = 0; i < words.length; i++)
			if (words[i] != 0)
				return false;
		return true;
	}

	@Override
	public BitSet toBitSet()
	{
		long[] longs = new long[(words.length + 1) / 2];
		for (int i=0; i<words.length; i+=2)
		{
			longs[i] = words[i];
			if (i+1 < words.length)
				longs[i] |= (long)words[i] << 32;
		}
		return BitSet.valueOf(longs);
	}

	@Override
	public void clear()
	{
		Arrays.fill(words, 0);
	}

	@Override
	public int cardinality()
	{
		int n = 0;
		for (int w : words)
			n += BitUtils.countBitsInt(w);
		return n;
	}

	@Override
	public int nextSetBit(int i)
	{
		int wi = (i >> WORDSHIFT);
		int bi = i & WORDMASK;
		while (wi < words.length)
		{
			int next = BitUtils.nextBit(words[wi], bi);
			if (next >= 0)
				return (wi << WORDSHIFT) + next;
			wi++;
			bi = 0;
		}
		return -1;
	}

	@Override
	public long longValue()
	{
		throw new NotSupportedException();
	}
}
