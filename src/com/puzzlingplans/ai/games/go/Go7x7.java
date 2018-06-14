package com.puzzlingplans.ai.games.go;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;

public class Go7x7 extends Go implements HashedPosition
{
	public static final Piece[] PieceValues = Piece.values();

	public static final int LevelsPerTurn = 1;
	
	protected static final int PASS = 63;

	//

	public Go7x7(int boardSize, int numPlayers)
	{
		super(boardSize, numPlayers);
		assert(boardSize <= 7);
	}

	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return board.getUnoccupied64() | (1L << PASS);
			}

			@Override
			public long getPreferredMoves()
			{
				return ~(1L << PASS); // don't pass in rollout
			}

			@Override
			public MoveResult choose(final int index) throws MoveFailedException
			{
				if (index == PASS)
					return pass();
				
				int col = board.i2x(index);
				int row = board.i2y(index);
				return makeMove(col, row);
			}
		});
	}

	@Override
	public int coord2index(String coord)
	{
		if (coord.toLowerCase().equals("pass"))
			return -1;
		
		char ch = coord.toLowerCase().charAt(0);
		if (ch > 'i')
			ch--;
		int x = ch - 'a';
		int y = Integer.parseInt(coord.substring(1)) - 1;
		if (!board.inBounds(x, y))
			return -1;
		else
			return board.xy2i(x, y);
	}

	@Override
	public String move2coord(Line<?> move)
	{
		int index = move.getMoveAtDepth(1).getMoveIndex();
		if (index == PASS)
			return "pass";
		
		int x = board.i2x(index);
		int y = board.i2y(index);
		if (!board.inBounds(x, y))
			return "?";
		
		char rank = (char)(x + 'A');
		if (rank >= 'I')
			rank++;
		int row = y + 1;
		return rank + "" + row;
	}
}
