package com.puzzlingplans.ai;





public class Line<T extends Line<T>>
{
	private T parent;
	private int moveIndex;
	private int level;
	private boolean isChanceNode;
	private boolean isEndOfTurn;
	
	//
	
	public Line(T parent, int index)
	{
		this.parent = parent;
		this.moveIndex = index;
		this.level = parent == null ? 0 : parent.getLevel() + 1;
	}

	public final T getParent()
	{
		return parent;
	}

	public final int getMoveIndex()
	{
		return moveIndex;
	}

	public final int getLevel()
	{
		return level;
	}

	public T getMoveAtDepth(int depth)
	{
		Line<T> l = this;
		do {
			if (l.getLevel() == depth)
				return (T) l;
			l = l.getParent();
		} while (l != null);
		return null;
	}

	/*
	public String toFullString()
	{
		return ((parent != null) ? parent.toFullString() : "") + this.toString();
	}
	*/

	public T getFirst()
	{
		return getMoveAtDepth(1);
	}

	public T getRoot()
	{
		return getMoveAtDepth(0);
	}

	@Override
	public String toString()
	{
		if (parent == null)
			return ":";
		else {
			StringBuffer st = new StringBuffer(getLevel() * 5 + 1);
			st.append(parent);
			if (parent.isChanceNode())
				st.append('*');
			if (moveIndex >= 0)
				st.append(moveIndex);
			else
				st.append("?"); // hidden node
			if (isEndOfTurn)
				st.append(':');
			else
				st.append('/');
			return st.toString();
		}
	}

	public final boolean isChanceNode()
	{
		return isChanceNode;
	}

	public void setIsChanceNode()
	{
		this.isChanceNode = true;
	}
	
	public final boolean isEndOfTurn()
	{
		return isEndOfTurn;
	}

	public void setIsEndOfTurn()
	{
		this.isEndOfTurn = true;
	}
	
	public int[] getIndices()
	{
		int[] arr = new int[level];
		Line<T> node = this;
		while (node.parent != null)
		{
			arr[node.level-1] = node.moveIndex;
			node = node.parent;
		}
		return arr;
	}

	public boolean isCompletePath()
	{
		Line<?> path = this;
		while (path != null)
		{
			// look for either end of turn of chance node
			// (ai will have to stop at first chance node)
			if (path.isEndOfTurn() || path.isChanceNode())
				return true;

			path = path.getParent();
		}
		return false;
	}

	public Line<?> removeChanceNodes()
	{
		// TODO: actually want to remove parents of chance nodes ... weird
		Line<?> n = this;
		while (n.getParent() != null && n.getParent().isChanceNode())
		{
			n = n.getParent();
		}
		Line<?> p = n.getParent();
		Line<?> newline = new Line(p != null ? p.removeChanceNodes() : null, n.getMoveIndex());
		if (n.isEndOfTurn())
			newline.setIsEndOfTurn();
		return newline;
	}

	public boolean isEmpty()
	{
		return getLevel() == 0;
	}

	public int countTurns()
	{
		Line<?> n = this;
		int count = 0;
		while (n != null)
		{
			if (n.isEndOfTurn())
				count++;
			n = n.getParent();
		}
		return count;
	}
}
