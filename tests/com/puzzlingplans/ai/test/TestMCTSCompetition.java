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
import com.puzzlingplans.ai.search.MCTS;
import com.puzzlingplans.ai.search.MCTS.Option;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class TestMCTSCompetition extends BaseTestCase
{
	private static final long P0_WINS = 1;
	private static final long P1_WINS = 2;
	
	public void testFourUp() throws MoveFailedException
	{
		FourUp game = new FourUp();
		int maxl = game.getBoard().getNumCells() + 1;
		assertEquals(P1_WINS, compete(game, 1, maxl, 1000, 40000));
		assertEquals(P0_WINS, compete(game, 1, maxl, 40000, 1000));
	}

	public void testPig() throws MoveFailedException
	{
		// best 2 out of 3
		Pig game = new Pig(2, 100);
		
		MCTS mcts = new MCTS(100);
		mcts.setGoodMoveProbability(50);
		
		assertEquals(P1_WINS, competeMultipleRounds(game, mcts, 25, 10000, 5));
		assertEquals(P0_WINS, competeMultipleRounds(game, mcts, 10000, 25, 5));
	}

	public void testDice() throws MoveFailedException
	{
		Dice game = new Dice(5, 5, 4, 2);

		MCTS mcts = new MCTS(5*5*2+2);
		mcts.setGoodMoveProbability(50);
		
		assertEquals(P1_WINS, competeMultipleRounds(game, mcts, 100, 40000, 3));
		assertEquals(P0_WINS, competeMultipleRounds(game, mcts, 40000, 100, 3));
	}

	public void testGoSmall() throws MoveFailedException
	{
		Go game = new Go(7, 2);
		assertEquals(P1_WINS, compete(game, Go.LevelsPerTurn, 500, 10000, 100000));
		assertEquals(P0_WINS, compete(game, Go.LevelsPerTurn, 500, 100000, 10000));
	}
	
	public void testLattaque() throws MoveFailedException
	{
		Lattaque game = new Lattaque();
		game.initBoard(new RandomXorshift128().nextLong(), 3, true);
		assertEquals(P1_WINS, compete(game, Lattaque.LevelsPerTurn, 200, 1000, 20000));
		assertEquals(P0_WINS, compete(game, Lattaque.LevelsPerTurn, 200, 20000, 1000));
	}
	
	public void xxxtestChess() throws MoveFailedException
	{
		Chess game = new Chess();
		game.initDefaultBoard();
		assertEquals(P1_WINS, compete(game, Chess.LevelsPerTurn, 300, 1000, 10000));
		assertEquals(P0_WINS, compete(game, Chess.LevelsPerTurn, 300, 10000, 1000));
	}

	public void testCheckmate() throws MoveFailedException
	{
		Chess game = getChessPosition("R1k5/5Q2/5q2/7R/8/1p1K4/3B4/8 w - - -");
		assertEquals(P0_WINS, compete(game, Chess.LevelsPerTurn, 300, 1000, 1000));
	}

	public void testPoker() throws MoveFailedException
	{
		// best 2 out of 3
		// TODO: should actually add up the winnings
		// TODO: also these players never fold
		// TODO: 		mcts.setOption(Option.UseAbsoluteScores, true); // so that winners can win
		Poker game = new Poker(2);

		MCTS mcts = new MCTS(100);
		mcts.setGoodMoveProbability(50);
		mcts.setExplorationConstant(1000.0 * game.getNumPlayers() / GameState.WIN);
		mcts.setOption(Option.UseAbsoluteScores, true);
		
		assertEquals(P1_WINS, competeMultipleRounds(game, mcts, 1000, 40000, 5));
		assertEquals(P0_WINS, competeMultipleRounds(game, mcts, 40000, 1000, 5));
	}

	public void xxxtestFreecell() throws MoveFailedException
	{
		Freecell game = new Freecell(0);
		game.dump();
		compete(game, Freecell.LevelsPerTurn, 50, 100000, 0);
	}
	
	//

	private long compete(GameState<?> game, int levelsPerTurn, int maxLevel, int iters_p0, int iters_p1)
	{
		MCTS common = new MCTS(maxLevel);
		common.setGoodMoveProbability(75);
		
		return compete(game, common, iters_p0, iters_p1);
	}

	private long compete(GameState<?> game, MCTS common, int iters_p0, int iters_p1)
	{
		MCTS[] playermcts = new MCTS[game.getNumPlayers()];
		for (int i=0; i<playermcts.length; i++)
		{
			playermcts[i] = common;
		}
		return compete(game, playermcts, iters_p0, iters_p1);
	}

	private long compete(GameState<?> game, MCTS[] playermcts, int iters_p0, int iters_p1)
	{
		playermcts[0].setNumIters(iters_p0);
		playermcts[1].setNumIters(iters_p1);
		return compete(game, playermcts);
	}
	
	private long competeMultipleRounds(GameState<?> game, MCTS mcts, int iters_p0, int iters_p1, int numRounds)
	{
		MCTS[] players = new MCTS[2];
		players[0] = new MCTS(mcts);
		players[0].setNumIters(iters_p0);
		players[1] = new MCTS(mcts);
		players[1].setNumIters(iters_p1);
		return competeMultipleRounds(game, players, numRounds);
	}

}
