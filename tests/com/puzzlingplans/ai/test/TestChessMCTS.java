package com.puzzlingplans.ai.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import junit.framework.ComparisonFailure;

import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.games.chess.EPDParser;
import com.puzzlingplans.ai.search.MCTS;
import com.puzzlingplans.ai.search.MCTS.Node;

public class TestChessMCTS extends BaseTestCase
{
	// http://en.wikipedia.org/wiki/Nolot
	
	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		//multiThreaded = false;
	}

	public void testRepeatableResult()
	{
		// TODO: should be repeatable, that's the point
		boolean oldmt = multiThreaded;
		multiThreaded = false;
		try {
			final Chess state = getChessPosition("8/2k5/4p3/1nb2p2/2K5/8/6B1/8 w - - bm Kxb5");
			Node node1 = simulate(state, 60000, 100, false, 75, 15, 0).getBestPath();
			Node node2 = simulate(state, 60000, 100, false, 75, 0, 1).getBestPath();
			assertEquals(node1.getMoveAtDepth(2).toString(), node2.getMoveAtDepth(2).toString());
		} finally {
			multiThreaded = oldmt;
		}
	}

	public void testWAC2() throws CloneNotSupportedException
	{
		String position = "5rk1/1ppb3p/p1pb4/6q1/3P1p1r/2P1R2P/PP1BQ1P1/5RKN w - - bm Rg3";
		// TODO: too many iters weakens it (or maybe we're lucky)
		mctsTest(position, 300000, 50*Chess.LevelsPerTurn);
	}

	public void testEndgame()
	{
		mctsTest("8/2k5/4p3/1nb2p2/2K5/8/6B1/8 w - - bm Kxb5", 300000, 100*Chess.LevelsPerTurn);
	}

	public void testBK1()
	{
		mctsTest("1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - bm Qd1+", 300000, 100*Chess.LevelsPerTurn);
	}

	public void testBK2()
	{
		// https://groups.google.com/forum/#!topic/rec.games.chess.computer/BzPq8eZdFhA
		mctsTest("3r1k2/4npp1/1ppr3p/p6P/P2PPPP1/1NR5/5K2/2R5 w - - bm d5", 300000, 100*Chess.LevelsPerTurn);
	}

	public void testCCR1()
	{
		mctsTest("rn1qkb1r/pp2pppp/5n2/3p1b2/3P4/2N1P3/PP3PPP/R1BQKBNR w KQkq - bm Qb3", 300000, 100*Chess.LevelsPerTurn);
	}

	private void mctsTest(String position, int iters, int depth)
	{
		Chess state = getChessPosition(position);
		assert(state.epdBestMove != null);
		MCTS mcts = new MCTS(depth);
		//mcts.setEarlyWinBonus(2);
		mcts.setGoodMoveProbability(75);
		mcts.setEarlyExitScores(2500, -2500);
		//mcts.setExplorationConstant(4000.0 * 1.41 / GameState.WIN); // combined value of all pieces == 4000
		MCTS.Node best = simulate(state, mcts, iters).getBestPath();
		for (MCTS.Node node : mcts.getRoot().getAllChildren())
		{
			int src = node.getMoveIndex();
			int dest = node.getMostRobustNode().getMoveIndex();
			System.out.println(state.getMoveString(src, dest) + "\t: " + node.toDesc());
		}
		verifyBestChessMove(state, best);
	}
	
	public void doEPDTestFile(String filename) throws IOException
	{
		int iters = 20000;
		int depth = 3*4*3;
		Chess state = new Chess();
		@SuppressWarnings("resource")
		BufferedReader r = new BufferedReader(new FileReader(filename));
		String line;
		while ((line = r.readLine()) != null)
		{
			EPDParser epd = new EPDParser(state);
			epd.parse(line);
			try {
				mctsTest(line, iters, depth);
			} catch (ComparisonFailure cfe) {
				System.err.println(cfe);
			}
		}
	}
	
	public void xxxtestEPDFiles() throws IOException
	{
		// TODO: main()?
		doEPDTestFile("/Users/voxi/funtime/GameAnalysis/bktest.epd");
	}

}
