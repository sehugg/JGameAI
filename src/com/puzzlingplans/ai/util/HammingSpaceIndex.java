package com.puzzlingplans.ai.util;

public class HammingSpaceIndex
{
	public static class Entry
	{
		public long key;
		public long good;
		public double m2;
		public float q;
		public int nv;
		
		@Override
		public String toString()
		{
			//return MiscUtils.format("[%x %x %f/%f %d = %f]", key, good, q, (float)getVariance(), nv, (float)getScore());
			return key + " " + good + " " + q + " " + getVariance() + " " + nv + " " + getScore();
		}

		public void updateScore(float value)
		{
			// http://en.wikipedia.org/wiki/Algorithms_for_calculating_variance#Incremental_Algorithm
			float delta = value - q;
			q += delta / ++nv;
			m2 += delta * (value - q);
		}
		
		public double getVariance()
		{
			//return (nv > 1) ? m2 / (nv - 1) : 0;
			return (m2 + 1) / nv;
		}

		public double getScore()
		{
			return q * (1 - getVariance());
		}
	}

	FastHash<Entry>[] tables;

	public HammingSpaceIndex(int log2)
	{
		tables = new FastHash[64];
		for (int i = 0; i < tables.length; i++)
		{
			// TODO: resize levels dynamically?
			tables[i] = new FastHash<Entry>(log2, 0.75f, 1);
		}
	}

	public void add(long mask, float value, int index)
	{
		int n = BitUtils.countBits(mask) - 1;
		assert (n >= 0);
		Entry entry = tables[n].getEntryAt(mask, mask);
		if (entry == null)
		{
			entry = new Entry();
			entry.key = mask;
			entry = tables[n].insertEntry(mask, mask, entry);
		}
		if (entry != null)
		{
			entry.updateScore(value);
			long m = 1L << index;
			float v = entry.q;
			if (v > 0)
				entry.good |= m;
			else if (v < 0)
				entry.good &= ~m;
			//System.out.println("+" + n + "\t" + entry);
		}
	}

	public long getBestMovesFor(long key, int radius)
	{
		int n = BitUtils.countBits(key) - 1;
		Entry bestentry = getBestEntryFor(n, key, radius);
		for (int i=1; bestentry == null && i<=radius/2; i++)
		{
			if (bestentry == null)
				bestentry = getBestEntryFor(n-i, key, radius);
			if (bestentry == null)
				bestentry = getBestEntryFor(n+i, key, radius);
		}
		return bestentry != null ? bestentry.good : 0;
	}

	private Entry getBestEntryFor(int n, long key, int maxdist)
	{
		if (n < 0 || n >= 64)
			return null;
		
		FastHash<Entry> tbl = tables[n];
		float bestscore = 0;
		Entry bestentry = null;
		int bestindex = -1;
		int index = tbl.hashIndex(key);
		int capacity = tbl.capacity();
		for (int i = 0; i < capacity; i++)
		{
			Entry entry = tbl.getEntryAtIndex(index);
			if (entry != null && entry.good != 0 && entry.q > 0)
			{
				int dist = BitUtils.countBits(entry.key ^ key);
				if (dist <= maxdist)
				{
					float score = (float) (entry.getScore() / (dist + 1));
					if (score > bestscore)
					{
						bestentry = entry;
						bestscore = score;
						bestindex = index;
					}
				}
			}
			if (++index >= capacity)
				index = 0;
		}
		if (bestindex >= 0)
		{
			tbl.addVisitedForEntryAtIndex(bestindex);
			//System.out.println(n + "\t" + bestentry);
		}
		return bestentry;
	}

	// TODO: Log
	public void dump()
	{
		for (int i=0; i<64; i++)
		{
			if (tables[i].keyCount() > 0)
				System.out.println((i+1) + " bits: " + tables[i]);
		}
	}

}
