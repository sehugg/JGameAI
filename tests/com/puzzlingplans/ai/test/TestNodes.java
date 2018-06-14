package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.search.IterableLine;

public class TestNodes extends BaseTestCase
{
	public void testIterable() throws MoveFailedException
	{
		GameState<?> state = new SampleGames.BinaryGame();
		IterableLine.debug = true;
		IterableLine root  = IterableLine.newRootInstance();
		assertEquals(":", root.toString());
		assertTrue(root.hasNext());
		IterableLine line0 = root.iterateTurn(state, 0);
		assertEquals(":0:", line0.toString());
		assertTrue(line0.hasNext());
		IterableLine line00 = line0.iterateTurn(state, 0);
		assertEquals(":0:0:", line00.toString());
		assertTrue(line0.hasNext());
		line00.complete();
		line00.complete();
		try {
			line00.iterateTurn(state, 0);
			fail();
		} catch (Exception e) {
			System.out.println(e);
		}
		//line00.end();
		assertEquals(":0:0:", line00.toString());
		assertTrue(line0.hasNext());
		IterableLine line01 = line0.iterateTurn(state, 0);
		assertEquals(":0:1:", line01.toString());
		assertTrue(line0.hasNext());
		IterableLine line02 = line0.iterateTurn(state, 0);
		assertEquals(":0:2:", line02.toString());
	}
}
