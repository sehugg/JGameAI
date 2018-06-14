package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.games.cards.Freecell;

public class TestSinglePlayer extends BaseTestCase
{
	public void testFreecellMCTS()
	{
		Freecell state = new Freecell(0, 13);
		state.dump();
		simulate(state, 300000, 200, false, 50, 24, 1);
	}

}
