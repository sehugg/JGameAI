package com.puzzlingplans.ai.search;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.MiscUtils;

public abstract class Simulator<T extends LevelInfo> implements Decider
{
	protected int maxLevel;
	protected int seekingPlayer;
	protected int numPlayers;
	protected int levelSlop = 10; // TODO
	protected int lookahead;
	protected GameState<?> initialState;

	protected int currentLevel;
	protected int currentPlayer;
	protected int iterCount;
	protected long currentTrail;
	protected GameState<?> currentState;
	protected int turnsPlayed;

	protected T[] linfo;

	public static final int NoPlayer = -1;
	public static final int NoAction = -1;

	public boolean debug;
	
	//
	
	@SuppressWarnings("unchecked")
	public Simulator(GameState<?> initialState, int maxLevel, int lookahead)
	{
		this.lookahead = lookahead;
		this.maxLevel = maxLevel + lookahead;
		this.initialState = initialState;
		this.seekingPlayer = initialState.getCurrentPlayer();
		this.numPlayers = initialState.getNumPlayers();
		this.linfo = MiscUtils.newArray(newLevelInfo().getClass(), maxLevel + levelSlop);
		for (int i=0; i<linfo.length; i++)
		{
			linfo[i] = (T) newLevelInfo();
			linfo[i].reset();
		}
	}

	protected LevelInfo newLevelInfo()
	{
		return new LevelInfo();
	}

	public void store(Choice choice, int level, int action, long trail)
	{
		if (debug)
			System.out.println("store() " + level + " P" + currentPlayer + " #" + action + " >" + Long.toHexString(trail));
		LevelInfo lrec = linfo[level];
		lrec.choice = choice;
		lrec.player = currentPlayer;
		lrec.action = action;
		lrec.trail = trail;
	}

	public void reset()
	{
		currentLevel = lookahead;
		currentTrail = 1;
		turnsPlayed = 0;
		for (int i=0; i<currentLevel; i++)
			linfo[i].reset();
	}

	public GameState<?> simulate() throws MoveFailedException
	{
		GameState<?> state = initialState.copy();
		this.currentState = state;
		iterCount++;
		HashedPosition hashable = state instanceof HashedPosition ? (HashedPosition)state : null;
		if (hashable != null)
			hashable.enableHashing(true);
		reset();
		while (!state.isGameOver() && currentLevel < maxLevel)
		{
			if (debug)
				System.out.println("simulate() level " + currentLevel + "/" + maxLevel);
			this.currentPlayer = state.getCurrentPlayer();
			// if game supports hashes, trail == hash at start of turn 
			if (hashable != null)
				this.currentTrail = hashable.hashFor(seekingPlayer) + (currentPlayer*67); // TODO?
			MoveResult result = state.playTurn(this);
			if (debug)
				state.dump();
			if (result == MoveResult.Canceled)
				return null;
			if (result != MoveResult.Ok)
				throw new MoveFailedException("Expected Ok, got " + result);
			turnsPlayed++;
		}
		// set end-of-turn marker
		currentPlayer = NoPlayer;
		currentState = null;
		store(null, currentLevel, NoAction, 0);
		return state;
	}

	protected MoveResult tryChoice(Choice choice, int action) throws MoveFailedException
	{
		int level = currentLevel;
		currentLevel++;
		// TODO: handle overflow of trail parameter?
		long oldTrail = currentTrail;
		currentTrail = updateTrail(currentTrail, action);
		if (debug)
			System.out.println("choose(): level " + level + " #" + action + " trail = " + Long.toHexString(currentTrail));

		MoveResult result = choice.choose(action);
		if (result == MoveResult.Ok)
		{
			store(choice, level, action, oldTrail);
			return result;
		}
		
		if (debug)
			System.out.println("choose(): level " + level + " invalid action #" + action);
		currentLevel--;
		currentTrail = oldTrail;
		return MoveResult.NoMoves;
	}
	
	protected long updateTrail(long trail, int action)
	{
		assert(action >= 0 && action < 64);
		return BitUtils.mix64(trail + action * 25165843L);
	}

	@Override
	public int getSeekingPlayer()
	{
		return seekingPlayer;
	}

	public void dump()
	{
		for (int i=lookahead; i<currentLevel; i++)
		{
			System.out.println(i + "\t" + linfo[i]);
		}
	}
	
	public static final String hex(long x)
	{
		return Long.toHexString(x);
	}

	public boolean isGameOver(GameState<?> state)
	{
		// TODO?
		return currentLevel == lookahead && state.isGameOver();
	}
}
