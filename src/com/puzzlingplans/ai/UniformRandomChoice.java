package com.puzzlingplans.ai;

import com.puzzlingplans.ai.util.BitUtils;


public abstract class UniformRandomChoice extends RandomChoice
{
	@Override
	public float getProbability(int choice)
	{
		return 1f / BitUtils.countBits(getPotentialMoves());
	}
}
