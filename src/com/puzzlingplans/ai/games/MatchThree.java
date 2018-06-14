package com.puzzlingplans.ai.games;

import java.io.PrintStream;
import java.util.Random;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.board.GridHelper64;
import com.puzzlingplans.ai.board.HashKeepingEnumGrid;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class MatchThree extends GameState<MatchThree> // implements HashedPosition
{
	public enum Piece
	{
	  Empty,
	  Stone,
	  Bomb,
	  Gem,
	  VStriped,
	  HStriped,
	  Wrapped,
	}
	
	public static final Piece Empty = Piece.Empty;

	private HashKeepingEnumGrid<Piece> board;
	private GridHelper64 helper;
	private int numColors;
	private Random masterRandom; // TODO??
	
	//

	public MatchThree(int numPlayers, int width, int height, int numColors)
	{
		super(numPlayers);
		
		this.numColors = numColors;
		this.board = new HashKeepingEnumGrid<Piece>(width, height, Empty, numColors, 0)
		{
			@Override
			protected String cellToString(int index, Piece cell)
			{
				if (cell == Piece.Empty)
					return "-";
				else if (cell == Piece.Gem)
					return getColor(index) + "";
				else
					return getColor(index) + "-" + super.cellToString(index, cell);
			}
		};
		this.helper = new GridHelper64(width, height);
		this.masterRandom = new RandomXorshift128();
	}

	public void setRandomSeed(long seed)
	{
		this.masterRandom = new RandomXorshift128(seed);
	}
	
	public void fillBoard()
	{
		fillBoard(masterRandom);
	}

	public void fillBoard(Random rnd)
	{
		for (int x=0; x<board.getWidth(); x++)
		{
			int desty = board.getHeight()-1;
			int srcy = desty;
			while (desty >= 0)
			{
				while (srcy >= 0 && board.get(x, srcy) == Empty)
				{
					srcy--;
				}
				if (srcy >= 0 && srcy != desty)
				{
					board.copy(board.xy2i(x, srcy), board.xy2i(x, desty));
				}
				else
				{
					int newcolor = rnd.nextInt(numColors);
					board.set(board.xy2i(x, desty), Piece.Gem, newcolor);
				}
				srcy--;
				desty--;
			}
		}
		while (removeAllMatchesAndFill(rnd))
			;
	}

	public void shuffleBoard(Random rnd)
	{
		for (int i=2; i<board.getNumCells(); i++)
		{
			// TODO: empty spaces
			board.swap(i, rnd.nextInt(i-1));
		}
	}


	@Override
	protected MatchThree clone() throws CloneNotSupportedException
	{
		MatchThree copy = super.clone();
		copy.board = board.clone();
		copy.setRandomSeed(masterRandom.nextLong());
		return copy;
	}
	
	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		masterRandom.setSeed(board.hash()); // TODO?? needed for trail consistency
		final long potentialMoves = getPotentialMatches();
		MoveResult result = decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return potentialMoves;
			}
			
			@Override
			public MoveResult choose(final int srci) throws MoveFailedException
			{
				int color = board.getColor(srci);
				long colorMask = board.getOccupied64(color);
				final long colorAdjacent = helper.adjacent(colorMask);
				return decider.choose(new Choice()
				{
					@Override
					public long getPotentialMoves()
					{
						return colorAdjacent; // helper.adjacent(1L << srci);
					}
					
					@Override
					public MoveResult choose(int desti) throws MoveFailedException
					{
						return makeMove(srci, desti, masterRandom);
					}
				});
			}
		});
		if (result == MoveResult.NoMoves)
		{
			shuffleBoard(masterRandom); // TODO?
			return MoveResult.Ok;
		} else
			return result;
	}

	// return every cell that is 2 away from or diagonal to same color
	private long getPotentialMatches()
	{
		long all = 0;
		for (int c = 0; c < numColors; c++)
		{
			long m = board.getOccupied64(c);
			all |= m & helper.adjacent(m, 2);
			all |= m & helper.diagonals(m);
		}
		return all;
	}

	@Override
	public void dump(PrintStream out)
	{
		board.dump(out);
	}

	private MoveResult makeMove(int srci, int desti, Random rnd)
	{
		board.swap(srci, desti);
		if (findAndRemoveMatches(srci, desti, rnd))
		{
			nextPlayer();
			return MoveResult.Ok;
		} else {
			board.swap(srci, desti);
			return MoveResult.NoMoves;
		}
	}

	private boolean findAndRemoveMatches(int srci, int desti, Random rnd)
	{
		long m = findMatches(srci, desti);
		if (m == 0)
			return false;
		
		removeMatches(m);
		fillBoard(rnd);
		while (removeAllMatchesAndFill(rnd))
			;
		return true;
	}

	private long findMatches(int srci, int desti)
	{
		int c1 = board.getColor(srci);
		int c2 = board.getColor(desti);
		return findMatchesForColor(srci, c1) | findMatchesForColor(desti, c2);
	}
	
	public boolean removeAllMatchesAndFill(Random rnd)
	{
		long total = removeAllMatches();
		if (total != 0)
			fillBoard(rnd);
		return total != 0;
	}

	public long removeAllMatches()
	{
		long total = 0;
		for (int c=0; c<numColors; c++)
		{
			long matches = findAllMatchesForColor(c);
			total |= matches;
			if (matches != 0)
				removeMatches(matches);
		}
		return total;
	}

	private void removeMatches(long mask)
	{
		int n = 0;
		for (int i=BitUtils.nextBit(mask, 0); i>=0; i=BitUtils.nextBit(mask, i+1))
		{
			n++;
			board.set(i, Empty, -1);
		}
		addPlayerScore(getCurrentPlayer(), n);
	}

	public long findAllMatchesForColor(int color)
	{
		long matches = 0;
		long m = board.getOccupiedFor(color).longValue();
		long horiz = m & helper.offset(m, -1, 0) & helper.offset(m, 1, 0);
		long vert = m & helper.offset(m, 0, -1) & helper.offset(m, 0, 1);
		long all = horiz | vert;
		for (int i=BitUtils.nextBit(all, 0); i>=0; i=BitUtils.nextBit(all, i+1))
		{
			matches |= findMatchesAt(i, m);
		}
		return matches;
	}

	private long findMatchesForColor(int index, int color)
	{
		// look for horiz or vert
		long m = board.getOccupiedFor(color).longValue();
		long horiz = m & helper.offset(m, -1, 0) & helper.offset(m, 1, 0);
		if (horiz != 0)
			return findMatchesAt(index, m);
		long vert = m & helper.offset(m, 0, -1) & helper.offset(m, 0, 1);
		if (vert != 0)
			return findMatchesAt(index, m);
		return 0;
	}

	private long findMatchesAt(int index, long occupied)
	{
		return helper.floodfill(occupied, index);
	}

	/*
	@Override
	public long hashFor(int seekingPlayer)
	{
		return board.hash();
	}

	@Override
	public void enableHashing(boolean enable)
	{
	}
	*/
}
