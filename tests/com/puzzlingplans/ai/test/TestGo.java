package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.games.go.Go;

public class TestGo extends BaseTestCase
{
	public void testCapture()
	{
		Go game = new Go(9, 2);
		parseRow(game, 0, "_	_	_	_	_	X	_	_	_");
		parseRow(game, 1, "_	_	_	_	X	X	_	_	_");
		parseRow(game, 2, "_	X	X	X	O	X	_	_	_");
		parseRow(game, 3, "X	X	X	X	O	_	X	X	_");
		parseRow(game, 4, "_	O	_	O	O	_	_	_	_");
		parseRow(game, 5, "_	O	O	_	_	_	O	O	_");
		parseRow(game, 6, "_	X	O	X	O	X	O	X	_");
		parseRow(game, 7, "_	O	_	O	_	O	_	O	_");
		parseRow(game, 8, "_	_	_	_	_	_	_	_	_");
		
		game.setCurrentPlayer(1);
		assertEquals(0, game.get(7, 6));
		assertEquals(MoveResult.Ok, game.makeMove(8, 6));
		game.dump();
		assertEquals(-1, game.get(7, 6));
		assertEquals(MoveResult.NoMoves, game.makeMove(8, 6));
		assertEquals(MoveResult.NoMoves, game.makeMove(-1, -1));
		assertEquals(MoveResult.NoMoves, game.makeMove(9, 9));
	}
	
	public void testCapture2()
	{
		Go game = new Go(9, 2);
		parseRow(game, 0, "_	_	_	O	O	_	_	_	_");
		parseRow(game, 1, "_	X	O	X	O	X	_	O	_");
		parseRow(game, 2, "O	O	X	X	O	X	X	_	O");
		parseRow(game, 3, "_	O	_	X	X	O	_	X	_");
		parseRow(game, 4, "_	_	_	O	X	O	X	_	_");
		parseRow(game, 5, "_	X	_	X	_	X	_	_	_");
		parseRow(game, 6, "O	_	X	_	X	O	_	_	_");
		parseRow(game, 7, "_	_	_	_	_	X	_	_	_");
		parseRow(game, 8, "_	_	_	_	O	X	O	_	_");
		
		game.setCurrentPlayer(1);
		game.dump();
		game.makeMove(2, 3);
		game.dump();
		assertEquals(0, game.get(4, 4));
	}

	private void parseRow(Go game, int y, String string)
	{
		String[] arr = string.split("\\s");
		for (int x=0; x<game.getBoard().getWidth(); x++)
		{
			int p = -1;
			if (arr[x].equals("X"))
				p = 0;
			else if (arr[x].equals("O"))
				p = 1;
			game.set(x, y, p);
		}
	}

	public void testClone()
	{
		Go game = new Go(9, 2);
		game.makeMove(0, 0);
		Go game2 = game.copy();
		assertTrue(game.hashFor(0) == game2.hashFor(0));
		game.makeMove(1, 1);
		assertTrue(game.hashFor(0) != game2.hashFor(0));
	}
	
}
