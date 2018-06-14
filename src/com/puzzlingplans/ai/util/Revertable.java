package com.puzzlingplans.ai.util;

public abstract class Revertable
{
	private Revertable next;
	
	public void undoAll()
	{
		undo();
		while (next != null)
		{
			next.undo();
			next = next.next;
		}
	}

	// TODO: what if we forget?
	public Revertable next(Revertable undo)
	{
		this.next = undo;
		return this;
	}

	public abstract void undo();

}
