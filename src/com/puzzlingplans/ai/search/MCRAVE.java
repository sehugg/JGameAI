package com.puzzlingplans.ai.search;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.GameOverException;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.RandomChoice;
import com.puzzlingplans.ai.UniformRandomChoice;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.FastHash;
import com.puzzlingplans.ai.util.HammingSpaceIndex;
import com.puzzlingplans.ai.util.MiscUtils;
import com.puzzlingplans.ai.util.RandomXorshift128;
import com.puzzlingplans.ai.util.ThreadUtils;

// From: http://www.cs.utexas.edu/~pstone/Courses/394Rspring11/resources/mcrave.pdf
public class MCRAVE extends SearchAlgorithmBase implements AISolver
{
	public final static class TreeNode
	{
		public TreeNode(long key, long mask, int level)
		{
			this.key = key;
			this.mask = mask;
			this.numVisits = 1;
			this.level = (short) level;
		}

		final long key;
		final long mask;
		final short level;

		long expandedMoves;
		long invalidMoves;
		long solvedMoves;
		// long visitedSubtreeMoves;
		ActionNode[] actNodes;
		int numVisits;

		@Override
		public String toString()
		{
			return "[mask=" + Long.toHexString(mask) + " expanded=" + Long.toHexString(expandedMoves) + " invalid="
					+ Long.toHexString(invalidMoves) + " solved=" + Long.toHexString(solvedMoves) + " visits="
					+ numVisits + "]";
		}

		public ActionNode getActionNode(int action)
		{
			ActionNode[] acts = actNodes;
			if (acts == null || action >= acts.length)
				return null;

			// TODO: ArrayIndexOutOfBoundsException during level generation
			return acts[action];
		}

		public int getMaxActionNodes()
		{
			ActionNode[] acts = actNodes;
			return (acts != null) ? acts.length : 0;
		}

		public ActionNode createOrGetActionNode(int action)
		{
			ActionNode[] acts = actNodes;
			if (acts == null)
			{
				acts = actNodes = new ActionNode[BitUtils.highSetBit(mask) + 1];
			}

			// TODO: why are we getting these out-of-bounds errors?
			if (action >= acts.length)
			{
				acts = Arrays.copyOf(acts, action+1);
				_numArrayResizes++;
			}
			ActionNode actnode = acts[action];
			if (actnode == null)
				return (acts[action] = new ActionNode());
			else
				return actnode;
		}

		/*
		 * public ActionNode createOrGetActionSubtreeNode(int action) { // if
		 * createAfterNVisits >= 2, skip the first time we visit this action //
		 * TODO: does this help? long actmask = 1L << action; boolean skip =
		 * false && (actmask & visitedSubtreeMoves) == 0; visitedSubtreeMoves |=
		 * actmask; if (skip) return null;
		 * 
		 * return createOrGetActionNode(action); }
		 */

		public int getNumVisits()
		{
			return numVisits;
		}

		public int getLevel()
		{
			return level;
		}

		public boolean isFullyExpanded()
		{
			return mask == (expandedMoves | invalidMoves);
		}

		public void setExpandedMove(int a)
		{
			long m = 1L << a;
			assert ((mask & m) != 0);
			// assert((invalidMoves & m) == 0); // TODO: this keeps going off
			// when multithreaded
			expandedMoves |= m;
		}

		public void setInvalidMove(int a)
		{
			long m = 1L << a;
			assert ((mask & m) != 0);
			// assert((expandedMoves & m) == 0); // TODO: this keeps going off
			// when multithreaded
			invalidMoves |= m;
		}
	}

	public final static class ActionNode
	{
		private float q, q2;
		private int n, n2;

		public float getValue(float bbsqr4)
		{
			float beta = n2 / (n + n2 + bbsqr4 * n * n2);
			assert (!Double.isNaN(beta));
			// if (n == 3000) System.out.println(n + " " + n2 + " " + beta + " "
			// + q + " " + q2 + " " + (q2 - q));
			return (float) ((1 - beta) * q + beta * q2);
		}

		public float getWinRate()
		{
			return q;
		}

		public void addRootOutcome(float value)
		{
			n++;
			q += (value - q) / n;
		}

		public void addSubtreeOutcome(float value)
		{
			n2++;
			q2 += (value - q2) / n2;
		}

		@Override
		public String toString()
		{
			return "[" + q + "/" + n + " " + q2 + "/" + n2 + " = " + getValue(0.1f) + "]";
		}
	}

	static class MCLine extends Line<MCLine>
	{
		public MCLine(MCLine parent, int index)
		{
			super(parent, index);
		}
	}

	//

	// TODO: move into Simulator, subclass for other data
	public static class Stats
	{
		public int numUpdatedActionNodes;
		public int numUpdatedSubtreeNodes;
		public int numNodesCreated;
		public int numNodeLevelsCreated;
		public int numGamesPlayed;
		public int numGamesCompleted;
		public int numGamesWon;
		public int numGamesLost;
		public int numGamesAhead;
		public int numGamesBehind;
		public int numMovesMade;
		public int numTurnsPlayed;
		public int numRepetitions;
		public int maxNodeLevel;
		public int maxSimLevel;
		public int maxTurns;

		@Override
		public String toString()
		{
			return MiscUtils
					.format("%d plays, %3.1f%% ahead, %3.1f%% completed, %3.1f%% won, max %d/%d/%d; per game: %3.2f new nodes, %3.1f/%3.1f moves/turns, %3.1f action/subtree, %d reps",
							numGamesPlayed,
							numGamesAhead * 100f / numGamesPlayed,
							numGamesCompleted * 100f / numGamesPlayed, 
							numGamesWon * 100f / numGamesCompleted,
							maxNodeLevel, maxSimLevel, maxTurns,
							numNodesCreated * 1f / numGamesPlayed,
							numMovesMade * 1f / numGamesPlayed,
							numTurnsPlayed * 1f / numGamesPlayed,
							// numUpdatedActionNodes*1f/numGamesPlayed,
							numUpdatedSubtreeNodes * 1f / numGamesPlayed,
							numRepetitions);
		}

		public String toShortString()
		{
			return MiscUtils.format("%3.1f%% ahead %3.1f%% complete %3.1f%% won %3.1f moves %d/%d levels",
					numGamesAhead * 100f / numGamesPlayed,
					numGamesCompleted * 100f / numGamesPlayed,
					numGamesWon * 100f / numGamesCompleted,
					numMovesMade * 1f / numGamesPlayed,
					maxNodeLevel, maxSimLevel);
		}
	}

	//

	public static class MCLevelInfo extends LevelInfo
	{
		TreeNode node;
		long nodeKey;
		long mask;
		int choiceKey;

		@Override
		public void reset()
		{
			super.reset();
			node = null;
			nodeKey = 0;
			mask = 0;
			choiceKey = 0;
		}
	}

	public class Sim extends Simulator<MCLevelInfo>
	{
		private Random rnd;
		private int nodesOutOfTree; // # of null nodes since we left the tree

		private int[] initialChoices;
		private int initialChoiceCount;

		public Stats stats = new Stats();
		public String prefixPathToDebug;
		public boolean exploration = true;

		//

		public Sim(GameState<?> initialState, int maxLevel, Random rnd)
		{
			super(initialState, maxLevel, 1);
			this.rnd = rnd;
		}

		@Override
		protected LevelInfo newLevelInfo()
		{
			return new MCLevelInfo();
		}

		public GameState<?> simulate() throws MoveFailedException
		{
			GameState<?> state = super.simulate();
			linfo[currentLevel].reset();
			return state;
		}

		@Override
		public void reset()
		{
			super.reset();
			nodesOutOfTree = 0;
		}

		@Override
		public MoveResult choose(Choice choice) throws MoveFailedException
		{
			RandomChoice rndchoice = (choice instanceof RandomChoice) ? (RandomChoice) choice : null;

			// lookup node in tree
			long mask = choice.getPotentialMoves();
			int choicekey = choice.key();
			long nodekey = currentTrail;

			// TODO: don't bother looking up node if way deep in the simulation
			TreeNode node = null;
			if (nodesOutOfTree < stopLookingNodeCount)
			{
				long nodekey2 = currentTrail ^ choicekey ^ mask;
				node = nodes.getEntryAt(nodekey, nodekey2);
				// TODO: handle race condition with FastHash
				if (node != null && node.mask != mask)
					node = nodes.getEntryAt(nodekey, nodekey2);
				// TODO: this asserts sometimes
				assert (node == null || node.mask == mask);
				// if state was repeated, then cancel this simulation
				if (findRepetitions && nodesOutOfTree > 0 && node != null && findRepetition(nodekey))
					return MoveResult.Canceled;
				// we're out of the tree as soon as we don't find a node in the table
				if (node == null)
					nodesOutOfTree++;
				else
					assert (node.mask == mask);
			}

			if (mask == 0)
				return MoveResult.NoMoves;

			// store some values for later
			MCLevelInfo lrec = linfo[currentLevel];
			lrec.mask = mask;
			lrec.node = node;
			lrec.nodeKey = nodekey;
			lrec.choiceKey = choicekey;

			if (prefixPathToDebug != null)
				debug = getLastMove().toString().startsWith(prefixPathToDebug);

			if (debug)
				prdebug("choose() " + currentLevel + ", node=" + node + " " + hex(nodekey));

			// if it's an initial choice, return it
			if (currentLevel - lookahead < initialChoiceCount)
			{
				int action = initialChoices[currentLevel - lookahead];
				assert (((1L << action) & mask) != 0); // make sure choice was
														// included in
														// previously searched
														// node
				return tryChoice(choice, action);
			}

			// check mask and eliminate invalid or solved moves from
			// consideration
			long extra = 0;
			if (node != null)
			{
				mask &= ~node.invalidMoves & ~node.solvedMoves;
				// if node is not yet completely expanded...
				if (!node.isFullyExpanded())
				{
					// exclude already-expanded moves?
					if (visitUnexpandedNodesFirst)
					{
						extra |= mask & node.expandedMoves;
						mask &= ~node.expandedMoves;
					}
					// prioritize preferred moves?
					if (randomEvent(preferredExpandProb))
					{
						long pm = choice.getPreferredMoves();
						if (pm != 0 && (mask & pm) != 0)
						{
							extra |= mask & ~pm;
							mask &= pm;
						}
					}
				}
			}
			// iterate through potential moves until we get a valid one
			float prob = 0;
			while (mask != 0)
			{
				int besta = -1;
				if (node != null && (rndchoice == null || optimisticChanceNodes))
				{
					// find the most promising moves
					// TODO: performance
					float max = Float.NEGATIVE_INFINITY;
					int factor = (currentPlayer == seekingPlayer) ? 1 : -1;
					for (int a = BitUtils.nextBit(mask, 0); a >= 0; a = BitUtils.nextBit(mask, a + 1))
					{
						float val = getTotalNodeValue(node, a, factor, debug);
						if (val > max)
						{
							max = val;
							besta = a;
							if (debug)
								prdebug("choose(): best action #" + a + " value = " + val);
						}
						// TODO: what if there are ties?
					}
				}
				// no best move? pick one using default policy
				// first try preferred moves from Choice (TODO: performance?)
				if (preferredMoveProb > 0 && besta < 0)
				{
					long preferred = choice.getPreferredMoves() & mask;
					if (preferred != 0 && randomEvent(preferredMoveProb))
					{
						besta = BitUtils.choose_bit(preferred, rnd);
					}
				}
				if (besta < 0)
				{
					// if no move, then look to historical heuristics
					if (rndchoice == null)
					{
						besta = getGoodChoice(mask, currentPlayer == seekingPlayer);
					} else
					{
						// choose random move
						besta = BitUtils.choose_bit(mask, rnd);
					}
					if (debug)
						prdebug("choose(): action #" + besta + " from " + hex(mask));
				}
				// skip improbable moves
				if (rndchoice != null && !(rndchoice instanceof UniformRandomChoice))
				{
					prob += rndchoice.getProbability(besta);
					if (rnd.nextFloat() > prob)
					{
						mask &= ~(1L << besta);
						continue;
					}
				}
				// try the move
				MoveResult result = tryChoice(choice, besta);
				if (result == MoveResult.Ok)
					return result;

				// failed, mark it invalid
				mask &= ~(1L << besta);
				if (node != null)
					node.setInvalidMove(besta);
				// if we run out of moves, pick up already-expanded moves we may
				// have missed
				if (mask == 0)
				{
					mask = extra;
					extra = 0;
				}
			}
			return MoveResult.NoMoves;
		}

		// 0 <= prob <= 0x100
		private boolean randomEvent(int prob)
		{
			return prob > 0 && ((prob >= 0x100) || ((rnd.nextInt() & 0xff) < prob));
		}

		private boolean findRepetition(long nodekey)
		{
			for (int i = lookahead; i < currentLevel; i++)
			{
				if (linfo[i].nodeKey == nodekey)
				{
					stats.numRepetitions++;
					if (debug)
						prdebug("choose(): repetition @ " + i);
					return true;
				}
			}
			return false;
		}

		protected int getGoodChoice(long mask, boolean maximize)
		{
			if (goodMoves != null)
			{
				long key = goodMoveKey(currentLevel);
				long good = goodMoves.getBestMovesFor(key, hammingRadius);
				if ((good & mask) != 0)
					mask &= good;
			}
			// TODO: use smarter policy
			return BitUtils.choose_bit(mask, rnd);
		}

		private long goodMoveKey(int level)
		{
			MCLevelInfo lrec1 = linfo[level];
			MCLevelInfo lrec2 = linfo[level - 1];
			long m1 = lrec1.mask;
			long m2 = lrec2.mask;
			return m1 ^ BitUtils.rotl(m2, 32) ^ (lrec1.choiceKey * 805306457L); // ^
																				// (indicesPerLevel[level-1]
																				// *
																				// 402653189L);
		}

		public void backprop(GameState<?> state)
		{
			// TODO: absolute score?
			boolean nodeCreated = false;
			int intscore = state.getModifiedScore(seekingPlayer) - currentLevel * depthPenalty;
			if (hardWinOrLose)
			{
				if (intscore > 0)
					intscore = GameState.WIN;
				else if (intscore < 0)
					intscore = GameState.LOSE;
			}
			float value = intscore / (1f * GameState.WIN);
			int i;
			for (i = lookahead; i < currentLevel; i++)
			{
				MCLevelInfo lrec = linfo[i];
				int player = lrec.player;
				long subtreeKey = lrec.nodeKey;
				TreeNode subtree = lrec.node;
				if (subtree == null)
				{
					// only create one node per run
					if (!nodeCreated)
					{
						// TODO: terminal nodes
						if (true || i < currentLevel - 1)
						{
							subtree = createNewNode(i, subtreeKey);
							if (subtree == null)
								continue;
							nodeCreated = true;
						} else
						{
							setTerminalNode(i);
							break;
						}
					} else
					{
						break;
					}
				}

				subtree.numVisits++;
				int choiceKey = lrec.choiceKey;
				int rootAction = lrec.action;
				long mask = lrec.mask;

				ActionNode rootActionNode = subtree.createOrGetActionNode(rootAction);
				assert (rootActionNode != null);
				rootActionNode.addRootOutcome(value);
				// rootActionNode.addSubtreeOutcome(value);
				stats.numUpdatedActionNodes++;
				if (debug)
					prdebug("Updated level " + i + " action " + rootAction + " value " + value + " = " + rootActionNode);

				// If an action au is legal in state su, but illegal in state
				// st, then no update is performed for this move.
				if (useRAVE)
				{
					long validActions = mask & ~(1L << rootAction);
					if (debug)
						prdebug("validActions = " + Long.toHexString(validActions));
					for (int j = i + 1; j < currentLevel && validActions != 0; j++)
					{
						// only update nodes matching player and choice class
						MCLevelInfo subinfo = linfo[j];
						if (subinfo.player == player && subinfo.choiceKey == choiceKey)
						{
							int subAction = subinfo.action;
							// If multiple moves are played at the same
							// intersection during a simulation,
							// then this update is only performed for the first
							// move at the intersection.
							if ((validActions & (1L << subAction)) != 0)
							{
								ActionNode subActionNode = subtree.createOrGetActionNode(subAction);
								if (subActionNode != null)
								{
									subActionNode.addSubtreeOutcome(value);
									validActions &= ~(1L << subAction);
									stats.numUpdatedSubtreeNodes++;
									if (debug)
										prdebug("Updated level " + i + "/" + j + " action " + subAction + " subtree "
												+ value + " = " + subActionNode);
								}
							}
						}
					}
				}
			}
			// update good moves table?
			if (goodMoves != null && currentLevel < maxLevel)
			{
				while (i < currentLevel)
				{
					MCLevelInfo lrec = linfo[i];
					long key = goodMoveKey(i);
					int action = lrec.action;
					if (lrec.player == seekingPlayer)
						goodMoves.add(key, value, action);
					else
						goodMoves.add(key, -value, action);
					i++;
				}
			}
		}

		private TreeNode createNewNode(int level, long trail)
		{
			MCLevelInfo lrec = linfo[level];
			long mask = lrec.mask;
			long nodekey = lrec.nodeKey;
			long nodekey2 = nodekey ^ lrec.choiceKey ^ mask;
			assert (useMultipleThreads || !nodes.containsEntry(nodekey, nodekey2));
			TreeNode newnode = nodes.insertEntry(nodekey, nodekey2, new TreeNode(nodekey, mask, level));
			if (newnode == null)
			{
				if (debug)
					prdebug("Failed to create node at iter " + iterCount + " level " + level + " key "
							+ Long.toHexString(nodekey));
				return null;
			}
			stats.numNodesCreated++;
			stats.numNodeLevelsCreated += level;
			stats.maxNodeLevel = Math.max(stats.maxNodeLevel, level);
			MCLevelInfo lrecparent = linfo[level - 1];
			TreeNode parentNode = lrecparent.node;
			if (parentNode != null)
				parentNode.setExpandedMove(lrecparent.action);
			if (debug)
				prdebug("Created node at iter " + iterCount + " level " + level);
			return newnode;
		}

		void setTerminalNode(int level)
		{
			MCLevelInfo lrecparent = linfo[level - 1];
			TreeNode parentnode = lrecparent.node;
			int action = lrecparent.action;
			parentnode.solvedMoves |= (1L << action);
			if (debug)
				prdebug("Set terminal node for #" + action);
		}

		public Line<?> getLastMove()
		{
			MCLine root = new MCLine(null, -1);
			MCLine node = root;
			for (int i = lookahead; i < currentLevel; i++)
			{
				MCLevelInfo lrec = linfo[i];
				if (lrec.node == null)
					break;

				node = new MCLine(node, lrec.action);
				if (linfo[i + 1].player != lrec.player)
					node.setIsEndOfTurn();
				if (lrec.choice != null && lrec.choice.isRandom())
					node.getParent().setIsChanceNode();
			}
			return node;
		}

		public GameState<?> iterate(int numIters) throws MoveFailedException
		{
			GameState<?> newstate = null;
			for (int i = 0; i < numIters; i++)
			{
				newstate = iterate();
			}
			return newstate;
		}

		public GameState<?> iterate() throws MoveFailedException
		{
			GameState<?> newstate;
			newstate = simulate();
			// if simulation failed, don't return anything
			if (newstate == null)
				return null;
			updateStats(newstate);
			backprop(newstate);
			if (debug)
			{
				dump();
				prdebug("Winners = " + newstate.getWinners() + "\t" + getLastMove());
			}
			return newstate;
		}

		private void updateStats(GameState<?> newstate)
		{
			stats.numGamesPlayed++;
			stats.numMovesMade += currentLevel - lookahead;
			stats.numTurnsPlayed += turnsPlayed;
			if (newstate.isGameOver())
			{
				stats.numGamesCompleted++;
				if (newstate.getWinners() == 1L << seekingPlayer)
					stats.numGamesWon++;
				else if (newstate.getWinners() != 0)
					stats.numGamesLost++;
			}
			int score = newstate.getModifiedScore(seekingPlayer);
			if (score > 0)
				stats.numGamesAhead++;
			else if (score < 0)
				stats.numGamesBehind++;
			stats.maxSimLevel = Math.max(stats.maxSimLevel, currentLevel - lookahead);
			stats.maxTurns = Math.max(stats.maxTurns, turnsPlayed);
		}

		// TODO: use Log
		private void prdebug(String string)
		{
			System.out.println("[" + iterCount + " L" + currentLevel + " " + getLastMove() + "] " + string);
		}

		public float getTotalNodeValue(TreeNode tnode, int action, int factor, boolean debug)
		{
			ActionNode anode = tnode.getActionNode(action);

			if (!exploration)
			{
				if (debug)
					prdebug("  #" + action + " " + anode);
				return anode != null ? anode.getWinRate() * factor : 0;
			}
			if (anode == null || anode.n == 0)
			{
				return uctConstant; // TODO: should have random order?
			} else if (useRAVE)
			{
				float rave = anode.getValue(raveBias_bbsqr4) * factor;
				double uct = uctConstant * uctTable.getUCT(tnode.numVisits, anode.n);
				assert (!Double.isNaN(uct));
				if (debug)
					prdebug("  #" + action + " " + anode + " + " + (float) uct + " = " + (float) (rave + uct));
				return (float) (rave + uct);
			} else
			{
				float rave = anode.getWinRate() * factor;
				double uct = uctConstant * uctTable.getUCT(tnode.numVisits, anode.n);
				assert (!Double.isNaN(uct));
				return (float) (rave + uct);
			}
		}

		public void setInitialChoices(int[] turnChoices, int turnIndex)
		{
			if (turnChoices != null)
			{
				this.initialChoices = Arrays.copyOf(turnChoices, turnIndex);
				this.initialChoiceCount = turnIndex;
			} else
			{
				this.initialChoices = null;
				this.initialChoiceCount = 0;
			}
		}

		public void setSeekingPlayer(int seekingPlayer)
		{
			this.seekingPlayer = seekingPlayer;
		}

		public void clearStats()
		{
			this.stats = new Stats();
		}
	}

	//

	private FastHash<TreeNode> nodes;
	private float uctConstant;
	private boolean useRAVE;
	private float raveBias;
	private float raveBias_bbsqr4;
	private FastUCTTable uctTable;

	// TODO: this doesn't work unless game is HashedPosition
	public boolean resetBeforeSolve = true; // TODO: make this derived from GameState or dynamic
	public boolean visitUnexpandedNodesFirst = false; // TODO: make 'true' pass unit tests
	public int preferredMoveProb = 0; // 0x100 means always use preferred move,  0x80 is 50%, etc.
	public int preferredExpandProb = 0; // 0x100 means always use preferred move, 0x80 is 50%, etc.
	public boolean optimisticChanceNodes = false;
	public boolean hardWinOrLose = false;
	public boolean findRepetitions = false;
	public int depthPenalty = 0;
	public int stopLookingNodeCount = 3;

	public HammingSpaceIndex goodMoves;
	public int hammingRadius = 3;

	public static int _numArrayResizes;


	//

	public MCRAVE(int nodesLog2)
	{
		// TODO: cuckoo hashing?
		this.nodes = new FastHash(nodesLog2, 0.75f, 2);
		setUCTConstant(1.0f);
		setRAVEBias(0.1f);
		this.uctTable = new FastUCTTable(6); // TODO: optimal value?
	}

	public MCRAVE(int nodesLog2, int maxLevel, int numIters)
	{
		this(nodesLog2);
		setMaxLevel(maxLevel);
		setNumIters(numIters);
	}

	public Sim newSimulator(GameState<?> initialState, int maxLevel, Random rnd)
	{
		// TODO: seekingPlayer?
		return new Sim(initialState, maxLevel, rnd);
	}

	@Override
	public String toString()
	{
		return "[nodes=" + nodes + "]";
	}

	public void setUCTConstant(float u)
	{
		this.uctConstant = u;
	}

	public void setRAVEBias(float b)
	{
		this.raveBias = b;
		this.raveBias_bbsqr4 = 4.0f * b * b;
		this.useRAVE = raveBias > 0;
	}

	public AIDecider newSolver(GameState<?> game)
	{
		return new MCRAVEDecider(game);
	}

	public void reset()
	{
		nodes.clear();
	}

	public void resetVisitCounts()
	{
		for (int i = 0; i < nodes.capacity(); i++)
		{
			TreeNode entry = nodes.getEntryAtIndex(i);
			if (entry != null)
			{
				// TODO: set everything to 1?
				entry.numVisits = 1;
				if (entry.actNodes != null)
				{
					for (ActionNode an : entry.actNodes)
					{
						if (an != null)
						{
							an.n = 1;
							an.n2 = 1;
						}
					}
				}
			}
		}
	}

	public FastHash<TreeNode> getNodes()
	{
		return nodes;
	}

	//

	public class MCRAVEDecider extends AIDecider
	{
		public MCRAVEDecider(GameState<?> initialState)
		{
			super(initialState);
			// resetBeforeSolve only applies when using hashed positions
			if (!(initialState instanceof HashedPosition))
				resetBeforeSolve = true;
		}

		@Override
		public Line<?> solve() throws MoveFailedException, InterruptedException
		{
			if (maxLevel <= 0)
				throw new IllegalArgumentException("Must set maxLevel");
			if (numIters <= 0)
				throw new IllegalArgumentException("Must set numIters");

			// TODO: what if not enough moves? repeat?
			if (resetBeforeSolve)
			{
				reset();
			} else
			{
				resetVisitCounts();
				// TODO: thin out some entries so we don't run into issues
				// storing nodes
				// (we run into problems in games like Poker where subsequent
				// turns may not be deterministic)
				/*
				 * while (nodes.keyCount() > nodes.capacity() / 4) { int culled
				 * = nodes.cullLeastVisitedEntries(3); // TODO: const?
				 * dbgout.println("Culled " + culled + "/" + nodes.capacity() +
				 * " entries"); }
				 */
			}
			Line<?> bestMove;
			int numThreads = useMultipleThreads ? ThreadUtils.numThreadsPerPool() : 1;
			Sim[] sims = new Sim[numThreads];
			RandomXorshift128 rnd = new RandomXorshift128();
			// TODO: keep sims around?
			for (int i = 0; i < numThreads; i++)
			{
				sims[i] = newSimulator(initialState, maxLevel, new RandomXorshift128(rnd.nextLong(), rnd.nextLong()));
				sims[i].setInitialChoices(turnActions, turnIndex);
				sims[i].setSeekingPlayer(seekingPlayer);
			}
			do
			{
				// multiple threads or single thread?
				if (numThreads > 1)
				{
					try
					{
						iterateMultiThreaded(sims, numIters);
					} catch (ExecutionException e)
					{
						throw new RuntimeException(e.getCause());
					}
				} else
				{
					sims[0].iterate(numIters);
				}

				if (canDebug())
				{
					log.debug(MCRAVE.this.toString());
					for (int i = 0; i < numThreads; i++)
						log.debug("Sim" + i + ": " + sims[i].stats);
				}

				Sim sim = sims[0];
				sim.exploration = false;
				// sim.debug = true;
				GameState<?> finalstate = sim.iterate();
				if (sim.isGameOver(finalstate))
				{
					// TODO: this is to handle checkmate in Chess; pretty dodgy
					log.debug("*** SIM OVER: " + sim);
					throw new GameOverException(finalstate);
				}
				bestMove = sim.getLastMove();
				if (canDebug())
				{
					log.debug("BEST = " + bestMove);
				}
			} while (!bestMove.isCompletePath());
			if (numThreads > 1)
				sumStats(totalStats, sims);
			else
				totalStats = sims[0].stats;
			return bestMove;
		}
	}

	//

	public boolean useMultipleThreads = true;
	public Stats totalStats = new Stats();

	public void resetStats()
	{
		totalStats = new Stats();
	}

	public void iterateMultiThreaded(Sim[] sims, int numIters) throws ExecutionException, InterruptedException
	{
		Runnable[] tasks = new Runnable[ThreadUtils.numThreadsPerPool()];
		AtomicInteger iters = new AtomicInteger(numIters);
		for (int i=0; i<tasks.length; i++)
			tasks[i] = new SimulateTask(sims[i], iters);
		ThreadUtils.submitAndWait(tasks);
	}

	class SimulateTask implements Runnable
	{
		private Sim sim;
		private AtomicInteger iters;

		public SimulateTask(Sim sim, AtomicInteger iters)
		{
			this.sim = sim;
			this.iters = iters;
		}

		@Override
		public void run()
		{
			try
			{
				int n = 100;
				int v;
				while ((v = iters.addAndGet(-n)) > -n)
				{
					sim.iterate(Math.min(n, v + n));
				}
			} catch (Throwable e)
			{
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		}
	}

	@Override
	public Thread[] getThreads()
	{
		return useMultipleThreads ? ThreadUtils.getThreadPoolExecutorThreads() : null;
	}

	// TODO: for GWT
	public void sumStats(Stats total, Sim[] sims)
	{
		Stats[] stats = new Stats[sims.length];
		for (int i=0; i<sims.length; i++)
			stats[i] = sims[i].stats;
		MiscUtils.sumFields(total, stats);
	}
}
