package com.puzzlingplans.ai.test;

import java.util.Random;
import java.util.concurrent.ExecutionException;

import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.games.Cluedo;
import com.puzzlingplans.ai.games.Dice;
import com.puzzlingplans.ai.games.FourUp;
import com.puzzlingplans.ai.games.Lattaque;
import com.puzzlingplans.ai.games.Pig;
import com.puzzlingplans.ai.games.TicTacToe;
import com.puzzlingplans.ai.games.MNKGame.Piece;
import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.games.go.Go;
import com.puzzlingplans.ai.search.MCTS;
import com.puzzlingplans.ai.search.MCTS.Node;
import com.puzzlingplans.ai.search.MCTS.Option;
import com.puzzlingplans.ai.util.MiscUtils;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class TestMCTS extends BaseTestCase
{
	private boolean debug = true;
	
	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		multiThreaded = false;
	}

	public void testSimpleRandomScoringGame()
	{
		// TODO: MCTS doesn't extend turns past max level like minimax
		doRandomScoringTest(4, 14);
		doRandomScoringTest(8, 18);
		doRandomScoringTest(16, 14);
	}

	private void doRandomScoringTest(int n, int s)
	{
		MCTS mcts = countLeaves(new SampleGames.BinaryRandomScoringGame(), (n), (1<<(n)));
		assertEquals(s, Math.round(mcts.getRoot().getAvgScore()));
	}

	public void testSimple50PercentWinGame()
	{
		int l = 8;
		int s = 0;
		MCTS mcts = countLeaves(new SampleGames.Binary50PercentWinGame(l), l, (1<<l));
		assertEquals(s, Math.round(mcts.getRoot().getAvgScore() * 100 / GameState.WIN));
	}

	public void testSimpleBinaryGame()
	{
		countLeaves(new SampleGames.BinaryGame(), 4, 2*2*2*2);
	}

	public void testSimpleNonBranchingGame()
	{
		countLeaves(new SampleGames.NonBranchingGame(), 10, 1);
	}

	public void testSimpleTwoLevelInvalidMoves()
	{
		countLeaves(new SampleGames.TwoLevelInvalidMoves(), 4, 3*2);
	}

	public void testSimpleOneValidMove()
	{
		countLeaves(new SampleGames.OneLevelOneValidMove(), 6, 1);
	}

	public void testSimpleTwoLevelOneValidMove()
	{
		countLeaves(new SampleGames.TwoLevelOneValidMove(), 6, 2*2*2);
	}

	private MCTS countLeaves(GameState<?> state, int l, int expected)
	{
		MCTS mcts = new MCTS(l);
		mcts.setDebug(debug);
		mcts.setOption(Option.OnlyRetainLeafScores, true);
		mcts.setOption(Option.PruneSubtrees, false);
		//mcts.setTranspositionTableSize(20);
		try
		{
			mcts.iterate(state, expected * 8);
			//mcts.iterateMultiThreaded(state, expected * 2, 60);
		} catch (InterruptedException e)
		{
			fail(e.toString());
		}
		System.out.println(mcts);
		System.out.println(mcts.getBestPath());
		System.out.println(mcts.getRoot().toDesc());
		mcts.dumpTree(System.out, 120);
		assertEquals(expected, mcts.numLeavesVisited());
		return mcts;
	}


	public void testMCTS1()
	{
		TicTacToe state = new TicTacToe();
		MCTS mcts = new MCTS(10);
		mcts.setDebug(true);
		MCTS.Simulator sim = mcts.newSimulator(state, 1);
		{
			TicTacToe state1 = (TicTacToe) sim.execute();
			state1.dump();
			assertTrue(state1.isGameOver());
		}
		state.dump();
		{
			TicTacToe state2 = (TicTacToe) sim.execute();
			state2.dump();
			assertTrue(state2.isGameOver());
		}
	}

	public void testTicTacToe()
	{
		final TicTacToe state = new TicTacToe();
		Node node = simulate(state, 200000, 10+1, false).getBestPath();
		assertEquals(":4:", node.getMoveAtDepth(1)+"");
		assertEquals(9, node.getLevel());
	}

	public void testTicTacToeXWins()
	{
		TicTacToe state = new TicTacToe();
		state.makeMove(0,0);
		state.makeMove(1,0);
		state.makeMove(0,1);
		state.makeMove(0,2);
		state.dump();
		{
			MCTS mcts = simulate(state, 1000, 10, true);
			mcts.getRoot().dumpToLevel(2);
			// http://en.wikipedia.org/wiki/File:Tictactoe-X.svg
			assertEquals(":4:8:5:", mcts.getBestPath().toString());
		}
	}

	public void testTicTacToeXLoses()
	{
		TicTacToe state = new TicTacToe();
		state.makeMove(1,0);
		state.makeMove(0,0);
		state.makeMove(1,2);
		state.makeMove(0,1);
		state.makeMove(2,1);
		state.makeMove(1,1);
		state.dump();
		{
			MCTS mcts = simulate(state, 100, 10, true);
			mcts.getRoot().dumpToLevel(5);
		}
	}

	public void testTicTacToeDraw()
	{
		TicTacToe state = new TicTacToe();
		state.getBoard().set(0, 0, Piece.X, 0);
		state.getBoard().set(1, 1, Piece.X, 0);
		state.getBoard().set(1, 2, Piece.X, 0);
		state.getBoard().set(1, 0, Piece.O, 1);
		state.getBoard().set(2, 0, Piece.O, 1);
		state.getBoard().set(2, 2, Piece.O, 1);
		state.dump();
		{
			MCTS mcts = simulate(state, 100, 10, true);
			mcts.getRoot().dumpToLevel(5);
		}
	}

	public void testFourUp()
	{
		final FourUp state = new FourUp();
		// Minimax = 111166.9 leaves/sec
		Node best = simulate(state, 1000, 50, false).getBestPath();
		assertEquals(3, best.getFirst().getMoveIndex()); // best move is center
		// TODO?
	}

	public void testFourUpGoodMoves()
	{
		final FourUp state = new FourUp();
		MCTS mcts = new MCTS(7*6+1);
		mcts.setGoodMoveProbability(50);
		Node best = simulate(state, mcts, 200).getBestPath(); // instead of 200
		assertEquals(3, best.getFirst().getMoveIndex());
	}

	public void xxxtestFourUpBig()
	{
		// TODO: why the drift?
		final FourUp state = new FourUp();
		Node best = simulate(state, 10000000, 7*6+1, false, 50, 0, 1.5).getBestPath();
		System.out.println(best);
	}

	public void testFourUpEndGame()
	{
		final FourUp state = new FourUp();
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
		MCTS mcts = simulate(state, 100000, 7*6, false, 0, 0, 0);
		mcts.getRoot().dumpToLevel(2);
		assertEquals(":2:2:1:0:0:", mcts.getBestPath().toString());
	}

	public void testPig()
	{
		Pig state = new Pig(2, 100);
		MCTS mcts = new MCTS(300);
		//mcts.setOption(Option.OnlyRetainLeafScores, true);
		//mcts.setTranspositionTableSize(16);
		//mcts.setGoodMoveProbability(50);
		//mcts.setDebug(true);
		Node node = simulate(state, mcts, 120000).getBestPath();
		MCTS.Node root = node.getRoot();
		System.out.println(root.toDesc());
		assertTrue(root.isChanceNode());
		assertTrue(root.isComplete());
		// cs.gettysburg.edu/~tneller/papers/umap10.pdf
		assertEquals(5306, Math.round(root.getAbsoluteWinRate()*10000));
	}

	public void testDice()
	{
		final Dice state = new Dice();
		MCTS mcts = new MCTS(100);
		mcts.setOption(Option.OnlyRetainLeafScores, true);
		Node node = simulate(state, mcts, 100000).getBestPath();
		MCTS.Node root = node.getRoot();
		System.out.println(root.toDesc());
		for (Node child : root.getAllChildren())
		{
			System.out.println(child + " " + child.toDesc());
		}
		// TOOD: not 65 because Minimax.testDice() is broken
		assertEquals(65, Math.round(root.getAbsoluteWinRate()*100));
	}

	public void testLattaque()
	{
		final Lattaque state = new Lattaque();
		state.initBoard(1);
		state.dump();
		MCTS mcts = new MCTS(100*Lattaque.LevelsPerTurn);
		mcts.setGoodMoveProbability(50);
		Node node = simulate(state, mcts, 1000).getBestPath();
		MCTS.Node root = node.getRoot();
		System.out.println(root.toDesc());
	}

	public void testGo9()
	{
		final Go state = new Go(9, 2);
		state.dump();
		MCTS mcts = new MCTS(9*9*Go.LevelsPerTurn);
		mcts.setGoodMoveProbability(50);
		Node node = simulate(state, mcts, 1000).getBestPath();
		MCTS.Node root = node.getRoot();
		System.out.println(root.toDesc());
	}

	public void testGo13()
	{
		final Go state = new Go(13, 2);
		state.dump();
		MCTS mcts = new MCTS(13*13*Go.LevelsPerTurn);
		mcts.setGoodMoveProbability(50);
		Node node = simulate(state, mcts, 10000).getBestPath();
		MCTS.Node root = node.getRoot();
		System.out.println(root.toDesc());
	}

	public void testImmediateCheckmate() throws MoveFailedException
	{
		Chess game = getChessPosition("R1k5/5Q2/5q2/7R/8/1p1K4/3B4/8 b - - -");
		Node node = simulate(game, 100, 100, true).getBestPath();
		assertEquals(0, node.getLevel());
	}

	public void testChess() throws MoveFailedException
	{
		Chess game = getChessPosition("1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - bm Qd1+");
		MCTS mcts = simulate(game, 20000, 100, false);
		mcts.getRoot().dumpToLevel(2);
		assertEquals(":43/2:", mcts.getBestPath().getMoveAtDepth(2).toString());
	}

	public void xxxtestConvergence() throws InterruptedException, ExecutionException
	{
		final Chess state = getChessPosition("8/2k5/4p3/1nb2p2/2K5/8/6B1/8 w - - bm Kxb5");
		String solution = "/26/33";
		//GameState state = new FourUp();
		//String solution = "/3//3//3//3//4/"; // ???
		Random rnd = new RandomXorshift128();
		String[] trials = new String[50];
		for (int trial=0; trial<trials.length; trial++)
		{
			int maxLevel = rnd.nextInt(10)*20 + 20;
			int goodMovePct = rnd.nextInt(10) * 10;
			int winBonus = rnd.nextInt(4);
			MCTS mcts = new MCTS(maxLevel);
			mcts.setGoodMoveProbability(goodMovePct);
			mcts.setEarlyWinBonus(winBonus);
			mcts.setRandomSeed(trial);
			int iters = 0;
			int stable = 0;
			int target = 10;
			do {
				int inc = 5000;
				mcts.iterateMultiThreaded(state, inc, 60);
				//mcts.iterate(state, inc);
				iters += inc;
				if ((mcts.getBestPath() + "").startsWith(solution))
					stable++;
				else
					stable = 0;
				if (stable == target)
					break;
			} while (iters < 200000);
			String key = MiscUtils.format("%d %d %d %d", iters, maxLevel, goodMovePct, winBonus);
			trials[trial] = key + " " + mcts + " " + mcts.getBestPath();
			System.out.println(trials[trial]);
		}
		for (int trial=0; trial<trials.length; trial++)
		{
			System.out.println(trials[trial]);
		}
	}
	
	public void testCluedo() throws MoveFailedException
	{
		Cluedo game = new Cluedo(3, 0);
		game.dump();
		MCTS mcts = new MCTS(300);
		//mcts.setGoodMoveProbability(75);
		mcts.setOption(Option.UseAbsoluteScores, true);
		//game.debug = true;
		//simulate(game, mcts, 10);
		simulate(game, mcts, 300000);
		mcts.getRoot().dumpToLevel(4);
		/*
		RandomDecider decider = new RandomDecider();
		while (!game.isGameOver())
		{
			//game.dump();
			game.playTurn(decider);
		}
		*/
	}

}
