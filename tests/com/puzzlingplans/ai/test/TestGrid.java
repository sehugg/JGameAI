package com.puzzlingplans.ai.test;

import com.puzzlingplans.ai.board.Grid;
import com.puzzlingplans.ai.board.HashKeepingGrid;
import com.puzzlingplans.ai.board.OccupiedGrid;
import com.puzzlingplans.ai.util.Revertable;

public class TestGrid extends BaseTestCase {

	public void testGrid()
	{
		Grid<Integer> igrid = new Grid<Integer>(3,3,0); 
		assertTrue(igrid.matchRow(0,1,0));
		assertTrue(igrid.matchRow(0,3,0));
		assertFalse(igrid.matchRow(0,4,0));
		assertFalse(igrid.matchRow(0,3,99));
		assertTrue(igrid.matchColumn(0,1,0));
		assertTrue(igrid.matchColumn(0,3,0));
		assertFalse(igrid.matchColumn(0,4,0));
		assertFalse(igrid.matchColumn(0,3,99));
		assertTrue(igrid.matchDiagonal(0,0,3,0));
		assertFalse(igrid.matchDiagonal(0,0,3,1));
		igrid.set(0, 0, 1);
		igrid.set(1, 1, 1);
		igrid.set(2, 2, 1);
		assertTrue(igrid.matchDiagonal(0,0,3,1));
		assertTrue(igrid.matchDiagonal(0,0,2,1));
		assertTrue(igrid.matchDiagonal(1,1,2,1));
		assertTrue(igrid.matchDiagonal(2,2,2,1));
		assertFalse(igrid.matchDiagonal(1,0,2,1));
	}
	
	public void testOccupied()
	{
		OccupiedGrid<Character> igrid = new OccupiedGrid<Character>(3, 3, ' ', 2);
		igrid.set(0,0,'a',0);
		igrid.set(1,1,'x',0);
		igrid.set(1,1,'y',1);
		igrid.set(2,1,'y',1);
		igrid.dump(System.out);
		assertEquals(1, igrid.getOccupied64(0));
		assertEquals("{0}", igrid.getOccupiedFor(0).toString());
		assertEquals(0x30, igrid.getOccupied64(1));
		assertEquals("{4, 5}", igrid.getOccupiedFor(1).toString());
		assertEquals(1, igrid.getOccupiedForRow(0));
		assertEquals(2|4, igrid.getUnoccupiedForRow(0));
		assertEquals(1, igrid.getUnoccupiedForRow(1));
		assertEquals(2|4, igrid.getOccupiedForRow(1));
		assertEquals(1|2|4, igrid.getUnoccupiedForRow(2));
		assertEquals(0, igrid.getOccupiedForRow(2));
		assertEquals(1, igrid.getOccupiedForColumn(0));
		assertEquals(2|4, igrid.getUnoccupiedForColumn(0));
		assertEquals(2, igrid.getOccupiedForColumn(1));
		assertEquals(1|4, igrid.getUnoccupiedForColumn(1));
		assertEquals(1, igrid.getColorOccupiedForRow(0, 0));
		assertEquals(0, igrid.getColorOccupiedForRow(0, 1));
		assertEquals(2|4, igrid.getColorOccupiedForRow(1, 1));
		assertEquals(1, igrid.getColorOccupiedForColumn(0, 0));
		assertEquals(0, igrid.getColorOccupiedForColumn(0, 1));
		assertEquals(0, igrid.getColorOccupiedForColumn(1, 0));
		assertEquals(2, igrid.getColorOccupiedForColumn(1, 1));
		assertEquals("{1, 2, 3, 6, 7, 8}", igrid.getAllUnoccupied().toString());
		assertEquals("{0, 4, 5}", igrid.getAllOccupied().toString());
	}

	public void testOccupiedBig()
	{
		OccupiedGrid<Character> igrid = new OccupiedGrid<Character>(9, 9, ' ', 2);
		igrid.set(0,0,'a',0);
		igrid.set(1,1,'x',0);
		igrid.set(1,1,'y',1);
		igrid.set(2,1,'y',1);
		igrid.dump(System.out);
		assertEquals("{0}", igrid.getOccupiedFor(0).toString());
		assertEquals("{10, 11}", igrid.getOccupiedFor(1).toString());
		assertEquals(1, igrid.getOccupiedForRow(0));
		assertEquals(((1L<<9)-1) ^ (1), igrid.getUnoccupiedForRow(0));
		assertEquals(2|4, igrid.getOccupiedForRow(1));
		assertEquals(((1L<<9)-1) ^ (2|4), igrid.getUnoccupiedForRow(1));
		assertEquals(0, igrid.getOccupiedForRow(2));
		assertEquals(1, igrid.getOccupiedForColumn(0));
		assertEquals(((1L<<9)-1) ^ (1), igrid.getUnoccupiedForColumn(0));
		assertEquals(2, igrid.getOccupiedForColumn(1));
		assertEquals(((1L<<9)-1) ^ (2), igrid.getUnoccupiedForColumn(1));
		assertEquals(1, igrid.getColorOccupiedForRow(0, 0));
		assertEquals(0, igrid.getColorOccupiedForRow(0, 1));
		assertEquals(2|4, igrid.getColorOccupiedForRow(1, 1));
		assertEquals(1, igrid.getColorOccupiedForColumn(0, 0));
		assertEquals(0, igrid.getColorOccupiedForColumn(0, 1));
		assertEquals(0, igrid.getColorOccupiedForColumn(1, 0));
		assertEquals(2, igrid.getColorOccupiedForColumn(1, 1));
		for (int i=3; i<9; i++)
		{
			assertEquals(0, igrid.getOccupiedForRow(i));
			igrid.set(1, i, '?', 1);
			igrid.set(7, i, '?', 1);
			assertEquals(2|128, igrid.getOccupiedForRow(i));
		}
		igrid.set(0, ' ', -1);
		for (int i=1; i<igrid.getNumCells()-1; i++)
			igrid.set(i, 'x', 0);
		assertEquals("{0, 80}", igrid.getAllUnoccupied().toString());
	}

	public void testCopy() throws CloneNotSupportedException
	{
		OccupiedGrid<Integer> igrid = new OccupiedGrid<Integer>(3, 3, -1, 2);
		igrid.set(1,2,99,0);
		assertEquals(-1, (int)igrid.get(0,0));
		assertEquals(99, (int)igrid.get(1,2));
		assertEquals("{7}", igrid.getOccupiedFor(0).toString());
		assertEquals("{}", igrid.getOccupiedFor(1).toString());
		OccupiedGrid<Integer> igrid2 = igrid.clone();
		igrid.set(0,0,0,1);
		igrid.set(1,2,0,1);
		assertEquals(-1, (int)igrid2.get(0,0));
		assertEquals(99, (int)igrid2.get(1,2));
		assertEquals("{7}", igrid2.getOccupiedFor(0).toString());
		assertEquals("{}", igrid2.getOccupiedFor(1).toString());
	}
	
	public void testUndo()
	{
		OccupiedGrid<Integer> igrid = new OccupiedGrid<Integer>(3, 3, -1, 2);
		assertEquals(-1, (int)igrid.get(0,0));
		Revertable undo = igrid.setWithUndo(0, 50, 0);
		assertEquals(50, (int)igrid.get(0,0));
		undo = igrid.setWithUndo(0, 51, 0).next(undo);
		assertEquals(51, (int)igrid.get(0,0));
		undo.undoAll();
		assertEquals(-1, (int)igrid.get(0,0));
	}
	
	public void testZobrist()
	{
		HashKeepingGrid<Character> grid = new HashKeepingGrid<Character>(10, 10, ' ', 2, 4, 0)
		{
			@Override
			public int getPieceTypeIndex(Character t)
			{
				return t.charValue() & 3;
			}
		};
		assertEquals(1, grid.hash());
		grid.set(0, 0, 'a', -1);
		//assertEquals(-8740502649270427258L, grid.hash());
		grid.set(0, 0, ' ', 1);
		grid.set(0, 0, ' ', 0);
		//assertEquals(2582830200849692245L, grid.hash());
		grid.set(0, 0, ' ', -1);
		assertEquals(1, grid.hash());
	}

	public void testZobristBigGrid()
	{
		HashKeepingGrid<Character> grid = new HashKeepingGrid<Character>(1000, 1000, ' ', 2, 4, 0)
		{
			@Override
			public int getPieceTypeIndex(Character t)
			{
				return t.charValue() & 3;
			}
		};
		assertEquals(1, grid.hash());
		grid.set(0, 0, 'a', -1);
		grid.set(0, 0, ' ', 1);
		grid.set(0, 0, ' ', 0);
		grid.set(900, 900, 'x', 0);
		grid.set(0, 0, ' ', -1);
		grid.set(900, 900, ' ', -1);
		assertEquals(1, grid.hash());
	}

	public void testPerformance()
	{
		// 540 ms without hashes
		// 330 ms with hashes
		// 340 ms with just the indices (4x smaller memory)
		final HashKeepingGrid<Character> grid = new HashKeepingGrid<Character>(10, 10, ' ', 3, 32, 0)
		{
			@Override
			public int getPieceTypeIndex(Character t)
			{
				return t.charValue() & 31;
			}
		};
		long time = benchmark("grid", 10000000, new Runnable()
		{
			int x = 0;
			int y = 0;
			char val = ' ';
			int color;
			@Override
			public void run()
			{
				grid.set(x, y, val, color);
				
				x = (x+1)&7;
				y = (y+1)&7;
				val = (char) (((val + 1) & 31) + ' ');
				color = (color+1)&1;
			}
		});
		assert(time < 500);
	}
}
