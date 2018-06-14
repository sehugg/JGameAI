package com.puzzlingplans.ai;

import java.util.Random;

import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.RandomXorshift128;

// TODO: remove
public class RandomDecider implements Decider
{
	private int seekingPlayer;
	private Random rnd;
	
	public RandomDecider()
	{
		this(Decider.RealLife);
	}

	public RandomDecider(int seekingPlayer, Random rnd)
	{
		this.seekingPlayer = seekingPlayer;
		this.rnd = rnd;
	}

	public RandomDecider(int seekingPlayer, long seed)
	{
		this(seekingPlayer, new RandomXorshift128(seed));
	}

	public RandomDecider(int seekingPlayer)
	{
		this(seekingPlayer, new RandomXorshift128());
	}

	@Override
	public MoveResult choose(Choice choice) throws MoveFailedException
	{
		// TODO: hidden choice?
		long mask = choice.getPotentialMoves();
		float prob = 0;
		while (mask != 0)
		{
			int i = BitUtils.choose_bit(mask, rnd);
			// if choice has probability < 1, do another random test
			if (choice instanceof RandomChoice)
			{
				prob += ((RandomChoice)choice).getProbability(i);
				if (rnd.nextFloat() > prob)
					continue;
			}
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
