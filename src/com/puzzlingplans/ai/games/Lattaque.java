package com.puzzlingplans.ai.games;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.Random;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.HiddenChoice;
import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.board.HashKeepingGrid;
import com.puzzlingplans.ai.board.OccupiedGrid;
import com.puzzlingplans.ai.util.RandomXorshift128;

// L'Attaque (1910)
public class Lattaque extends GameState<Lattaque> implements HashedPosition
{
	public static final int RED = 0;
	public static final int BLUE = 1;
	public static int BOARDX = 10;
	public static int BOARDY = 10;
	public static int YDEEP = 4;
	public static final int LevelsPerTurn = 3;
	
	public enum PieceType {
		_, Wall, Bomb, Flag, Spy, Scout, Miner, Sergeant, Lieutenant, Captain, Major, Colonel, General, Marshall,
	}
	
	public static final PieceType[] PieceTypeValues = PieceType.values();
	public static final int numPieceTypes = PieceTypeValues.length;
	
	public enum AttackResult {
		LOSE, DRAW, WIN
	}
	
	static final String PIECE_CHARS = " #*$%S34567890";

	static int PIECES_PER_TYPE[] = { 0, 0, 6, 1, 1, 8, 5, 4, 4, 4, 3, 2, 1, 1 };
	static int MOVES_PER_TYPE[] = { 0, 0, 0, 0, 1, 10, 1, 1, 1, 1, 1, 1, 1, 1 };
	static int RANKS_PER_TYPE[] = { 0, 0, 11, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };

	private static Piece EMPTY = new Piece(-1, PieceType._);
	private static Piece WALL  = new Piece(-1, PieceType.Wall);

	public static final int NORTH = 0;
	public static final int EAST  = 1;
	public static final int SOUTH = 2;
	public static final int WEST  = 3;
	
	public static int DIRX[] = { 0, 1, 0, -1, 0 };
	public static int DIRY[] = { -1, 0, 1, 0, 0 };

	static class PlayerStrategy
	{
		int piece_values[];
		int unrevealed_value;
		int move_penalty;
	}

	static PlayerStrategy DEFAULT_STRATEGY = new PlayerStrategy();
	static
	{
		DEFAULT_STRATEGY.piece_values = new int[] { 0, 0, 75, 0, 100, 10, 100, 20, 50, 100, 140, 175, 300, 400 };
		DEFAULT_STRATEGY.unrevealed_value = 100; // TODO?
		DEFAULT_STRATEGY.move_penalty = 5;
	}

	//

	public static class Piece
	{
		public PieceType type;
		public byte player;
		public boolean moved;
		public boolean revealed;

		public Piece(int player, PieceType pieceType)
		{
			this.type = pieceType;
			this.player = (byte) player;
		}

		public Piece(Piece p)
		{
			this.type = p.type;
			this.player = p.player;
			this.moved = p.moved;
			this.revealed = p.revealed;
		}

		@Override
		public String toString()
		{
			char ch = PIECE_CHARS.charAt(type.ordinal());
			char r = (revealed || type.ordinal() <= 2) ? ' ' : (moved ? '.' : '=');
			if (player == 0)
				return "[" + r + ch + " ";
			else if (player == 1)
				return " " + ch + r + "]";
			else
				return " " + ch + ch + " ";
			/*
			if (player == 0)
				return "[" + r + ch + r + "]";
			else if (player == 1)
				return "<" + r + ch + r + ">";
			else
				return " " + r + ch + r + " ";
				*/
		}

		public Piece asMoved()
		{
			if (!moved)
			{
				Piece p = new Piece(this);
				p.moved = true;
				return p;
			} else
				return this;
		}

		public Piece asRevealed()
		{
			if (!revealed)
			{
				Piece p = new Piece(this);
				p.revealed = true;
				return p;
			} else
				return this;
		}

		public Piece asMovedAndRevealed()
		{
			if (!moved || !revealed)
			{
				Piece p = new Piece(this);
				p.moved = true;
				p.revealed = true;
				return p;
			} else
				return this;
		}

		public static int numTypes()
		{
			// for moved + revealed
			return PieceTypeValues.length * 2 * 2;
		}

		public int typeIndex()
		{
			return (type.ordinal() << 2) + (moved?1:0) + (revealed?2:0);
		}
	}
	
	//

	GameGrid board;
	byte[] piecesConcealed;
	int numTurns;
	
	static PlayerStrategy[] strategies = new PlayerStrategy[] { DEFAULT_STRATEGY, DEFAULT_STRATEGY };
	
	public class GameGrid extends HashKeepingGrid<Piece>
	{
		public GameGrid(int width, int height, int numColors, long seed)
		{
			super(width, height, EMPTY, numColors, Piece.numTypes() * numColors, seed);
		}

		@Override
		public int getPieceTypeIndex(Piece t)
		{
			return t.typeIndex();
		}
	}
	
	//
	
	public Lattaque()
	{
		super(2);
		this.piecesConcealed = new byte[2 * numPieceTypes];
		// make a board that maintains each player's score
		board = new GameGrid(BOARDX, BOARDY, 2, 0);
		// create lakes
		for (int y=0; y<BOARDY; y++)
			for (int x=0; x<BOARDX; x++)
				if (isWall(x, y))
					set(x, y, WALL, -1);
	}

	public Lattaque(long seed)
	{
		this();
		initBoard(seed);
	}

	public void set(int x, int y, Piece val, int color)
	{
		set(board.xy2i(x, y), val, color);
	}

	public void set(int i, Piece val, int color)
	{
		board.set(i, val, color);
	}
	
	private boolean isWall(int x, int y)
	{
		return !((y < YDEEP || y >= BOARDY-YDEEP) || ((x&3)>>1)==0);
	}

	protected void modifyState(Piece piece, int factor, boolean revealed)
	{
		if (piece.player >= 0)
		{
			addPlayerScore(piece.player, getValueOfPiece(piece) * factor);
			int pli = getPieceTypeIndex(piece);
			if (!revealed)
				piecesConcealed[pli] += factor;
			//System.out.println(MiscUtils.format("modifyState() %s %2d = %d", piece, factor, piecesConcealed[pli]));
			assert(piecesConcealed[pli] >= 0);
		}
	}

	private int getPieceTypeIndex(Piece piece)
	{
		return piece.type.ordinal() + piece.player * numPieceTypes;
	}

	private int getValueOfPiece(Piece piece)
	{
		assert(piece.player >= 0);
		// TODO: don't we want to use the seeking player's strategy?
		PlayerStrategy strategy = strategies[piece.player];
		// use default score if not revealed
		if (piece.revealed)
		{
			return strategy.piece_values[piece.type.ordinal()];
		} else {
			return strategy.unrevealed_value - (piece.moved ? strategy.move_penalty : 0);
		}
	}

	public void initBoard(long seed)
	{
		initBoard(seed, 0, false);
	}

	public void initBoard(long seed, int piecesToSubtract, boolean symmetric)
	{
		Random rnd = new RandomXorshift128(seed);
		for (int player = RED; player <= BLUE; player++)
		{
			for (PieceType type : PieceTypeValues)
			{
				int npieces = PIECES_PER_TYPE[type.ordinal()];
				if (npieces > 0)
				{
					npieces = Math.max(1, npieces - piecesToSubtract);
				}
				for (int i = 0; i < npieces; i++)
				{
					Piece p = new Piece(player, type);
					int x, y;
					do
					{
						x = rnd.nextInt(BOARDX);
						y = rnd.nextInt(YDEEP);
						if (player == BLUE)
							y = BOARDY - 1 - y;
					} while (board.get(x, y).type != PieceType._);
					set(x, y, p, p.player);
					modifyState(p, 1, false);
					if (symmetric)
					{
						Piece pp = new Piece(p.player ^ 1, type);
						set(x, BOARDY - 1 - y, pp, pp.player);
						modifyState(pp, 1, false);
					}
				}
			}
			if (symmetric)
				break;
		}
	}

	@Override
	public Lattaque clone() throws CloneNotSupportedException
	{
		Lattaque copy = super.clone();
		copy.board = (GameGrid) board.clone();
		copy.piecesConcealed = Arrays.copyOf(piecesConcealed, piecesConcealed.length);
		// don't copy stats
		return copy;
	}

	@Override
	public void dump(PrintStream out)
	{
		board.dump(out);
	}

	public OccupiedGrid<Piece> getBoard()
	{
		return board;
	}
	
	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		// TODO: initial placement is part of it!
		// TODO: all of this could be faster
		final int player = getCurrentPlayer();
		MoveResult result = decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				// choose row
				return choiceMask(BOARDY);
			}
			
			@Override
			public MoveResult choose(final int row) throws MoveFailedException
			{
				return decider.choose(new Choice()
				{
					@Override
					public long getPotentialMoves()
					{
						return board.getColorOccupiedForRow(player, row);
					}
					
					@Override
					public MoveResult choose(final int col) throws MoveFailedException
					{
						return decider.choose(new Choice()
						{

							@Override
							public long getPotentialMoves()
							{
								return getValidMoves(col, row);
							}

							@Override
							public MoveResult choose(int dir) throws MoveFailedException
							{
								return makeMove(col, row, dir&3, (dir>>2)+1, decider);
							}
						});
					}
				});
			}
		});
		// player cannot move, forfeit
		if (result == MoveResult.NoMoves)
		{
			lose();
			return MoveResult.Ok;
		} else
			return result;
	}
	
	AttackResult getAttackResult(PieceType a, PieceType d)
	{
	    if (a == PieceType.Spy && d == PieceType.Marshall)
	      return AttackResult.WIN;

	    if (a == PieceType.Miner && d == PieceType.Bomb)
	      return AttackResult.WIN;

	    int x = RANKS_PER_TYPE[a.ordinal()] - RANKS_PER_TYPE[d.ordinal()];
	    if (x > 0)
	      return AttackResult.WIN;
	    else if (x < 0)
	      return AttackResult.LOSE;
	    else
	      return AttackResult.DRAW;
	}


	MoveResult makeMove(final int x, final int y, int dir, int nspaces, final Decider decider) throws MoveFailedException
	{
		final int player = getCurrentPlayer();
		final Piece srcp = board.get(x, y);
		final int x2 = x + DIRX[dir] * nspaces;
		final int y2 = y + DIRY[dir] * nspaces;
		assert(nspaces > 0);
		assert(srcp.player == player);
		assert(board.inBounds(x2, y2));
		
		final Piece destp = board.get(x2, y2);
		// can't attack own player
		if (destp.player == player || destp.type == PieceType.Wall)
			return MoveResult.NoMoves;
		
		// figure out who can see what
		int seekingPlayer = decider.getSeekingPlayer();
		final boolean srcRevealed = srcp.revealed || srcp.player == seekingPlayer || seekingPlayer == Decider.RealLife;
		final boolean destRevealed = destp.revealed || destp.player == seekingPlayer || seekingPlayer == Decider.RealLife;
		
		if (destp.type == PieceType._)
		{
			// just a simple move, no attack
			assert(nspaces == 1 || srcp.type == PieceType.Scout);
			assert(nspaces >= 1 && nspaces <= BOARDY);
			// we know it's a Scout if it moves more than one space
			Piece newpiece = nspaces > 1 ? srcp.asMovedAndRevealed() : srcp.asMoved();
			set(x2, y2, newpiece, player);
			set(x, y, EMPTY, -1);
			nextPlayer();
			return MoveResult.Ok;
		}
		else if (srcRevealed && destRevealed)
		{
			// if it's revealed, we just attack
			assert(destp.player == other(srcp.player));
			// attack
			return completeAttack(x, y, x2, y2, srcp, destp, true, true);
		}
		else
		{
			final long srcTypes = srcRevealed ? (1L<<srcp.type.ordinal()) : getPossibleUnitTypes(srcp);
			final long destTypes = destRevealed ? (1L<<destp.type.ordinal()) : getPossibleUnitTypes(destp);
			assert(destp.player == other(srcp.player));
			//System.out.println(MiscUtils.format("%d %s -> %s (%x %x)", player, srcp, destp, srcTypes, destTypes));
			// TODO: need a new class with probabilities and stuff
			if (srcRevealed || destRevealed)
			{
				return decider.choose(new HiddenChoice()
				{
					@Override
					public long getPotentialMoves()
					{
						return srcRevealed ? destTypes : srcTypes;
					}
					@Override
					public MoveResult choose(final int index) throws MoveFailedException
					{
						// TODO: preserve move/revealed flags
						PieceType srcType = srcRevealed ? srcp.type : PieceTypeValues[index];
						PieceType destType = destRevealed ? destp.type : PieceTypeValues[index];
						Piece newsrc = srcRevealed ? srcp : new Piece(player, srcType);
						Piece newdest = destRevealed ? destp : new Piece(other(player), destType);
						return completeAttack(x, y, x2, y2, newsrc, newdest, srcRevealed, destRevealed);
					}
					@Override
					public int getActualOutcome()
					{
						return (srcRevealed ? destp.type : srcp.type).ordinal();
					}
					@Override
					public float getProbability(int index)
					{
						PieceType pieceType = PieceTypeValues[index];
						if (srcRevealed)
							return probabilityForPieceType(destp, pieceType);
						else
							return probabilityForPieceType(srcp, pieceType);
					}
				});
			} else {
				return decider.choose(new HiddenChoice()
				{
					@Override
					public long getPotentialMoves()
					{
						return srcTypes;
					}
					@Override
					public int getActualOutcome()
					{
						return srcp.type.ordinal();
					}
					@Override
					public float getProbability(int srci)
					{
						return probabilityForPieceType(srcp, PieceTypeValues[srci]);
					}
					@Override
					public MoveResult choose(final int srci) throws MoveFailedException
					{
						return decider.choose(new HiddenChoice()
						{
							@Override
							public long getPotentialMoves()
							{
								return destTypes;
							}
							@Override
							public int getActualOutcome()
							{
								return destp.type.ordinal();
							}
							@Override
							public MoveResult choose(final int desti) throws MoveFailedException
							{
								// TODO: preserve move/revealed flags
								PieceType srcType = PieceTypeValues[srci];
								PieceType destType = PieceTypeValues[desti];
								Piece newsrc = new Piece(player, srcType);
								Piece newdest = new Piece(other(player), destType);
								return completeAttack(x, y, x2, y2, newsrc, newdest, srcRevealed, destRevealed);
							}
							@Override
							public float getProbability(int desti)
							{
								return probabilityForPieceType(destp, PieceTypeValues[desti]);
							}
						});
					}
				});
			}
		}
	}

	protected float probabilityForPieceType(Piece p, PieceType pieceType)
	{
		int total = 0;
		int start = p.player * numPieceTypes;
		for (int i=2; i<numPieceTypes; i++)
		{
			// if piece has moved, it can't be a bomb or flag
			if (p.moved && MOVES_PER_TYPE[i] == 0)
				continue;

			total += piecesConcealed[i + start];
		}
		int num = piecesConcealed[start + pieceType.ordinal()];
		assert(num > 0);
		assert(total > 0);
		return ((float)num) / total;
	}

	protected long getPossibleUnitTypes(Piece p)
	{
		assert(!p.revealed);
		long mask = 0;
		int start = p.player * numPieceTypes;
		for (int i=2; i<numPieceTypes; i++)
		{
			// if piece has moved, it can't be a bomb or flag
			if (p.moved && MOVES_PER_TYPE[i] == 0)
				continue;
			// if pieces are all gone, it can't be that one
			if (piecesConcealed[i + start] == 0)
				continue;
			
			mask |= 1L << i;
		}
		return mask;
	}

	protected MoveResult completeAttack(int x, int y, int x2, int y2, Piece srcp, Piece destp, boolean srcRevealed, boolean destRevealed)
	{
		assert(board.get(x, y).type != PieceType._);
		assert(board.get(x2, y2).type != PieceType._);
		AttackResult outcome = getAttackResult(srcp.type, destp.type);

		//System.out.println(MiscUtils.format("completeAttack(%d,%d -> %d,%d) %s %s[%d] -> %s[%d]",
			//	x, y, x2, y2, outcome, srcp, piecesConcealed[getPieceTypeIndex(srcp)], destp, piecesConcealed[getPieceTypeIndex(destp)]));
		assert(srcRevealed || piecesConcealed[getPieceTypeIndex(srcp)] > 0);
		assert(destRevealed || piecesConcealed[getPieceTypeIndex(destp)] > 0);
		
		assert(srcp != destp);
		switch (outcome)
		{
			case LOSE:
				// if attacker loses, defender still reveals their piece
				modifyState(srcp, -1, srcRevealed);
				set(x2, y2, destp.asRevealed(), destp.player);
				break;
			case WIN:
				// if attacker wins, replaces defender's position
				modifyState(destp, -1, destRevealed);
				set(x2, y2, srcp.asMovedAndRevealed(), srcp.player);
				// get the flag and win
				if (destp.type == PieceType.Flag)
					win();
				break;
			case DRAW:
				// if draw, both are removed
				set(x2, y2, EMPTY, -1);
				modifyState(srcp, -1, srcRevealed);
				modifyState(destp, -1, destRevealed);
				break;
		}
		// either way, attacker is no longer at source square
		set(x, y, EMPTY, -1);
		nextPlayer();
		return MoveResult.Ok;
	}

	private long getValidMoves(int x, int y)
	{
		Piece p = board.get(x,y);
		int nmoves = MOVES_PER_TYPE[p.type.ordinal()];
		long mask = 0;
		long dirmask = 1|2|4|8; // cardinal directions
		for (int i=1; i<=nmoves; i++)
		{
			long flags = 0;
			if (canMove(p, x, y-i, i))
				flags |= 1<<NORTH;
			if (canMove(p, x, y+i, i))
				flags |= 1<<SOUTH;
			if (canMove(p, x-i, y, i))
				flags |= 1<<WEST;
			if (canMove(p, x+i, y, i))
				flags |= 1<<EAST;
			if (flags == 0)
				break;
			mask |= (flags & dirmask) << ((i - 1) * 4);
			dirmask &= flags;
		}
		return mask;
	}

	private boolean canMove(Piece p, int x, int y, int n)
	{
		if (!board.inBounds(x, y))
			return false;
		Piece destp = board.get(x,y);
		// can attack on first move -- scout cannot move and strike on same turn
		// FIXME: Scout jumps over one opponent
		if (destp.player == other(p.player) && n == 1)
			return true;
		// can move to empty
		return destp.type == PieceType._;
	}

	private int other(int player)
	{
		assert(player == 0 || player == 1);
		return player ^ 1;
	}

	@Override
	public long hashFor(int seekingPlayer)
	{
		// TODO: modify with hash for hidden states for each player
		return board.hash() + Arrays.hashCode(piecesConcealed);
	}

	@Override
	public void enableHashing(boolean enable)
	{
		board.enableHashing(enable);
	}

	public Turn decodeTurn(Line<?> move)
	{
		return new Turn(move);
	}
	
	// concise representation of a single turn
	public class Turn
	{
		public int x1,y1;
		public int x2,y2;
		public Piece srcp,destp;
		public AttackResult outcome;
		
		public Turn(Line<?> move)
		{
			int[] indices = move.getIndices();
			assert(indices.length >= 3);

			y1 = indices[0];
			x1 = indices[1];
			int dirlen = indices[2];
			int dir = dirlen & 3;
			int nspaces = (dirlen >> 2) + 1;
			x2 = x1 + DIRX[dir] * nspaces;
			y2 = y1 + DIRY[dir] * nspaces;
			srcp = board.get(x1, y1);
			assert(srcp.type != PieceType._);
			destp = board.get(x2, y2);
			if (destp.type != PieceType._)
			{
				outcome = getAttackResult(srcp.type, destp.type);
			}
		}
	}
}
