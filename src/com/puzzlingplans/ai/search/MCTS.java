package com.puzzlingplans.ai.search;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameOverException;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.RandomChoice;
import com.puzzlingplans.ai.search.TranspositionTable.Entry;
import com.puzzlingplans.ai.search.TranspositionTable.EntryType;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.MiscUtils;
import com.puzzlingplans.ai.util.RandomXorshift128;
import com.puzzlingplans.ai.util.ThreadUtils;

public class MCTS extends SearchAlgorithmBase implements AISolver
{
	public class Node extends Line<Node> implements Comparator<Node>
	{
		private Map<Integer, Node> map = new HashMap<Integer, Node>();
		private SortedSet<Node> queue = new TreeSet<Node>(this);
		private long allMoves;
		private int factor;				// 1 = us, -1 = them, 0 = chance

		// TODO: have to work on terminology -- visited, invalid, etc
		private int numVisits;
		private long visitedMoves;
		private long invalidMoves; // hit from multiple threads
		private long solvedMoves; // hit from multiple threads
		private double value = Double.NEGATIVE_INFINITY;
		private double totalScore;
		private double totalWeight;
		private double logNumVisits;
		Set<Node> transpositions;		// shared set of transpositions
		// TODO? GameState intermediateState;
		private int pessimisticBound = Integer.MIN_VALUE;
		private int optimisticBound = Integer.MAX_VALUE;
		
		//

		public Node(Node parent, int moveIndex, int factor, long moveMask)
		{
			super(parent, moveIndex);
			this.factor = factor;
			this.allMoves = moveMask;
		}
		
		public void addScore(double score, double weight)
		{
			Node parent = getParent();
			if (parent != null)
			{
				parent.addScore(score, weight);
			}
			if (hasOption(Option.IncrementalUpdate) && parent != null)
			{
				totalScore += (score - totalScore) * weight / parent.numVisits;
				totalWeight = 1;
			} else {
				totalScore += score * weight;
				totalWeight += weight;
			}
			numVisits++;
			logNumVisits = Math.log(numVisits);
			if (parent != null)
			{
				recalc();
				assert(parent.queue.size() == parent.map.size());
				// recalc() child with least value so it doesn't get stuck
				// TODO: maybe another fn that doesn't require recalculation
				Node last = parent.queue.last();
				if (last != this)
				{
					last.recalc();
				}
			}
			assert(totalWeight > 0);
			assert(!Double.isNaN(totalScore));
		}

		private void recalc()
		{
			Node parent = getParent();
			parent.queue.remove(this);
			double winrate = getModifiedWinRate();
			double explorerate = explorationConstant * Math.sqrt(parent.logNumVisits / numVisits);
			// if this node has been solved, we can just take the value
			value = winrate + explorerate;
			// don't bother with solved nodes in the queue
			if (isSolved())
				value = Double.NEGATIVE_INFINITY;
			parent.queue.add(this);
		}

		public double getAbsoluteWinRate()
		{
			return scoreToRatio(getAvgScore());
		}

		public double getModifiedWinRate()
		{
			return scoreToRatio(getAvgScore() * factor);
		}
		
		private double scoreToRatio(double d)
		{
			return (double)d / (GameState.WIN - GameState.LOSE) + 0.5;
		}

		public double getAvgScore()
		{
			return totalScore / totalWeight;
		}

		public int numVisits()
		{
			return numVisits;
		}

		public boolean hasChildren()
		{
			return !map.isEmpty();
		}

		public int getChildCount()
		{
			return map.size();
		}
		
		public Collection<Node> getSearchableChildren()
		{
			return Collections.unmodifiableCollection(queue);
		}

		public Collection<Node> getAllChildren()
		{
			return Collections.unmodifiableCollection(queue); // TODO: future version may use map.values()
		}

		public Node getBestUnexploredNode()
		{
			assert (hasChildren());
			Node node = queue.first();
			if (node.isSolved())
			{
				Iterator<Node> it = queue.iterator();
				it.next();
				while (it.hasNext())
				{
					Node n = it.next();
					if (!n.isSolved())
						return n;
				}
				return null;
			} else 
				return node;
		}
		
		public Node getMostVisitedNode()
		{
			assert(hasChildren());
			Node bestnode = null;
			int bestval = Integer.MIN_VALUE;
			for (Node node : map.values())
			{
				if (node.numVisits > bestval)
				{
					bestval = node.numVisits;
					bestnode = node;
				}
			}
			return bestnode;
		}

		public Node getMostWinningNode()
		{
			// TODO: assertion fires sometimes, combine with robustness? (aka visit count)
			Node bestnode = null;
			double bestval = Double.NEGATIVE_INFINITY;
			for (Node node : map.values())
			{
				double winrate = node.getModifiedWinRate();
				if (winrate > bestval)
				{
					bestval = winrate;
					bestnode = node;
				}
			}
			return bestnode;
		}

		public Node getMostRobustNode()
		{
			// TODO? what if it's a solved node?
			Node n = getMostWinningNode();
			return (n != null && n.hasChildren()) ? n : getMostVisitedNode();
		}

		public Node createOrGet(int index, int factor, long moveMask)
		{
			Node newnode = map.get(index);
			if (newnode != null)
			{
				assert(newnode.allMoves == moveMask);
				return newnode;
			}

			newnode = new Node(this, index, factor, moveMask);
			map.put(index, newnode);
			assert((visitedMoves & (1L<<index)) == 0);
			visitedMoves |= 1L << index;
			stats.totalNodeCount++;
			if (map.size() > 1)
				stats.totalLeafCount++;
			if (debug)
				prdebug(newnode, "Created " + stats.totalNodeCount + "th node");
			return newnode;
		}

		public String toDesc()
		{
			return "(#" + getMoveIndex() + " " + (float)value + " " + numVisits + " " + (float)getAbsoluteWinRate()*100 + "%" 
				+ " " + getChildCount() + "+" + BitUtils.countBits(invalidMoves|solvedMoves) + "<=" + BitUtils.countBits(allMoves)
				+ " " + factor
				+ " " + score2ratiostr(pessimisticBound) + "/" + score2ratiostr(optimisticBound)
				+ (isSolved() ? " solved" : "")
				+ (isComplete() ? " complete" : "")
				+ ")";
		}

		private String score2ratiostr(int score)
		{
			if (score == Integer.MIN_VALUE)
				return "-Inf";
			if (score == Integer.MAX_VALUE)
				return "+Inf";
			else
				return MiscUtils.format("%1.3f", score * 0.5 / GameState.WIN + 0.5);
		}

		public String toFullString()
		{
			return (getParent() != null && getParent().getParent() != null ? getParent().toFullString() : "") + toDesc();
		}
		
		@Override
		public int compare(Node o1, Node o2)
		{
			int signum = (int) Math.signum(o2.value - o1.value);
			if (signum == 0)
				signum = (int) Math.signum(o2.getMoveIndex() - o1.getMoveIndex());
			return signum;
		}

		public Node getChildWithIndex(int index)
		{
			return map.get(index);
		}

		public Node getPredeterminedNode()
		{
			// if initial choices set, follow them down the beginning of the move path
			// TODO: what if not there?
			if (initialChoices != null && getLevel() < initialChoices.length)
			{
				int index = initialChoices[getLevel()];
				// moves can be marked invalid by other threads, we may get unlucky
				//assert(!isInvalidIndex(index));
				return getChildWithIndex(index);
			}
			else
				return null;
		}

		public void setInvalidIndex(int index)
		{
			long mask = 1L << index;
			assert ((mask & allMoves) != 0);
			invalidMoves |= mask;
			if (debug)
				prdebug(this, "setInvalidIndex " + index);
			// TODO: if all moves are invalid, set node to invalid in parent?
			if (invalidMoves == allMoves && getParent() != null)
			{
				//getParent().setInvalidIndex(getMoveIndex());
				assert(false);
			}
		}

		private boolean isInvalidIndex(int index)
		{
			return ((1L << index) & invalidMoves) != 0;
		}
		
		public void setSolvedIndex(int index)
		{
			long mask = 1L << index;
			assert ((mask & allMoves) != 0);
			solvedMoves |= mask;
			if (debug)
				prdebug(this, "setSolvedIndex " + index);
			// if all moves are invalid, set node to invalid in parent
			if (isSolved() && getParent() != null)
			{
				getParent().setSolvedIndex(getMoveIndex());
			}
		}

		// return true if all non-invalid children were visited
		public boolean isComplete()
		{
			assert((allMoves | visitedMoves | invalidMoves) == allMoves);
			return allMoves == (visitedMoves | invalidMoves);
		}

		public void setSolved(int score)
		{
			if (hasOption(Option.SolveSubtrees))
			{
				//assert(allMoves != 0);
				solvedMoves = allMoves;
				pessimisticBound = optimisticBound = score;
				
				// update index, bounds
				if (getParent() != null)
				{
					getParent().setSolvedIndex(getMoveIndex());
					// TODO: chance nodes?
					getParent().updatePessimisticBounds(this);
					getParent().updateOptimisticBounds(this);
				}
			} else {
				addScore(0, 0);
			}
		}

		private void updatePessimisticBounds(Node child)
		{
			int oldPess = pessimisticBound;
			if (oldPess < child.pessimisticBound)
			{
				if (child.factor > 0) // max node?
				{
					pessimisticBound = child.pessimisticBound;
					if (getParent() != null)
						getParent().updatePessimisticBounds(this);
				}
				else if (child.factor < 0 && isComplete()) // min node?
				{
					pessimisticBound = GameState.WIN;
					for (Node n : getAllChildren())
						if (n.pessimisticBound < pessimisticBound)
							pessimisticBound = n.pessimisticBound;
					if (oldPess < pessimisticBound && getParent() != null)
						getParent().updatePessimisticBounds(this);
				}
				prune();
			}
		}

		private void updateOptimisticBounds(Node child)
		{
			int oldOpti = optimisticBound;
			if (oldOpti > child.optimisticBound)
			{
				if (child.factor > 0 && isComplete()) // max node?
				{
					optimisticBound = GameState.LOSE;
					for (Node n : getAllChildren())
						if (n.optimisticBound > optimisticBound)
							optimisticBound = n.optimisticBound;
					if (oldOpti > optimisticBound && getParent() != null)
						getParent().updateOptimisticBounds(this);
				}
				else if (child.factor < 0) // min node?
				{
					optimisticBound = child.optimisticBound;
					if (getParent() != null)
						getParent().updateOptimisticBounds(this);
				}
				prune();
			}
		}

		private void prune()
		{
			if (optimisticBound <= pessimisticBound && hasOption(Option.PruneSubtrees))
			{
				solvedMoves = allMoves;
				stats.totalPruned++;
			}
			if (debug)
				prdebug(this, "bounds = " + score2ratiostr(pessimisticBound) + "/" + score2ratiostr(optimisticBound) + " " + isSolved());
		}

		public long getAllMoves()
		{
			return allMoves;
		}

		public boolean isSolved()
		{
			return (solvedMoves | invalidMoves) == allMoves && hasOption(Option.SolveSubtrees);
		}

		public void dumpToLevel(int maxl)
		{
			if (getLevel() <= maxl)
			{
				System.out.print(toString());
				System.out.print("        ");
				System.out.print(toDesc());
				if (getLevel() < maxl)
				{
					System.out.println();
					for (Node n : getAllChildren())
					{
						n.dumpToLevel(maxl);
					}
				} else {
					System.out.println(" ...");
				}
			}
		}
	}
	
	//

	private Node root;
	private int levelSlop;
	private long randomSeed;
	private boolean debug;

	private double explorationConstant = 1.4142135623;
	private boolean earlyExit;
	private int shallowMinimaxDepth;
	private int winningScore = GameState.WIN - GameState.WINLOSE_THRESHOLD;
	private int losingScore = GameState.LOSE + GameState.WINLOSE_THRESHOLD;
	private MoveMaskHash goodMoves;
	private int goodMoveProb;
	private double earlyWinBonus;
	private int options;
	
	public enum Option {
		AccumulateGoodMoves,
		RemoveBadMoves,
		UseAbsoluteScores,
		OnlyRetainLeafScores,
		SolveSubtrees,
		PruneSubtrees,
		IncrementalUpdate, 
	};
	
	private TranspositionTable transpositionTable;

	private int[] initialChoices;	// limit search from root

	// TODO: share with simulator?
	private Random masterRandom;
	
	public class Stats
	{
		int totalNodeCount;
		int totalLeafCount;
		int totalPlays;
		int totalWins;
		int totalLosses;
		int totalCompleted;
		int totalMoves;
		int totalGoodMoves;
		int totalPruned;
	}
	
	Stats stats = new Stats();

	//

	public MCTS(int maxLevel)
	{
		setMaxLevel(maxLevel);
		this.levelSlop = 20;
		this.randomSeed = 0;
		setOption(Option.SolveSubtrees, true);
		//setOption(Option.PruneSubtrees, true);
		setOption(Option.IncrementalUpdate, true);
		reset();
	}

	public MCTS(MCTS mcts)
	{
		// TODO?
		this(mcts.maxLevel);
		
		this.options = mcts.options;
		this.earlyExit = mcts.earlyExit;
		this.earlyWinBonus = mcts.earlyWinBonus;
		this.explorationConstant = mcts.explorationConstant;
		this.shallowMinimaxDepth = mcts.shallowMinimaxDepth;
		this.winningScore = mcts.winningScore;
		this.losingScore = mcts.losingScore;
		this.goodMoves = mcts.goodMoves;
		this.goodMoveProb = mcts.goodMoveProb;
		// got to clear trans table because we are going to create new nodes (TODO: what if shared between threads?)
		this.transpositionTable = mcts.transpositionTable;
		if (transpositionTable != null)
			transpositionTable.clear();
	}

	@Override
	public void reset()
	{
		this.root = null;
		setRandomSeed(randomSeed);
	}

	public void setRandomSeed(long seed)
	{
		this.randomSeed = seed;
		this.masterRandom = new RandomXorshift128(seed);
	}
	
	public void setExplorationConstant(double c)
	{
		this.explorationConstant = c;
	}
	
	public void setEarlyWinBonus(double bonus)
	{
		this.earlyWinBonus = bonus;
	}
	
	public void setMinimaxSearchDepth(int depth)
	{
		assert(depth>=0);
		this.shallowMinimaxDepth = depth;
		this.earlyExit = depth > 0;
	}

	public void setEarlyExitScores(int win, int lose)
	{
		assert(win>0 && lose<0);
		this.winningScore = win;
		this.losingScore = lose;
		this.earlyExit = win < GameState.WIN || lose > GameState.LOSE;
	}
	
	// "good moves" are those that improve the score of the player making them
	public void setGoodMoveProbability(int percent)
	{
		this.goodMoveProb = percent;
		this.goodMoves = (percent>0) ? new MoveMaskHash() : null;
	}
	
	public void setTranspositionTableSize(int numEntriesLog2)
	{
		// TODO: fix how transposition tables work, or remove
		/*
		if (numEntriesLog2 > 0)
		{
			this.transpositionTable = new TranspositionTable(numEntriesLog2, 8);
		}
		else
			this.transpositionTable = null;
		*/
	}

	public Simulator newSimulator(GameState<?> state, int iters)
	{
		return new Simulator(state, iters, maxLevel);
	}
	
	// TODO: what if state changes between runs? should disallow or set state in cons
	
	public void iterate(GameState<?> state, int iters) throws InterruptedException
	{
		Simulator sim = newSimulator(state, iters);
		sim.run();
	}

	public boolean useMultipleThreads = true;

	public void iterateMultiThreaded(GameState<?> state, int numIters, int timeoutSecs) throws ExecutionException, InterruptedException
	{
		Simulator[] sims = new Simulator[ThreadUtils.numThreadsPerPool()];
		Runnable[] tasks = new Runnable[sims.length];
		AtomicInteger iters = new AtomicInteger(numIters);
		for (int i = 0; i < sims.length; i++)
		{
			sims[i] = newSimulator(state, 1);
			tasks[i] = new SimulateTask(sims[i], iters);
		}
		ThreadUtils.submitAndWait(tasks);
		if (debug)
			for (int i=0; i<sims.length; i++)
				prdebug(root, "Simulator " + i + " " + sims[i].timeInSelect + " " + sims[i].timeInSimulate + " " + sims[i].timeInBackprop);
	}

	// TODO: merge w/ MCRAVE
	class SimulateTask implements Runnable
	{
		private Simulator sim;
		private AtomicInteger iters;

		public SimulateTask(Simulator sim, AtomicInteger iters)
		{
			this.sim = sim;
			this.iters = iters;
		}

		@Override
		public void run()
		{
			try
			{
				while (iters.decrementAndGet() >= 0)
				{
					if (sim.execute() == null)
						iters.set(-1);
				}
			} catch (Throwable e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}
	//

	private static final float EPSILON_PROBABILITY = 1e-6f;

	public class Simulator implements Runnable
	{
		private GameState<?> initialState;
		private int iters;
		private int maxLevel;
		
		private Random rnd;
		private int[] indices;
		private float[] probabilities;
		private int[] playersPerLevel;
		private long[] allMovesPerLevel;
		private int replayLevel;
		private Node lastReplayNode;
		private int lastReplayPlayer;
		private int seekingPlayer;
		protected int currentLevel;
		
		private long timeInSelect;
		private long timeInSimulate;
		private long timeInBackprop;
		private boolean running;
		
		//

		public Simulator(GameState<?> state, int iters, int maxLevel)
		{
			this.initialState = state;
			this.iters = iters;
			this.maxLevel = maxLevel;
			this.indices = new int[maxLevel + levelSlop];
			this.probabilities = new float[maxLevel + levelSlop];
			this.playersPerLevel = new int[maxLevel + levelSlop];
			this.allMovesPerLevel = new long[maxLevel + levelSlop];
			this.rnd = new RandomXorshift128(masterRandom.nextLong());
		}

		public void run()
		{
			assert(!running);
			running = true;
			for (int i=0; i<iters; i++)
			{
				if (execute() == null)
					break;
			}
		}
		
		public GameState<?> execute()
		{
			if (root != null && root.isSolved())
				return null; // TODO: is this enough? should notify other levels?
			
			GameState<?> state = initialState.copy();
			// disable hash updates, for now
			if (state instanceof HashedPosition)
				((HashedPosition) state).enableHashing(false);
			// lock the main tree and select
			long t1 = System.currentTimeMillis();
			synchronized (MCTS.this)
			{
				if (!select() && lastReplayNode == null)
					return null;
			}
			// TODO: have copies of intermediate states in strategic places
			long t2 = System.currentTimeMillis();
			try
			{
				simulate(state);
			} catch (MoveFailedException e)
			{
				// TODO: should assume it's a draw? not always
				throw new RuntimeException(e);
			}
			// lock the main tree and backprop
			long t3 = System.currentTimeMillis();
			synchronized (MCTS.this)
			{
				backpropagate(state);
			}
			long t4 = System.currentTimeMillis();
			timeInSelect += t2-t1;
			timeInSimulate += t3-t2;
			timeInBackprop += t4-t3;
			return state;
		}

		boolean select()
		{
			replayLevel = 0;
			Node node = root;
			if (node == null)
			{
				return true;
			}
			if (debug)
				prdebug(node, "select()");
			while (node.hasChildren())
			{
				//debug = (node+"").startsWith(":2:2:1:");
				assert(node.numVisits > 0);
				if (!node.isComplete())
				{
					if (debug)
						prdebug(node,
								"select() node incomplete: " + Long.toHexString(node.allMoves) + " "
										+ Long.toHexString(node.visitedMoves) + " "
										+ Long.toHexString(node.invalidMoves));
					break;
				}
				if (debug)
					prdebug(node, "select() " + replayLevel + " = " + node.toDesc());
				Node n2 = node.getPredeterminedNode();
				Node next = n2 != null ? n2 : node.getBestUnexploredNode();
				if (next == null && replayLevel == 0)
				{
					if (debug)
						prdebug(null, "select(): all nodes exhausted");
					lastReplayNode = null;
					return false;
				}
				// TODO: these fire off in TestMCTSCompetition.testDice and testGoSmall due to race conditions(?)
				if (next == null || next.isSolved())
				{
					if (debug)
						prdebug(null, "select(): invalid node " + next);
					lastReplayNode = node; // TODO: want to restart, not exit
					return false;
				}
				assert(next != null);
				assert(!next.isSolved());
				assert(!node.isInvalidIndex(next.getMoveIndex()));
				node = next;
				allMovesPerLevel[replayLevel] = node.getAllMoves();
				indices[replayLevel] = node.getMoveIndex();
				replayLevel++;
			}
			lastReplayNode = node;
			if (debug)
				prdebug(node, "select() to level " + replayLevel + " = " + node.toDesc());
			assert(lastReplayNode != null);
			return true;
		}

		void simulate(GameState<?> state) throws MoveFailedException
		{
			if (debug)
				prdebug(lastReplayNode, "simulate()");
			this.seekingPlayer = state.getCurrentPlayer();
			this.lastReplayPlayer = -1;
			this.currentLevel = 0;
			//HashedPosition hashable = transpositionTable != null && (state instanceof HashedPosition) ? (HashedPosition)state : null;
			while (!state.isGameOver() && currentLevel < maxLevel)
			{
				int turnPlayer = state.getCurrentPlayer();
				if (debug)
				{
					if (currentLevel < replayLevel)
						prdebug(lastReplayNode, "simulate() " + currentLevel + "/" + maxLevel + " = #" + indices[currentLevel]);
					else
						prdebug(lastReplayNode, "simulate() " + currentLevel + "/" + maxLevel);
				}
				if (currentLevel <= replayLevel)
					lastReplayPlayer = turnPlayer;

				int level = currentLevel;
				
				// record transpositions at leaf node(s)
				/*
				if (hashable != null && level == replayLevel)
				{
					recordTranspositions(hashable, turnPlayer, level);
				}
				*/
				//int oldscore = state.getModifiedScore(turnPlayer);
				MoveResult result = state.playTurn(compositeDecider);
				Arrays.fill(playersPerLevel, level, currentLevel, turnPlayer);
				if (result != MoveResult.Ok)
				{
					if (debug)
						prdebug(lastReplayNode, "Result " + result);
					break;
				}
				
				// make sure we either have progress or game is over
				if (currentLevel <= level && !state.isGameOver())
				{
					throw new MoveFailedException("No choices made in playTurn()");
				}
				if (currentLevel >= replayLevel)
				{
					if (earlyExit)
					{
						if (shallowMinimaxDepth > 0)
							break;
						else if (Math.abs(getFinalScore(state)) >= state.getWinningScore())
							break;
					}
				}
			}
			// make sure that if the tree runs out that it ends on a turn
			if (state.isGameOver() && replayLevel == currentLevel && lastReplayNode != null)
			{
				assert(lastReplayNode.isEndOfTurn());
			}
			playersPerLevel[currentLevel] = -1;
			allMovesPerLevel[currentLevel] = 0;
			if (debug)
				prdebug(lastReplayNode, "simulate() from " + replayLevel + " to " + currentLevel + " score = " + state.getModifiedScore(seekingPlayer));
		}

		private void recordTranspositions(HashedPosition hashable, int turnPlayer, int level)
		{
			long hash = hashable.hashFor(seekingPlayer);
			Entry entry = transpositionTable.getEntryAt(hash, turnPlayer);
			if (entry != null && entry.line != lastReplayNode && entry.getLevel() == level)
			{
				Node trans = (Node) entry.line;
				Set<Node> set = trans.transpositions;
				if (set == null)
					set = lastReplayNode.transpositions;
				if (set == null)
					set = new HashSet<Node>();
				trans.transpositions = lastReplayNode.transpositions = set;
				set.add(trans);
				set.add(lastReplayNode);
				if (debug)
					prdebug(lastReplayNode, "simulate() transposition found: " +  hash + " " + entry + " " + set);
			}
			else if (entry == null)
			{
				entry = transpositionTable.newEntry(hash, turnPlayer, level, 0, EntryType.EXACT);
				if (entry != null)
					entry.line = lastReplayNode;
			}
		}

		private long goodMoveKeyForLevel(int i)
		{
			return BitUtils.rotl(allMovesPerLevel[i], indices[i-1]) ^ (1L<<indices[i-1]); // ^ masksPerLevel[i-1]; // 
		}

		boolean backpropagate(GameState<?> state)
		{
			// create root and set mask for root level, if need be
			if (root == null)
			{
				long allMoves = allMovesPerLevel[0];
				root = new Node(null, -1, 1, allMoves);
				if (allMoves == 0)
				{
					root.setSolved(getFinalScore(state));
				}
				stats.totalLeafCount++;
				// TODO: set chance?
				if (debug)
					prdebug(root, "backpropagate(): Created new root " + root.toDesc());
				// exit, we'll take another pass next time
				return false;
			}
			// if we didn't yet have a root last time, just exit
			Node previousLeaf = lastReplayNode;
			if (previousLeaf == null)
				return false;
			
			int score = getFinalScore(state);
			// did we not make it out of the tree?
			if (replayLevel == currentLevel)
			{
				if (debug)
					prdebug(previousLeaf, "backpropagate(): Reached level " + replayLevel + "; score = " + score);
				previousLeaf.setSolved(score);
				return false;
			}
			assert (replayLevel < currentLevel);
			stats.totalPlays++;
			boolean won = score >= state.getWinningScore();
			boolean lost = score <= state.getLosingScore();
			if (won)
				stats.totalWins++;
			if (lost)
				stats.totalLosses++;
			if (state.isGameOver())
				stats.totalCompleted++;

			// update good moves if win/lose
			if (goodMoves != null && (won | lost) && replayLevel > 0 && currentLevel > replayLevel)
			{
				updateGoodMoves(won);
			}
			int factor = (previousLeaf != null && previousLeaf.isChanceNode()) ? 0 : (lastReplayPlayer == seekingPlayer) ? 1 : -1;
			// earlyWinBonus = bonus if search ended early
			double weight = Math.round(1 + earlyWinBonus * (maxLevel - currentLevel + 0.0) / maxLevel);
			// take chance node probability into account
			// TODO: asymmetric trees might skew the weights
			float prob = probabilities[replayLevel];
			if (prob > EPSILON_PROBABILITY)
			{
				weight *= prob;
			}
			// TODO: this is the only way testDice() passes ... is there a better way?
			//if (!(won|lost))
				//score = (long) lastReplayNode.getAvgScore();
			// create new node
			int moveIndex = indices[replayLevel];
			long nextMask = allMovesPerLevel[replayLevel+1];
			if (debug)
				prdebug(previousLeaf, "Creating " + moveIndex + " next 0x" + Long.toHexString(nextMask) + " level " + replayLevel);
			// save this leaf's previous scores (for OnlyRetainLeafScores)
			double prevLeafScore = previousLeaf.totalScore;
			double prevLeafWeight = previousLeaf.totalWeight;
			boolean hadChildren = previousLeaf.hasChildren();
			// create leaf if neccessary, add score
			Node node = previousLeaf.createOrGet(moveIndex, factor, nextMask);
			node.addScore(score, weight);
			// kill scores of previous leaf to maintain accuracy (TODO: might not be accurate)
			if (hasOption(Option.OnlyRetainLeafScores)
					&& !hasOption(Option.IncrementalUpdate)
					&& !hadChildren
					&& prevLeafWeight > 0)
			{
				previousLeaf.addScore(prevLeafScore/prevLeafWeight, -prevLeafWeight);
				if (debug)
					prdebug(previousLeaf, "Removing score " + prevLeafScore/prevLeafWeight + ", now " + previousLeaf.getAvgScore());
			}
			// set end of turn flag
			{
				int p1 = playersPerLevel[replayLevel];
				int p2 = playersPerLevel[replayLevel+1];
				if (p1 != p2)
					node.setIsEndOfTurn();
				if (debug && node.isEndOfTurn())
					prdebug(node, "backpropagate(): End of turn, P" + p1 + " -> P" + p2);
			}
 			// make invalid if all moves == 0
			if (nextMask == 0)
			{
				// is it a proven win or loss?
				node.setSolved(score);
				if (debug)
					prdebug(node, "backpropagate(): Solved node, game over " + state.isGameOver());
			}
			if (debug)
				prdebug(node, "backpropagate(): Updated " + node.toDesc());
			return true;
		}

		private void updateGoodMoves(boolean win)
		{
			for (int i=replayLevel; i<currentLevel; i++)
			{
				// TODO: option to use hashed value?
				long goodMoveKey = goodMoveKeyForLevel(i);
				if ((playersPerLevel[i] != seekingPlayer) ^ win)
				{
					// TODO: add or replace? replaces solves BK2
					if (hasOption(Option.AccumulateGoodMoves))
						goodMoves.addIndex(goodMoveKey, indices[i]);
					else
						goodMoves.replaceIndex(goodMoveKey, indices[i]);
				} else {
					// player lost, kill all moves
					if (hasOption(Option.RemoveBadMoves))
						goodMoves.removeAllIndices(goodMoveKey);
						//goodMoves.removeIndex(goodMoveKey, 0);
				}
			}
		}

		private int getFinalScore(GameState<?> state)
		{
			if (shallowMinimaxDepth > 0)
			{
				// TODO: we should be able to repeat call minimax and use TT
				Minimax mmax = new Minimax(state);
				mmax.setMaxLevel(shallowMinimaxDepth);
				return mmax.solveFromPlayer(seekingPlayer);
			} else {
				// TODO: rethink this absolute/relative score thing
				if (hasOption(Option.UseAbsoluteScores))
					return state.getAbsoluteScore(seekingPlayer);
				else
				{
					int score = state.getModifiedScore(seekingPlayer);
					if (score > state.getWinningScore())
						return GameState.WIN;
					else if (score < state.getLosingScore())
						return GameState.LOSE;
					else
						return score;
				}
			}
		}
		
		//

		Decider compositeDecider = new Decider()
		{
			@Override
			public MoveResult choose(Choice choice) throws MoveFailedException
			{
				Decider decider = currentLevel < replayLevel ? replayDecider : randomDecider;
				return decider.choose(choice);
			}

			@Override
			public int getSeekingPlayer()
			{
				return seekingPlayer;
			}
		};

		Decider replayDecider = new Decider()
		{
			@Override
			public MoveResult choose(Choice choice) throws MoveFailedException
			{
				int index = indices[currentLevel++];
				MoveResult result = choice.choose(index);
				if (result != MoveResult.Ok)
				{
					// descended a tree where all nodes marked invalid/solved
					// this happens if we have no valid moves and a low maxlevel (testSimpleTwoLevelOneValidMove)
					// and could also happen if we have multiple threads involved (testDice)
					lastReplayNode.getMoveAtDepth(currentLevel-1).setInvalidIndex(index);
					//throw new MoveFailedException("Result " + result + " at " + lastReplayNode + " level " + (currentLevel-1));
				}
				return result;
			}

			@Override
			public int getSeekingPlayer()
			{
				return seekingPlayer;
			}
		};

		Decider randomDecider = new Decider()
		{

			@Override
			public MoveResult choose(Choice choice) throws MoveFailedException
			{
				// TODO: speedup this method?
				int level = currentLevel;
				assert(level >= replayLevel);
				long mask = choice.getPotentialMoves(); // TODO: cache?
				allMovesPerLevel[level] = mask;
				boolean chance = choice instanceof RandomChoice;
				// good-move simulation policy - chance nodes do not participate
				long extra = 0;
				if (goodMoves != null && !chance)
				{
					if (level > 0 && rnd.nextInt(100) < goodMoveProb)
					{
						long best = goodMoves.getForMask(goodMoveKeyForLevel(level));
						if ((best & mask) != 0)
						{
							extra = mask & ~best;
							mask &= best;
						}
					}
				}
				// are we at a leaf of the tree?
				Node replayNode = level == replayLevel ? lastReplayNode : null;
				if (replayNode != null)
				{
					// this node may have returned NoMoves or made no progress in simulate()
					if (replayNode.getAllMoves() == 0)
						return MoveResult.NoMoves;
					
					//if (!(replayNode.getAllMoves() == allMovesPerLevel[level])) prdebug(replayNode, replayNode.getAllMoves()+" "+allMovesPerLevel[level]+" " + replayNode.isSolved);
					assert(replayNode.getAllMoves() == allMovesPerLevel[level]);
					// set whether it's a chance node
					if (chance)
						replayNode.setIsChanceNode();
					// if some indices are invalid, don't bother hitting them again
					mask &= ~replayNode.invalidMoves;
					extra &= ~replayNode.invalidMoves;
					if (mask == 0)
						mask = extra;
				}
				// choose moves until no more left
				while (mask != 0)
				{
					int bit = chooseRandomBit(mask);
					assert(((1L<<bit) & mask) != 0);
					assert(currentLevel == level);
					int index = bit;
					// save probability for this choice
					probabilities[level] = chance ? ((RandomChoice)choice).getProbability(index) : -1;
					// make the move
					MoveResult result = choose(choice, index);
					if (result == MoveResult.Ok)
					{
						// save good moves stats
						if (!chance)
						{
							stats.totalMoves++;
							if (extra != 0)
								stats.totalGoodMoves++;
						}
						assert(probabilities[level] != 0);
						// save index of this choice
						indices[level] = index;
						return MoveResult.Ok;
					}
					if (debug)
						prdebug(lastReplayNode, "chooseMask() failed on index #" + index + ", result " + result);
					// if we get an invalid result on the leaf node,
					// mark this index as invalid within that node
					// TODO: race conditions?
					if (replayNode != null)
					{
						replayNode.setInvalidIndex(index);
					}
					// don't visit this choice again
					mask &= ~(1L<<bit);
					if (mask == 0)
					{
						mask = extra;
						extra = 0;
					}
				}
				return MoveResult.NoMoves;
			}

			private MoveResult choose(Choice choice, int index) throws MoveFailedException
			{
				currentLevel++;
				MoveResult result = choice.choose(index);
				if (result != MoveResult.Ok)
					currentLevel--;
				return result;
			}

			@Override
			public int getSeekingPlayer()
			{
				return seekingPlayer;
			}
		};

		public int chooseRandomBit(long mask)
		{
			return BitUtils.choose_bit(mask, rnd);
		}
	}
	
	//

	public void setDebug(boolean b)
	{
		this.debug = b;
	}

	public final boolean hasOption(Option option)
	{
		return ((options & (1<<option.ordinal())) != 0);
	}
	
	public void setOption(Option option, boolean set)
	{
		if (set)
			options |= (1<<option.ordinal());
		else
			options &= ~(1<<option.ordinal());
	}

	public Node getRoot()
	{
		return root;
	}

	public Node getBestPath()
	{
		Node n = root;
		while (n.hasChildren())
		{
			Node n2 = n.getPredeterminedNode();
			if (n2 == null)
				n2 = n.getMostRobustNode();
			if (n2 == null)
				return n;
			n = n2;
		}
		return n;
	}
	
	public String toString()
	{
		return stats.totalPlays + " plays, " 
				+ MiscUtils.format("%3.1f", stats.totalCompleted * 100.0 / stats.totalPlays) + "% completed, "
				+ MiscUtils.format("%3.1f", stats.totalWins * 100.0 / stats.totalCompleted) + "% wins, "
				//+ MiscUtils.format("%3.1f", totalLosses * 100.0 / totalCompleted) + "% losses, "
				+ (stats.totalMoves/(stats.totalPlays+1)) + " moves/play ("
				+ MiscUtils.format("%3.1f", stats.totalGoodMoves * 100.0 / stats.totalMoves) + "% good), "
				+ stats.totalNodeCount + " nodes.";
	}

	private void prdebug(Node node, String string)
	{
		if (node != null && node != root)
		{
			System.out.print("[");
			System.out.print(node);
			System.out.print("] ");
			//System.out.print(node.toDesc());
			//System.out.print(" ");
		}
		System.out.println(string);
	}


	// ASCII TREE DUMP

	public void dumpTree(PrintStream out, int width)
	{
		int level = 1;
		while (dumpTreeRow(out, level, width))
			level++;
	}

	private boolean dumpTreeRow(PrintStream out, int level, int width)
	{
		System.out.print(level + "\t");
		int n = 0;
		for (int x=0; x<width; x++)
		{
			char ch = iterateTree(level, x*1.0f/width, root);
			if (ch != ' ')
				n++;
			System.out.print(ch);
		}
		System.out.println();
		return n > 0;
	}

	private char iterateTree(int level, float pos, Node node)
	{
		if (node.getLevel() == level)
		{
			if (node.isSolved())
			{
				return '@';
			}
			else if (!node.isComplete())
				return '+';
			else
				return signedValueToChar(node.getModifiedWinRate()*2-1, 1);
		}

		if (!node.hasChildren())
			return ' ';
		
		ArrayList<Node> list = new ArrayList();
		list.addAll(node.getAllChildren());
		int index = (int) (pos*list.size());
		if (index >= list.size())
			index = list.size()-1;
		Node child = list.get(index);
		float x1 = index*1.0f/list.size();
		float x2 = (index+1)*1.0f/list.size();
		float x = (pos-x1)/(x2-x1);
		return iterateTree(level, x, child);
	}

	private char signedValueToChar(double score, int range)
	{
		int n = 25;
		int i = (int) Math.round(score*n/range);
		if (i == 0)
			return '.';
		if (i > n)
			i = n;
		if (i < -n)
			i = -n;
		if (i > 0)
			return (char)('a' + i - 1);
		else
			return (char)('A' - i - 1);
	}

	public int numLeavesVisited()
	{
		return stats.totalLeafCount;
	}

	public void setInitialChoices(int[] choices, int len)
	{
		if (choices == null || len == 0)
			this.initialChoices = null;
		else
			this.initialChoices = Arrays.copyOf(choices, len);
	}

	public TranspositionTable getTranspositionTable()
	{
		return transpositionTable;
	}

	//
	
	public AIDecider newSolver(GameState<?> initialState)
	{
		return new Solver(initialState, getNumIters());
	}
	
	public class Solver extends AIDecider
	{
		private MCTS mcts;
		private int iters;

		public Solver(GameState<?> initialState, int iters)
		{
			super(initialState);
			this.iters = iters;
			this.mcts = MCTS.this;
		}
		
		public Line<?> solve() throws MoveFailedException, InterruptedException, ExecutionException
		{
			// start from initial state, but use indices we've gathered so far in this turn
			mcts = new MCTS(mcts);
			mcts.setRandomSeed(new Random().nextLong()); // TODO: seed?
			mcts.setInitialChoices(turnActions, turnIndex); // TODO: will always visit these?
			//mcts.setDebug(true);

			//for (int i=0; i<turnIndex; i++)
				//System.out.println(i + ": " + turnChoices[i]);
			Node bestPath;
			// TODO: what if we don't find a solution?
			// TODO: what if solution ends with a bunch of randomness?
			do {
				int count1 = mcts.stats.totalPlays;
				if (useMultipleThreads)
					mcts.iterateMultiThreaded(initialState, iters, 300);
				else
					mcts.iterate(initialState, iters);
				//mcts.iterate(initialState, iters);
				int count2 = mcts.stats.totalPlays;
				
				bestPath = mcts.getBestPath();
				log.debug(" Best: " + bestPath);

				if (mcts.stats.totalPlays == 0)
				{
					GameState<?> finalState = initialState.nullMoveEndsGame();
					if (finalState != null)
						throw new GameOverException(finalState);
				}
				// TODO?
				if (count1 == count2)
					throw new MoveFailedException(seekingPlayer, "No progress on search: " + mcts);
				
			} while (bestPath == null || !bestPath.isCompletePath());
			//mcts.getRoot().dumpToLevel(2);
			return bestPath;
		}

	}

	@Override
	public Thread[] getThreads()
	{
		return useMultipleThreads ? ThreadUtils.getThreadPoolExecutorThreads() : null;
	}

	@Override
	public void resetStats()
	{
		stats = new Stats();
	}

}
