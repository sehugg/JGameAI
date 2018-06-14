package com.puzzlingplans.ai.test;

import junit.textui.TestRunner;

import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.games.chess.Chess.Statistics;
import com.puzzlingplans.ai.search.Minimax;

public class TestChess extends BaseTestCase
{
	private boolean debug = false;

	// http://www.talkchess.com/forum/viewtopic.php?t=47318
	
	public void testIllegalEnPassant()
	{
		countLeaves("8/5bk1/8/2Pp4/8/1K6/8/8 w - d6", 6, 824064);
	}
	
	public void testEnPassantCaptureChecksOpponent()
	{
		countLeaves("8/8/1k6/2b5/2pP4/8/5K2/8 b - d3", 6, 1440467);
	}

	public void testShortCastlingGivesCheck()
	{
		countLeaves("5k2/8/8/8/8/8/8/4K2R w K - 0", 6, 661072);
	}

	public void testLongCastlingGivesCheck()
	{
		countLeaves("3k4/8/8/8/8/8/8/R3K3 w Q - 0", 6, 803711);
	}

	public void testCastlingRookCapture()
	{
		countLeaves("r3k2r/1b4bq/8/8/8/8/7B/R3K2R w KQkq - 0", 4, 1274206);
	}

	public void testCastlingPrevented()
	{
		countLeaves("r3k2r/8/3Q4/8/8/5q2/8/R3K2R b KQkq - 0", 4, 1720476);
	}
	
	public void testPromoteOutOfCheck()
	{
		// something's wrong with the king's initial moves here
		countLeaves("3K4/8/8/8/8/8/4p3/2k2R2 b - - 0 1", 6, 3821001);
		countLeaves("2K2r2/4P3/8/8/8/8/8/3k4 w - - 0 1", 6, 3821001);
	}

	public void testDiscoveredCheck()
	{
		countLeaves("8/8/1P2K3/8/2n5/1q6/8/5k2 b - - 0", 5, 1004658);
	}

	public void testPromoteToGiveCheck()
	{
		countLeaves("4k3/1P6/8/8/8/8/K7/8 w - - 0", 6, 217342);
	}

	public void testUnderpromoteToGiveCheck()
	{
		countLeaves("8/P1k5/K7/8/8/8/8/8 w - - 0", 6, 92683);
	}
	
	public void testSelfStalemate()
	{
		countLeaves("K1k5/8/P7/8/8/8/8/8 w - - 0", 6, 2217);
	}

	public void testStalemateCheckmate()
	{
		countLeaves("8/k1P5/8/1K6/8/8/8/8 w - - 0", 7, 567584);
	}
	
	public void testDoubleCheck()
	{
		countLeaves("8/8/2k5/5q2/5n2/8/5K2/8 b - - 0", 4, 23527);
	}

	private void countLeaves(String position, int level, int nleaves)
	{
		Chess state = getChessPosition(position);
		final Minimax mmax = perft(state, level, getName());
		assertEquals(nleaves, mmax.numLeavesVisited() - state.stats.numCheckmates - state.stats.numStalemates);
		/*
		state.flipHorizontally();
		state.resetStats();
		final Minimax mmax2 = perft(state, level, getName());
		assertEquals(nleaves, mmax2.numLeavesVisited());
		*/
	}
	
	public void testPerftSmall()
	{
		Chess state = new Chess();
		state.initDefaultBoard();
		final Minimax mmax = perft(state, 3, "initial");
		assertEquals(12, state.stats.numChecks);
		assertEquals(34, state.stats.numCaptures);
		assertEquals(0 /*8*/, state.stats.numCheckmates);
		assertEquals(0, state.stats.numPromotions);
		assertEquals(0, state.stats.numCastles);
		assertEquals(0, state.stats.numEnpassants);
		assertEquals(8902, mmax.numLeavesVisited());
	}
	
	public void testPerft5()
	{
		Chess state = new Chess();
		state.initDefaultBoard();
		final Minimax mmax = perft(state, 5, "initial");
		// https://chessprogramming.wikispaces.com/Perft+Results
		assertEquals(12+469+27351, state.stats.numChecks);
		assertEquals(34+1576+82719, state.stats.numCaptures);
		assertEquals(8 /*347*/, state.stats.numCheckmates);
		assertEquals(0, state.stats.numPromotions);
		assertEquals(0, state.stats.numCastles);
		assertEquals(258, state.stats.numEnpassants);
		//assertEquals(15483, mmax.numMovesFailed());
		assertEquals(8+4865609, mmax.numLeavesVisited());
	}

	public void testPerftKiwipete()
	{
		Chess state = getChessPosition("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq -");
		final Minimax mmax = perft(state, 3, "kiwipete");
		assertEquals(3+993, state.stats.numChecks);
		assertEquals(8+351+17102, state.stats.numCaptures);
		assertEquals(0 /*1*/, state.stats.numCheckmates);
		assertEquals(0, state.stats.numPromotions);
		assertEquals(2+91+3162, state.stats.numCastles);
		assertEquals(1+45, state.stats.numEnpassants);
		assertEquals(97862, mmax.numLeavesVisited());
	}
	
	public void testPerftPos3()
	{
		Chess state = getChessPosition("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -");
		final Minimax mmax = perft(state, 5, "position3");
		assertEquals(2+10+267+1680+52950, state.stats.numChecks);
		assertEquals(1+14+209+3348+52051, state.stats.numCaptures);
		assertEquals(17, state.stats.numCheckmates);
		assertEquals(0, state.stats.numPromotions);
		assertEquals(0, state.stats.numCastles);
		assertEquals(2+123+1165, state.stats.numEnpassants);
		assertEquals(674624, mmax.numLeavesVisited());
	}
	
	public void testPerftMirror()
	{
		{
			Chess state = getChessPosition("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1");
			final Minimax mmax = perft(state, 3, "position4a");
			//assertMirror(state, mmax);
		}
		{
			Chess state = getChessPosition("r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1");
			final Minimax mmax = perft(state, 3, "position4b");
			//assertMirror(state, mmax);
		}
	}

	private void assertMirror(Chess state, final Minimax mmax)
	{
		assertEquals(10+38, state.stats.numChecks);
		assertEquals(87+1021, state.stats.numCaptures);
		assertEquals(0 /*22*/, state.stats.numCheckmates);
		assertEquals(48+120, state.stats.numPromotions);
		assertEquals(6, state.stats.numCastles);
		assertEquals(4, state.stats.numEnpassants);
		assertEquals(6+264+9467, mmax.numLeavesVisited());
	}

	private Minimax perft(Chess state, int perftLevel, String perftName)
	{
		final Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(perftLevel*Chess.LevelsPerTurn);
		mmax.setPruning(false);
		mmax.setDebug(debug );
		mmax.setPrintStatsAtLevel(3);
		//mmax.duplicateDebug = true; // TODO
		benchmark(perftName + "(" + perftLevel + ")", new Benchmarkable()
		{
			@Override
			public int run()
			{
				mmax.solve();
				return mmax.numLeavesVisited();
			}
		});
		System.out.println(mmax);
		System.out.println(state.stats);
		return mmax;
	}
	
	public void testEPDParser()
	{
		{
			Chess state = getChessPosition("r1b2rk1/ppbn1ppp/4p3/1QP4q/3P4/N4N2/5PPP/R1B2RK1 b - - bm c6; id \"\"");
			assertEquals(1, state.getCurrentPlayer());
			assertEquals(-8042226549443693128L, state.hashFor(0));
		}
		{
			Chess state = getChessPosition("1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - bm Qd1+");
			assertEquals(1, state.getCurrentPlayer());
			assertEquals("Qd1+", state.getMoveString(43, 3));
		}
		// TODO?
	}

	public void testFlipHoriz()
	{
		//Chess state = getPosition("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - -");
		//Chess state = getPosition("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w - - 0 1");
		Chess state = getChessPosition("13k21/8/8/8/8/8/1p11211/1211RK1 w - - 0 1");
		Minimax mmax1 = perft(state, 4, "fliph1");
		Statistics stats1 = state.stats;
		state.flipHorizontally();
		state.dump();
		state.resetStats();
		Minimax mmax2 = perft(state, 4, "fliph2");
		Statistics stats2 = state.stats;
		compareStats(stats1, stats2);
	}

	private void compareStats(Statistics stats1, Statistics stats2)
	{
		assertEquals(stats1.numCaptures, stats2.numCaptures);
		assertEquals(stats1.numCastles, stats2.numCastles);
		assertEquals(stats1.numCheckmates, stats2.numCheckmates);
		assertEquals(stats1.numChecks, stats2.numChecks);
		assertEquals(stats1.numEnpassants, stats2.numEnpassants);
		assertEquals(stats1.numPromotions, stats2.numPromotions);
	}

	public static void main(String[] args)
	{
		TestRunner.run(TestChess.class);
	}
}
