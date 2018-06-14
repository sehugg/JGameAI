package com.puzzlingplans.ai.test;

import java.util.Random;

import com.puzzlingplans.ai.search.MoveMaskHash;
import com.puzzlingplans.ai.search.TranspositionTable;
import com.puzzlingplans.ai.search.TranspositionTable.Entry;
import com.puzzlingplans.ai.search.TranspositionTable.EntryType;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.FastBitSet;
import com.puzzlingplans.ai.util.FastHash;
import com.puzzlingplans.ai.util.HammingSpaceIndex;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class TestHashtables extends BaseTestCase
{
	public void testTranspositionTable()
	{
		TranspositionTable tt = new TranspositionTable(8, 1);
		tt.setAutoCull(0);
		Entry entry = tt.newEntry(1, 2, 3, 4, EntryType.EXACT);
		assertEquals(3, entry.getLevel());
		assertEquals(4, entry.getValue());
		Entry entry2 = tt.newEntry(1, 2, 4, 5, EntryType.EXACT);
		assertEquals(4, entry2.getLevel());
		assertEquals(5, entry2.getValue());
		Entry entry3 = tt.newEntry(1, 3, 10, 20, EntryType.EXACT);
		assertNotNull(entry3);
		Entry entry4 = tt.newEntry(1, 4, 10, 20, EntryType.EXACT);
		assertNull(entry4); // collision
		assertNull(tt.getEntryAt(1, 10));
		assertNull(tt.getEntryAt(2, 2));
		assertNull(tt.getEntryAt(1+256, 10));
		assertEquals(4, tt.getEntryAt(1, 2).getLevel());
		assertEquals(10, tt.getEntryAt(1, 3).getLevel());
		for (int i=0; i<256; i++)
			tt.newEntry(i, i, 0, 0, EntryType.UPPER);
		assertEquals(256, tt.countEntries());
		assertEquals(0, tt.cullLeastVisitedEntries(0));
		assertEquals(254, tt.cullLeastVisitedEntries(1));
		assertEquals(2, tt.countEntries());
	}

	public void testMoveMaskHash()
	{
		MoveMaskHash mmh = new MoveMaskHash();
		mmh.addIndex(0, 0);
		mmh.addIndex(0, 1);
		assertEquals(0, mmh.getForMask(0));
		mmh.addIndex(1, 0);
		assertEquals(1, mmh.getForMask(1));
		mmh.replaceIndex(1, 2);
		assertEquals(1<<2, mmh.getForMask(1));
		mmh.replaceIndex(0, 2);
		assertEquals(0, mmh.getForMask(0));
		//mmh.addIndex(1, 1);
		//assertEquals(1, mmh.getForMask(1));
		mmh.addIndex(7, 0);
		mmh.addIndex(7, 1);
		assertEquals(3, mmh.getForMask(7));
		mmh.addIndex(7, 2);
		assertEquals(4, mmh.getForMask(7)); // reset when all bits filled
		mmh.removeIndex(7, 2);
		assertEquals(0, mmh.getForMask(7));
		mmh.removeIndex(7, 0);
		assertEquals(0, mmh.getForMask(7));
	}
	
	public void testPerformance()
	{
		final int n = 18;
		final MoveMaskHash mmh = new MoveMaskHash();
		benchmark("moveMaskHash", 1<<n, new Runnable()
		{
			int i;
			@Override
			public void run()
			{
				int x = i & 0xffffff;
				mmh.addIndex(x, BitUtils.nextBit(x, 0));
				i++;
			}
		});
		final TranspositionTable tt = new TranspositionTable(n, 0.75f, 2);
		benchmark("transTable", 1<<n, new Runnable()
		{
			int i;
			@Override
			public void run()
			{
				BitUtils.nextBit(i, 0);
				tt.newEntry(i, i, 0, 0, EntryType.EXACT);
				i++;
			}
		});
	}

	public void testThreaded() throws Throwable
	{
		final int n = 18;
		final int m = 3;
		final TranspositionTable tt = new TranspositionTable(n, 0.99f, 2);
		benchmarkMultiThreaded("transTable", 20, new Runnable()
		{
			@Override
			public void run()
			{
				for (int i=0; i < (m<<n); i++)
				{
					long k1 = i * 0xffffl;
					long k2 = -i;
					assertNotNull(tt.newEntry(k1, k2, i, 0, EntryType.EXACT));
					Entry entry = tt.getEntryAt(k1, k2);
					if (entry != null && entry.getLevel() != i)
					{
						fail(this + " failed @ " + i + ": " + entry);
					}
					tt.getEntryAt(k1/2, k2/2);
				}
			}
		});
		System.out.println(tt);
		assertEquals(0, tt.totalFailedInserts);
		assertTrue(tt.totalRetries > 0);
		assertTrue(tt.keyCount() < 1<<n);
	}
	
	public void testLRU()
	{
		Random rnd = new RandomXorshift128();
		int n = 16;
		FastHash<Integer> fh = new FastHash<Integer>(n, 0.95f, 1);
		for (int i=2; i<(1<<n); i++)
		{
			int x = rnd.nextInt(i) + 1;
			if (fh.getEntryAt(x, x) == null)
				fh.insertEntry(x, x, x);
		}
		System.out.println(fh);
		for (int i=2; i<100; i++)
		{
			System.out.print(i + " ");
			assertNotNull(fh.getEntryAt(i, i));
		}
		System.out.println();
	}
	
	public void testCollisionEquation()
	{
		assertEquals(16, FastHash.suggestedMaxCollisionsForRatio(0.5f));
		assertEquals(20, FastHash.suggestedMaxCollisionsForRatio(0.75f));
		assertEquals(25, FastHash.suggestedMaxCollisionsForRatio(0.99f));
	}
	
	public void testHammingSpaceIndex()
	{
		int r = 2;
		HammingSpaceIndex hsi = new HammingSpaceIndex(10);
		assertEquals(0, hsi.getBestMovesFor(1, r));
		hsi.add(1, 0.5f, 4);
		hsi.add(1, 0.5f, 4);
		assertEquals(0x10, hsi.getBestMovesFor(1, r));
		assertEquals(0x10, hsi.getBestMovesFor(2, r));
		assertEquals(0x10, hsi.getBestMovesFor(3, r));
		assertEquals(0x0, hsi.getBestMovesFor(7, r));
		hsi.add(5, 0.5f, 5);
		hsi.add(5, 0.5f, 5);
		assertEquals(0x20, hsi.getBestMovesFor(7, r));
		hsi.add(1, -1, 4);
		assertEquals(0x20, hsi.getBestMovesFor(1, r)); // take next entry, since we took the good moves out
		hsi.add(0x1111111, 0.5f, 8);
		hsi.add(0x1111111, 0.5f, 8);
		assertEquals(0x100, hsi.getBestMovesFor(0x1113111, r));
	}
	
	public void testFastBitSet()
	{
		for (int s=2; s<=256; s++)
		{
			FastBitSet<?> bs = FastBitSet.create(s);
			bs.set(0);
			bs.set(s-1);
			assertEquals(2, bs.cardinality());
			assertEquals(0, bs.nextSetBit(0));
			assertEquals(s-1, bs.nextSetBit(1));
			if (s < 64)
				assertEquals(1 | (1L<<(s-1)), bs.getRange64(0, 63));
			bs.clear(0);
			assertEquals(1, bs.cardinality());
			bs.clear();
			assertEquals(0, bs.cardinality());
			assertTrue(bs.isEmpty());
			bs.invert();
			assertEquals(s, bs.cardinality());
		}
	}
}
