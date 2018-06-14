package com.puzzlingplans.ai.io;


public interface Fungible<T>
{
	public FungibleTicket<T> toTicket();
}
