package com.puzzlingplans.ai;

public class GameOverException extends MoveFailedException
{
	private GameState<?> finalstate;

	public GameOverException(GameState<?> finalstate)
	{
		this.finalstate = finalstate;
	}

	public GameState<?> getFinalState()
	{
		return finalstate;
	}
}
