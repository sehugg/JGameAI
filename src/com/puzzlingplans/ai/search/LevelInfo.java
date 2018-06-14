package com.puzzlingplans.ai.search;

import com.puzzlingplans.ai.Choice;

public class LevelInfo
{
	int action;
	int player;
	long trail;
	Choice choice;
	
	public void reset()
	{
		choice = null;
		trail = 0;
		action = 0;
		player = Simulator.NoPlayer;
	}
	
	@Override
	public String toString()
	{
		return "#" + action + "\tP" + player + "\tH" + Long.toHexString(trail);
	}
}