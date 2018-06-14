package com.puzzlingplans.ai;

import com.puzzlingplans.ai.util.BitUtils;

// TODO: remove
public class InOrderDecider implements Decider
{
	private int seekingPlayer;
	
	public InOrderDecider()
	{
		this(Decider.RealLife);
	}

	public InOrderDecider(int seekingPlayer)
	{
		this.seekingPlayer = seekingPlayer;
	}

	@Override
	public MoveResult choose(Choice choice) throws MoveFailedException
	{
		// TODO: hidden choice?
		long mask = choice.getPotentialMoves();
		int i = -1;
		while (mask != 0)
		{
			i = BitUtils.nextBit(mask, i+1);
			assert(i >= 0);
			if (tryChoice(choice, i) == MoveResult.Ok)
				return MoveResult.Ok;

			mask &= ~(1L<<i);
		}
		return MoveResult.NoMoves;
	}

	protected MoveResult tryChoice(Choice choice, int action) throws MoveFailedException
	{
		return choice.choose(action);
	}

	@Override
	public int getSeekingPlayer()
	{
		return seekingPlayer;
	}

}
