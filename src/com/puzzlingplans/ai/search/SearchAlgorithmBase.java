package com.puzzlingplans.ai.search;

public abstract class SearchAlgorithmBase implements AISolver
{
	protected int numIters;
	protected int maxLevel;

	public int getNumIters()
	{
		return numIters;
	}
	public void setNumIters(int numIters)
	{
		this.numIters = numIters;
	}
	public int getMaxLevel()
	{
		return maxLevel;
	}
	public void setMaxLevel(int maxLevel)
	{
		this.maxLevel = maxLevel;
	}
}
