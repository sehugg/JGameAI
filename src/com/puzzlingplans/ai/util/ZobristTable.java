package com.puzzlingplans.ai.util;

import java.util.Random;

public class ZobristTable
{
	private final int n;
	private final long[] table;

	public ZobristTable(int n, long seed)
	{
		this.n = n;
		this.table = new long[n];
		
		Random rnd = new RandomXorshift128(seed);
		for (int i=0; i<n; i++)
		{
			table[i] = rnd.nextLong();
		}
	}
	
	public ZobristTable(int n, Class<?> seedClass)
	{
		this(n, seedClass.getName());
	}

	public ZobristTable(int n, String name)
	{
		this(n, name.hashCode());
	}

	public final int size()
	{
		return n;
	}
	
	public final long get(int i)
	{
		return table[i];
	}
}
