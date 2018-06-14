package com.puzzlingplans.ai;

import com.puzzlingplans.ai.util.BitUtils;


public abstract class RandomChoice extends Choice
{
	public abstract float getProbability(int choice);

	public int chooseActionWithProbability(long mask, float val)
	{
		float prob = 0;
		for (int i = BitUtils.nextBit(mask, 0); i >= 0; i = BitUtils.nextBit(mask, i + 1))
		{
			float pd = getProbability(i);
			float p2 = prob + pd;
			if (val > prob && val < p2)
			{
				return i;
			}
			prob = p2;
		}
		// TODO: make sure this doesn't happen often, but might because of roundoff
		assert(prob > 1 - 1e-5f);
		return BitUtils.highSetBit(mask);
	}
	
	@Override
	public boolean isRandom()
	{
		return true;
	}
}
