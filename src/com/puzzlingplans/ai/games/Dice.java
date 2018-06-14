package com.puzzlingplans.ai.games;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.UniformRandomChoice;

// http://lists.gnu.org/archive/html/bug-gnubg/2003-08/msg00407.html
public class Dice extends MNKGame<Dice>
{
	public static final int LevelsPerTurn = 2;
	
	public boolean skipTurnWhenNoMoves = false; // either way tests a different set of stuff...
	
	//

	public Dice()
	{
		this(3, 3, 3, 2);
	}
	
	public Dice(int width, int height, int k, int numPlayers)
	{
		super(width, height, k, numPlayers);
	}
	
	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		// out of squares?
		if (board.getAllUnoccupied().isEmpty())
		{
			draw();
			return MoveResult.Ok;
		}
		
		final boolean rows = (getCurrentPlayer() & 1) != 0;
		// determine which row or column
		return decider.choose(new UniformRandomChoice()
		{
			@Override
			public MoveResult choose(final int rowcol) throws MoveFailedException
			{
				final long moves = rows ? getBoard().getUnoccupiedForRow(rowcol) : getBoard().getUnoccupiedForColumn(rowcol);
				if (skipTurnWhenNoMoves && moves == 0)
				{
					// no move in row/col, pass
					nextPlayer();
					return MoveResult.Ok;
				}
				return decider.choose(new Choice()
				{
					@Override
					public long getPotentialMoves()
					{
						return moves;
					}

					@Override
					public MoveResult choose(int index) throws MoveFailedException
					{
						if (rows)
							makeMove(index, rowcol);
						else
							makeMove(rowcol, index);
						return MoveResult.Ok;
					}
				});
			}

			@Override
			public long getPotentialMoves()
			{
				return choiceMask(rows ? getBoard().getHeight() : getBoard().getWidth());
			}
		});
	}

}
