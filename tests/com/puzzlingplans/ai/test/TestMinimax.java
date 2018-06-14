package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.ReplayDecider;
import com.puzzlingplans.ai.games.Dice;
import com.puzzlingplans.ai.games.FourUp;
import com.puzzlingplans.ai.games.Lattaque;
import com.puzzlingplans.ai.games.Pig;
import com.puzzlingplans.ai.games.TicTacToe;
import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.search.Minimax;

public class TestMinimax extends BaseTestCase
{
	boolean debug = false;
	
	private Minimax countLeaves(GameState<?> state, int l, int expected)
	{
		Minimax mmax = new Minimax(state);
		mmax.setPruning(false);
		mmax.setMaxLevel(l);
		mmax.setDebug(debug);
		System.out.println("SCORE = " + mmax.solve());
		System.out.println(mmax);
		System.out.println("BEST = " + mmax.getPrincipalVariation());
		assertEquals(expected, mmax.numLeavesVisited());
		return mmax;
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
		countLeaves(new SampleGames.TwoLevelOneValidMove(), 6, 2*2*2);
	}

	public void testSimpleTwoLevelVariableResults()
	{
		Minimax mmax = countLeaves(new SampleGames.TwoLevelVariableResults(), 6, 30);// TODO: correct?
		assertEquals(15, mmax.numGamesCompleted());
	}

	public void testSimpleRandomScoringGame()
	{
		doRandomScoringTest(4, 14);
		doRandomScoringTest(8, 18);
		doRandomScoringTest(16, 14);
	}

	public void testSimpleBinary50PercentWin()
	{
		Minimax mmax = countLeaves(new SampleGames.Binary50PercentWinGame(16), 16+1, 1<<(16+1));
		assertEquals(0, mmax.getLastRootScore() * 100 / GameState.WIN);
	}

	private void doRandomScoringTest(int l, int s)
	{
		Minimax mmax = countLeaves(new SampleGames.BinaryRandomScoringGame(), l, 1<<l);
		assertEquals(s, mmax.getLastRootScore());
		assertEquals(s, mmax.solve());
		mmax.setPruning(true);
		assertEquals(s, mmax.solve());
		System.out.println(mmax);
		mmax.setTranspositionTableSize(l);
		int tts = mmax.solve();
		System.out.println(mmax);
		System.out.println(mmax.getTranspositionTable());
		assertEquals(s, tts);
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
			Minimax mmax = new Minimax(state);
			mmax.setDebug(debug);
			assertEquals(0, state.getCurrentPlayer());
			assertEquals(GameState.LOSE, mmax.solve());
		}
	}

	public void testTicTacToeDraw()
	{
		TicTacToe state = new TicTacToe();
		state.makeMove(0,0);
		state.makeMove(1,0);
		state.makeMove(1,1);
		state.makeMove(2,0);
		state.makeMove(1,2);
		state.makeMove(2,2);
		state.dump();
		{
			Minimax mmax = new Minimax(state);
			mmax.setDebug(!debug);
			assertEquals(0, state.getCurrentPlayer());
			assertEquals(GameState.DRAW, mmax.solve());
			assertEquals(":5:", mmax.getBestMove().toString()); //2,1,X
			assertEquals(":5:3:6:", mmax.getPrincipalVariation().toString()); //(2,1,X) (0,1,O) (0,2,X)
		}
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
			Minimax mmax = new Minimax(state);
			mmax.setDebug(debug);
			mmax.setDepthPenalty(1);
			assertEquals(0, state.getCurrentPlayer());
			assertEquals(GameState.WIN-3, mmax.solve());
			assertEquals(":4:2:5:", mmax.getPrincipalVariation().toString());
		}
	}

	public void testFourUpEndGameTT() throws MoveFailedException
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
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(12);
		mmax.setDebug(debug);
		mmax.setDepthPenalty(1);
		mmax.setTranspositionTableSize(18);
		assertEquals(GameState.WIN-5, mmax.solve());
		System.out.println(mmax);
		System.out.println(mmax.getTranspositionTable());
		assertEquals(":2:", mmax.getBestMove().toString());
		// TODO: defeatist minimax makes random move?
		System.out.println(mmax.getPrincipalVariation());
		assertEquals(":2:2:1:0:0:", mmax.getPrincipalVariation().toString());
	}
	
	public void testPig()
	{
		Pig state = new Pig(2, 25);
		Minimax mmax = new Minimax(state);
		mmax.setDebug(debug);
		mmax.setPrintStatsAtLevel(1);
		mmax.setTranspositionTableSize(20);
		mmax.setMaxLevel(32);
		int score = mmax.solve();
		score = mmax.solve();
		System.out.println(mmax);
		System.out.println(mmax.getTranspositionTable());
		System.out.println(mmax.getPrincipalVariation());
		// cs.gettysburg.edu/~tneller/papers/umap10.pdf
		assertEquals(71, score*100/GameState.WIN); // TODO : correct?
	}

	public void testLattaque()
	{
		Lattaque state = new Lattaque();
		state.initBoard(0);
		state.dump();
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(Lattaque.LevelsPerTurn*6);
		mmax.setPrintStatsAtLevel(Lattaque.LevelsPerTurn);
		mmax.setTranspositionTableSize(24);
		mmax.setDebug(debug);
		int score = mmax.solve();
		System.out.println(mmax);
		System.out.println(mmax.getTranspositionTable());
		System.out.println(mmax.getPrincipalVariation());
		System.out.println(mmax.getBestMove());
		System.out.println(score);
		// TODO?
	}

	public void testDice()
	{
		// http://lists.gnu.org/archive/html/bug-gnubg/2003-08/msg00407.html
		Dice state = new Dice();
		state.skipTurnWhenNoMoves = false; // with turn skipping we need a lot more search depth
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(10*2+2); // 3x3, 2 choices each level
		mmax.setDebug(debug);
		mmax.setTranspositionTableSize(16);
		int score = mmax.solve();
		System.out.println(mmax);
		System.out.println(mmax.getPrincipalVariation());
		// TODO: doesn't work because turns have chance/non-chance nodes
		assertEquals(296826, score); // 0.648413
	}

	public void testCutoffPerformance()
	{
		FourUp state = new FourUp();
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(10);
		mmax.setDepthPenalty(1);
		mmax.setPruning(true);
		//mmax.setDebug(debug);
		long t1 = System.currentTimeMillis();
		mmax.solve();
		System.out.println(mmax);
		long t2 = System.currentTimeMillis();
		mmax = new Minimax(state, mmax);
		mmax.solve();
		long t3 = System.currentTimeMillis();
		System.out.println(mmax);
		System.out.println(t2-t1 + " msec, " + mmax.numLeavesVisited()*1000.0f/(t2-t1) + " leaves/sec");
		System.out.println(t3-t2 + " msec, " + mmax.numLeavesVisited()*1000.0f/(t3-t2) + " leaves/sec");
		assertEquals(229291, mmax.numLeavesVisited());
		assertTrue(t3-t2 < (t2-t1)/2); // move ordering works 
		assertTrue(t3-t2 < 1000);
	}
	
	public void testTransTablePerformance()
	{
		Chess state = new Chess();
		state.initDefaultBoard();
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(10);
		mmax.setDepthPenalty(1);
		mmax.setPruning(true);
		//mmax.setDebug(debug);
		long t1 = System.currentTimeMillis();
		// fill the trans table and move ordering table
		mmax.solve();
		mmax.setTranspositionTableSize(18);
		mmax.solve();
		String s1 = mmax + "\n" + mmax.getTranspositionTable();
		long t2 = System.currentTimeMillis();
		mmax = new Minimax(state, mmax);
		mmax.setMaxLevel(12);
		mmax.solve();
		long t3 = System.currentTimeMillis();
		System.out.println(s1);
		System.out.println(mmax);
		System.out.println(mmax.getTranspositionTable());
		System.out.println(t2-t1 + " msec, " + mmax.numLeavesVisited()*1000.0f/(t2-t1) + " leaves/sec");
		System.out.println(t3-t2 + " msec, " + mmax.numLeavesVisited()*1000.0f/(t3-t2) + " leaves/sec");
		assertEquals(331940, mmax.numLeavesVisited());
		assertTrue(t3-t2 < (t2-t1)); // trans table works 
		assertTrue(t3-t2 < 1000);
	}
	
	public void testTicTac()
	{
		TicTacToe state = new TicTacToe();
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(10);
		mmax.setPruning(false);
		assertEquals(0, mmax.solve());
		assertEquals(255168, mmax.numLeavesVisited());
	}

	public void testFourUpSmallBoard()
	{
		// http://homepages.cwi.nl/~tromp/c4/c4.html
		// TODO: we can't count # of positions, but we can estimate based on # cutoffs
		FourUp state = new FourUp(4, 4, 4, 2);
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(4*4+1);
		mmax.setPruning(true);
		mmax.solve();
		System.out.println(mmax);
		int n = mmax.numLeavesVisited();
		assertTrue(n < 161029);
	}

	public void testChess()
	{
		Chess state = new Chess();
		state.initDefaultBoard();
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(3*Chess.LevelsPerTurn);
		mmax.setPruning(false);
		mmax.solve();
		assertEquals(8902, mmax.numLeavesVisited());
	}

	public void testChess2()
	{
		Chess state = new Chess();
		state.initDefaultBoard();
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(4*Chess.LevelsPerTurn);
		mmax.setPruning(false);
		mmax.solve();
		assertEquals(0, state.stats.numCheckmates);
		assertEquals(197281, mmax.numLeavesVisited());
	}

	public void testChess3()
	{
		String position = "1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - bm Qd1+";
		int maxLevel = 6;
		minimaxChessTest(position, maxLevel, GameState.WIN, 0);
	}

	public void testChess3TT()
	{
		String position = "1k1r4/pp1b1R2/3q2pp/4p3/2B5/4Q3/PPP2B2/2K5 b - - bm Qd1+";
		int maxLevel = 6;
		minimaxChessTest(position, maxLevel, GameState.WIN, 16);
	}

	public void testChessCompareTT()
	{
		String position = "8/2k5/4p3/1nb2p2/2K5/8/6B1/8 w - - -";
		int maxLevel = 8;
		int score = 330;
		long t1 = System.currentTimeMillis();
		minimaxChessTest(position, maxLevel, score, 20);
		long t2 = System.currentTimeMillis();
		minimaxChessTest(position, maxLevel, score, 0);
		long t3 = System.currentTimeMillis();
		System.out.println((t2-t1) + " ms for tt vs. " + (t3-t2));
		assertTrue(t2-t1 < t3-t2);
	}

	public void testChessCompareTT2()
	{
		String position = "3k4/p7/K3BP2/8/7p/8/2P4P/8 w - - -";
		int maxLevel = 12;
		int score = GameState.WIN;
		long t1 = System.currentTimeMillis();
		minimaxChessTest(position, maxLevel, score, 20);
		/*
		long t2 = System.currentTimeMillis();
		minimaxChessTest(position, maxLevel, score, 0);
		long t3 = System.currentTimeMillis();
		System.out.println((t2-t1) + " ms for tt vs. " + (t3-t2));
		assertTrue(t2-t1 < t3-t2);
		*/
	}

	private void minimaxChessTest(String position, int maxLevel, Integer targetScore, int transTableOrder)
	{
		Chess game = getChessPosition(position);
		Minimax mmax = new Minimax(game);
		mmax.setFailSoft(true);
		mmax.setMaxLevel(maxLevel*Chess.LevelsPerTurn);
		mmax.setTranspositionTableSize(transTableOrder);
		mmax.setPrintStatsAtLevel(2);
		int score = mmax.solve();
		System.out.println(mmax);
		System.out.println(mmax.getTranspositionTable());
		System.out.println("Score " + score + "; " + mmax);
		System.out.println(game.stats);
		Line<?> pv = mmax.getPrincipalVariation();
		System.out.println(pv);
		int srci = pv.getMoveAtDepth(1).getMoveIndex();
		int desti = pv.getMoveAtDepth(2).getMoveIndex();
		System.out.println(srci + " " + desti);
		String moveString = game.getMoveString(srci, desti);
		System.out.println(moveString);
		if (game.epdBestMove != null)
			assertEquals(game.epdBestMove, moveString);
		if (targetScore != null)
			assertEquals((int)targetScore, score);
	}
	
	public void testMultiPlayer() throws MoveFailedException
	{
		FourUp state = new FourUp(5, 5, 4, 3);
		state.makeMove(2);
		state.makeMove(0);
		state.makeMove(0);
		state.makeMove(2);
		state.makeMove(1);
		state.makeMove(1);
		state.dump();
		Minimax mmax = new Minimax(state);
		mmax.setMaxLevel(state.getBoard().getNumCells()+1);
		mmax.setPruning(true);
		mmax.setTranspositionTableSize(20);
		int score = mmax.solve();
		System.out.println(mmax);
		System.out.println(mmax.getPrincipalVariation());
		ReplayDecider decider = new ReplayDecider(mmax.getPrincipalVariation(), state.getCurrentPlayer());
		while (decider.hasNext())
		{
			state.playTurn(decider);
		}
		state.dump();
		assertEquals(GameState.LOSE, score);
	}
	
	public static void main(String[] args)
	{
		new TestMinimax().testChess3TT();
	}
}
