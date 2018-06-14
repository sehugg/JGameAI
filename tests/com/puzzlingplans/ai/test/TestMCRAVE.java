package com.puzzlingplans.ai.test;

import java.util.concurrent.ExecutionException;

import junit.textui.TestRunner;

import com.puzzlingplans.ai.GameOverException;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.RandomDecider;
import com.puzzlingplans.ai.ReplayDecider;
import com.puzzlingplans.ai.games.Dice;
import com.puzzlingplans.ai.games.FourUp;
import com.puzzlingplans.ai.games.Lattaque;
import com.puzzlingplans.ai.games.MatchThree;
import com.puzzlingplans.ai.games.Pig;
import com.puzzlingplans.ai.games.TicTacToe;
import com.puzzlingplans.ai.games.cards.Poker;
import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.games.go.Go;
import com.puzzlingplans.ai.games.go.Go7x7;
import com.puzzlingplans.ai.search.AIDecider;
import com.puzzlingplans.ai.search.MCRAVE;
import com.puzzlingplans.ai.search.MCRAVE.Sim;
import com.puzzlingplans.ai.util.HammingSpaceIndex;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class TestMCRAVE extends BaseTestCase
{
	boolean debug = false;
	
	private Line<?> doMCRAVE(GameState<?> game, int maxLevel, final int numIters, int tableSizeLog2, int expectedWinners)
			throws MoveFailedException
	{
		final MCRAVE mcrave = new MCRAVE(tableSizeLog2);
		return doMCRAVE(mcrave, game, maxLevel, numIters, expectedWinners);
	}

	private Line<?> doMCRAVE(final MCRAVE mcrave, GameState<?> game, int maxLevel, final int numIters,
			int expectedWinners) throws MoveFailedException
	{
		final Sim sim = mcrave.newSimulator(game, maxLevel, new RandomXorshift128(0));
		sim.debug = debug;
		//if (!debug) sim.prefixPathToDebug = ":43/3:2/3:51/30:3/4:"; // for testChess
		benchmark(game.getClass().getName(), new Benchmarkable()
		{
			@Override
			public int run()
			{
				try
				{
					sim.iterate(numIters);
					return numIters;
				} catch (MoveFailedException e)
				{
					throw new RuntimeException(e);
				}
			}
		});
		GameState<?> newstate = null;
		sim.exploration = false;
		sim.debug = true;
		sim.prefixPathToDebug = null;
		System.out.println(mcrave);
		//System.out.println(sim);
		System.out.println(sim.stats);
		Line<?> lastMove = null;
		for (int i=0; i<10; i++)
		{
			newstate = sim.iterate(1);
			newstate.dump();
			lastMove = sim.getLastMove();
			System.out.println("BEST = " + lastMove);
			if (expectedWinners >= 0)
			{
				assertTrue(newstate.isGameOver());
				assertEquals(expectedWinners, newstate.getWinners());
			}
		}
		return lastMove;
	}

	//
	
	public void testTicTacToe() throws MoveFailedException
	{
		TicTacToe state = new TicTacToe();
		doMCRAVE(state, 10, 3000, 16, 0);
	}

	public void testTicTacToe2() throws MoveFailedException
	{
		TicTacToe state = new TicTacToe();
		debug = true;
		doMCRAVE(state, 10, 100, 16, 0);
	}

	public void testTicTacToeExpandFirst() throws MoveFailedException
	{
		TicTacToe state = new TicTacToe();
		MCRAVE mcrave = new MCRAVE(16);
		mcrave.setRAVEBias(0.01f);
		mcrave.visitUnexpandedNodesFirst = true;
		doMCRAVE(mcrave, state, 10, 1000, 0);
	}

	public void testTicTacToeXWins() throws MoveFailedException
	{
		TicTacToe state = new TicTacToe();
		state.makeMove(0,0);
		state.makeMove(1,0);
		state.makeMove(0,1);
		state.makeMove(0,2);
		doMCRAVE(state, 10, 200, 8, 1);
	}

	public void testTicTacToeXLoses() throws MoveFailedException
	{
		TicTacToe state = new TicTacToe();
		state.makeMove(1,0);
		state.makeMove(0,0);
		state.makeMove(1,2);
		state.makeMove(0,1);
		state.makeMove(2,1);
		state.makeMove(1,1);
		doMCRAVE(state, 10, 10, 8, 2);
	}

	public void testFourUpEndGame() throws MoveFailedException
	{
		FourUp state = new FourUp();
		// http://www.pomakis.com/c4/expert_play.html
		state.makeMove(2);
		state.makeMove(1);
		state.makeMove(2);
		state.makeMove(3);
		state.makeMove(3);
		state.makeMove(3);
		state.makeMove(3);
		state.makeMove(5);
		state.dump();
		Line<?> best = doMCRAVE(state, state.getBoard().getNumCells()+1, 1000, 16, 1);
		//assertEquals(":2:2:1:0:0:", best.toString());
		assertEquals(":2:2:1:", best.toString().substring(0, 7));
		assertTrue(best.toString().endsWith(":0:"));
	}

	public void testDice() throws MoveFailedException
	{
		Dice state = new Dice();
		doMCRAVE(state, 30, 5000, 16, -1);
	}

	public void testPig() throws MoveFailedException
	{
		// TODO: handle graph cycles?
		Pig game = new Pig();
		doMCRAVE(game, 300, 10000, 16, -1);
	}

	public void testPoker() throws MoveFailedException
	{
		Poker state = new Poker(3);
		RandomDecider decider = new RandomDecider(0, 0);
		state.playTurn(decider);
		state.playTurn(decider);
		state.playTurn(decider);
		doMCRAVE(state, 100, 10000, 16, -1);
	}

	public void testChess() throws MoveFailedException
	{
		Chess game = getChessPosition("1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - bm Qd1+");
		//debug = true;
		// TODO: these params are really fiddly and Minimax takes < 1 sec to do this
		MCRAVE mcrave = new MCRAVE(20);
		mcrave.setRAVEBias(0.1f);
		mcrave.preferredExpandProb = 0x100;
		mcrave.findRepetitions = true;
		Line<?> best = doMCRAVE(mcrave, game, 12, 200000, 2);
		assertEquals(":43/3:2/3:51/30:3/4:59/3:", best.toString());
	}

	public void testChessPreferredMoves() throws MoveFailedException
	{
		Chess game = getChessPosition("3r1k2/4npp1/1ppr3p/p6P/P2PPPP1/1NR5/5K2/2R5 w - - bm d5");
		MCRAVE mcrave = new MCRAVE(20);
		mcrave.preferredMoveProb = 0x20;
		mcrave.preferredExpandProb = 0x100;
		mcrave.findRepetitions = true;
		Line<?> best = doMCRAVE(mcrave, game, 300, 10000, -1);
		verifyBestChessMove(game, best);
	}

	public void testCheckmate() throws MoveFailedException, InterruptedException, ExecutionException
	{
		Chess game = getChessPosition("R1k5/5Q2/5q2/7R/8/1p1K4/3B4/8 w - - -");
		Line<?> move1 = doMCRAVE(game, 100, 100, 20, 1);
		game.playTurn(new ReplayDecider(move1));
		Line<?> move2 = doMCRAVE(game, 100, 100, 20, 1);
		assertEquals(":", move2.toString());
		AIDecider solver = new MCRAVE(8, 50, 100).newSolver(game);
		try {
			Line<?> move3 = solver.solve();
			fail();
		} catch (GameOverException goe) {
			//
		}
	}

	public void testHashtableStress() throws MoveFailedException
	{
		Lattaque game = new Lattaque();
		game.initBoard(0);
		doMCRAVE(game, 100, 10000, 8, -1);
	}

	public void testGo() throws MoveFailedException
	{
		Go state = new Go(9, 2);
		doMCRAVE(state, state.getBoard().getNumCells() * 3, 5000, 16, -1);
	}

	public void testGoRAVE() throws MoveFailedException
	{
		Go state = new Go7x7(7, 2);
		doMCRAVE(state, state.getBoard().getNumCells() * 3, 100000, 20, -1);
	}

	// make sure each iteration results in at least a complete best move
	// because we have a well-behaved hash table
	public void testSolverTwoPlayers() throws MoveFailedException
	{
		Lattaque game = new Lattaque();
		game.initBoard(0, 3, true);
		final MCRAVE mcrave = new MCRAVE(12);
		mcrave.useMultipleThreads = false;
		int seeking = 1;
		while (!game.isGameOver())
		{
			final Sim sim = mcrave.newSimulator(game, 100, new RandomXorshift128(0));
			sim.setSeekingPlayer(seeking);
			sim.iterate(1000);
			Line<?> best = sim.getLastMove();
			System.out.println(mcrave);
			System.out.println(sim.stats);
			System.out.println(best);
			if (best.getLevel() == 0)
			{
				sim.debug = true;
				sim.iterate(1);
				fail();
			}
			game.playTurn(new ReplayDecider(best, seeking));
			game.dump();
			assertEquals(0, mcrave.getNodes().totalFailedInserts);
		}
	}

	public void testChessHammingIndex() throws MoveFailedException
	{
		Chess game = getChessPosition("1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - bm Qd1+");
		assertEquals(1, game.getCurrentPlayer());
		//debug = true;
		// TODO: these params are really fiddly and Minimax takes < 1 sec to do this
		MCRAVE mcrave = new MCRAVE(20);
		mcrave.goodMoves = new HammingSpaceIndex(7);
		mcrave.hammingRadius = 4;
		Line<?> best = doMCRAVE(mcrave, game, 20, 50000, -1);
		mcrave.goodMoves.dump();
		assertEquals(":43/3:2/3:51/30:3/4:59/3:", best.toString());
	}

	public void testMatch3() throws MoveFailedException
	{
		MatchThree game = new MatchThree(2, 7, 9, 6);
		game.setRandomSeed(0);
		game.fillBoard();
		doMCRAVE(game, 100, 50, 20, 1);
	}

	//

	public static void main(String[] args) throws MoveFailedException
	{
		TestRunner.run(TestMCRAVE.class);
	}
}
