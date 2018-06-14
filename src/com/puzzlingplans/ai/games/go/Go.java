package com.puzzlingplans.ai.games.go;

import java.io.PrintStream;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.board.HashKeepingEnumGrid;
import com.puzzlingplans.ai.board.OccupiedGrid;
import com.puzzlingplans.ai.util.FastBitSet;

public class Go extends GameState<Go> implements HashedPosition
{
	public static final Piece[] PieceValues = Piece.values();

	public enum Piece {
		_, X, O, A, B, C, D, E, F,
	}
	
	public static final int EMPTY = -1;
	public static final int WALL  = -2;
	
	public static final int LevelsPerTurn = 2;
	
	private static final int PASS_PENALTY = GameState.WIN/20; // penalize both players for passing

	HashKeepingEnumGrid<Piece> board;
	FastBitSet<?> visited;
	FastBitSet<?> captured;
	int consecutive_passes;
	long allRowsPlusPass;
	long allRowsWithoutPass;
	
	//

	public Go(int boardSize, int numPlayers)
	{
		super(numPlayers);
		board = new HashKeepingEnumGrid<Go.Piece>(boardSize, boardSize, Piece._, numPlayers, 0);
		visited = FastBitSet.create(board.getWidth() * board.getHeight());
		captured = FastBitSet.create(board.getWidth() * board.getHeight());
		allRowsPlusPass = choiceMask(board.getHeight() + 1);
		allRowsWithoutPass = choiceMask(board.getHeight());
	}

	@Override
	public Go clone() throws CloneNotSupportedException
	{
		Go copy = super.clone();
		copy.board = board.clone();
		// TODO: need a multi-threaded safe clone()
		copy.visited = (FastBitSet) visited.clone();
		copy.captured = (FastBitSet) captured.clone();
		return copy;
	}

	public OccupiedGrid<Piece> getBoard()
	{
		return board;
	}
	
	@Override
	public void dump(PrintStream out)
	{
		board.dump(out);
	}
	
	// TODO: probably inefficient to use both Piece and board color
	
	public int get(int x, int y)
	{
		return board.inBounds(x, y) ? board.get(x, y).ordinal() - 1 : WALL;
	}
	
	public void set(int x, int y, int player)
	{
		board.set(x, y, PieceValues[player+1], player);
	}

	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return allRowsPlusPass;
			}
			
			@Override
			public long getPreferredMoves()
			{
				// avoid passing
				return allRowsWithoutPass;
			}

			@Override
			public MoveResult choose(final int row) throws MoveFailedException
			{
				if (row == board.getHeight())
					return pass();
				
				return decider.choose(new Choice()
				{
					@Override
					public long getPotentialMoves()
					{
						return board.getUnoccupiedForRow(row);
					}

					@Override
					public MoveResult choose(int col) throws MoveFailedException
					{
						return makeMove(col, row);
					}
				});
			}
		});
	}

	public MoveResult pass()
	{
		if (++consecutive_passes == getNumPlayers())
		{
			// ai_add_player_score(ai_current_player(), -100);
			final_scoring();
			highestScoreWins();
		} else {
			nextPlayer();
		}
		return MoveResult.Ok;
	}

	@Override
	public int getModifiedScore(int player)
	{
		return super.getModifiedScore(player) - consecutive_passes * PASS_PENALTY;
	}

	public void final_scoring()
	{
		consecutive_passes = 0;
		for (int i=0; i<getNumPlayers(); i++)
			setPlayerScore(i, board.getOccupiedFor(i).cardinality());
	}
	
	private void area_scoring()
	{
		// TODO
		for (int player = 0; player < getNumPlayers(); player++)
		{
			int score = 0;
			visited.clear();
			for (int y = 0; y < board.getHeight(); y++)
			{
				for (int x = 0; x < board.getWidth(); x++)
				{
					score += surrounds_area(player, x, y) ? 1 : 0;
				}
			}
			setPlayerScore(player, score);
		}
	}

	boolean surrounds_area(int player, int x, int y)
	{
		// how many liberties?
		int p = get(x, y);
		// flood-fill empty areas
		if (p == EMPTY)
		{
			// don't visit cells twice (opposite of has_liberties)
			int i = board.xy2i(x, y);
			if (visited.get(i))
				return false;

			set(x, y, EMPTY);
			visited.set(i);
			return surrounds_area(player, x - 1, y)
					&& surrounds_area(player, x + 1, y)
					&& surrounds_area(player, x, y - 1)
					&& surrounds_area(player, x, y + 1);
		} else
		{
			// we control an area if it's bordered by our piece or by the wall
			return p == player || p == WALL;
		}
	}

	public MoveResult makeMove(int x, int y)
	{
		// has to be empty space
		if (get(x, y) != EMPTY)
		{
			return MoveResult.NoMoves;
		}
		int player = getCurrentPlayer();
		set(x, y, player);
		// look for captures in adjacent stones
		// do other players first
		captured.clear();
		for (int p = 0; p < getNumPlayers(); p++)
		{
			if (p == player)
				continue;
			visited.clear();
			find_capture(p, x - 1, y);
			find_capture(p, x + 1, y);
			find_capture(p, x, y - 1);
			find_capture(p, x, y + 1);
		}
		// now do self-capture
		// we make it illegal, so return 0 if this option is taken
		if (!has_liberties(player, x, y))
		{
			return MoveResult.NoMoves;
		}
		
		// remove captured stones
		remove_stones();
		addPlayerScore(player, captured.cardinality());
		// we moved, so set state.consecutive_passes to 0
		consecutive_passes = 0;
		// TODO: ko rule: don't repeat previous position
		// next player
		nextPlayer();
		return MoveResult.Ok;

	}

	private void remove_stones()
	{
		for (int i=captured.nextSetBit(0); i>=0; i=captured.nextSetBit(i+1))
		{
			int x = board.i2x(i);
			int y = board.i2y(i);
			set(x, y, EMPTY);
		}
	}

	void find_capture(int player, int x, int y)
	{
		if (get(x, y) != player)
			return;

		// don't visit cells twice
		int i = board.xy2i(x, y);
		if (visited.get(i))
			return;

		//System.out.println("find_capture " + x + " " + y);
		if (!has_liberties(player, x, y))
		{
			capture_stones(x, y);
		}
		//System.out.println("find_capture " + x + " " + y + " " + visited);
	}

	private void capture_stones(int x, int y)
	{
		// don't visit cells twice (opposite of has_liberties)
		if (!board.inBounds(x, y))
			return;
		int i = board.xy2i(x, y);
		if (!visited.get(i))
			return;

		//System.out.println("capture_stones " + x + " " + y);
		visited.clear(i);
		captured.set(i);
	    capture_stones(x-1, y);
	    capture_stones(x+1, y);
	    capture_stones(x, y-1);
	    capture_stones(x, y+1);
	}

	boolean has_liberties(int player, int x, int y)
	{
		// how many liberties?
		int p = get(x, y);
		if (p == player)
		{
			// don't visit cells twice
			int i = board.xy2i(x, y);
			if (visited.get(i))
				return false;

			//System.out.println("has_liberties " + x + " " + y);
			visited.set(i);
			// this is our piece, so count our liberties
			// (don't short-circuit, we have to count all the visited nodes)
			return has_liberties(player, x - 1, y) 
					| has_liberties(player, x + 1, y)
					| has_liberties(player, x, y - 1)
					| has_liberties(player, x, y + 1);
		} else if (p == EMPTY)
		{
			return true; // nothing there, 1 liberty
		} else
		{
			return false; // enemy or edge, 0 liberties
		}
	}

	@Override
	public long hashFor(int seekingPlayer)
	{
		return board.hash() + consecutive_passes;
	}

	@Override
	public void enableHashing(boolean enable)
	{
		// TODO: will we need hashing for ko rule?
		board.enableHashing(enable);
	}

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

	public String move2coord(Line<?> move)
	{
		int y = move.getMoveAtDepth(1).getMoveIndex();
		if (y == board.getHeight())
			return "pass";
		
		int x = move.getMoveAtDepth(2).getMoveIndex();
		if (!board.inBounds(x, y))
			return "?";
		
		char rank = (char)(x + 'A');
		if (rank >= 'I')
			rank++;
		int row = y + 1;
		return rank + "" + row;
	}
}
