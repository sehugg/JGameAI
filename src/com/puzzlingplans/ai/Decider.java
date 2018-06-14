package com.puzzlingplans.ai;

public interface Decider
{
	public MoveResult choose(Choice choice) throws MoveFailedException;
	
	public int getSeekingPlayer();
	
	// when there is no seeking player (i.e. real game is being simulated)
	public final static int RealLife = -1;
}
