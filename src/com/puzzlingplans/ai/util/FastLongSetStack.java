package com.puzzlingplans.ai.util;

import java.util.Arrays;
import java.util.NoSuchElementException;

public class FastLongSetStack extends CloningObject
{
	long[] elements;
	int top;
	
	//
	
	public FastLongSetStack(int initialCapacity)
	{
		this.elements = new long[initialCapacity];
	}

	public FastLongSetStack(FastLongSetStack flss)
	{
		// TODO?
		this.elements = flss.elements;
		this.top = flss.top;
	}
	
	public FastLongSetStack clone() throws CloneNotSupportedException
	{
		// do not copy elements
		return (FastLongSetStack) super.clone();
	}

	public void push(long hash)
	{
		// TODO: linked arrays?
		if (top == elements.length)
		{
			elements = Arrays.copyOf(elements, elements.length*2);
		}
		elements[top++] = hash;
	}
	
	public long pop()
	{
		if (top == 0)
			throw new NoSuchElementException();
		
		return elements[--top];
	}
	
	public long peek()
	{
		if (top == 0)
			throw new NoSuchElementException();

		return elements[top-1];
	}
	
	public int size()
	{
		return top;
	}

	public boolean contains(long hash)
	{
		// TODO: faster
		for (int i=top-1; i>=0; i--)
			if (elements[i] == hash)
				return true;
		return false;
	}

	public int count(long hash)
	{
		int total = 0;
		for (int i=0; i<top; i++)
			if (elements[i] == hash)
				total++;
		return total;
	}

}
