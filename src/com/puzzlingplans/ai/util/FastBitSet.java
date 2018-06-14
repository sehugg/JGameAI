package com.puzzlingplans.ai.util;

import java.util.BitSet;

public abstract class FastBitSet<T> extends CloningObject
{
	protected final int nbits;
	
	//

	public FastBitSet(int nbits)
	{
		this.nbits = nbits;
	}
	
	public int numBits()
	{
		return nbits;
	}
	
	@Override
	public T clone() throws CloneNotSupportedException
	{
		return (T) super.clone();
	}

	public abstract boolean get(int index);
	public abstract void set(int index);
	public abstract void clear(int index);
	public abstract void or(T a);
	public abstract void and(T a);
	public abstract void xor(T a);
	public abstract void invert();
	public abstract void clear();
	public abstract long getRange64(int a, int b);
	public abstract boolean isEmpty();
	public abstract BitSet toBitSet();
	public abstract int cardinality();
	public abstract int nextSetBit(int i);
	public abstract long longValue();

	public void set(int index, boolean b)
	{
		if (b)
			set(index);
		else
			clear(index);
	}

	@Override
	public String toString()
	{
		return toBitSet() + "";
	}
	
	//

	public static FastBitSet<?> create(int nbits)
	{
		if (nbits > 64)
			return new FastBitSetLong(nbits);
		else
			return new FastBitSet64(nbits);
	}

}
