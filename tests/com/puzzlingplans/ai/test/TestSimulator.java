package com.puzzlingplans.ai.test;

import java.util.Random;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveExplorer;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.games.Dice;
import com.puzzlingplans.ai.games.Lattaque;
import com.puzzlingplans.ai.games.Pig;
import com.puzzlingplans.ai.games.TicTacToe;
import com.puzzlingplans.ai.search.LevelInfo;
import com.puzzlingplans.ai.search.Simulator;
import com.puzzlingplans.ai.util.BitUtils;

public class TestSimulator extends BaseTestCase
{
	class Node extends Line<Node>
	{
		public Node(Node parent, int index)
		{
			super(parent, index);
		}

		public Node()
		{
			super(null, -1);
		}
	}
	
	public void testSim() throws MoveFailedException
	{
		TicTacToe game = new TicTacToe();
		GameState<?> newstate = doSim(game, 10);
		assertTrue(newstate.isGameOver());
	}

	public void testInvalidMoves() throws MoveFailedException
	{
		GameState<?> game = new SampleGames.TwoLevelVariableResults();
		GameState<?> newstate = doSim(game, 10);
		assertTrue(newstate.isGameOver());
	}

	private GameState<?> doSim(GameState<?> game, int maxLevel) throws MoveFailedException
	{
		Simulator sim = new Simulator<LevelInfo>(game, maxLevel, 1)
		{
			@Override
			public MoveResult choose(Choice choice) throws MoveFailedException
			{
				long mask = choice.getPotentialMoves();
				while (mask != 0)
				{
					int i = BitUtils.nextBit(mask, 0);
					if (i < 0)
						break;
					if (tryChoice(choice, i) == MoveResult.Ok)
						return MoveResult.Ok;
					mask &= ~(1 << i);
				}
				return MoveResult.NoMoves;
			}
		};
		sim.debug = true;
		{
			GameState<?> newstate = sim.simulate();
			sim.dump();
			return newstate;
		}
	}
	
	public void testMoveExplorer()
	{
		{
			GameState<?> game = new TicTacToe();
			MoveExplorer ex = new MoveExplorer(game, new int[0]);
			assertEquals(":{0, 1, 2, 3, 4, 5, 6, 7, 8}", ex.explore().toString());
			assertEquals(MoveResult.Canceled, ex.explore().result);
		}
		{
			GameState<?> game = new TicTacToe();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 1, 2 });
			assertEquals(":1:2:", ex.explore().toString());
			assertEquals(MoveResult.Ok, ex.explore().result);
			assertEquals(":1:2:", ex.explore().removeChanceNodes().toString());
		}
		{
			GameState<?> game = new Dice();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 2 });
			assertEquals(":*2/{0, 1, 2}", ex.explore().toString());
			assertEquals(MoveResult.Canceled, ex.explore().result);
		}
		{
			GameState<?> game = new Dice();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 1, 2 });
			assertEquals(":*1/2:", ex.explore().toString());
			assertEquals(MoveResult.Ok, ex.explore().result);
			assertEquals(":2:", ex.explore().removeChanceNodes().toString());
		}
		{
			GameState<?> game = new TicTacToe();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 10 });
			assertEquals(":X", ex.explore().toString());
			assertEquals(MoveResult.NoMoves, ex.explore().result);
			assertEquals(0, ex.explore().countTurns());
		}
		{
			GameState<?> game = new TicTacToe();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 1, 10 });
			assertEquals(":1:X", ex.explore().toString());
			assertEquals(MoveResult.NoMoves, ex.explore().result);
		}
		{
			GameState<?> game = new TicTacToe();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 1, 1 });
			assertEquals(":1:X", ex.explore().toString());
			assertEquals(MoveResult.NoMoves, ex.explore().result);
		}
		{
			GameState<?> game = new TicTacToe();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
			assertEquals(":0:1:2:3:4:5:6:", ex.explore().toString());
			assertEquals(7, ex.explore().countTurns());
		}
		{
			GameState<?> game = new Dice();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 0, 0, 0, 1, 0, 2, 0, 1 });
			assertEquals(":*0/0:*0/1:*0/2:*0/X", ex.explore().toString());
			assertEquals(":0:1:2:", ex.explore().removeChanceNodes().toString());
			assertEquals(3, ex.explore().countTurns());
		}
		{
			GameState<?> game = new Dice();
			MoveExplorer ex = new MoveExplorer(game, new int[] { 0, 0, 0, 1, 0, 2, 0, 1 });
			ex.setSplitTurns();
			assertEquals(":*0/X", ex.explore().toString());
			assertEquals("[:*0/0:, :*0/1:, :*0/2:]", ex.getTurns().toString());
		}
	}

	public void testMoveExplorerHiddenMoves()
	{
		Lattaque game = new Lattaque(0);
		game.dump();
		MoveExplorer ex;
		ex = new MoveExplorer(game, new int[] { 3, 0 });
		assertEquals(":3/0/{2, 6}", ex.explore().toString());
		ex = new MoveExplorer(game, new int[] { 3, 0, 6 });
		assertEquals(":3/0/6:", ex.explore().toString());
		ex = new MoveExplorer(game, new int[] { 3, 0, 6, 6, 0, 0 });
		assertEquals(":3/0/6:6/0/0/{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13}", ex.explore().toString());
		ex.setRealLife();
		assertEquals(":3/0/6:6/0/0:", ex.explore().toString());
		// should have resolved the hidden choice (attack)
		ex.currentState().dump();
		assertEquals(1, ((Lattaque)ex.currentState()).getBoard().get(0, 5).player);
		// TODO: omit hidden choices
	}

	public void testMoveExplorerSplitTurns()
	{
		Lattaque game = new Lattaque(0);
		MoveExplorer ex;
		Node move = new Node(new Node(new Node(new Node(), 3), 0), 6);
		move.setIsEndOfTurn();
		System.out.println(move);
		{
			ex = new MoveExplorer(game, move);
			ex.setSplitTurns();
			assertEquals(":", ex.explore().toString());
			assertEquals("[:3/0/6:]", ex.getTurns().toString());
		}
		move = new Node(new Node(new Node(move, 6), 0), 0);
		move.setIsEndOfTurn();
		System.out.println(move);
		{
			// last turn incomplete due to hidden move
			ex = new MoveExplorer(game, move);
			ex.setSplitTurns();
			assertEquals(":6/0/0/{2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13}", ex.explore().toString());
			assertEquals("[:3/0/6:]", ex.getTurns().toString());
		}
		{
			ex = new MoveExplorer(game, move);
			ex.setSplitTurns();
			ex.setRealLife();
			assertEquals(":", ex.explore().toString());
			assertEquals("[:3/0/6:, :6/0/0:]", ex.getTurns().toString());
		}
	}

	public void testMoveExplorerRandomMoves()
	{
		{
			Dice game = new Dice();
			MoveExplorer ex = new MoveExplorer(game, new int[] { -1, 0 });
			ex.setEvaluateRandomChoices(new Random(0));
			assertEquals(":*2/0:", ex.explore().toString());
			assertEquals(":*2/0:", ex.explore().toString());
			assertEquals(":*0/0:", ex.explore().toString());
			assertEquals(":*1/0:", ex.explore().toString());
		}
		{
			Pig game = new Pig();
			MoveExplorer ex = new MoveExplorer(game, new int[] { -1 });
			ex.setEvaluateRandomChoices(new Random(0));
			assertEquals(":*4:", ex.explore().toString());
		}
	}
}
