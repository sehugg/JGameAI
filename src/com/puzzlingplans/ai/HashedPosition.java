package com.puzzlingplans.ai;

public interface HashedPosition
{
	public long hashFor(int seekingPlayer);
	
	public void enableHashing(boolean enable);
}
