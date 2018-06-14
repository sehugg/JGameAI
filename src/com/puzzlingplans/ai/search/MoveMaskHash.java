package com.puzzlingplans.ai.search;

import java.util.HashMap;
import java.util.Map;

public class MoveMaskHash
{
	// TODO: could be faster impl
	Map<Long,Long> hash = new HashMap<Long,Long>();
	
	public synchronized void addIndex(long key, int index)
	{
		if (key == 0)
			return;
		
		Long ordered = hash.get(key);
		long value = 1L<<index;
		//assert((key & value) != 0);
		if (ordered == null || (ordered | value) == key) // reset if key==value
			hash.put(key, value);
		else if ((ordered | value) != ordered)
			hash.put(key, ordered | value);
	}

	public synchronized void removeIndex(long key, int index)
	{
		if (key == 0)
			return;
		
		Long ordered = hash.get(key);
		long value = 1L<<index;
		//assert((key & value) != 0);
		if (ordered != null && (ordered | value) != 0)
			hash.put(key, ordered & ~value);
	}

	public synchronized long getForMask(long key)
	{
		Long l = hash.get(key);
		return l != null ? l : 0;
	}

	public synchronized void replaceIndex(long key, int index)
	{
		if (key == 0)
			return;

		long value = 1L<<index;
		//assert((key & value) != 0);
		hash.put(key, value);
	}

	public synchronized void removeAllIndices(long key)
	{
		hash.remove(key);
	}

}
