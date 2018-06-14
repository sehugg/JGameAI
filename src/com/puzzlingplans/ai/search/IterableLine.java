package com.puzzlingplans.ai.search;


import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.RandomChoice;
import com.puzzlingplans.ai.util.BitUtils;

// TODO: Not yet complete
public class IterableLine<T extends IterableLine<T>> extends Line<T>
{
	public static boolean debug = false;
	
	protected long originalMask;
	protected long nodeMask;	// current possible moves
	protected long extraMask; // mask after cutoff moves have been tried
	protected long okMask;	// moves that have returned Ok
	protected long visitMask;	// moves that have been visited
	
	private T currentChild;
	private int nextIndex;
	private boolean closed;
	private boolean advanced;
	
	//
	
	public IterableLine(T parent, int index)
	{
		super(parent, index);
	}
	
	// should override in subclass
	protected T newNode(T parent, int nextIndex)
	{
		return (T) new IterableLine<T>(parent, nextIndex);
	}

	public T nextNode(Choice choice)
	{
		if (originalMask == 0)
		{
			originalMask = choice.getPotentialMoves();
			if (originalMask == 0)
				return null;

			if (choice instanceof RandomChoice)
			{
				setIsChanceNode();
			}
			setNodeMask();
			advance();
		} else {
			assert(!debug || choice.getPotentialMoves() == originalMask);
		}
		advanced = false; // reset advanced flag
		return currentChild;
	}

	protected void setNodeMask()
	{
		nodeMask = originalMask;
	}

	void advance()
	{
		assert(!closed);
		// don't allow double-advance until nextNode() called again
		if (advanced)
		{
			if (debug)
				prdebug(currentChild, "advance, ignored");
			return;
		}
		if (nextIndex < 0)
		{
			assert(currentChild != null);
			if (debug)
				prdebug(currentChild, "advance, last node");
			end();
			return;
		}
		if (currentChild == null)
		{
			nextIndex = BitUtils.nextBit(nodeMask, 0);
		}
		currentChild = newNode((T) this, nextIndex); // TODO?
		nextIndex = BitUtils.nextBit(nodeMask, nextIndex+1);
		if (nextIndex < 0 && extraMask != 0)
		{
			nodeMask = extraMask;
			extraMask = 0;
			nextIndex = BitUtils.nextBit(nodeMask, 0);
		}
		if (debug)
			prdebug(this, "advance, current " + currentChild.getMoveIndex() + " next " + nextIndex);
		advanced = true;
	}
	
	public boolean hasNext()
	{
		return nextIndex == 0 || currentChild != null;
	}

	public void end()
	{
		assert(!closed);
		if (debug)
			prdebug(this, "end()");
		assert(originalMask == visitMask);
		if (getParent() != null)
			getParent().advance();
		currentChild = null;
		closed = true;
		if (debug)
			prdebug(this, "ended");
	}

	public void complete()
	{
		if (hasNext())
		{
			visitMask = originalMask;
			end();
		}
	}

	// TODO: Log
	protected void prdebug(IterableLine<T> node, String string)
	{
		if (node != null)
			System.out.print("[" + node + "] ");
		System.out.println(string);
	}
	
	//
	
	// TODO: use or remove
	public class ExpandingDecider implements Decider
	{
		private T currentNode;
		private int seekingPlayer;
		
		public ExpandingDecider(T root, int seekingPlayer)
		{
			this.currentNode = root;
			this.seekingPlayer = seekingPlayer;
		}

		@Override
		public MoveResult choose(Choice choice) throws MoveFailedException
		{
			T parent = currentNode;
			if (debug)
				prdebug(parent, "start chooseMask");
			do {
				assert(currentNode == parent);
				assert(parent.hasNext());
				T node = parent.nextNode(choice);
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

	}

	public static IterableLine newRootInstance()
	{
		return new IterableLine(null, -1);
	}

	// TODO: incomplete, can't return state and node
	public T iterateTurn(GameState<?> state, int seekingPlayer) throws MoveFailedException
	{
		if (closed)
			throw new IllegalStateException(this + " cannot expand; closed");
		if (!hasNext())
			throw new IllegalStateException(this + " cannot expand; no next node");
		//if (advanced)
			//throw new IllegalStateException(this + " cannot expand; clear advance flag");
		
		GameState<?> newstate = state.copy();
		if (debug)
			prdebug(this, "play turn");

		ExpandingDecider decider = new ExpandingDecider((T) this, seekingPlayer);
		MoveResult turnResult = newstate.playTurn(decider);
		
		// TODO: child may not be direct child
		T child = decider.currentNode;
		if (debug)
			prdebug(child, "turn result " + turnResult);
		assert(child != null);
		// if move was canceled, we ran out of moves
		if (turnResult == MoveResult.Canceled)
			return null;
		
		child.setIsEndOfTurn();
		assert(turnResult == MoveResult.Ok);
		// if we didn't advance, assume the game ended
		return child;
	}
}
