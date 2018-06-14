package com.puzzlingplans.ai.games.cards;

import java.io.PrintStream;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.UniformRandomChoice;
import com.puzzlingplans.ai.cards.Hand;
import com.puzzlingplans.ai.cards.ShuffledDeck;

public class Poker extends GameState<Poker>
{
	private ShuffledDeck deck;
	private Hand[] hands;
	private int maxPlayerBet;
	private int pot;
	private int phase;
	private int bettingRound;
	private int numPlayersFolded;

	private int ante = 20;
	private int incBetAmt = 100;
	private int maxRaiseAmt = 300;
	private int cardsPerHand = 5;

	Phase[] states = { Phase.Deal, Phase.Bet, Phase.Draw, Phase.Bet, Phase.Show };
	
	private boolean determinized;
	
	//
	
	public enum Phase
	{
		Deal,
		Bet,
		Draw,
		Show
	}
	
	//

	public Poker(int numPlayers)
	{
		super(numPlayers);

		this.hands = new Hand[numPlayers];
		for (int i = 0; i < numPlayers; i++)
			hands[i] = new Hand();
		this.deck = new ShuffledDeck(52);
	}
	
	@Override
	protected Poker clone() throws CloneNotSupportedException
	{
		Poker copy = super.clone();
		copy.deck = (ShuffledDeck) deck.clone();
		copy.hands = new Hand[hands.length];
		for (int i = 0; i < hands.length; i++)
			copy.hands[i] = hands[i].clone();
		return copy;
	}
	
	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		switch (states[phase])
		{
			case Deal:
				// deal cards until player hand is full
				return deal(decider);

			case Bet:
				// skip players until we have a bet, or loop back around
				int initialPlayer = getCurrentPlayer();
				do {
					int currentBet = -getAbsoluteScore(getCurrentPlayer());
					int minBet = maxPlayerBet - currentBet;
					int maxBet = maxRaiseAmt * bettingRound - currentBet;
					// make sure there's a bet, and this player hasn't folded
					if (maxBet > 0 && !hands[getCurrentPlayer()].isEmpty())
					{
						int m = minBet / incBetAmt;
						int n = (maxBet - minBet) / incBetAmt;
						// bit 1 = option to fold if call for raise
						final long options = (choiceMask(n + 1) << (m + 1)) | (minBet > 0 ? 1 : 0);
						//System.out.println(currentBet + " " + minBet + " " + maxBet + " " + Long.toBinaryString(options));
						// fold (bit 0) call (bit 1) or bet (bits 2+)
						return decider.choose(new Choice()
						{
							@Override
							public long getPotentialMoves()
							{
								return options;
							}
				
							@Override
							public MoveResult choose(int choice) throws MoveFailedException
							{
								// fold?
								int player = getCurrentPlayer();
								if (choice == 0)
								{
									// TODO: do we care how the rest of this plays out if the seeking player folds?
									hands[player].clearAll();
									numPlayersFolded++;
									// one player left? they win (not really a showdown...)
									if (numPlayersFolded == getNumPlayers() - 1)
									{
										showdown(false);
										return MoveResult.Ok;
									}
								}
								// bet?
								else if (choice > 1)
								{
									int bet = (choice - 1) * incBetAmt;
									addToPot(player, bet);
								}
								nextPlayer();
								return endTurn(decider);
							}
						});
					}
					nextPlayer();
				} while (getCurrentPlayer() != initialPlayer);
				// back at the starting player
				gotoNextPhase();
				return playTurn(decider);
				
			case Draw:
				// choose cards one-at-a-time to discard
				long cardsInHand = hands[getCurrentPlayer()].getCards();
				// if no cards, skip to next player
				if (cardsInHand != 0)
				{
					return discard(decider, cardsInHand);
				} else {
					nextPlayerOrPhase();
					return playTurn(decider);
				}
				
			case Show:
				showdown(true);
				return MoveResult.Ok;
				
			default:
				throw new RuntimeException(states[phase] + "???");
		}
	}
		
	protected MoveResult endTurn(Decider decider) throws MoveFailedException
	{
		// if any players have concealed information, determinize it (shuffle their cards)
		if (decider.getSeekingPlayer() != Decider.RealLife && !determinized)
		{
			determinized = true;
			putOtherPlayersHoleCardsBackInDeck(decider.getSeekingPlayer());
			return redealHoleCards(decider);
		} else {
			return MoveResult.Ok;
		}
	}

	private void putOtherPlayersHoleCardsBackInDeck(int seekingPlayer)
	{
		for (int i=0; i<getNumPlayers(); i++)
		{
			if (i == seekingPlayer)
				continue;
			
			deck.addCards(hands[i].getHoleCards());
		}
	}

	private MoveResult redealHoleCards(Decider decider) throws MoveFailedException
	{
		for (int i=0; i<getNumPlayers(); i++)
		{
			if (i == decider.getSeekingPlayer())
				continue;
			
			if (hands[i].hasHoleCards())
			{
				redealHoleCardsForPlayer(decider, i);
			}
		}
		return MoveResult.Ok;
	}

	private MoveResult redealHoleCardsForPlayer(final Decider decider, final int player) throws MoveFailedException
	{
		// player is assumed to have at least one hole card
		assert(hands[player].hasHoleCards());
		// take a hole card out
		int holeCard = hands[player].firstHoleCard();
		hands[player].removeCard(holeCard);
		
		return decider.choose(new UniformRandomChoice()
		{
			@Override
			public long getPotentialMoves()
			{
				return deck.getCards();
			}

			@Override
			public MoveResult choose(int choice) throws MoveFailedException
			{
				// deal a new card from the deck
				deck.removeCard(choice);
				hands[player].addCard(choice);
				// recurse, we might have to finish replacing the hand
				//System.out.println(player + "\t" + Long.toHexString(deck.getCards()) + "\t" + hands[player]);
				return redealHoleCards(decider);
			}
		});
	}

	static long NoCard = 63;

	private MoveResult discard(final Decider decider, final long cards) throws MoveFailedException
	{
		if (cards == 0)
			return MoveResult.Ok;
		
		// choose cards one-at-a-time to keep, or end discarding and draw
		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return cards | (1L<<NoCard);
			}
			
			@Override
			public MoveResult choose(int choice) throws MoveFailedException
			{
				// finished, draw cards
				if (choice == NoCard)
				{
					hands[getCurrentPlayer()] = new Hand(0, cards);
					//System.out.println("Discard to " + hands[getCurrentPlayer()]);
					// draw new cards
					return deal(decider);
				} else {
					// option to discard another one
					return discard(decider, cards & ~(1L<<choice));
				}
			}
		});
	}

	private MoveResult deal(final Decider decider) throws MoveFailedException
	{
		final int player = getCurrentPlayer();
		// if not enough cards, deal one
		if (hands[player].size() < cardsPerHand)
		{
			return decider.choose(new UniformRandomChoice()
			{
				@Override
				public long getPotentialMoves()
				{
					return deck.getCards();
				}

				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					deck.removeCard(choice);
					hands[player].addHoleCard(choice);
					return deal(decider);
				}
			});
		} else {
			nextPlayerOrPhase();
			return endTurn(decider);
		}
	}

	private void nextPlayerOrPhase()
	{
		nextPlayer();
		if (getCurrentPlayer() == 0)
			gotoNextPhase();
	}

	private void gotoNextPhase()
	{
		setCurrentPlayer(0);
		phase++;
		if (states[phase] == Phase.Bet)
			bettingRound++;
	}

	private void addToPot(int player, int amt)
	{
		pot += amt;
		addPlayerScore(player, -amt);
		maxPlayerBet = Math.max(maxPlayerBet, -getAbsoluteScore(player));
	}

	protected void showdown(boolean showCards)
	{
		// TODO: split the pot?
		long best = -1;
		int bestplayer = -1;
		for (int i = 0; i < getNumPlayers(); i++)
		{
			long rank = hands[i].getPokerRank(5);
			assert(rank >= 0);
			//System.out.println(hands[i] + " " + rank);
			if (rank > best)
			{
				best = rank;
				bestplayer = i;
			}
			// ante at end
			addToPot(i, ante);
		}
		// give the pot to the winner
		addPlayerScore(bestplayer, pot);
		setGameOver(true);
	}

	@Override
	public void dump(PrintStream out)
	{
		System.out.println("Phase " + phase + " " + states[phase] + ", $" + pot + " in pot, " + deck.size() + " cards in deck");
	}

	@Override
	public String playerToString(int player)
	{
		return super.playerToString(player) + "\t" + hands[player];
	}

	public Phase getPhase()
	{
		return states[phase];
	}

	// any above-zero score is a winning score
	public int getWinningScore()
	{
		return 1;
	}
	
	// use absolute scores
	@Override
	public int getModifiedScore(int player)
	{
		return getAbsoluteScore(player);
	}

}
