package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.games.cards.Freecell;
import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.search.MTDf;
import com.puzzlingplans.ai.search.Minimax;

public class TestMTDf extends BaseTestCase
{
	public void testFreecellMTDf()
	{
		Freecell state = new Freecell(0, 12);
		state.dump();
		Minimax mmax = new Minimax(state);
		mmax.setFailSoft(true);
		mmax.setTranspositionTableSize(24);
		//mmax.setDepthPenalty(1);
		//mmax.setDebug(true);
		mmax.setPrintStatsAtLevel(2);

		MTDf mtdf = new MTDf(mmax, 0, 4, 2, 42);
		while (mtdf.iterate())
		{
			System.out.println(mtdf);
		}
		int score = mtdf.getScore();
		assertEquals(GameState.WIN, score);
	}

	public void testChessMTDf()
	{
		//Chess state = getChessPosition("1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - bm Qd1+");
		//Chess state = getChessPosition("rn1qkb1r/pp2pppp/5n2/3p1b2/3P4/2N1P3/PP3PPP/R1BQKBNR w KQkq - bm Qb3");
		//http://members.aon.at/computerschach/quick/quicke.htm
		Chess state = getChessPosition("3k4/p7/K3BP2/8/7p/8/2P4P/8 w - - bm Kb7");
		//state.dump();
		Minimax mmax = new Minimax(state);
		mmax.setFailSoft(true); // required
		mmax.setTranspositionTableSize(24);
		//mmax.setDepthPenalty(1);
		//mmax.setDebug(true);
		mmax.setPrintStatsAtLevel(2);

		// TODO: why is convergence susceptible to starting level and increment?
		MTDf mtdf = new MTDf(mmax, 0, Chess.LevelsPerTurn*8, Chess.LevelsPerTurn*2, Chess.LevelsPerTurn*20);
		while (mtdf.iterate())
		{
			System.out.println(mtdf);
			System.out.println(mmax.getTranspositionTable());
		}
		int score = mtdf.getScore();
		System.out.println(mmax.getPrincipalVariation());
		assertEquals(GameState.WIN, score);
	}
}
