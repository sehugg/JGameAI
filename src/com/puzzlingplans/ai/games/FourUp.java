package com.puzzlingplans.ai.games;

import java.util.Arrays;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;

public class FourUp extends MNKGame<FourUp>
{
	int[] depths;
	long colmask;
	
	//

	public FourUp()
	{
		this(7, 6, 4, 2);
	}
	
	public FourUp(int width, int height, int k, int numPlayers)
	{
		super(width, height, k, numPlayers);
		depths = new int[width];
		colmask = ((1L<<width)-1);
	}
	
	@Override
	public FourUp clone() throws CloneNotSupportedException
	{
		FourUp copy = super.clone();
		copy.board = board.clone();
		copy.depths = Arrays.copyOf(depths, depths.length);
		return copy;
	}

	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		if (colmask == 0)
		{
			assert(!isGameOver());
			draw();
			return MoveResult.Ok;
		}
		
		return decider.choose(new Choice()
		{
			@Override
			public MoveResult choose(int index)
			{
				int x = board.i2x(index);
				makeMove(x);
				return MoveResult.Ok;
			}

			@Override
			public long getPotentialMoves()
			{
				return colmask;
			}
		});
	}

	public void makeMove(int x)
	{
		Piece piece = PieceValues[getCurrentPlayer()+1];
		int y = depths[x];
		assert(y < board.getHeight());
		assert(board.get(x, y) == Piece._);
		board.set(x, y, piece, getCurrentPlayer());
		// simple heuristic - doubled squares
		addPlayerScore(getCurrentPlayer(), doesWin(x, y, piece, 2) ? 1 : 0);
		if (++depths[x] >= board.getHeight())
		{
			colmask &= ~(1L<<x);
		}
		if (doesWin(x, y, piece, k))
		{
			win();
		} else {
			nextPlayer();
		}
	}

}
