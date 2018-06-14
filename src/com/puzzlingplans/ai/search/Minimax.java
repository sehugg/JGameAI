package com.puzzlingplans.ai.search;

import java.util.Arrays;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameOverException;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.search.TranspositionTable.Entry;
import com.puzzlingplans.ai.search.TranspositionTable.EntryType;
import com.puzzlingplans.ai.util.MiscUtils;

public class Minimax implements Decider, AISolver
{
	private GameState<?> initialState;
	private int maxLevel;
	private int maxNumLeaves;
	private boolean cutoff;
	private MoveMaskHash cutoffHintHash;
	private int depthPenalty;
	private boolean failSoft;
	private TranspositionTable transpositionTable;
	private boolean disallowChanceNodes;
	
	private boolean debug;
	private boolean printLevelStats;
	private int printStatsLevel;
	private int lastStatsNumLeaves;

	private int seekingPlayer;
	private Node rootNode;
	private Node currentNode;
	private int[] initialChoices;
	private int initialChoicesLength;
	
	private int numLeavesVisited;
	private int numGamesCompleted;
	private int numCutoffs;
	private int numEarlyCutoffs;
	private int numTransTableExact;
	private int numTransTableBounds;
	private int lastRootScore;
	
	static final int RESET = 0;
	static final int END  = -1;
	static final int NEXT = -2;
	
	//
	
	class Node extends IterableLine<Node>
	{
		long cutoffMask;
		Node pv;
		
		public Node(Node parent, int index)
		{
			super(parent, index);
		}

		@Override
		protected Node newNode(Node parent, int nextIndex)
		{
			return new Node(parent, nextIndex);
		}

		@Override
		protected void setNodeMask()
		{
			nodeMask = originalMask;
			// limit selection to initial choices?
			if (getLevel() < initialChoicesLength)
			{
				nodeMask = 1L << initialChoices[getLevel()];
				originalMask = nodeMask;
				extraMask = 0;
				if (debug)
					prdebug(this, "initial choice " + getLevel() + ": 0x" + nodeMask);
			}
			// move ordering hint available?
			else if (cutoffHintHash != null)
			{
				long cutoffMask = cutoffHintHash.getForMask(nodeMask);
				if ((cutoffMask & nodeMask) != 0 && nodeMask != cutoffMask)
				{
					if (debug)
						prdebug(this, "cutoff mask for " + Long.toHexString(nodeMask) + " = " + Long.toHexString(cutoffMask));
					// try cutoff moves first, put into nodeMask, rest goes into extraMask
					extraMask = nodeMask & ~cutoffMask;
					if (extraMask != 0)
					{
						nodeMask = cutoffMask;
						this.cutoffMask = cutoffMask;
					}
				}
			}
		}

		public void markForCutoff()
		{
			Node parent = getParent();
			int moveIndex = getMoveIndex();
			if (cutoffHintHash != null && parent != null && parent.originalMask != (1L<<moveIndex))
			{
				//System.out.println(this + " " + Long.toHexString(parent.nodeMask) + " " + moveIndex);
				cutoffHintHash.addIndex(parent.originalMask, moveIndex);
			}
		}
	}

	//

	public Minimax(GameState<?> state)
	{
		this.initialState = state.copy();
		this.maxLevel = 12;
		setTranspositionTableSize(0);
		setPruning(true);
		reset();
	}

	public Minimax(GameState<?> state, Minimax prev)
	{
		this.initialState = state.copy();
		this.maxLevel = prev.maxLevel;
		this.cutoff = prev.cutoff;
		this.cutoffHintHash = prev.cutoffHintHash;
		this.depthPenalty = prev.depthPenalty;
		this.failSoft = prev.failSoft;
		this.transpositionTable = prev.transpositionTable;
		this.printLevelStats = prev.printLevelStats;
		this.printStatsLevel = prev.printStatsLevel;
		reset();
	}

	@Override
	public void reset()
	{
		rootNode = null;
		// TODO?
	}

	public void setPruning(boolean b)
	{
		this.cutoff = b;
		this.cutoffHintHash = b ? new MoveMaskHash() : null; // TODO: setter, share hash between instances
	}
	
	public void setFailSoft(boolean b)
	{
		this.failSoft = b;
	}

	public void setDepthPenalty(int dp)
	{
		this.depthPenalty = dp;
	}
	
	public void setAllowChanceNodes(boolean b)
	{
		this.disallowChanceNodes = !b;
	}

	public void setMaxLevel(int max)
	{
		this.maxLevel = max;
		reset();
	}

	public void setMaxNumLeaves(int max)
	{
		this.maxNumLeaves = max;
		reset();
	}

	public void setDebug(boolean b)
	{
		this.debug = b;
	}

	public void setPrintStatsAtLevel(int level)
	{
		this.printStatsLevel = level;
		this.printLevelStats = true;
	}

	public void setTranspositionTableSize(int numEntriesLog2)
	{
		if (numEntriesLog2 > 0)
		{
			// throw an exception if GameState not hashable
			((HashedPosition)initialState).enableHashing(true);
			this.transpositionTable = new TranspositionTable(numEntriesLog2, 0.75f, 2);
		}
		else
		{
			this.transpositionTable = null;
			if (initialState instanceof HashedPosition)
				((HashedPosition)initialState).enableHashing(false);
		}
	}

	public Line<Node> getPrincipalVariation()
	{
		return rootNode.pv;
	}

	public Line<?> getBestMove()
	{
		if (rootNode.pv == null)
			return null;
		else
			return rootNode.pv.getFirst();
	}
	
	public int numLeavesVisited()
	{
		return numLeavesVisited;
	}

	public int numGamesCompleted()
	{
		return numGamesCompleted;
	}

	public String toString()
	{
		return numLeavesVisited + " leaves, "
				+ MiscUtils.format("%3.1f", numGamesCompleted*100.0f/numLeavesVisited) + "% completed, "
				+ numCutoffs + " cutoffs, "
				+ MiscUtils.format("%3.1f", numEarlyCutoffs*100.0f/numCutoffs) + "% early, "
				+ numTransTableExact + "/" + numTransTableBounds + " exact/bounds. "
				;
	}
	
	public int solve()
	{
		return solveFromPlayer(initialState.getCurrentPlayer());
	}

	public int solveFromPlayer(int seekingPlayer)
	{
		return solve(seekingPlayer, GameState.SCORE_MIN, GameState.SCORE_MAX);
	}

	public int solveWithAlphaBeta(int alpha, int beta)
	{
		return solve(initialState.getCurrentPlayer(), alpha, beta);
	}

	public int solve(int seekingPlayer, int alpha, int beta)
	{
		this.seekingPlayer = seekingPlayer;
		rootNode = new Node(null, -1);
		currentNode = null;
		try
		{
			lastRootScore = minimax(initialState, rootNode, alpha, beta);
			return lastRootScore;
		} catch (MoveFailedException e)
		{
			throw new RuntimeException(e);
		} catch (CloneNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
	}
	
	// TODO???
	private static final boolean v0 = true;
	private static final boolean v1 = true;
	private static final boolean v2 = !true;
	private static final boolean v3 = !true;
	private static final boolean v4 = true;
	private static final boolean v5 = true;
	private static final boolean v6 = true;

	private int minimax(GameState<?> oldstate, final Node node, int alpha, int beta) throws MoveFailedException, CloneNotSupportedException
	{
		final int player = oldstate.getCurrentPlayer();
		final boolean max = seekingPlayer == player;
		final int level = node.getLevel();
		
		this.currentNode = node;
		long hash = 0;
		long ttkey = 0;
		if (transpositionTable != null && (max||v4))
		{
			hash = ((HashedPosition)oldstate).hashFor(seekingPlayer);
			ttkey = v6 ? hash+player : hash;
			Entry entry = transpositionTable.getEntryAt(hash, ttkey);
			// TODO: chance node?
			// make sure level > 0 because we want to return at least one move as principal variation
			// TODO: store PV with trans table?
			if (entry != null && entry.getLevel() >= maxLevel - node.getLevel() && level > 0)
			{
				if (debug)
					prdebug(node, "Looked up " + entry);
				switch (entry.getType())
				{
					case EXACT:
						node.complete();
						node.markForCutoff(); // TODO: correct?
						numTransTableExact++;
						return entry.getValue();
					case LOWER:
						if (max || v0)
							alpha = Math.max(alpha, entry.getValue());
						numTransTableBounds++;
						break;
					case UPPER:
						if (max || v1)
							beta = Math.min(beta, entry.getValue());
						numTransTableBounds++;
						break;
				}
				if (cutoff && beta <= alpha)
				{
					if (debug)
						prdebug(node, "tt pruned " + beta + " <= " + alpha);
					node.complete();
					node.markForCutoff();
					return entry.getValue(); // TODO? max ? alpha : beta;
				}
			}
		}
		
		int nchildren = 0;
		long total = 0;
		if (debug)
			prdebug(node, "start " + (max?"max":"min"));
		
		int best = max ? GameState.SCORE_MIN : GameState.SCORE_MAX;
		EntryType entryType = v2||max ? EntryType.UPPER : EntryType.LOWER;
		if (level < maxLevel && !oldstate.isGameOver())
		{
			while (node.hasNext())
			{
				// play a turn with new cloned state
				GameState<?> newstate = oldstate.copy();
				this.currentNode = node;
				if (debug)
					prdebug(node, "play turn");

				MoveResult turnResult = newstate.playTurn(this);
				
				// TODO: child may not be direct child
				Node child = currentNode;
				if (debug)
					prdebug(child, "turn result " + turnResult);
				assert(child != null);
				// if move was canceled, we ran out of moves
				if (turnResult == MoveResult.Canceled)
					break;

				child.setIsEndOfTurn();
				// if we didn't advance, assume the game ended
				if (child == node)
				{
					assert(newstate.isGameOver());
					return leafNode(node, newstate);
				}
				assert(turnResult == MoveResult.Ok);
				// TODO: chance nodes mixed with non-chance-nodes in same turn do not work
				//boolean chance = node.isChanceNode(); // isChanceNode is not valid until 1st turn
				// just check to see if any of the nodes in this turn are chance nodes
				boolean chance = false;
				Node n = child;
				while (n != null) {
					if (n.isChanceNode())
						chance = true;
					if (n == node)
						break;
					n = n.getParent();
				}
				if (chance)
				{
					if (disallowChanceNodes)
						throw new SearchOverflowException("Chance node not allowed");
					
					alpha = GameState.SCORE_MIN;
					beta = GameState.SCORE_MAX;
					// TODO: add probabilities
				}
				
				// recurse
				int val = minimax(newstate, child, alpha, beta);

				currentNode = child;
				
				nchildren++;
				
				if (node.isChanceNode())
				{
					// TODO: weight by probability?
					// TODO: expectimax star1, star2
					total += val;
					node.pv = child.pv != null ? child.pv : child; // TODO?
				} else {
					// compute new alpha and beta
					if (max)
					{
						if (val > best)
						{
							if (val > alpha)
							{
								node.pv = child.pv != null ? child.pv : child;
								child.markForCutoff();
								if (debug)
									prdebug(node, "raised alpha from " + alpha + " -> " + val + "; pv = " + node.pv);
								alpha = val;
								entryType = EntryType.EXACT;
							}
							best = val;
						}
					} else
					{
						if (val < best)
						{
							if (val < beta)
							{
								node.pv = child.pv != null ? child.pv : child;
								child.markForCutoff();
								if (debug)
									prdebug(node, "lowered beta from " + beta + " -> " + val + "; pv = " + node.pv);
								beta = val;
								entryType = EntryType.EXACT;
							}
							best = val;
						}
					}
					// do alpha-beta cutoff
					if (cutoff && beta <= alpha)
					{
						if (debug)
							prdebug(node, "pruned " + beta + " <= " + alpha);
						numCutoffs++;
						// use getParent() to make sure it works when a turn has >1 levels
						if ((child.getParent().cutoffMask & (1L << child.getMoveIndex())) != 0)
							numEarlyCutoffs++;
						child.markForCutoff();
						node.complete();
						entryType = v3||max ? EntryType.LOWER : EntryType.UPPER;
						break;
					}
				}
			}
			if (printLevelStats && level == printStatsLevel)
			{
				printLevelStats(node);
				System.out.println(" a/b = " + formatScore(alpha) + "/" + formatScore(beta) + " best " + formatScore(best));
			}
		}
		// leaf node?
		if (nchildren == 0)
		{
			return leafNode(node, oldstate);
		} else {
			int score;
			if (node.isChanceNode())
			{
				if (debug)
					prdebug(node, "avg " + total*1.0f/nchildren);
				score = (int) ((total + nchildren/2) / nchildren);
				entryType = EntryType.EXACT; // TODO?
			} else {
				if (debug)
					prdebug(node, "alpha " + alpha + " beta " + beta + " best " + best);
				if (failSoft)
					score = best;
				else
					score = max ? alpha : beta;
			}
			if (transpositionTable != null && (max||v5))
			{
				Entry entry = transpositionTable.newEntry(hash, ttkey, maxLevel - level, score, entryType);
				if (debug)
					prdebug(node, "Stored " + entry);
			}
			return score;
		}
	}

	private int leafNode(final Node node, GameState<?> oldstate) throws MoveFailedException
	{
		numLeavesVisited++;
		if (maxNumLeaves > 0 && numLeavesVisited > maxNumLeaves)
			throw new SearchOverflowException("Max leaves visited");
		if (oldstate.isGameOver())
		{
			if (debug)
				prdebug(node, "game over");
			numGamesCompleted++;
		}
		int score = getScore(node, oldstate);
		if (debug)
			prdebug(node, "leaf score = " + score);
		if (node.hasNext())
			node.end();
		return score;
	}

	private String formatScore(int x)
	{
		if (x == Integer.MIN_VALUE)
			return "-inf";
		else if (x == Integer.MAX_VALUE)
			return "inf";
		else
			return Integer.toString(x);
	}

	private void printLevelStats(Node node)
	{
		// kind of like Gaviota's setboard, display, perftdiv
		int delta = numLeavesVisited - lastStatsNumLeaves;
		lastStatsNumLeaves = numLeavesVisited;
		System.out.print(delta + "\t" + node + "\t" + this + " ");
	}

	private int getScore(Node node, GameState<?> state)
	{
		int score = state.getModifiedScore(seekingPlayer);
		if (depthPenalty == 0)
		{
			return score;
		} else
		{
			int penalty = node.getLevel() * depthPenalty;
			return score >= 0 ? Math.max(0, score - penalty) : Math.min(0, score + penalty);
		}
	}

	private void prdebug(Node node, String string)
	{
		/*
		int currentLevel = currentNode != null ? currentNode.level : 0;
		if (currentLevel > 0)
			for (int i=0; i<currentLevel; i++)
				System.out.print("  ");
				*/
		if (node != null)
			System.out.print("[" + node + "] ");
		System.out.println(string);
	}

	@Override
	public MoveResult choose(Choice choice) throws MoveFailedException
	{
		Node parent = currentNode;
		if (debug)
			prdebug(parent, "start chooseMask");
		do {
			assert(currentNode == parent);
			assert(parent.hasNext());
			Node node = parent.nextNode(choice);
			if (debug)
				prdebug(parent, "nextNode = " + node);
			if (node == null)
				break;
			
			this.currentNode = node;
			int moveIndex = node.getMoveIndex();
			long moveMask = 1L << moveIndex;
			assert(moveIndex >= 0);
			parent.visitMask |= moveMask;
			if (debug)
				prdebug(parent, "visit mask = 0x" + Long.toHexString(parent.visitMask));

			// recursion possible
			MoveResult result = choice.choose(moveIndex);
 			
			if (debug)
				prdebug(node, "result = " + result);
			if (result == MoveResult.Ok)
			{
				//assert((parent.okMask & moveMask) == 0); // not yet visited
				parent.okMask |= moveMask;
				return MoveResult.Ok;
			}
			
			this.currentNode = parent;
			//if (result != MoveResult.Canceled)
			if (parent.hasNext()) // TODO?
				parent.advance();
		} while (parent.hasNext());
		if (debug)
			prdebug(parent, "end chooseMask");
		// return Canceled if we had at least one move
		//System.out.println(parent + " " + parent.okMask);
		return parent.okMask != 0 ? MoveResult.Canceled : MoveResult.NoMoves;
	}

	@Override
	public int getSeekingPlayer()
	{
		return seekingPlayer;
	}

	public long getLastRootScore()
	{
		return lastRootScore;
	}

	public TranspositionTable getTranspositionTable()
	{
		return transpositionTable;
	}

	//
	
	@Override
	public AIDecider newSolver(GameState<?> initialState)
	{
		return new Solver(initialState, 0);
	}

	public AIDecider newSolver(GameState<?> initialState, int levelInc)
	{
		return new Solver(initialState, levelInc);
	}
	
	public class Solver extends AIDecider
	{
		private Minimax minimax;
		private int levelInc;

		public Solver(GameState<?> state, int levelInc)
		{
			super(state);
			this.minimax = Minimax.this;
			this.levelInc = levelInc;
		}

		@Override
		public Line<Node> solve() throws MoveFailedException
		{
			// TODO: start from initial state, but use indices we've gathered so far in this turn
			// TODO: trans table works if nodes are at lower levels on next turn?
			minimax = new Minimax(initialState, minimax);
			minimax.setInitialChoices(turnActions, turnIndex); // TODO: will always visit these?
			if (levelInc == 0 || minimax.transpositionTable == null)
			{
				minimax.solve();
			} else {
				// iterative deepening
				int levelMax = minimax.maxLevel; // TODO
				for (int l=levelInc*2; l<=levelMax; l+=levelInc*2)
				{
					minimax.setMaxLevel(l);
					minimax.solve();
				}
			}
			Line<Node> bestPath = minimax.getPrincipalVariation();
			System.out.println(minimax + " Best: " + bestPath);
			if (bestPath == null)
			{
				GameState<?> finalState = initialState.nullMoveEndsGame();
				if (finalState != null)
					throw new GameOverException(finalState);
			}
			return bestPath;
		}
		
	}

	public void setInitialChoices(int[] choices, int len)
	{
		if (choices == null || len == 0)
		{
			this.initialChoices = null;
			this.initialChoicesLength = 0;
		}
		else
		{
			this.initialChoices = Arrays.copyOf(choices, len);
			this.initialChoicesLength = len;
		}
	}

	@Override
	public Thread[] getThreads()
	{
		return null;
	}

	@Override
	public void resetStats()
	{
		// TODO Auto-generated method stub
		System.out.println("resetStats() not implemented");
	}

}

