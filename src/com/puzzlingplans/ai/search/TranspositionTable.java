package com.puzzlingplans.ai.search;

import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.search.TranspositionTable.Entry;
import com.puzzlingplans.ai.util.FastHash;


public class TranspositionTable extends FastHash<Entry>
{
	public static class Entry
	{
		int level;
		int value;
		EntryType type;
		public Line<?> line;
		
		public EntryType getType()
		{
			return type;
		}

		public int getValue()
		{
			return value;
		}

		public int getLevel()
		{
			return level;
		}
		
		@Override
		public String toString()
		{
			return "Entry [level=" + level + ", value=" + value + ", type=" + type
					+ ", line=" + line
					+ "]";
		}
	}

	public enum EntryType
	{
		EXACT, LOWER, UPPER
	}

	//
	
	public TranspositionTable(int numEntriesLog2, float cullRatio, int cullOrder)
	{
		super(numEntriesLog2, cullRatio, cullOrder);
	}
	
	public TranspositionTable(int numEntriesLog2, int numCollisions)
	{
		super(numEntriesLog2, numCollisions);
	}

	public Entry newEntry(long hash, long key, int level, int value, EntryType type)
	{
		Entry entry = new Entry();
		entry.level = level;
		entry.value = value;
		entry.type = type;
		return insertEntry(hash, key, entry);
	}

}
