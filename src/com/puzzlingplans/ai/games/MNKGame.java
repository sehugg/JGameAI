package com.puzzlingplans.ai.games;

import java.io.PrintStream;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.board.HashKeepingEnumGrid;
import com.puzzlingplans.ai.board.OccupiedGrid;

public class MNKGame<T extends MNKGame<T>> extends GameState<T> implements HashedPosition
{
	public static final Piece[] PieceValues = Piece.values();

	public enum Piece {
		_, X, O, Y, Z, XX, OO, YY, ZZ
	}

	HashKeepingEnumGrid<Piece> board;
	int k;
	
	//

	public MNKGame(int m, int n, int k, int players)
	{
		super(players);
		assert(players <= 8);
		// TODO: only need piece enum or players, not both
		board = new HashKeepingEnumGrid<Piece>(m, n, Piece._, players, 0);
		this.k = k;
	}

	@Override
	public T clone() throws CloneNotSupportedException
	{
		T copy = super.clone();
		copy.board = board.clone();
		return copy;
	}

	public OccupiedGrid<Piece> getBoard()
	{
		return board;
	}
	
	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		final long unoccupied = board.getUnoccupied64();
		if (unoccupied == 0)
		{
			assert(!isGameOver());
			draw();
			return MoveResult.Ok;
		}
		
		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return unoccupied;
			}

			@Override
			public MoveResult choose(int index)
			{
				int x = board.i2x(index);
				int y = board.i2y(index);
				makeMove(x, y);
				return MoveResult.Ok;
			}
		});
	}

	public void makeMove(int x, int y)
	{
		Piece piece = PieceValues[getCurrentPlayer()+1];
		assert(board.get(x, y) == Piece._);
		board.set(x, y, piece, getCurrentPlayer());
		// simple heuristic - doubled squares
		addPlayerScore(getCurrentPlayer(), doesWin(x, y, piece, 2) ? 1 : 0);
		// k-in-a-row wins
		if (doesWin(x, y, piece, k))
		{
			win();
		} else {
			nextPlayer();
		}
	}

	public boolean doesWin(int x, int y, Piece p, int n)
	{
		if (board.matchRow(y, n, p))
			return true;
		if (board.matchColumn(x, n, p))
			return true;
		if (board.matchDiagonal(x, y, n, p))
			return true;
		return false;
	}

	@Override
	public void dump(PrintStream out)
	{
		board.dump(out);
	}

	@Override
	public long hashFor(int seekingPlayer)
	{
		return board.hash();
	}

	@Override
	public void enableHashing(boolean enable)
	{
		board.enableHashing(enable);
	}

}
