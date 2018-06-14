package com.puzzlingplans.ai;

// TODO: remove
public class ReplayDecider implements Decider
{
	private int seekingPlayer;
	private int[] move;
	private int current;
	
	//

	public ReplayDecider(Line<?> end)
	{
		this(end, RealLife);
	}

	public ReplayDecider(Line<?> end, int seekingPlayer)
	{
		this.seekingPlayer = seekingPlayer;
		this.move = end.getIndices();
	}

	@Override
	public MoveResult choose(Choice choice) throws MoveFailedException
	{
		int action;
		if (choice instanceof HiddenChoice && seekingPlayer == RealLife)
		{
			action = ((HiddenChoice)choice).getActualOutcome();
		} else {
			action = move[current++];
		}
		MoveResult result = choice.choose(action);
		assert (result == MoveResult.Ok);
		return result;
	}

	@Override
	public int getSeekingPlayer()
	{
		return seekingPlayer;
	}

	public boolean hasNext()
	{
		return current < move.length;
	}

}
