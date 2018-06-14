package com.puzzlingplans.ai;

import com.puzzlingplans.ai.util.BitUtils;


public abstract class UniformHiddenChoice extends HiddenChoice
{
	@Override
	public float getProbability(int choice)
	{
		return 1f / BitUtils.countBits(getPotentialMoves());
	}
}
