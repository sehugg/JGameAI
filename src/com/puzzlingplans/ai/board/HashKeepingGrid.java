package com.puzzlingplans.ai.board;

import java.util.Arrays;

import com.puzzlingplans.ai.util.ZobristTable;

public abstract class HashKeepingGrid<T> extends OccupiedGrid<T>
{
	private ZobristTable zobrist;
	private short[] cellHashIndices;
	private int numPieceTypes;
	private int hashesPerCell;
	private long hash;
	private boolean maintainHash = true;
	
	//
	
	public HashKeepingGrid(int width, int height, T defaultValue, int numColors, int numPieceTypes, long seed)
	{
		super(width, height, defaultValue, numColors);
		
		this.numPieceTypes = numPieceTypes;
		this.hashesPerCell = (numColors+1) * numPieceTypes;
		int numCells = getNumCells();
		this.zobrist = new ZobristTable(numCells * hashesPerCell, seed);
		this.hash = 1; // starting hash value (all cells == default) 
		if (zobrist.size() <= 0x8000)
		{
			this.cellHashIndices = new short[numCells];
			for (int i=0; i<cellHashIndices.length; i++)
				cellHashIndices[i] = (short) getHashIndex(i, getPieceTypeIndex(defaultValue), -1);
		}
	}

	public abstract int getPieceTypeIndex(T t);

	public HashKeepingGrid<T> clone() throws CloneNotSupportedException
	{
		HashKeepingGrid<T> copy = (HashKeepingGrid<T>) super.clone();
		if (cellHashIndices != null)
			copy.cellHashIndices = Arrays.copyOf(cellHashIndices, cellHashIndices.length);
		return copy;
	}

	@Override
	public void set(int i, T val, int color)
	{
		if (maintainHash)
		{
			int hashIndex = getHashIndex(i, getPieceTypeIndex(val), color);
			if (cellHashIndices != null)
			{
				hash ^= zobrist.get(cellHashIndices[i]);
				super.set(i, val, color);
				cellHashIndices[i] = (short) hashIndex;
			} else {
				hash ^= zobrist.get(getHashIndex(i, getPieceTypeIndex(get(i)), getColor(i)));
				super.set(i, val, color);
			}
			hash ^= zobrist.get(hashIndex);
		} else {
			super.set(i, val, color);
		}
	}

	public int getHashIndex(int cell, int piece, int color)
	{
		assert(color < numColors && color >= -1);
		assert(piece < numPieceTypes && piece >= 0);
		// TODO: is locality better if we rearrange?
		return hashesPerCell * cell + (color + 1) * numPieceTypes + piece;
	}

	public long hash()
	{
		assert(maintainHash);
		return hash;
	}

	public void enableHashing(boolean enable)
	{
		this.maintainHash = enable;
	}
}
