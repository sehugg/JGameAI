package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.games.Dice;
import com.puzzlingplans.ai.games.FourUp;
import com.puzzlingplans.ai.games.Lattaque;
import com.puzzlingplans.ai.games.Pig;
import com.puzzlingplans.ai.games.cards.Freecell;
import com.puzzlingplans.ai.games.cards.Poker;
import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.games.go.Go;
import com.puzzlingplans.ai.games.go.Go7x7;
import com.puzzlingplans.ai.search.AISolver;
import com.puzzlingplans.ai.search.MCRAVE;
import com.puzzlingplans.ai.search.MCTS;
import com.puzzlingplans.ai.search.Minimax;
import com.puzzlingplans.ai.util.HammingSpaceIndex;
import com.puzzlingplans.ai.util.MiscUtils;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class TestMCRAVECompetition extends BaseTestCase
{
	private static final long P0_WINS = 1;
	private static final long P1_WINS = 2;
	
	public void testFourUp() throws MoveFailedException
	{
		FourUp game = new FourUp();
		int maxl = game.getBoard().getNumCells() + 1;
		assertEquals(P1_WINS, competeMultipleRounds(game, 1, maxl, 1000, 40000, 3));
		assertEquals(P0_WINS, competeMultipleRounds(game, 1, maxl, 40000, 1000, 3));
	}

	public void testFourUpVsMinimax() throws MoveFailedException
	{
		FourUp game = new FourUp();

		MCRAVE p0 = new MCRAVE(18, 300, 10000);
		
		Minimax p1 = new Minimax(game); // TODO: change constructors
		p1.setMaxLevel(12);
		p1.setTranspositionTableSize(20);

		AISolver[] players = new AISolver[] { p0, p1 };
		assertEquals(P0_WINS, compete(game, players));
	}

	public void testPig() throws MoveFailedException
	{
		// best 2 out of 3
		Pig game = new Pig(2, 100);
		
		assertEquals(P1_WINS, competeMultipleRounds(game, 1, 300, 25, 1000, 5));
		assertEquals(P0_WINS, competeMultipleRounds(game, 1, 300, 1000, 25, 5));
	}

	public void testDice() throws MoveFailedException
	{
		Dice game = new Dice(5, 5, 4, 2);
		int maxl = 5*5*2+2;
		assertEquals(P1_WINS, competeMultipleRounds(game, Dice.LevelsPerTurn, maxl, 100, 40000, 5));
		assertEquals(P0_WINS, competeMultipleRounds(game, Dice.LevelsPerTurn, maxl, 40000, 100, 5));
	}

	public void testGoSmall() throws MoveFailedException
	{
		Go game = new Go(9, 2);
		assertEquals(P1_WINS, compete(game, Go.LevelsPerTurn, 500, 1000, 10000));
		assertEquals(P0_WINS, compete(game, Go.LevelsPerTurn, 500, 10000, 10000));
	}
	
	public void testLattaque() throws MoveFailedException
	{
		Lattaque game = new Lattaque();
		game.initBoard(3, 2, true);
		assertEquals(P1_WINS, compete(game, Lattaque.LevelsPerTurn, 200, 1000, 20000));
		assertEquals(P0_WINS, compete(game, Lattaque.LevelsPerTurn, 200, 20000, 1000));
	}

	public void testGoVsRAVE() throws MoveFailedException
	{
		Go7x7 game = new Go7x7(7, 2);
		int maxl = game.getBoard().getNumCells() * 2;

		MCRAVE p0 = new MCRAVE(18, maxl, 10000);
		p0.setRAVEBias(0); // disable RAVE
		p0.preferredMoveProb = 0x100;
		//p0.preferredExpandProb = 0x100;

		MCRAVE p1 = new MCRAVE(18, maxl, 10000);
		p1.setRAVEBias(0.1f);
		p1.preferredMoveProb = 0x100;
		//p1.preferredExpandProb = 0x100;

		AISolver[] players = new AISolver[] { p0, p1 };
		
		long winners = competeMultipleRounds(game, players, 3);
		assertEquals(P1_WINS, winners);
	}

	public void testGo9x9VsRAVE() throws MoveFailedException
	{
		Go game = new Go(9, 2);
		int maxl = game.getBoard().getNumCells() * 2;

		MCRAVE p0 = new MCRAVE(18, maxl, 10000);
		p0.setRAVEBias(0); // disable RAVE
		p0.preferredMoveProb = 0x100;

		MCRAVE p1 = new MCRAVE(18, maxl, 10000);
		p1.preferredMoveProb = 0x100;

		AISolver[] players = new AISolver[] { p0, p1 };
		
		long winners = competeMultipleRounds(game, players, 3);
		assertEquals(P1_WINS, winners);
	}

	public void testGoVsReset() throws MoveFailedException
	{
		Go game = new Go(9, 2);

		MCRAVE p0 = new MCRAVE(18, 300, 10000);
		p0.resetBeforeSolve = true;

		MCRAVE p1 = new MCRAVE(18, 300, 10000);
		p1.resetBeforeSolve = false;

		AISolver[] players = new AISolver[] { p0, p1 };
		
		long winners = competeMultipleRounds(game, players, 3);
		assertEquals(P1_WINS, winners);
	}

	public void testGoVsVisit() throws MoveFailedException
	{
		Go game = new Go(9, 2);

		MCRAVE p0 = new MCRAVE(18, 300, 10000);
		p0.visitUnexpandedNodesFirst = true;

		MCRAVE p1 = new MCRAVE(18, 300, 10000);
		p1.visitUnexpandedNodesFirst = false;

		AISolver[] players = new AISolver[] { p0, p1 };
		
		long winners = competeMultipleRounds(game, players, 3);
		assertEquals(P1_WINS, winners);
	}

	public void testGoVsGoodMoves() throws MoveFailedException
	{
		Go7x7 game = new Go7x7(7, 2);

		MCRAVE p0 = new MCRAVE(18, 300, 10000);

		MCRAVE p1 = new MCRAVE(18, 300, 10000);
		p1.goodMoves = new HammingSpaceIndex(8);
		p1.hammingRadius = 2;

		AISolver[] players = new AISolver[] { p0, p1 };
		
		long winners = competeMultipleRounds(game, players, 3);
		if (p1.goodMoves != null) p1.goodMoves.dump();
		assertEquals(P1_WINS, winners);
	}

	public void testGoVsMinimax() throws MoveFailedException
	{
		Go7x7 game = new Go7x7(7, 2);

		MCRAVE p0 = new MCRAVE(18, 300, 10000);
		p0.resetBeforeSolve = false;
		//p0.setRAVEBias(1);
		//p0.visitUnexpandedNodesFirst = true;
		//p0.goodMoves = new HammingSpaceIndex(7);
		//p0.hammingRadius = 4;
		
		Minimax p1 = new Minimax(game); // TODO: change constructors
		p1.setMaxLevel(Go.LevelsPerTurn * 6);
		p1.setTranspositionTableSize(20);
		AISolver[] players = new AISolver[] { p0, p1 };
		
		assertEquals(P0_WINS, compete(game, players));
		if (p0.goodMoves != null) p0.goodMoves.dump();
	}

	public void testGoVsOldMCTS() throws MoveFailedException
	{
		Go7x7 game = new Go7x7(7, 2);

		MCTS p0 = new MCTS(200);
		p0.setNumIters(30000);
		//p0.setGoodMoveProbability(80);
		
		MCRAVE p1 = new MCRAVE(18, 200, 10000);
		p1.preferredMoveProb = 0x100;
		
		AISolver[] players = new AISolver[] { p0, p1 };
		assertEquals(P1_WINS, competeMultipleRounds(game, players, 3));
	}

	public void testDiceVsOldMCTS() throws MoveFailedException
	{
		Dice game = new Dice(5, 5, 4, 2);

		MCTS p0 = new MCTS(200);
		p0.setNumIters(30000);
		//p0.setGoodMoveProbability(80);
		
		MCRAVE p1 = new MCRAVE(18, 200, 10000);
		
		AISolver[] players = new AISolver[] { p0, p1 };
		assertEquals(P1_WINS, competeMultipleRounds(game, players, 3));
	}

	public void testLattaqueVsOldMCTS() throws MoveFailedException
	{
		Lattaque game = new Lattaque();
		game.initBoard(0, 3, true);

		MCTS p0 = new MCTS(300);
		p0.setNumIters(9000);
		//p0.setGoodMoveProbability(80);
		
		MCRAVE p1 = new MCRAVE(20, 300, 3000);
		//p1.optimisticChanceNodes = true;
		
		AISolver[] players = new AISolver[] { p0, p1 };
		assertEquals(P1_WINS, competeMultipleRounds(game, players, 3));
	}

	public void testChessVsMinimax() throws MoveFailedException
	{
		Chess game = new Chess();
		game.initDefaultBoard();

		MCRAVE p0 = new MCRAVE(18, Chess.LevelsPerTurn * 10, 10000);
		p0.resetBeforeSolve = false;
		p0.setRAVEBias(1);
		p0.preferredExpandProb = 0x100;
		//p0.visitUnexpandedNodesFirst = true;
		//p0.goodMoves = new HammingSpaceIndex(7);
		//p0.hammingRadius = 4;
		
		Minimax p1 = new Minimax(game); // TODO: change constructors
		p1.setMaxLevel(Chess.LevelsPerTurn * 6);
		p1.setTranspositionTableSize(20);
		AISolver[] players = new AISolver[] { p0, p1 };
		
		assertEquals(P1_WINS, compete(game, players));
		System.out.println(p0.goodMoves);
	}

	public void testCheckmate() throws MoveFailedException
	{
		Chess game = getChessPosition("R1k5/5Q2/5q2/7R/8/1p1K4/3B4/8 w - - -");
		assertEquals(P0_WINS, compete(game, Chess.LevelsPerTurn, 300, 1000, 1000));
	}

	public void testPoker() throws MoveFailedException
	{
		// TODO: should actually accumulate the winnings
		// TODO: these players fold too often
		Poker game = new Poker(2);

		assertEquals(P1_WINS, competeMultipleRounds(game, 1, 100, 100, 10000, 5));
		assertEquals(P0_WINS, competeMultipleRounds(game, 1, 100, 10000, 100, 5));
	}

	public void testFreecell() throws MoveFailedException
	{
		Freecell game = new Freecell(0);
		game.dump();
		compete(game, Freecell.LevelsPerTurn, 50, 100000, 0);
	}
	
	//

	private long compete(GameState<?> game, int levelsPerTurn, int maxLevel, int iters_p0, int iters_p1)
	{
		return competeMultipleRounds(game, levelsPerTurn, maxLevel, iters_p0, iters_p1, 1);
	}

	private long competeMultipleRounds(GameState<?> game, int levelsPerTurn, int maxLevel, int iters_p0, int iters_p1, int numRounds)
	{
		MCRAVE[] players = new MCRAVE[game.getNumPlayers()];
		for (int i=0; i<players.length; i++)
		{
			players[i] = new MCRAVE(18);
			if (i == 0)
				players[i].setNumIters(iters_p0);
			else
				players[i].setNumIters(iters_p1);
			players[i].setMaxLevel(levelsPerTurn * maxLevel);
		}
		return competeMultipleRounds(game, players, numRounds);
	}

	//
	
	public static void main(String[] args)
	{
		RandomXorshift128 rnd = new RandomXorshift128();
		MCRAVE p0 = getRandomMCRAVE(rnd);
		MCRAVE p1 = getRandomMCRAVE(rnd);
		Go game = new Go(9, 2);
		while (true)
		{
			AISolver[] players = new AISolver[] { p0, p1 };
			try {
				new TestMCRAVECompetition().compete(game, players);
			} catch (Throwable t) {
				t.printStackTrace();
			}
			p0 = p1;
			p1 = getRandomMCRAVE(rnd);
		}
	}

	private static MCRAVE getRandomMCRAVE(RandomXorshift128 rnd)
	{
		int nodesLog2 = rnd.nextInt(12) + 8;
		int maxLevel = rnd.nextInt(280) + 20;
		int numIters = rnd.nextInt(100) * 1000;
		float raveBias = (float) Math.exp(rnd.nextGaussian());
		float uct = rnd.nextFloat() * 10;
		boolean resetBeforeSolve = rnd.nextBoolean();
		boolean visitUnexpanded = rnd.nextBoolean();
		
		System.out.println(MiscUtils.format("!!MCRAVE %d %d %d %f %f %b %b", nodesLog2, maxLevel, numIters, raveBias, uct, resetBeforeSolve, visitUnexpanded));
		
		MCRAVE mcrave = new MCRAVE(nodesLog2);
		mcrave.setMaxLevel(maxLevel);
		mcrave.setNumIters(numIters);
		mcrave.setRAVEBias(raveBias);
		mcrave.setUCTConstant(uct);
		mcrave.resetBeforeSolve = resetBeforeSolve;
		mcrave.visitUnexpandedNodesFirst = visitUnexpanded;
		return mcrave; 
	}

}
