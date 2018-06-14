package com.puzzlingplans.ai.search;

import com.puzzlingplans.ai.GameState;

// http://people.csail.mit.edu/plaat/mtdf.html
// http://en.wikipedia.org/wiki/MTD-f
// http://chessprogramming.wikispaces.com/MTD(f)
// http://www.stmintz.com/ccc/index.php?id=359886
// http://www.gamedev.net/topic/503234-transposition-table-question/

public class MTDf
{
	Minimax mmax;
	int depth;
	int depthInc;
	int score;
	int maxdepth;
	int upper = GameState.SCORE_MAX;
	int lower = GameState.SCORE_MIN;
	
	//
	
	public MTDf(Minimax mmax, int initialGuess, int initialLevel, int incLevel, int maxLevel)
	{
		this.mmax = mmax;
		this.score = initialGuess;
		this.depth = initialLevel;
		this.depthInc = incLevel;
		this.maxdepth = maxLevel;
	}

	public boolean isDone()
	{
		if (lower >= upper)
			return true;
		//if (Math.abs(score) > GameState.WIN - GameState.WINLOSE_THRESHOLD)
			//return true;
		if (depth >= maxdepth)
			return true;
		
		return false;
	}
	
	public boolean iterate()
	{
		if (isDone())
			return false;
		
		int beta;
		if (score == lower)
			beta = score+1;
		else
			beta = score;
		
		mmax.setMaxLevel(depth);
		score = mmax.solveWithAlphaBeta(beta-1, beta);
		
		if (score < beta)
			upper = score;
		else
			lower = score;

		depth += depthInc;
		return true;
	}

	public int getScore()
	{
		return score;
	}

	@Override
	public String toString()
	{
		return "score=" + score + " range " + lower + "/" + upper + " depth=" + depth;
	}
}
