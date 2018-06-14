package com.puzzlingplans.ai.search;

import com.puzzlingplans.ai.GameState;

public interface AISolver
{
	public AIDecider newSolver(GameState<?> initialState);

	public Thread[] getThreads();

	public void reset();

	public void resetStats();
}
