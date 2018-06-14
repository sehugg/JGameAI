package com.puzzlingplans.ai;

public class MoveFailedException extends Exception
{
	private int player;

	public MoveFailedException()
	{
	}

	public MoveFailedException(String string)
	{
		super(string);
	}

	public MoveFailedException(int player, String string)
	{
		super(string);
		this.player = player;
	}
	
	public int getPlayer()
	{
		return player;
	}

	@Override
	public String toString()
	{
		return super.toString() + " (Player " + player + ")";
	}
}
