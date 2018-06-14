package com.puzzlingplans.ai.search;

import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ExecutionException;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HiddenChoice;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.RandomChoice;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.MiscUtils;
import com.puzzlingplans.ai.util.RandomXorshift128;
import com.puzzlingplans.log.Log;
import com.puzzlingplans.log.LogAdapter;
import com.puzzlingplans.log.LogUtils;

public abstract class AIDecider implements Decider
{
	protected GameState<?> initialState;
	protected int seekingPlayer;

	protected int turnIndex;
	protected int[] turnActions;
	protected Choice[] turnChoiceObjs;
	
	private int[] nextChoices;

	private Random rnd;

	public int totalSolveCount;
	public long totalSolveTime;

	protected Log log = new LogAdapter(getClass());

	//

	// TODO: pass turnChoices, turnIndex?
	
	public abstract Line<?> solve() throws MoveFailedException, InterruptedException, ExecutionException;

	//
	
	public AIDecider(GameState<?> state)
	{
		this.initialState = state;
		this.seekingPlayer = state.getCurrentPlayer();
		this.turnActions = new int[16]; // array grows as needed
		this.turnChoiceObjs = new Choice[16];
		this.turnIndex = 0;
		initRandom();
	}
	
	public void initRandom()
	{
		this.rnd = new RandomXorshift128();
	}

	public void setRandomSeed(long i)
	{
		this.rnd = new RandomXorshift128(i);
	}

	@Override
	public MoveResult choose(Choice choice) throws MoveFailedException
	{
		int index;
		long moves = choice.getPotentialMoves();
		if (moves == 0)
			return MoveResult.NoMoves;
		
		if (turnIndex >= turnActions.length)
		{
			turnActions = Arrays.copyOf(turnActions, turnActions.length*2);
			turnChoiceObjs = Arrays.copyOf(turnChoiceObjs, turnChoiceObjs.length*2);
		}
		
		// TODO: hierarchy ?!?!
		MoveResult result;
		if (choice instanceof HiddenChoice)
		{
			// throw away precomputed choices, as we don't know future state
			nextChoices = null;
			// make the actual choice, since we're replaying the turn
			index = ((HiddenChoice)choice).getActualOutcome();
		}
		else if (choice instanceof RandomChoice)
		{
			// throw away precomputed choices, as we don't know future state
			nextChoices = null;
			index = getRandomChoice(moves);
		}
		else
		{
			index = nextChoice();
		}
		// save this for later, in case we need to recompute turn state
		if (canDebug())
			log.debug("Move @" + turnIndex + ": chose #" + index + " from " + Long.toBinaryString(moves));
		// negative means no moves, or perhaps checkmate
		if (index < 0)
			return MoveResult.NoMoves;
		
		assert(0 != (moves & (1L<<index)));
		turnChoiceObjs[turnIndex] = choice;
		turnActions[turnIndex++] = index;
		result = choice.choose(index);
		
		if (result != MoveResult.Ok)
		{
			if (canDebug())
				log.debug("Move @" + turnIndex + ": result = " + result);
			turnIndex--;
			nextChoices = null; // TODO: invalidate if there is more than one choice?
		}
		return result;
	}

	private int nextChoice() throws MoveFailedException
	{
		if (nextChoices == null || turnIndex >= nextChoices.length)
		{
			if (canDebug())
				log.debug("Solving @" + turnIndex + " " + Arrays.toString(Arrays.copyOf(turnActions, turnIndex)));
			if (!makeChoices())
				return -1;
			assert(nextChoices != null);
		}
		return nextChoices[turnIndex];
	}

	private boolean makeChoices() throws MoveFailedException
	{
		long t1 = System.currentTimeMillis();
		Line<?> bestPath;
		try
		{
			bestPath = solve();
		} catch (InterruptedException e)
		{
			throw new RuntimeException(e);
		} catch (ExecutionException e)
		{
			throw new RuntimeException(e.getCause());
		}
		long t2 = System.currentTimeMillis();
		if (canDebug())
			log.debug("Solved " + bestPath + " in " + (t2-t1) + " msec");
		totalSolveTime += t2-t1;
		totalSolveCount++;

		if (bestPath == null)
			throw new MoveFailedException("No next move");
		
		this.nextChoices = bestPath.getIndices();
		return nextChoices != null && nextChoices.length > 0;
	}

	protected final boolean canDebug()
	{
		return log.getLogLevel() >= Log.LOG_DEBUG;
	}

	@Override
	public int getSeekingPlayer()
	{
		return seekingPlayer;
	}
	
	public int getRandomChoice(long bitmask)
	{
		// TODO: option to use another decider
		if (bitmask == 0)
			return -1;

		return BitUtils.choose_bit(bitmask, rnd);
	}

	public Line<?> getCompleteMove()
	{
		Line<?> node = new Line(null, -1);
		for (int i=0; i<turnIndex; i++)
		{
			node = new Line(node, turnActions[i]);
			if (turnChoiceObjs[i].isRandom())
				node.getParent().setIsChanceNode();
		}
		node.setIsEndOfTurn(); //?
		return node;
	}

	// TODO: make sure everyone implements this
	public void setSeekingPlayer(int player)
	{
		this.seekingPlayer = player;
	}
}
