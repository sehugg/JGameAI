package com.puzzlingplans.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.puzzlingplans.ai.util.BitUtils;


public class MoveExplorer implements Decider
{
	private GameState<?> initialState;
	private GameState<?> currentState;
	private int[] actions;
	private MoveNode rootNode;
	private MoveNode currentNode;
	private int seekingPlayer;
	private boolean omitHiddenChoices;
	private Random rnd;
	private List<MoveNode> turns;
	private int levelBias;
	
	//

	public MoveExplorer(GameState<?> initialState, int[] actions, int seekingPlayer)
	{
		this.initialState = initialState;
		this.actions = actions;
		setSeekingPlayer(seekingPlayer);
	}

	public MoveExplorer(GameState<?> initialState, int[] actions)
	{
		this(initialState, actions, initialState.getCurrentPlayer());
	}
	
	public MoveExplorer(GameState<?> initialState, Line<?> move, int seekingPlayer)
	{
		this(initialState, move.getIndices(), seekingPlayer);
	}

	public MoveExplorer(GameState<?> initialState, Line<?> move)
	{
		this(initialState, move.getIndices());
	}

	public MoveExplorer setRealLife()
	{
		this.seekingPlayer = RealLife;
		return this;
	}
	
	public MoveExplorer setOmitHiddenChoices()
	{
		this.omitHiddenChoices = true;
		return this;
	}
	
	public MoveExplorer setSplitTurns()
	{
		this.turns = new ArrayList<MoveNode>();
		return this;
	}
	
	public List<MoveNode> getTurns()
	{
		return turns;
	}

	public MoveExplorer setEvaluateRandomChoices(Random rnd)
	{
		this.rnd = rnd;
		return this;
	}

	public MoveExplorer setSeekingPlayer(int player)
	{
		this.seekingPlayer = player;
		return this;
	}

	public MoveNode explore()
	{
		currentState = initialState.copy();
		resetRoot();
		try
		{
			while (!currentState.isGameOver() && currentNode.getLevel() + levelBias <= actions.length)
			{
				MoveResult result = playTurn();
				if (result == MoveResult.Ok)
				{
					currentNode.setIsEndOfTurn();
					saveTurn();
					if (currentNode.getLevel() + levelBias >= actions.length)
						break;
				} else {
					currentNode.result = result;
					break;
				}
			}
		} catch (MoveFailedException e)
		{
			e.printStackTrace(); // TODO?
		}
		return currentNode;
	}

	private void saveTurn()
	{
		if (turns != null)
		{
			levelBias += currentNode.getLevel();
			turns.add(currentNode);
			resetRoot();
		}
	}

	private void resetRoot()
	{
		rootNode = currentNode = new MoveNode(null, -1);
	}

	protected MoveResult playTurn() throws MoveFailedException
	{
		MoveResult result = currentState.playTurn(this);
		return result;
	}

	@Override
	public MoveResult choose(Choice choice) throws MoveFailedException
	{
		MoveNode oldNode = currentNode;
		int level = currentNode.getLevel() + levelBias;
		long mask = choice.getPotentialMoves();
		
		// resolve hidden choices?
		if (choice instanceof HiddenChoice && seekingPlayer == RealLife && omitHiddenChoices)
		{
			int actualOutcome = ((HiddenChoice)choice).getActualOutcome();
			return choice.choose(actualOutcome);
		}
		int action = (level < actions.length) ? actions[level] : -1;
		// chance node?
		if (choice instanceof RandomChoice)
		{
			currentNode.setIsChanceNode();
			if (rnd != null)
			{
				float val = rnd.nextFloat();
				action = ((RandomChoice)choice).chooseActionWithProbability(mask, val);
				//System.out.println(val + " " + action);
				// TODO: handle invalid moves?
			}
		}
		// see if we have run out of actions, if so return Canceled
		if (action >= 0)
		{
			MoveNode newNode = new MoveNode(currentNode, action);
			oldNode.next = currentNode;
			if (action < 0 || action >= 64 || ((1L << action) & mask) == 0)
			{
				return (currentNode.result = MoveResult.NoMoves);
			}
			currentNode = newNode;
			MoveResult result = choice.choose(action);
			newNode.result = result;
			return result;
		} else {
			currentNode.potentialMoves = mask;
			return MoveResult.Canceled;
		}
	}

	@Override
	public int getSeekingPlayer()
	{
		return seekingPlayer;
	}

	public GameState<?> currentState()
	{
		if (currentState == null)
			explore();
		return currentState;
	}
	
	public Line<?> currentNode()
	{
		if (currentNode == null)
			explore();
		return currentNode;
	}

	//
	
	public class MoveNode extends Line<MoveNode>
	{
		public long potentialMoves;
		public MoveNode next;
		public MoveResult result;

		public MoveNode(MoveNode parent, int index)
		{
			super(parent, index);
		}
		
		public final MoveResult getResult()
		{
			return result;
		}
		
		public final long getPotentialMoves()
		{
			return potentialMoves;
		}
		
		@Override
		public String toString()
		{
			if (result == MoveResult.NoMoves)
				return super.toString() + "X";
			else if (result == MoveResult.Canceled && next == null)
				return super.toString() + BitUtils.toBitSet(potentialMoves);
 			else
				return super.toString();
		}

	}

}
