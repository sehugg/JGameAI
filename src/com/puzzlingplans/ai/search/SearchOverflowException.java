package com.puzzlingplans.ai.search;

import com.puzzlingplans.ai.MoveFailedException;

public class SearchOverflowException extends MoveFailedException
{
	public SearchOverflowException(String string)
	{
		super(string);
	}
}
