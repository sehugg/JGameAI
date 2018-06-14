package com.puzzlingplans.ai.cards;

import java.util.Random;

import com.puzzlingplans.ai.util.BitUtils;


public class ShuffledDeck extends Hand
{
	public ShuffledDeck(int numCards)
	{
		super(0, (1L << numCards) - 1);
	}

	public PlayingCard deal(Random rnd)
	{
		if (getCards() == 0)
			return null;

		int index = BitUtils.choose_bit(getCards(), rnd);
		PlayingCard card = new PlayingCard(index);
		removeCard(card);
		return card;
	}

}
