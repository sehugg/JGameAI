package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.RandomDecider;
import com.puzzlingplans.ai.cards.Hand;
import com.puzzlingplans.ai.cards.PlayingCard;
import com.puzzlingplans.ai.cards.PlayingCard.Rank;
import com.puzzlingplans.ai.cards.PlayingCard.Suit;
import com.puzzlingplans.ai.games.cards.Poker;
import com.puzzlingplans.ai.search.MCTS;
import com.puzzlingplans.ai.search.MCTS.Option;
import com.puzzlingplans.ai.util.MiscUtils;

public class TestCards extends BaseTestCase
{
	public void testHand1()
	{
		Hand hand = new Hand();
		hand.addCard(Rank._2, Suit.Diamonds);
		hand.addCard(Rank._2, Suit.Hearts);
		hand.addCard(Rank._3, Suit.Hearts);
		hand.addCard(Rank._4, Suit.Hearts);
		hand.addCard(Rank._8, Suit.Clubs);
		hand.addCard(Rank._9, Suit.Spades);
		assertEquals("[2h, 3h, 4h]", Hand.toCards(hand.highestFlush(2)).toString());
		assertEquals("[2h, 3h, 4h]", Hand.toCards(hand.highestFlush(3)).toString());
		assertEquals("[]", Hand.toCards(hand.highestFlush(4)).toString());
		assertEquals("[2h, 2d]", Hand.toCards(hand.allNofaKind(2)).toString());
		assertEquals("[]", Hand.toCards(hand.allNofaKind(3)).toString());
		assertEquals("[]", Hand.toCards(hand.highestStraight(4)).toString());
		assertEquals("[2h, 3h, 4h]", Hand.toCards(hand.highestStraight(3)).toString());
		assertEquals("[2h, 3h, 4h]", Hand.toCards(hand.highestStraight(2)).toString());
		hand.removeCard(new PlayingCard(Rank._2, Suit.Hearts));
		assertEquals("[2d, 3h, 4h]", Hand.toCards(hand.highestStraight(2)).toString());
		hand.removeCard(new PlayingCard(Rank._3, Suit.Hearts));
		assertEquals("[8C, 9S]", Hand.toCards(hand.highestStraight(2)).toString());
	}
	
	public void testPokerHands()
	{
		Hand hand;
		hand = new Hand();
		hand.addCard(Rank._5, Suit.Diamonds);
		hand.addCard(Rank._5, Suit.Hearts);
		hand.addCard(Rank._3, Suit.Hearts);
		hand.addCard(Rank._4, Suit.Hearts);
		hand.addCard(Rank._8, Suit.Clubs);
		Hand pair = hand;

		hand = new Hand();
		hand.addCard(Rank._5, Suit.Clubs);
		hand.addCard(Rank._5, Suit.Hearts);
		hand.addCard(Rank._3, Suit.Hearts);
		hand.addCard(Rank._4, Suit.Hearts);
		hand.addCard(Rank._8, Suit.Hearts);
		Hand pair2 = hand;

		hand = new Hand();
		hand.addCard(Rank._6, Suit.Clubs);
		hand.addCard(Rank._6, Suit.Hearts);
		hand.addCard(Rank._9, Suit.Hearts);
		hand.addCard(Rank._4, Suit.Hearts);
		hand.addCard(Rank._8, Suit.Hearts);
		Hand pair3 = hand;

		hand = new Hand();
		hand.addCard(Rank._3, Suit.Clubs);
		hand.addCard(Rank._3, Suit.Hearts);
		hand.addCard(Rank._2, Suit.Hearts);
		hand.addCard(Rank._2, Suit.Spades);
		hand.addCard(Rank._8, Suit.Hearts);
		Hand twopair = hand;

		hand = new Hand();
		hand.addCard(Rank._3, Suit.Clubs);
		hand.addCard(Rank._3, Suit.Hearts);
		hand.addCard(Rank._4, Suit.Hearts);
		hand.addCard(Rank._4, Suit.Spades);
		hand.addCard(Rank._8, Suit.Hearts);
		Hand twopair2 = hand;

		hand = new Hand();
		hand.addCard(Rank._2, Suit.Diamonds);
		hand.addCard(Rank._2, Suit.Hearts);
		hand.addCard(Rank._2, Suit.Spades);
		hand.addCard(Rank._4, Suit.Hearts);
		hand.addCard(Rank._8, Suit.Clubs);
		Hand three = hand;

		hand = new Hand();
		hand.addCard(Rank._2, Suit.Diamonds);
		hand.addCard(Rank._3, Suit.Hearts);
		hand.addCard(Rank._4, Suit.Spades);
		hand.addCard(Rank._5, Suit.Clubs);
		hand.addCard(Rank._6, Suit.Clubs);
		Hand straight = hand;

		hand = new Hand();
		hand.addCard(Rank._2, Suit.Hearts);
		hand.addCard(Rank._3, Suit.Hearts);
		hand.addCard(Rank._4, Suit.Hearts);
		hand.addCard(Rank._6, Suit.Hearts);
		hand.addCard(Rank._7, Suit.Hearts);
		Hand flush = hand;

		hand = new Hand();
		hand.addCard(Rank._2, Suit.Diamonds);
		hand.addCard(Rank._2, Suit.Hearts);
		hand.addCard(Rank._3, Suit.Spades);
		hand.addCard(Rank._3, Suit.Clubs);
		hand.addCard(Rank._3, Suit.Hearts);
		Hand fullhouse = hand;

		hand = new Hand();
		hand.addCard(Rank._2, Suit.Diamonds);
		hand.addCard(Rank._2, Suit.Hearts);
		hand.addCard(Rank._2, Suit.Spades);
		hand.addCard(Rank._2, Suit.Clubs);
		hand.addCard(Rank._8, Suit.Clubs);
		Hand four = hand;

		hand = new Hand();
		hand.addCard(Rank._2, Suit.Spades);
		hand.addCard(Rank._3, Suit.Spades);
		hand.addCard(Rank._4, Suit.Spades);
		hand.addCard(Rank._5, Suit.Spades);
		hand.addCard(Rank._6, Suit.Spades);
		Hand straightflush = hand;

		assertHandTies(pair2, pair);
		assertHandBeats(pair3, pair);
		assertHandBeats(pair3, pair2);
		assertHandBeats(twopair, pair3);
		assertHandBeats(twopair2, twopair);
		assertHandBeats(three, pair);
		assertHandBeats(straight, three);
		assertHandBeats(flush, straight);
		assertHandBeats(fullhouse, flush);
		assertHandBeats(four, fullhouse);
		assertHandBeats(straightflush, four);
	}

	private void assertHandTies(Hand a, Hand b)
	{
		assertEquals(a.getPokerRank(5), b.getPokerRank(5));
	}

	private void assertHandBeats(Hand winner, Hand loser)
	{
		long win = winner.getPokerRank(5);
		long lose = loser.getPokerRank(5);
		System.out.println(MiscUtils.format("%x %x", win, lose));
		assertTrue(win > lose);
	}

	public void testPokerPlayout() throws MoveFailedException
	{
		Poker state = new Poker(3);
		RandomDecider decider = new RandomDecider();
		while (!state.isGameOver())
		{
			state.playTurn(decider);
			state.dump();
		}
	}

	public void testPokerMCTSBet() throws MoveFailedException
	{
		Poker state = new Poker(3);
		RandomDecider decider = new RandomDecider();
		// deal cards
		for (int i=0; i<state.getNumPlayers(); i++)
			state.playTurn(decider);
		state.dump();
		// do the math
		MCTS mcts = new MCTS(100);
		mcts.setDebug(false);
		mcts.setExplorationConstant(1000.0 * state.getNumPlayers() / GameState.WIN);
		mcts.setGoodMoveProbability(80); // so much branching we don't really care...??
		mcts.setOption(Option.UseAbsoluteScores, true); // so that winners can win
		simulate(state, mcts, 100000);
	}

}
