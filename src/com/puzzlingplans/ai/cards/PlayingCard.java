package com.puzzlingplans.ai.cards;

public class PlayingCard
{
	public static final Suit[] SuitValues = Suit.values();
	public static final Color[] ColorValues = Color.values();
	public static final Rank[] RankValues = Rank.values();
	public static final int NumSuits = SuitValues.length;
	public static final int NumRanks = RankValues.length;

	public enum Color { Red, Black };
	public enum Suit { Hearts, Diamonds, Clubs, Spades };
	public enum Rank { Ace, _2, _3, _4, _5, _6, _7, _8, _9, _10, Jack, Queen, King, Joker }
	
	public String SUIT_TO_CHAR = "hdCS";
	public String RANK_TO_CHAR = "A234567890JQK*";

	private int index;
	
	//
	
	public static int rs2i(Rank rank, Suit suit)
	{
		return suit.ordinal() + (rank.ordinal() << 2);
	}

	public PlayingCard(Rank rank, Suit suit)
	{
		this(rs2i(rank, suit));
	}

	public PlayingCard(int index)
	{
		this.index = index;
	}
	
	public int index()
	{
		return index;
	}
	
	public Suit suit()
	{
		return SuitValues[index&3];
	}
	
	public Rank rank()
	{
		return RankValues[index>>2];
	}
	
	public Color color()
	{
		return ColorValues[(index&2)>>1];
	}
	
	@Override
	public String toString()
	{
		char rch = RANK_TO_CHAR.charAt(rank().ordinal());
		char sch = SUIT_TO_CHAR.charAt(suit().ordinal());
		String pch = rch == '0' ? "1" : "";
		return pch + rch + sch;
	}

	@Override
	public int hashCode()
	{
		return index;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		PlayingCard other = (PlayingCard) obj;
		if (index != other.index)
			return false;
		return true;
	}

	public static int numberOfPossibleCards()
	{
		return NumRanks * NumSuits;
	}
}
