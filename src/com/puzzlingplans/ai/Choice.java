package com.puzzlingplans.ai;

import com.puzzlingplans.ai.util.BitUtils;

public abstract class Choice
{
	/**
	 * Get bitmask of potential actions (0-63) to take at this Choice point.
	 * If zero, no moves are valid.
	 */
	public abstract long getPotentialMoves();
	
	/**
	 * Called after action is chosen.
	 */
	public abstract MoveResult choose(int action) throws MoveFailedException;

	/**
	 * Optional "preferred" actions to try first.
	 * Actions not in getPotentialMoves() will not be considered.
	 */
	public long getPreferredMoves()
	{
		return 0;
	}
	
	/**
	 * Total number of actions possible since this turn began.
	 * Defaults to high bit of getPotentialMoves().
	 * Can be overridden if a turn requires more than one choice.
	 * TODO: not implemented yet
	 */
	public int getNumTurnActions()
	{
		return BitUtils.highSetBit(getPotentialMoves()) + 1;
	}
	
	/**
	 * Converts a choice action into a turn action.
	 * Can be overridden if a turn requires more than one choice.
	 * TODO: not implemented yet
	 */
	public int getTurnAction(int action)
	{
		return action;
	}

	/**
	 * @return Unique key for this Choice class.
	 */
	public int key()
	{
		//return getClass().getName().hashCode();
		return System.identityHashCode(getClass()); // TODO: not repeatable in VM
	}

	/**
	 * @return true if this one is a random choice
	 */
	public boolean isRandom()
	{
		return false;
	}
}
