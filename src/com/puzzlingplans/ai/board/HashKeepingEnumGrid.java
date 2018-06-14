package com.puzzlingplans.ai.board;


public class HashKeepingEnumGrid<T extends Enum<T>> extends HashKeepingGrid<T>
{
	public HashKeepingEnumGrid(int width, int height, T defaultValue, int numColors, long seed)
	{
		super(width, height, defaultValue, numColors, defaultValue.getClass().getEnumConstants().length, seed);
	}

	@Override
	public HashKeepingEnumGrid<T> clone() throws CloneNotSupportedException
	{
		return (HashKeepingEnumGrid<T>) super.clone();
	}

	@Override
	public int getPieceTypeIndex(T t)
	{
		return t.ordinal();
	}
}
