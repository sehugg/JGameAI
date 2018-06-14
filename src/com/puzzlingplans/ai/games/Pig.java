package com.puzzlingplans.ai.games;

import java.io.PrintStream;
import java.util.Arrays;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.UniformRandomChoice;

public class Pig extends GameState<Pig> implements HashedPosition
{
	private int[] turn_total;
	private int winningScore;
	
	//
	
	public Pig()
	{
		this(2, 100);
	}
	
	public Pig(int numPlayers, int winningScore)
	{
		super(numPlayers);
		this.winningScore = winningScore;
		this.turn_total = new int[getNumPlayers()];
	}

	@Override
	public Pig clone() throws CloneNotSupportedException
	{
		Pig copy = super.clone();
		copy.turn_total = Arrays.copyOf(turn_total, turn_total.length);
		return copy;
	}

	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		final int player = getCurrentPlayer();
		if (turn_total[player] > 0)
		{
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					switch (choice)
					{
						case 0: // hold
							addPlayerScore(player, turn_total[player]);
							turn_total[player] = 0;
							nextPlayer();
							return MoveResult.Ok;
						case 1: // roll
							return roll(decider);
						default:
							throw new IllegalStateException(choice+"");
					}
				}

				@Override
				public long getPotentialMoves()
				{
					return choiceMask(2);
				}
			});
		} else {
			return roll(decider);
		}
	}

	private MoveResult roll(Decider decider) throws MoveFailedException
	{
		final int player = getCurrentPlayer();
		return decider.choose(new UniformRandomChoice()
		{
			@Override
			public MoveResult choose(int choice) throws MoveFailedException
			{
				switch (choice)
				{
					case 0: // forfeit
						turn_total[player] = 0;
						nextPlayer();
						return MoveResult.Ok;
					default:
						turn_total[player] += choice + 1;
						if (getAbsoluteScore(player) + turn_total[player] >= winningScore)
						{
							win();
						}
						return MoveResult.Ok;
				}
			}

			@Override
			public long getPotentialMoves()
			{
				return choiceMask(6);
			}
		});
	}

	@Override
	public String playerToString(int player)
	{
		return super.playerToString(player) + "\t + " + turn_total[player];
	}

	@Override
	public void dump(PrintStream out)
	{
	}

	@Override
	public long hashFor(int seekingPlayer)
	{
		long n = 1;
		int prime = 389;
		int p = Math.max(winningScore*2 + 1, prime);
		for (int i=0; i<getNumPlayers(); i++)
		{
			n = (n * p) + getAbsoluteScore(i);
			n = (n * p) + turn_total[i];
		}
		return n * p;
	}

	@Override
	public void enableHashing(boolean enable)
	{
	}

}
