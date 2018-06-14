package com.puzzlingplans.ai.util;

import java.util.Arrays;

public class FastHash<T>
{
	private final int order;
	private final int size;
	private final int mask;
	
	private final long[] keys;
	private final int[] visits;
	private final T[] entries;
	private final int maxCollisions;
	
	private int cullOrder;
	
	private volatile int numKeys;
	
	public int totalInserts;
	public int totalFailedInserts;
	public int totalSwaps;
	public int totalCulls;
	public int totalRetries;

	// desired probability of a failed insert given uniform hash distribution
	private static final double FAILED_INSERT_PROBABILITY = 1e-10;
	
	private static final long NullHash = -9178294791873491491L;

	//
	
	public FastHash(int numEntriesLog2, int numCollisions)
	{
		assert(numEntriesLog2 > 0);
		this.order = numEntriesLog2;
		this.size = 1 << numEntriesLog2;
		this.cullOrder = 0;
		this.mask = size-1;
		this.keys = new long[size];
		this.visits = new int[size];
		this.entries = (T[]) new Object[size];
		this.maxCollisions = numCollisions;
		Arrays.fill(keys, NullHash);
	}

	public FastHash(int numEntriesLog2, float cullRatio, int cullOrder)
	{
		this(numEntriesLog2, suggestedMaxCollisionsForRatio(cullRatio));
		setAutoCull(cullOrder);
	}

	// http://preshing.com/20110504/hash-collision-probabilities/
	public static int suggestedMaxCollisionsForRatio(float cullRatio)
	{
		// P collision probability =~ 1 - exp(-(k^2/(2 N)))
		// k = number of keys
		// N = hash capacity
		// probability of C collisions in a row = (1 - exp(-r/2)) ^ C
		// C = Log[P] / Log[1 - exp(-(k^2/(2 N)))]
		double prob = FAILED_INSERT_PROBABILITY;
		double r = cullRatio;
		return (int) Math.ceil(Math.log(prob) / Math.log(1 - Math.exp(-r/2)));
		// TODO: this only applies if hash is uniform
	}

	public void setAutoCull(int order)
	{
		this.cullOrder = order;
	}
	
	public int entryIndex(long hash, long key)
	{
		int i = hashIndex(hash);
		int n = 0;
		do {
			long k = keys[i];
			if (k == key)
				return i+1;
			if (k == NullHash)
				return -i-1;

			//if (n > maxCollisions-5) System.out.println(MiscUtils.format("(%8x %8x) %3d %8x %5d %8x %s", hash, key, n, i, visits[i], k, entries[i]));
			i = (i + 1) & mask;
		} while (n++ < maxCollisions);
		return 0;
	}

	public final int hashIndex(long hash)
	{
		return (int) (hash & mask);
	}

	public T getEntryAt(long hash, long key)
	{
		int i = entryIndex(hash, key);
		if (i > 0)
		{
			i--;
			visits[i]++;
			int hi = hashIndex(hash);
			// see if we should swap higher priority entry with a lower priority entry
			if (i != hi && (visits[i] >> 1) > visits[hi])
			{
				// make sure nothing changed, then lock and swap the values
				synchronized (this)
				{
					if (keys[i] == key && keys[hi] != key)
					{
						swap(i, hi);
						i = hi;
					}
				}
			}
			T entry = entries[i];
			// make sure key is still the same, if not, retry
			if (keys[i] != key)
			{
				totalRetries++;
				return getEntryAt(hash, key);
			}
			else
				return entry;
		}
		return null;
	}

	private void swap(int i, int j)
	{
		long k = keys[i];
		int v = visits[i];
		T e = entries[i];
		long k2 = keys[j];
		keys[i] = ~k2; // make keys temporarily unavailable 
		keys[j] = ~k;  // so we don't get entries out of order...
		visits[j] = v;
		visits[i] = visits[j];
		entries[i] = entries[j];
		entries[j] = e;
		keys[i] = k2;
		keys[j] = k;
		totalSwaps++;
	}

	public T insertEntry(long hash, long key, T entry)
	{
		int i = entryIndex(hash, key);
		synchronized (this)
		{
			if (i == 0)
			{
				// insert failed; try to free up some room
				if (cullOrder > 0 && cullLeastVisitedEntries(cullOrder) > 0)
				{
					return insertEntry(hash, key, entry);
				}
				totalFailedInserts++;
				return null;
			}
			int index = i > 0 ? i-1 : -i-1;
			// make sure key hasn't changed -- if so, retry
			long oldkey = keys[index];
			if (oldkey != key && oldkey != NullHash)
			{
				totalRetries++;
				return insertEntry(hash, key, entry);
			}
			if (oldkey == NullHash)
				numKeys++;
			totalInserts++;
			keys[index] = ~oldkey; // make sure we don't grab while modifying
			entries[index] = entry;
			visits[index] = 1;
			keys[index] = key;
			return entry;
		}
	}

	@Override
	public String toString()
	{
		return "[keys=" + numKeys + "/" + size + ", inserts=" + totalInserts + ", failedInserts=" + totalFailedInserts + ", swaps="
				+ totalSwaps + ", culls=" + totalCulls + ", retries=" + totalRetries + "]";
	}

	public void clear()
	{
		Arrays.fill(keys, NullHash);
		Arrays.fill(visits, 0);
		Arrays.fill(entries, null);
		numKeys = 0;
		resetStatistics();
	}

	public void resetStatistics()
	{
		this.totalFailedInserts = 0;
		this.totalInserts = 0;
		this.totalSwaps = 0;
		this.totalCulls = 0;
	}

	public synchronized int cullLeastVisitedEntries(int nlog2)
	{
		int n = 0;
		for (int i=0; i<size; i++)
		{
			if (visits[i] > 0 && (visits[i] >>>= nlog2) == 0)
			{
				keys[i] = NullHash;
				entries[i] = null;
				n++;
			}
		}
		this.numKeys -= n;
		this.totalCulls += n;
		//System.out.println(this + " culled " + n);
		return n;
	}

	public int countEntries()
	{
		int n = 0;
		for (int i=0; i<size; i++)
			if (entries[i] != null)
				n++;
		return n;
	}

	public int maxCollisions()
	{
		return maxCollisions;
	}

	public boolean containsEntry(long hash, long key)
	{
		return entryIndex(hash, key) > 0;
	}

	public int keyCount()
	{
		return numKeys;
	}

	public int capacity()
	{
		return size;
	}

	public T getEntryAtIndex(int i)
	{
		return entries[i];
	}

	public void addVisitedForEntryAtIndex(int i)
	{
		visits[i]++;
	}

}
