package com.puzzlingplans.ai.games.cards;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.cards.Pile;
import com.puzzlingplans.ai.cards.PlayingCard;
import com.puzzlingplans.ai.cards.ShuffledDeck;
import com.puzzlingplans.ai.util.FastLongSetStack;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class Freecell extends GameState<Freecell> implements HashedPosition
{
	private static final int CELL_PENALTY = 25;
	private static final int FIRST_FOUNDATION_REWARD = 100;
	private static final int NEXT_FOUNDATION_REWARD = 50;
	private static final int EMPTY_CASCADE_REWARD = 0;
	
	public static final int LevelsPerTurn = 2;

	private PlayingCard[] cells;
	private Pile[] foundations;
	private Pile[] cascades;
	
	private int cardsToGo;
	private Random rnd;

	int numCascades;
	int numCells;
	int numFoundations;
	
	int lastSource, lastTarget;
	FastLongSetStack previousStateHashes;

	//

	public Freecell(int randomSeed)
	{
		this(4, 8, randomSeed, 52);
	}

	public Freecell(int randomSeed, int numCards)
	{
		this(4, 8, randomSeed, numCards);
	}

	public Freecell(int numCells, int numCascades, int randomSeed, int numCards)
	{
		super(1);

		this.numCells = numCells;
		this.numCascades = numCascades;
		this.numFoundations = 4;
		this.cells = new PlayingCard[numCells];
		this.cascades = new Pile[numCascades];
		for (int i = 0; i < numCascades; i++)
			cascades[i] = new Pile();
		this.foundations = new Pile[4]; // 4 suits
		for (int i = 0; i < numFoundations; i++)
			foundations[i] = new Pile();
		
		this.previousStateHashes = new FastLongSetStack(1024);

		rnd = new RandomXorshift128(randomSeed);
		initBoard(numCards);
	}

	private void initBoard(int numCards)
	{
		ShuffledDeck deck = new ShuffledDeck(52);
		while (!deck.isEmpty())
		{
			PlayingCard card = deck.deal(rnd);
			int c = (card.index() % numCascades);
			cascades[c].push(card);
			cardsToGo++;
		}
	}
	
	@Override
	public Freecell clone() throws CloneNotSupportedException
	{
		Freecell game = super.clone();
		game.cells = cells.clone();
		game.cascades = new Pile[numCascades];
		for (int i=0; i<cascades.length; i++)
			game.cascades[i] = (Pile) cascades[i].clone();
		game.foundations = new Pile[numFoundations];
		for (int i=0; i<foundations.length; i++)
			game.foundations[i] = (Pile) foundations[i].clone();
		game.previousStateHashes = new FastLongSetStack(previousStateHashes);
		return game;
	}

	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		// don't repeat states
		final long statehash = hashFor(0);
		if (previousStateHashes.contains(statehash))
			return MoveResult.NoMoves;

		long mask = 0;
		// source can be non-empty cascade...
		for (int i = 0; i < numCascades; i++)
			if (cascades[i].size() > 0)
				mask |= choiceIndex(i);

		// cell...
		for (int i = 0; i < numCells; i++)
			if (cells[i] != null)
				mask |= choiceIndex(i + numCascades);

		// or foundation...
		for (int i = 0; i < numFoundations; i++)
			if (foundations[i].size() > 0)
				mask |= choiceIndex(i + numCascades + numCells);

		final long sourceMask = mask;
		MoveResult topResult = decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return sourceMask;
			}

			@Override
			public MoveResult choose(final int source) throws MoveFailedException
			{
				PlayingCard pcard = getSourceCard(source);
				assert (pcard != null);

				// choose compatible destination
				long mask = 0;
				
				// target can be cascade...
				boolean foundEmpty = false;
				for (int i = 0; i < numCascades; i++)
				{
					if (cascades[i].isEmpty())
					{
						// only use one empty cascade
						if (!foundEmpty)
						{
							foundEmpty = true;
							mask |= choiceIndex(i);
						}
					}
					else if (isCompatibleCascade(pcard, cascades[i]))
					{
						mask |= choiceIndex(i);
					}
				}

				// cell...
				if (source < numCascades || source >= numCascades + numCells) // do not move from cell to cell .. no point
				{
					for (int i = 0; i < numCells; i++)
					{
						if (cells[i] == null)
						{
							mask |= choiceIndex(i + numCascades);
							break; // only use first available empty cell
						}
					}
				}
				
				// or foundation...
				if (hasCompatibleFoundation(pcard)) // foundations are assigned to suits
				{
					mask |= choiceIndex(pcard.suit().ordinal() + numCascades + numCells);
				}

				// don't make source == target
				// or move the same card we just moved
				final long targetMask = mask & ~choiceIndex(source) & ~choiceIndex(lastTarget);
				
				if (targetMask == 0)
				{
					return MoveResult.NoMoves;
				}
				else
				{
					return decider.choose(new Choice()
					{
						@Override
						public long getPotentialMoves()
						{
							return targetMask;
						}

						@Override
						public MoveResult choose(int target) throws MoveFailedException
						{
							assert (source != target);
							MoveResult result = moveCard(source, target, statehash);
							assert(result == MoveResult.Ok);
							return result;
						}
					});
				}
			}

		});
		
		return topResult;
	}

	protected boolean hasCompatibleFoundation(PlayingCard card)
	{
		int fi = card.suit().ordinal();
		Pile topdeck = foundations[fi];
		if (topdeck.isEmpty())
			return card.rank() == PlayingCard.Rank.Ace;
		else
			return topdeck.peek().rank().ordinal() == card.rank().ordinal() - 1;
	}

	protected boolean isCompatibleCascade(PlayingCard card, Pile deck)
	{
		// descending alternating colors
		PlayingCard topdeck = deck.peek();
		return topdeck.color() != card.color() && topdeck.rank().ordinal() == card.rank().ordinal() + 1;
	}

	private PlayingCard getSourceCard(final int source)
	{
		PlayingCard pcard;
		// what card did we pick for source?
		if (source < numCascades)
		{
			pcard = cascades[source].peek();
		} else if (source < numCascades + numCells)
		{
			pcard = cells[source - numCascades];
		} else
		{
			pcard = foundations[source - numCascades - numCells].peek();
		}
		return pcard;
	}

	private MoveResult moveCard(int source, int target, long statehash)
	{
		PlayingCard pcard = getSourceCard(source);
		
		// pop card
		int player = getCurrentPlayer();
		if (source < numCascades)
		{
			cascades[source].pop();
			if (cascades[source].isEmpty())
				addPlayerScore(player, EMPTY_CASCADE_REWARD); // reward for empty cascade
		} else if (source < numCascades + numCells)
		{
			cells[source - numCascades] = null;
			addPlayerScore(player, CELL_PENALTY); // reward for moving out of cell
		} else if (source < numCascades + numCells + numFoundations)
		{
			Pile found = foundations[source - numCascades - numCells];
			found.pop();
			if (found.isEmpty())
				addPlayerScore(player, -FIRST_FOUNDATION_REWARD); // penalty for moving out of foundation
			else
				addPlayerScore(player, -NEXT_FOUNDATION_REWARD); // penalty for moving out of foundation
			cardsToGo++;
		} else
			assert(false);

		// move it to the target
		if (target < numCascades)
		{
			if (cascades[target].isEmpty())
				addPlayerScore(player, -EMPTY_CASCADE_REWARD); // penalty for non-empty cascade
			cascades[target].push(pcard);
		} else if (target < numCascades + numCells)
		{
			cells[target - numCascades] = pcard;
			addPlayerScore(player, -CELL_PENALTY); // penalty for moving into cell
		} else if (target < numCascades + numCells + numFoundations)
		{
			Pile found = foundations[target - numCascades - numCells];
			if (found.isEmpty())
				addPlayerScore(player, FIRST_FOUNDATION_REWARD); // reward for moving into foundation
			else
				addPlayerScore(player, NEXT_FOUNDATION_REWARD); // reward for moving into foundation
			found.push(pcard);
			cardsToGo--;
		} else
			assert (false);

		assert(cardsToGo >= 0);
		if (cardsToGo == 0)
		{
			win();
		}
		//System.out.println(source + "\t" + target + "\t" + pcard);
		this.lastSource = source;
		this.lastTarget = target;
		previousStateHashes.push(statehash);
		return MoveResult.Ok;
	}

	@Override
	public void dump(PrintStream out)
	{
		for (Pile c : cascades)
		{
			System.out.println("CASCADE\t\t" + c);
		}
		System.out.print("CELLS\t\t");
		for (PlayingCard card : cells)
		{
			if (card == null)
				System.out.print("---" + "\t");
			else
				System.out.print(card + "\t");
		}
		System.out.println();
		for (Pile f : foundations)
		{
			System.out.println("FOUNDATION\t" + f);
		}
	}

	public long hashFor(int seekingPlayer)
	{
		// TODO: this is pretty lazy
		final int prime = 49157; // http://planetmath.org/goodhashtableprimes
		long result = 1;
		result = prime * result ^ Arrays.hashCode(cascades);
		result = prime * result ^ Arrays.hashCode(cells);
		result = prime * result ^ Arrays.hashCode(foundations);
		//System.out.println(Long.toHexString(result));
		return result;
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
		Freecell other = (Freecell) obj;
		if (!Arrays.equals(cascades, other.cascades))
			return false;
		if (!Arrays.equals(cells, other.cells))
			return false;
		if (!Arrays.equals(foundations, other.foundations))
			return false;
		return true;
	}

	@Override
	public void enableHashing(boolean enable)
	{
	}

}
