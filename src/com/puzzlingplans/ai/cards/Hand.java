package com.puzzlingplans.ai.cards;

import java.util.ArrayList;
import java.util.Collection;

import com.puzzlingplans.ai.cards.PlayingCard.Rank;
import com.puzzlingplans.ai.cards.PlayingCard.Suit;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.CloningObject;

// TODO: generic class?
public class Hand extends CloningObject
{
	private long mask;
	private long hole;
	private int ranks;

	//

	public Hand()
	{
	}

	public Hand(long board, long hole)
	{
		// TODO: speedup
		this(toCards(board).toArray(new PlayingCard[0]), toCards(hole).toArray(new PlayingCard[0]));
		assert((board & hole) == 0);
	}

	public Hand(PlayingCard[] board, PlayingCard[] hole)
	{
		for (PlayingCard card : board)
			addCard(card);
		for (PlayingCard card : hole)
			addHoleCard(card);
	}

	public Hand clone() throws CloneNotSupportedException
	{
		Hand copy = (Hand) super.clone();
		return copy;
	}
	
	public long getCards()
	{
		return mask;
	}

	public long getHoleCards()
	{
		return hole;
	}

	public void clearAll()
	{
		mask = 0;
		hole = 0;
		ranks = 0;
	}

	public boolean addCard(PlayingCard card)
	{
		return addCard(card.index());
	}

	public boolean addCard(Rank rank, Suit suit)
	{
		return addCard(PlayingCard.rs2i(rank, suit));
	}

	public boolean addCard(int i)
	{
		assert(i>=0 && i<PlayingCard.numberOfPossibleCards());
		long bit = 1L << i;
		if ((mask & bit) == 0)
		{
			mask  |= bit;
			ranks |= 1 << (i >> 2);
			return true;
		} else
			return false;
	}

	public void addCards(long cardsToAdd)
	{
		for (int i=BitUtils.nextBit(cardsToAdd, 0); i>=0; i=BitUtils.nextBit(cardsToAdd, i+1))
		{
			addCard(i);
		}
	}

	public boolean removeCard(PlayingCard card)
	{
		return removeCard(card.index());
	}

	public boolean removeCard(Rank rank, Suit suit)
	{
		return removeCard(PlayingCard.rs2i(rank, suit));
	}

	public boolean removeCard(int i)
	{
		assert(i>=0 && i<PlayingCard.numberOfPossibleCards());
		long bit = 1L << i;
		if ((mask & bit) != 0)
		{
			mask &= ~bit;
			hole &= ~bit;
			if ((mask & (0xf << ((i >> 2) << 2))) == 0)
				ranks &= ~(1 << (i >> 2));
			return true;
		} else
			return false;
	}

	public boolean addHoleCard(PlayingCard card)
	{
		return addHoleCard(card.index());
	}

	public boolean addHoleCard(Rank rank, Suit suit)
	{
		return addHoleCard(PlayingCard.rs2i(rank, suit));
	}

	public boolean addHoleCard(int i)
	{
		hole |= (1L << i);
		return addCard(i);
	}

	public static Collection<PlayingCard> toCards(long mask)
	{
		Collection<PlayingCard> cards = new ArrayList<PlayingCard>(BitUtils.countBits(mask));
		for (int i = BitUtils.nextBit(mask, 0); i >= 0; i = BitUtils.nextBit(mask, i + 1))
		{
			cards.add(new PlayingCard(i));
		}
		return cards;
	}

	public boolean isEmpty()
	{
		return mask == 0;
	}

	public boolean hasHoleCards()
	{
		return hole != 0;
	}

	public int firstHoleCard()
	{
		return BitUtils.nextBit(hole, 0);
	}

	public int size() 
	{
		return BitUtils.countBits(mask);
	}
	
	@Override
	public String toString()
	{
		return toCards(mask ^ hole).toString() + "\tHole: " + toCards(hole);
	}

	//

	public long highestFlush(int ncards)
	{
		long match = 0x1111111111111111L; // all hearts
		int shifts = 4;
		return matchHighest(match, ncards, shifts, 1);
	}

	/*
	public long highestNofaKind(int ncards)
	{
		long match = 0xf; // 4 suits
		int shifts = Rank.values().length;
		return matchHighest(match, ncards, shifts, 4);
	}
	*/

	// TODO: slow
	public long allNofaKind(int ncards)
	{
		long match = 0xf; // 4 suits
		int shifts = PlayingCard.NumRanks;
		return matchAllExact(match, ncards, shifts, 4);
	}

	public long highestStraight(int ncards)
	{
		int bestn = ncards;
		long bestm = 0;
		int i = BitUtils.nextBit(ranks, 0);
		do
		{
			int j = BitUtils.nextBit(ranks + (1 << i), i + 1);
			if (j < 0)
				break;

			int n = j - i;
			if (n >= bestn)
			{
				bestn = n;
				bestm = ((1L << (n * 4)) - 1) << (i * 4); // set consecutive bits to 0, add 1 after
			}
			/*
			System.out.println(toString() + " " + i + " " + j + " "
					+ Integer.toBinaryString(ranks) + " "
					+ Integer.toBinaryString(ranks+(1<<i)) + " "
					+ Long.toBinaryString(bestm));
			*/
			i = BitUtils.nextBit(ranks, j);
		} while (i >= 0);
		return oneFromEachRank(bestm & mask);
	}

	private static long oneFromEachRank(long m)
	{
		if (m == 0)
			return 0;
		
		long cards = 0;
		for (int i = BitUtils.nextBit(m, 0); i >= 0; i = BitUtils.nextBit(m, (i + 4) & ~0x3))
		{
			cards |= 1L << i;
		}
		return cards;
	}

	private long matchHighest(long match, int ncards, int shifts, int spacing)
	{
		int bestn = ncards;
		long bestm = 0;
		for (int i = 0; i < shifts; i++)
		{
			long m = mask & match;
			int n = BitUtils.countBits(m);
			// look for higher # of cards, or higher top card
			if (n > bestn || (n == bestn && m > bestm))
			{
				bestn = n;
				bestm = m;
			}
			match <<= spacing;
		}
		return bestm;
	}

	private long matchAllExact(long match, int ncards, int shifts, int spacing)
	{
		long bestm = 0;
		for (int i = 0; i < shifts; i++)
		{
			long m = mask & match;
			int n = BitUtils.countBits(m);
			// look for higher # of cards, or higher top card
			if (n == ncards)
			{
				bestm |= m;
			}
			match <<= spacing;
		}
		return bestm;
	}

	private int highCard()
	{
		return BitUtils.highSetBit(ranks);
	}

	public long getPokerRank(int ncards)
	{
		int nr = 16;
		int ns = 4;
		long x = 0;

		long flush = highestFlush(ncards);
		long straight = highestStraight(ncards);
		long fours = allNofaKind(4);
		long threes = allNofaKind(3);
		long pairs = allNofaKind(2);
		
		// TODO: high ace, wildcards

		// straight flush
		if (BitUtils.countBits(flush & straight) >= ncards)
			x = (x*nr) + highestRank(flush & straight); // TODO?
		else
			x *= nr;
		// four of a kind
		x = (x*nr) + highestRank(fours);
		// full house
		if (pairs != 0 && threes != 0)
			x = (x*nr) + highestRank(pairs | threes);
		else
			x *= nr;
		// flush
		x = (x*nr) + highestRank(flush);
		// straight
		x = (x*nr) + highestRank(straight);
		// three of a kind
		x = (x*nr) + highestRank(threes);
		
		// two pair
		if (BitUtils.countBits(pairs) >= 4)
			x = (x*nr) + highestRank(pairs);
		else
			x *= nr;
		// one pair
		x = (x*nr) + highestRank(pairs);
		// high card
		x = (x*nr) + highCard() + 1;
		
		//if (x >= 0x100)	System.out.println(this + ": " + Long.toHexString(x));
		return x;
	}

	private final static long highestRank(long cards)
	{
		return (BitUtils.highSetBit(cards) + 1) / 4;
	}

}
