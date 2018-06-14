package com.puzzlingplans.ai.test;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

import junit.framework.TestCase;

import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameOverException;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.games.chess.Chess;
import com.puzzlingplans.ai.games.chess.EPDParser;
import com.puzzlingplans.ai.search.AIDecider;
import com.puzzlingplans.ai.search.AISolver;
import com.puzzlingplans.ai.search.Analyzer;
import com.puzzlingplans.ai.search.MCTS;
import com.puzzlingplans.ai.search.MCTS.Node;
import com.puzzlingplans.ai.util.MiscUtils;
import com.puzzlingplans.ai.util.ThreadUtils;

public class BaseTestCase extends TestCase
{
	protected boolean multiThreaded = true;
	protected int lastNumCompeteTurns;
	protected int maxTurns = 300;
	protected PerformanceCounter pc = PerformanceCounter.getInstance();
	protected List<Line<?>> competeMoves;
	
	public abstract class Benchmarkable
	{
		public abstract int run();
	}
	
	protected long currentCPUTimeMillis()
	{
		return pc.currentCPUTimeMillis();
	}

	protected long currentCPUTimeMillis(Thread[] threads)
	{
		return pc.currentCPUTimeMillis(threads);
	}

	public long benchmark(String string, Benchmarkable task)
	{
		System.gc();
		long t1 = currentCPUTimeMillis();
		int iters = task.run();
		long t2 = currentCPUTimeMillis();
		System.out.println(string + ": " + (t2-t1) + " cpu msec, " + iters*1000.0f/(t2-t1) + " ops/sec");
		return t2-t1;
	}

	public long benchmark(String string, int iters, Runnable task)
	{
		System.gc();
		long t1 = currentCPUTimeMillis();
		for (int i=0; i<iters; i++)
			task.run();
		long t2 = currentCPUTimeMillis();
		System.out.println(string + ": " + (t2-t1) + " cpu msec, " + iters*1000.0f/(t2-t1) + " ops/sec");
		return t2-t1;
	}
	
	static ThreadPoolExecutor threadPool = (ThreadPoolExecutor) Executors.newCachedThreadPool();

	public long benchmarkMultiThreaded(String string, int numThreads, final Runnable task)
	{
		System.gc();
		Future<?>[] futures = new Future[numThreads];
		for (int i=0; i<numThreads; i++)
		{
			futures[i] = threadPool.submit(task);
		}
		long t1 = currentCPUTimeMillis(ThreadUtils.getThreadPoolExecutorThreads(threadPool));
		for (Future<?> f : futures)
		{
			try
			{
				f.get();
			} catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			} catch (ExecutionException e)
			{
				throw new RuntimeException(e.getCause());
			}
		}
		long t2 = currentCPUTimeMillis(ThreadUtils.getThreadPoolExecutorThreads(threadPool));
		System.out.println(string + ": " + numThreads + " threads, " + (t2-t1) + " cpu msec");
		return t2-t1;
	}

	public MCTS simulate(final GameState<?> state, int iters, int maxLevel, boolean debug, int goodMovePct, int transTableSize, double earlyWinBonus)
	{
		MCTS mcts = new MCTS(maxLevel);
		mcts.setDebug(debug);
		mcts.setGoodMoveProbability(goodMovePct);
		mcts.setTranspositionTableSize(transTableSize);
		mcts.setEarlyWinBonus(earlyWinBonus);
		return simulate(state, mcts, iters);
	}

	public MCTS simulate(final GameState<?> state, int iters, int maxLevel, boolean debug)
	{
		return simulate(state, iters, maxLevel, debug, 0, 0, 0);
	}

	public MCTS simulate(final GameState<?> state, final MCTS mcts, final int iters)
	{
		// TODO: finalize doesn't work
		HangDetector hd = new HangDetector(5000)
		{
			@Override
			public void hangDetected()
			{
				mcts.setDebug(true);
			}
			
			@Override
			public String getCanaryString()
			{
				return mcts.toString();
			}
		};
		benchmark("MCTS " + state, new Benchmarkable()
		{
			@Override
			public int run()
			{
				try {
					for (int i=0; i<iters; i=Math.min(i+20000, iters))
					{
						int min = Math.min(iters-i, 20000);
						if (multiThreaded)
							mcts.iterateMultiThreaded(state, min, 60);
						else
							mcts.iterate(state, min);
						System.out.println("[" + MiscUtils.format("%8d", i) + "] " + mcts + " Best: " + mcts.getBestPath());
					}
					return iters;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
		hd.destroy();
		System.out.println(mcts);
		if (mcts.getTranspositionTable() != null)
			System.out.println(mcts.getTranspositionTable());
		mcts.dumpTree(System.out, 120);
		Node root = mcts.getRoot();
		MCTS.Node best = mcts.getBestPath();
		for (Node child : root.getAllChildren())
		{
			System.out.println(child + "\t" + child.toDesc());
		}
		System.out.println("BEST: " + best + " " + best.toFullString());
		System.out.println();
		return mcts;
	}

	public Chess getChessPosition(String epdstring)
	{
		Chess state = new Chess();
		EPDParser epd = new EPDParser(state);
		epd.parse(epdstring);
		String bm = epd.getBestMoveString();
		System.out.println(epdstring + "/" + bm);
		// TODO: handle semicolons and other formats
		if (bm != null && bm.startsWith("bm "))
			state.epdBestMove = bm.split("\\s+")[1];
		state.dump();
		return state;
	}
	
	protected long compete(GameState<?> game, AISolver[] players)
	{
		game = game.copy();
		int turn = 0;
		long[] times = new long[game.getNumPlayers()];
		competeMoves = new ArrayList<Line<?>>();
		
		while (!game.isGameOver() && turn < maxTurns)
		{
			turn++;
			int currentPlayer = game.getCurrentPlayer();
			AIDecider decider = players[currentPlayer].newSolver(game);
			Thread[] threads = players[currentPlayer].getThreads();
			long t1 = currentCPUTimeMillis(threads);
			try
			{
				game.playTurn(decider);
			} catch (GameOverException e)
			{
				game = e.getFinalState();
				assertTrue(game.isGameOver());
			} catch (MoveFailedException e)
			{
				e.printStackTrace();
				fail(e.toString());
			}
			long t2 = currentCPUTimeMillis(threads);
			times[currentPlayer] += (t2-t1);
			Line<?> move = decider.getCompleteMove();
			competeMoves.add(move);
			game.dump();
		}
		long winners = game.getWinners();
		this.lastNumCompeteTurns = turn;
		for (int i=0; i<game.getNumPlayers(); i++)
		{
			System.out.println(game.playerToString(i) + "\t" + MiscUtils.format("%3.1f", times[i]/1000.0f) + "s elapsed");
		}
		System.out.println("*** GAME OVER, turn " + turn + ", winners = " + BitSet.valueOf(new long[] { winners }));
		return winners;
	}
	
	protected long competeMultipleRounds(GameState<?> game, AISolver[] players, int numRounds)
	{
		Scores rounds = new Scores(game.getNumPlayers());
		for (int round=0; round<numRounds; round++)
		{
			if (numRounds > 1)
			{
				System.out.println();
				System.out.println("*** ROUND " + (round+1));
			}
			System.out.println();
			long mask = compete(game, players);
			for (int i=0; i<64; i++)
				if ((mask & (1L<<i)) != 0)
					rounds.addPlayerScore(i, 1);
		}
		System.out.println("*** RESULTS");
		rounds.dump();
		System.out.println("***");
		return rounds.getWinners();
	}

	protected void verifyBestChessMove(Chess game, Line<?> best)
	{
		int srci = best.getMoveAtDepth(1).getMoveIndex();
		int desti = best.getMoveAtDepth(2).getMoveIndex();
		assertEquals(game.epdBestMove, game.getMoveString(srci, desti));
	}

	// use to keep track of multiple-round scores
	public class Scores extends GameState<Scores>
	{
		public Scores(int numPlayers)
		{
			super(numPlayers);
		}

		@Override
		public MoveResult playTurn(Decider decider) throws MoveFailedException
		{
			return null;
		}

		@Override
		public void dump(PrintStream out)
		{
		}
		
		@Override
		public int getWinningScore()
		{
			return 1;
		}
	}

	//

	public void analyze(GameState<?> game, int maxLevel, int iters)
	{
		final Analyzer sim = new Analyzer(game, maxLevel);
		benchmark(game.toString(), iters, new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					sim.simulate();
				} catch (MoveFailedException e)
				{
					throw new RuntimeException(e);
				}
			}
		});
		sim.dump();
		System.out.println();
		assertEquals(0, sim.totalCollisionsFound);
	}

}
