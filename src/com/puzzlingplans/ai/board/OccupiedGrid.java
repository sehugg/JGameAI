package com.puzzlingplans.ai.board;

import com.puzzlingplans.ai.util.FastBitSet;
import com.puzzlingplans.ai.util.Revertable;


public class OccupiedGrid<T> extends Grid<T>
{
	protected T defaultValue;
	protected final int numColors;
	protected FastBitSet<?>[] occupied;
	protected long allSquaresMask;
	
	//
	
	public OccupiedGrid(int width, int height, T defaultValue, int numColors)
	{
		super(width, height, defaultValue);
		
		this.defaultValue = defaultValue;
		this.numColors = numColors;
		this.occupied = new FastBitSet[numColors];
		for (int j=0; j<numColors; j++)
			occupied[j] = FastBitSet.create(width*height);
		this.allSquaresMask = (w*h == 64) ? -1 : (1L << (w * h)) - 1;
	}

	public OccupiedGrid<T> clone() throws CloneNotSupportedException
	{
		OccupiedGrid<T> copy = (OccupiedGrid<T>) super.clone();
		copy.occupied = new FastBitSet[numColors];
		for (int i=0; i<numColors; i++)
			copy.occupied[i] = (FastBitSet) occupied[i].clone();
		return copy;
	}

	public void set(int x, int y, T val)
	{
		throw new IllegalStateException("Cannot set without color");
	}

	public void set(int i, T val)
	{
		throw new IllegalStateException("Cannot set without color");
	}

	public final void set(int x, int y, T val, int color)
	{
		int i = x + y * w;
		set(i, val, color);
	}

	public void set(int i, T val, int color)
	{
		super.set(i, val);
		
		for (int j=0; j<numColors; j++)
		{
			if (j == color)
			{
				occupied[j].set(i, true);
			} else {
				occupied[j].set(i, false);
			}
		}
	}

	public Revertable setWithUndo(final int x, final int y, T val, final int color)
	{
		return setWithUndo(xy2i(x, y), val, color);
	}
	
	public Revertable setWithUndo(final int i, T val, final int color)
	{
		final T old = get(i);
		final int oldcolor = getColor(i);
		Revertable rev = new Revertable()
		{
			@Override
			public void undo()
			{
				set(i, old, oldcolor);
			}
		};
		set(i, val, color);
		return rev;
	}

	public int getColor(int x, int y)
	{
		return getColor(xy2i(x, y));
	}

	public int getColor(int index)
	{
		// TODO? slow?
		for (int i=0; i<numColors; i++)
			if (occupied[i].get(index))
				return i;
		return -1;
	}

	public FastBitSet getOccupiedFor(int color)
	{
		return occupied[color];
	}

	public FastBitSet getAllOccupied()
	{
		FastBitSet all = FastBitSet.create(getNumCells());
		for (int i=0; i<occupied.length; i++)
			all.or(occupied[i]);
		return all;
	}

	public FastBitSet getAllUnoccupied()
	{
		FastBitSet unocc = getAllOccupied();
		unocc.invert();
		return unocc;
	}

	public long getOccupied64(int color)
	{
		return occupied[color].longValue();
	}

	public long getAllOccupied64()
	{
		assert(w*h <= 64);
		long m = 0;
		for (int i=0; i<occupied.length; i++)
			m |= getOccupied64(i);
		return m;
	}

	public long getUnoccupied64()
	{
		return getAllOccupied64() ^ getAllSquares64();
	}

	private long getAllSquares64()
	{
		assert(w*h <= 64);
		return allSquaresMask;
	}

	public void flipHorizontally()
	{
		for (int y=0; y<h; y++)
		{
			for (int i=0; i<w/2; i++)
			{
				int x1 = i;
				int x2 = w-1-i;
				T p1 = get(x1,y);
				T p2 = get(x2,y);
				int c1 = getColor(x1,y);
				int c2 = getColor(x2,y);
				set(x2,y,p1,c1);
				set(x1,y,p2,c2);
			}
		}
	}

	public long getUnoccupiedForRow(int row)
	{
		assert(w <= 64);
		return getOccupiedForRow(row) ^ ((1L<<w)-1);
	}

	public long getOccupiedForRow(int row)
	{
		long occ = 0;
		for (int i=0; i<numColors; i++)
			occ |= getColorOccupiedForRow(i, row);
		return occ;
	}

	public long getUnoccupiedForColumn(int row)
	{
		assert(w <= 64);
		return getOccupiedForColumn(row) ^ ((1L<<w)-1);
	}

	public long getOccupiedForColumn(int col)
	{
		long occ = 0;
		for (int i=0; i<numColors; i++)
			occ |= getColorOccupiedForColumn(i, col);
		return occ;
	}

	public long getColorOccupiedForRow(int player, int row)
	{
		return occupied[player].getRange64(row*w, row*w+w);
	}

	public long getColorOccupiedForColumn(int player, int col)
	{
		long occ = 0;
		for (int y=0; y<h; y++)
			if (occupied[player].get(xy2i(col, y)))
				occ |= (1L<<y);
		return occ;
	}

	public void copy(int srci, int desti)
	{
		// TODO: faster
		int c1 = getColor(srci);
		T p1 = get(srci);
		set(desti, p1, c1);
	}

	public void swap(int srci, int desti)
	{
		// TODO: faster
		int c1 = getColor(srci);
		int c2 = getColor(desti);
		T p1 = get(srci);
		T p2 = get(desti);
		set(desti, p1, c1);
		set(srci, p2, c2);
	}

}
