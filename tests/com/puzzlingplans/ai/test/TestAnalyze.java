package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.games.Lattaque;
import com.puzzlingplans.ai.games.MatchThree;
import com.puzzlingplans.ai.games.Pig;
import com.puzzlingplans.ai.games.cards.Poker;
import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.games.go.Go;
import com.puzzlingplans.ai.games.go.Go7x7;

public class TestAnalyze extends BaseTestCase
{
	public void testGo7x7()
	{
		Go7x7 game = new Go7x7(7, 2);
		int maxLevel = game.getBoard().getNumCells() * 2 * Go7x7.LevelsPerTurn;
		analyze(game, maxLevel, 10000);
	}

	public void testGo()
	{
		Go game = new Go(9, 2);
		int maxLevel = game.getBoard().getNumCells() * 2 * Go.LevelsPerTurn;
		analyze(game, maxLevel, 10000);
	}
	
	public void testChess()
	{
		Chess game = new Chess();
		game.initDefaultBoard();
		int maxLevel = game.getBoard().getNumCells() * 2 * Chess.LevelsPerTurn;
		analyze(game, maxLevel, 10000);
	}

	public void testLattaque()
	{
		Lattaque game = new Lattaque();
		game.initBoard(0);
		int maxLevel = 300;
		analyze(game, maxLevel, 10000);
	}

	public void testPig()
	{
		Pig game = new Pig();
		int maxLevel = 300;
		analyze(game, maxLevel, 100000);
	}
	
	public void testPoker()
	{
		Poker game = new Poker(3);
		analyze(game, 100, 10000);
	}
	
	public void testMatchThree()
	{
		MatchThree game = new MatchThree(2, 7, 9, 6);
		game.setRandomSeed(0);
		game.fillBoard();
		game.dump();
		assertEquals(0, game.removeAllMatches());
		game.dump();
		analyze(game, 100, 1000);
	}

}
