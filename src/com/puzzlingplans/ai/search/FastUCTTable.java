package com.puzzlingplans.ai.search;

public class FastUCTTable
{
	private int s;
	private int max;
	private double[] tab;

	public FastUCTTable(int s)
	{
		this.s = s;
		this.max = 1<<s;
		this.tab = new double[1<<(s*2)];
		int i = 0;
		for (int p=0; p<max; p++)
			for (int n=0; n<max; n++)
				tab[i++] = calcUCT(p, n);
	}
	
	public double getUCT(int p, int n)
	{
		assert(p > 0);
		assert(n > 0);
		if (p < max && n < max)
			return tab[n + (p<<s)];
		else
			return calcUCT(p, n);
	}

	private static double calcUCT(int p, int n)
	{
		return Math.sqrt(Math.log(p) / n);
	}
}
