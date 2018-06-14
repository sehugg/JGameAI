package com.puzzlingplans.ai.test;

import java.util.BitSet;

import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.RandomDecider;
import com.puzzlingplans.ai.games.Dice;
import com.puzzlingplans.ai.games.FourUp;
import com.puzzlingplans.ai.games.Lattaque;
import com.puzzlingplans.ai.games.Pig;
import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.games.go.Go;
import com.puzzlingplans.ai.search.AIDecider;
import com.puzzlingplans.ai.search.MCTS;
import com.puzzlingplans.ai.search.Minimax;
import com.puzzlingplans.ai.util.FastLongSetStack;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class TestMinimaxVsMCTS extends BaseTestCase
{
	private static final long P0_WINS = 1;
	private static final long P1_WINS = 2;
	private static final int MAX_TURNS = 300;

	public void testFourUp() throws MoveFailedException
	{
		FourUp game = new FourUp();
		int maxl = game.getBoard().getNumCells() + 1;
		assertEquals(P1_WINS, compete(game, 1, maxl, 100,  maxl/4, false));
		assertEquals(P0_WINS, compete(game, 1, maxl, 40000, maxl/4, false));
	}

	public void testPig() throws MoveFailedException
	{
		Pig game = new Pig(2, 100);
		// TODO: MCTS always wins!
		assertEquals(P1_WINS, compete(game, 1, 25, 25, 12, false));
		assertEquals(P0_WINS, compete(game, 1, 500, 20000, 4, false));
	}

	public void testDice() throws MoveFailedException
	{
		Dice game = new Dice(5, 5, 4, 2);
		assertEquals(P1_WINS, compete(game, Dice.LevelsPerTurn, 100, 50, 6, false));
		assertEquals(P0_WINS, compete(game, Dice.LevelsPerTurn, 100, 40000, 2, false));
	}

	public void testGoSmall() throws MoveFailedException
	{
		Go game = new Go(7, 2);
		assertEquals(P1_WINS, compete(game, Go.LevelsPerTurn, 500, 10000, 4, true));
		assertEquals(P0_WINS, compete(game, Go.LevelsPerTurn, 500, 100000, 4, true));
	}
	
	public void testLattaque() throws MoveFailedException
	{
		Lattaque game = new Lattaque();
		game.initBoard(new RandomXorshift128().nextLong());
		assertEquals(P1_WINS, compete(game, Lattaque.LevelsPerTurn, 100, 1000, 6, true));
		assertEquals(P0_WINS, compete(game, Lattaque.LevelsPerTurn, 100, 20000, 4, true));
	}
	
	public void testChess() throws MoveFailedException
	{
		Chess game = new Chess();
		game.initDefaultBoard();
		assertEquals(P1_WINS, compete(game, Chess.LevelsPerTurn, 300, 50, 6, true));
		assertEquals(P0_WINS, compete(game, Chess.LevelsPerTurn, 300, 20000, 4, true));
	}

	public void testCheckmate() throws MoveFailedException
	{
		Chess game = getChessPosition("R1k5/5Q2/5q2/7R/8/1p1K4/3B4/8 w - - -");
		assertEquals(P0_WINS, compete(game, Chess.LevelsPerTurn, 300, 1000, 10, true));
	}
	
	public void testCheckmate2() throws MoveFailedException
	{
		Chess game = getChessPosition("R1k5/5Q2/5q2/7R/8/1p1K4/3B4/8 b - - -");
		assertEquals(P0_WINS, compete(game, Chess.LevelsPerTurn, 300, 1000, 10, true));
	}

	//

	private long compete(GameState<?> game, int levelsPerTurn, int maxTurns_p0, int iters_p0, int maxTurns_p1, boolean threefold)
	{
		game = game.copy();
		int turn = 0;
		FastLongSetStack previous_hashes = new FastLongSetStack(levelsPerTurn * Math.max(maxTurns_p0, maxTurns_p1) * 2);
		HashedPosition hashable = (game instanceof HashedPosition) ? (HashedPosition)game : null;
		
		MCTS player0 = new MCTS(maxTurns_p0 * levelsPerTurn);
		player0.setGoodMoveProbability(75);
		player0.setEarlyWinBonus(1);
		//player0.setTranspositionTableSize(20);
		
		//TODO: shouldn't need to pass game yet?
		Minimax player1 = new Minimax(game);
		player1.setMaxLevel(maxTurns_p1 * levelsPerTurn);
		player1.setDepthPenalty(1);
		player1.setTranspositionTableSize(20);
		//player1.setPrintStatsAtLevel(levelsPerTurn);
		
		long[] times = new long[2];
		
		while (!game.isGameOver())
		{
			if (turn++ > MAX_TURNS)
			{
				System.out.println("*** TOO MANY TURNS");
				return 0;
			}
			if (hashable != null && threefold)
			{
				long hash = hashable.hashFor(0); // must be same for all players
				previous_hashes.push(hash);
				if (previous_hashes.count(hash) >= 3)
				{
					System.out.println("*** THREEFOLD REPETITION");
					return 0;
				}
			}
			int currentPlayer = game.getCurrentPlayer();
			AIDecider decider;
			// TODO: figure out if we need to make so many copies of all these objects
			if (currentPlayer == 0)
			{
				MCTS mcts = new MCTS(player0);
				mcts.setNumIters(iters_p0);
				decider = mcts.newSolver(game);
			} else {
				// TODO: should be able to take ttable with us between turns
				player1.setTranspositionTableSize(20);
				decider = player1.newSolver(game, levelsPerTurn);
			}
			try
			{
				game.playTurn(decider);
			} catch (MoveFailedException e)
			{
				System.out.println(e);
				// try one more turn to make sure game is over
				try
				{
					game.playTurn(new RandomDecider());
				} catch (MoveFailedException e1)
				{
					e1.printStackTrace();
				}
				// if game is over, just exit
				// otherwise play a random move
				if (game.isGameOver())
				{
					break;
				} else {
					System.out.println("*** ALL NODES VISITED");
				}
			}
			times[currentPlayer] += decider.totalSolveTime;
			game.dump();
		}
		long winners = game.getWinners();
		System.out.println("*** GAME OVER, turn " + turn + ", winners = " + BitSet.valueOf(new long[] { winners }));
		for (int i=0; i<2; i++)
		{
			System.out.println("Player " + i + " thought for " + times[i] + " msec");
		}
		return winners;
	}
	
}
