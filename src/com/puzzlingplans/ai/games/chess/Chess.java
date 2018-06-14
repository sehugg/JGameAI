package com.puzzlingplans.ai.games.chess;

import java.io.PrintStream;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.RandomDecider;
import com.puzzlingplans.ai.board.GridHelper64;
import com.puzzlingplans.ai.board.HashKeepingGrid;
import com.puzzlingplans.ai.board.OccupiedGrid;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.CloningObject;
import com.puzzlingplans.ai.util.Revertable;

public class Chess extends GameState<Chess> implements HashedPosition
{
	public static final int WHITE = 0;
	public static final int BLACK = 1;
	public static int BOARDX = 8;
	public static int BOARDY = 8;
	public static final int LevelsPerTurn = 2;

	public enum PieceType {
		_, Pawn, Knight, Bishop, Rook, Queen, King,
	}
	
	public static final PieceType[] PieceTypeValues = PieceType.values();
	public static final int NumPieceTypes = PieceTypeValues.length;

	public static int CANONICAL_PIECE_VALUES[] = { 0, 100, 320, 330, 510, 880, 0 };
	private static final int CHECK_PENALTY = 0; // should be >= 0

	public static String PIECE_CHARS[] = { " ", " ♙♘♗♖♕♔", " ♟♞♝♜♛♚" };

	public static class Piece
	{
		PieceType type;
		byte player;

		public Piece(int player, PieceType pieceType)
		{
			this.type = pieceType;
			this.player = (byte) player;
		}

		@Override
		public String toString()
		{
			//return player>0 ? type.toString().toUpperCase() : type.toString().toLowerCase();
			return PIECE_CHARS[player + 1].charAt(type.ordinal()) + "";
		}
	}

	public static class PlayerState extends CloningObject
	{
		byte kingpos; // index of king
		byte enpassant; // if pawn just moved two squares, will be nonzero and index of capture point
		byte castling;
		boolean incheck; // player in check?
		
		public PlayerState clone() throws CloneNotSupportedException
		{
			return (PlayerState) super.clone();
		}
		
		public String toString()
		{
			return "kp " + kingpos + " ep " + enpassant + " castling " + (castling&0xff) + (incheck?" check":"");
		}

		public long hash()
		{
			return enpassant + (castling<<6) + (incheck?128:0);
		}
	}

	private static Piece EMPTY = new Piece(-1, PieceType._);
	private static long KNIGHTMOVES;
	private static long ROYALMOVES;
	static
	{
		initMasks();
	}

	private Piece[] PIECES;
	private HashKeepingGrid<Piece> board;
	private PlayerState[] pstate;
	private int numTurns;
	
	private GridHelper64 helper;

	public class Statistics
	{
		public int numChecks;
		public int numCheckmates;
		public int numCaptures;
		public int numPromotions;
		public int numCastles;
		public int numEnpassants;
		public int numStalemates;
		
		public String toString()
		{
			return numChecks + " checks, " + (numCheckmates+numStalemates) + " mates, " + numCaptures + " captures, " + numPromotions
					+ " promotions, " + numEnpassants + " en passants, " + numCastles + " castles.";
		}
	}

	public Statistics stats;
	public String epdBestMove;
	
	private String oldboard;
	private String oldpstates;
	
	class ChessGrid extends HashKeepingGrid<Piece>
	{
		public ChessGrid(int width, int height, Piece defaultValue, int numColors, int numPieceTypes, long seed)
		{
			super(width, height, defaultValue, numColors, numPieceTypes, seed);
		}

		@Override
		public int getPieceTypeIndex(Piece t)
		{
			return t.type.ordinal();
		}
	}

	//

	public Chess()
	{
		super(2);
		
		board = new ChessGrid(BOARDX, BOARDY, EMPTY, getNumPlayers(), NumPieceTypes, 1L);
		pstate = new PlayerState[getNumPlayers()];
		for (int i = 0; i < pstate.length; i++)
			pstate[i] = new PlayerState();
		PIECES = new Piece[NumPieceTypes * getNumPlayers()];
		for (int i=0; i<PIECES.length; i++)
			PIECES[i] = new Piece(i / NumPieceTypes, PieceTypeValues[i % NumPieceTypes]);
		helper = new GridHelper64(BOARDX, BOARDY);
		resetStats();
	}

	private final Piece newPiece(int player, PieceType type)
	{
		return PIECES[player * NumPieceTypes + type.ordinal()];
	}

	public void resetStats()
	{
		stats = new Statistics();
	}

	@Override
	public Chess clone() throws CloneNotSupportedException
	{
		Chess copy = super.clone();
		copy.pstate = new PlayerState[pstate.length];
		for (int i = 0; i < pstate.length; i++)
			copy.pstate[i] = pstate[i].clone();
		copy.board = board.clone();
		// don't copy stats
		return copy;
	}

	public OccupiedGrid<Piece> getBoard()
	{
		return board;
	}

	private static void initMasks()
	{
		KNIGHTMOVES = BM(1, 0) | BM(0, 1) | BM(0, 3) | BM(1, 4) | BM(3, 4) | BM(4, 3) | BM(4, 1) | BM(3, 0);
		ROYALMOVES = BM(0, 0) | BM(1, 0) | BM(2, 0) | BM(0, 1) | BM(2, 1) | BM(0, 2) | BM(1, 2) | BM(2, 2);
	}

	private static final long CHOICE(int i)
	{
		return 1L << i;
	}

	private static final int BI(int x, int y)
	{
		return x + y * BOARDX;
	}

	private static final long BM(int x, int y)
	{
		if (x >= 0 && y >= 0 && x < BOARDX && y < BOARDY)
			return CHOICE(BI(x, y));
		else
			return 0;
	}

	boolean can_castle(int player, int y, int xr, long both)
	{
		if ((pstate[player].castling & (1<<xr)) != 0)
		{
			// no pieces between king and rook
			long castlepath = (xr == 0 ? (BM(1, 0) | BM(2, 0) | BM(3, 0)) : (BM(5, 0) | BM(6, 0))) << (y * BOARDX);
			if ((castlepath & both) != 0)
				return false;
			// square next to king on castling side cannot be under attack
			if (is_threatened(xr == 0 ? 3 : 5, y, player))
				return false;
			return true;
		} else
			return false;
	}

	boolean can_capture(int attacking_player, long defending, long attacking, int allowed_pieces)
	{
		// only look at attacking player's pieces
		attacking &= board.getOccupied64(attacking_player);
		// DEBUG("What if player %d attacked %llx with %llx (allowed types 0x%x)\n", attacking_player, defending, attacking, allowed_pieces);
		// iterate thru each attacking piece
		for (int index = BitUtils.nextBit(attacking, 0); index >= 0; index = BitUtils.nextBit(attacking, index + 1))
		{
			Piece def = board.get(index);
			// skip it if it's not an allowed piece
			if ((allowed_pieces & (1 << def.type.ordinal())) != 0)
			{
				// do the valid moves of this piece intersect our defending pieces?
				long attackmoves = get_valid_moves(index, board.get(index));
				long attacked = defending & attackmoves;
				if (attacked != 0)
				{
					return true;
				}
			}
		}
		return false;
	}

	static int m_Pawn = (1 << PieceType.Pawn.ordinal());
	static int m_Knight = (1 << PieceType.Knight.ordinal());
	static int m_Bishop = (1 << PieceType.Bishop.ordinal());
	static int m_Rook = (1 << PieceType.Rook.ordinal());
	static int m_Queen = (1 << PieceType.Queen.ordinal());
	static int m_King = (1 << PieceType.King.ordinal());

	boolean is_threatened(int x0, int y0, int player)
	{
		int ind = BI(x0, y0);
		return is_threatened(ind, player);
	}

	private boolean is_threatened(int ind, int player)
	{
		// compute masks for various attack vectors
		// now check those bitmasks to see if any of those pieces have a feasible attack
		long pos = CHOICE(ind);
		return can_capture(other(player), pos, get_valid_moves(ind, newPiece(player, PieceType.Knight)), m_Knight)
				|| can_capture(other(player), pos, get_valid_moves(ind, newPiece(player, PieceType.Rook)), m_Rook | m_Queen | m_King)
				|| can_capture(other(player), pos, get_valid_moves(ind, newPiece(player, PieceType.Bishop)), m_Bishop | m_Pawn | m_Queen | m_King);
	}

	public long get_valid_moves(int srci, Piece def)
	{
		assert (srci >= 0 && srci < BOARDX * BOARDY);
		assert (def != null);

		int player = def.player;
		final int x = board.i2x(srci);
		final int y = board.i2y(srci);
		final long us = board.getOccupied64(player);
		final long them = board.getOccupied64(other(player));
		final long both = us | them;

		switch (def.type)
		{
			case Pawn:
			{
				int dir = player > 0 ? -1 : 1;
				// move one space forward if unoccupied
				long mask = BM(x, y + dir) & ~both;
				// move two spaces forward if first move and unoccupied
				if (y == (player > 0 ? BOARDY - 2 : 1) && mask != 0)
				{
					mask |= BM(x, y + dir * 2) & ~both;
				}
				// capture piece diagonally
				mask |= (BM(x - 1, y + dir) | BM(x + 1, y + dir)) & them;
				// en passant opportunity?
				if (pstate[other(player)].enpassant != 0)
				{
					// capture en passant piece diagonally
					long em = CHOICE(pstate[other(player)].enpassant);
					mask |= (BM(x - 1, y + dir) | BM(x + 1, y + dir)) & em;
				}
				return mask;
			}
			case Knight:
			{
				// KNIGHTMOVES mask is offset by 2,2
				return helper.offset(KNIGHTMOVES, x - 2, y - 2) & ~us;
			}
			case Bishop:
			case Rook:
			case Queen:
			{
				long m = 0;
				// horizontal
				if (def.type == PieceType.Rook || def.type == PieceType.Queen)
				{
					m |= helper.project(x, y, us, them, -1, 0);
					m |= helper.project(x, y, us, them, 1, 0);
					m |= helper.project(x, y, us, them, 0, 1);
					m |= helper.project(x, y, us, them, 0, -1);
				}
				// diagonal
				if (def.type == PieceType.Bishop || def.type == PieceType.Queen)
				{
					m |= helper.project(x, y, us, them, -1, -1);
					m |= helper.project(x, y, us, them, 1, -1);
					m |= helper.project(x, y, us, them, -1, 1);
					m |= helper.project(x, y, us, them, 1, 1);
				}
				return m;
			}
			case King:
			{
				// ROYALMOVES mask is offset by 1,1
				long m = helper.offset(ROYALMOVES, x - 1, y - 1) & ~us;
				// can castle? pieces must not have moved, and must not be in check
				if (!pstate[player].incheck && pstate[player].castling != 0)
				{
					// defer x assert in case we flip the board - just set castling flags = 0 in that case
					assert (y == player * 7);
					// check to see if Rooks moved
					if (can_castle(player, y, 0, both))
					{
						assert (x == 4);
						m |= BM(x - 2, y);
					}
					if (can_castle(player, y, BOARDX - 1, both))
					{
						assert (x == 4);
						m |= BM(x + 2, y);
					}
				}
				return m;
			}
			default:
				throw new IllegalStateException(def.toString());
		}
	}

	public void init()
	{
		compute_kings_in_check();
	}

	public void initDefaultBoard()
	{
		int player;
		for (player = WHITE; player <= BLACK; player++)
		{
			int y = player > 0 ? BOARDY - 1 : 0;
			initSquare(0, y, player, PieceType.Rook);
			initSquare(1, y, player, PieceType.Knight);
			initSquare(2, y, player, PieceType.Bishop);
			initSquare(3, y, player, PieceType.Queen);
			initSquare(4, y, player, PieceType.King);
			initSquare(5, y, player, PieceType.Bishop);
			initSquare(6, y, player, PieceType.Knight);
			initSquare(7, y, player, PieceType.Rook);
			y = player > 0 ? BOARDY - 2 : 1;
			for (int x = 0; x < BOARDX; x++)
				initSquare(x, y, player, PieceType.Pawn);
			pstate[player].castling = (byte) (1|128);
		}
		init();
	}

	public void initSquare(int x, int y, int player, PieceType type)
	{
		Piece piece = newPiece(player, type);
		initSquare(x, y, piece);
	}

	public void initSquare(int x, int y, Piece piece)
	{
		board.set(x, y, piece, piece.player);
		if (piece.type == PieceType.King)
		{
			pstate[piece.player].kingpos = (byte) board.xy2i(x, y);
		}
	}

	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		final int player = getCurrentPlayer();
		pstate[player].enpassant = 0; // reset en passant flag
		final long srcmask = board.getOccupied64(player);
		MoveResult srcresult = decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return srcmask;
			}
			@Override
			public MoveResult choose(final int srcindex) throws MoveFailedException
			{
				Piece srcp = board.get(srcindex);
				assert (srcp.player == player);
				assert (srcp.type != PieceType._);
				final long valid = get_valid_moves(srcindex, srcp) & ~CHOICE(pstate[other(player)].kingpos); // don't capture the king
				//System.out.println(Long.toBinaryString(srcmask) + " -> " + Long.toBinaryString(valid));
				return decider.choose(new Choice()
				{
					@Override
					public long getPotentialMoves()
					{
						return valid;
					}
					@Override
					public long getPreferredMoves()
					{
						return board.getOccupied64(other(player));
					}
					@Override
					public MoveResult choose(int destindex) throws MoveFailedException
					{
						return makeMove(srcindex, destindex, decider);
					}
				});
			}
		});
		// if player can't move, they are checkmated
		if (srcresult == MoveResult.NoMoves)
		{
			assert(!isGameOver());
			if (pstate[getCurrentPlayer()].incheck)
				checkmate();
			else
				stalemate();
			//System.out.println("CHECKMATE");
			//dump();
			return MoveResult.Ok;
		} else
			return srcresult;
	}

	protected MoveResult makeMove(final int src, final int dest, Decider decider) throws MoveFailedException
	{
		final int player = getCurrentPlayer();
		final Piece srcp = board.get(src);
		assert (srcp.type != PieceType._);
		assert (srcp.player == player);
		final Piece captured = board.get(dest);
		assert (captured.type != PieceType.King); // can't capture king
		assert (captured.player != srcp.player); // can't capture self
		int x1 = board.i2x(src);
		int y1 = board.i2y(src);
		int x2 = board.i2x(dest);
		int y2 = board.i2y(dest);
		// some things we use an undo buffer for
		Revertable undo = null;
		//System.out.println(numTurns + ": " + getMoveString(src, dest));
		//assert((this.oldpstates = pstate[0] + " " + pstate[1]) != null);
		//assert((this.oldboard = board.toString()) != null);
		
		board.set(dest, srcp, player);
		board.set(src, EMPTY, -1);
		
		// is this an en passant capture?
		Piece enp_captured = null;
		if (dest != 0 && srcp.type == PieceType.Pawn && dest == pstate[other(player)].enpassant)
		{
			int pawny = y2 + (player > 0 ? 1 : -1); // look one space in front of capture pos
			assert (captured.type == PieceType._); // there should be nothing on our destination square
			assert (captured.player == -1);
			enp_captured = board.get(x2, pawny);
			assert (enp_captured.type == PieceType.Pawn); // there should be a pawn there
			undo = board.setWithUndo(x2, pawny, EMPTY, -1).next(undo); // get rid of that pawn
		}

		// castling? move rook also
		if (srcp.type == PieceType.King && Math.abs(x2 - x1) == 2)
		{
			int xr1 = x2 > x1 ? 7 : 0;
			int xr2 = x2 > x1 ? x2 - 1 : x2 + 1;
			assert (y1 == y2);
			assert (board.get(xr1, y1).type == PieceType.Rook);
			Piece rook = newPiece(player, PieceType.Rook);
			undo = board.setWithUndo(xr1, y1, EMPTY, -1).next(undo);
			undo = board.setWithUndo(xr2, y2, rook, rook.player).next(undo);
		}
		// promote this pawn?
		else if (srcp.type == PieceType.Pawn && y2 == (player > 0 ? 0 : BOARDY - 1))
		{
			// player chooses which piece to promote to
			final Revertable finalundo = undo;
			final Piece final_enp_captured = enp_captured;
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					Piece piece = newPiece(player, PieceTypeValues[choice]);
					board.set(dest, piece, player);
					return end_turn(src, dest, player, srcp, captured, final_enp_captured, piece, finalundo);
				}

				@Override
				public long getPotentialMoves()
				{
					return m_Bishop|m_Knight|m_Rook|m_Queen;
				}
			});
		}
		
		return end_turn(src, dest, player, srcp, captured, enp_captured, srcp, undo);
	}

	private MoveResult end_turn(int src, int dest, final int player, Piece srcp, Piece captured, Piece enp_captured, Piece promoted, Revertable undo)
	{
		// king moved? updated kingpos
		PlayerState psp = pstate[player];
		if (srcp.type == PieceType.King)
		{
			assert (psp.kingpos == src);
			psp.kingpos = (byte) dest;
		}
		// un-set castling flags (because we'll compute check for both players)
		// first, save castle flags for both players
		int castle0 = pstate[0].castling;
		int castle1 = pstate[1].castling;
		if (psp.castling != 0)
		{
			// rook moved, unset castle on that side
			if (srcp.type == PieceType.Rook)
			{
				rookMoved(player, src);
			}
			// king moved (or castled), unset all castle flags
			else if (srcp.type == PieceType.King)
			{
				psp.castling = 0;
			}
		}
		// rook capture? un-set castle flag for that rook
		if (captured.type == PieceType.Rook)
		{
			rookMoved(other(player), dest);
		}
		
		// we cannot end our turn with our king in check
		int oldcheck = compute_kings_in_check();
		
		// move is invalid if ends with player in check
		if (psp.incheck)
		{
			if (undo != null)
				undo.undoAll();
			board.set(src, srcp, srcp.player);
			board.set(dest, captured, captured.player);
			if (srcp.type == PieceType.King)
				psp.kingpos = (byte) src;
			setInCheck(0, (oldcheck & 1) != 0);
			setInCheck(1, (oldcheck & 2) != 0);
			pstate[0].castling = (byte) castle0;
			pstate[1].castling = (byte) castle1;
			// make sure rollback happened
			//assert(oldpstates.equals(pstate[0] + " " + pstate[1]));
			//assert(oldboard.equals(board.toString()));
			return MoveResult.NoMoves;
		}

		// mark pawn for en passant opportunity?
		if (srcp.type == PieceType.Pawn && Math.abs(src-dest) == BOARDX*2)
		{
			psp.enpassant = (byte) (player > 0 ? dest + BOARDX : dest - BOARDX); // set to capture point (one behind pawn) 
		}
		addPlayerScore(player, scoreFor(captured));
		if (enp_captured != null)
			addPlayerScore(player, scoreFor(captured));
		nextPlayer();
		numTurns++;
		
		// update stats
		if (captured.type != PieceType._)
		{
			stats.numCaptures++;
		}
		if (enp_captured != null)
		{
			stats.numCaptures++;
			stats.numEnpassants++;
		}
		if (srcp.type == PieceType.King && Math.abs(src-dest) == 2)
			stats.numCastles++;
		if (pstate[other(player)].incheck)
		{
			stats.numChecks++;
		}
		if (promoted != srcp)
		{
			stats.numPromotions++;
		}
		
		return MoveResult.Ok;
	}

	private void rookMoved(final int player, int src)
	{
		// TODO: what if this is a promoted pawn?
		int x = board.i2x(src);
		int y = board.i2y(src);
		if ((x == 0 || x == 7) && (y == player * 7))
		{
			pstate[player].castling &= ~(1<<x);
		}
	}

	public int compute_kings_in_check()
	{
		// compute king check status
		int oldcheck = (pstate[0].incheck ? 1 : 0) | (pstate[1].incheck ? 2 : 0);
		for (int p = 0; p < 2; p++)
		{
			byte kingpos = pstate[p].kingpos;
			assert (board.get(kingpos).type == PieceType.King);
			assert (board.get(kingpos).player == p);
			boolean check = is_threatened(kingpos, p);
			setInCheck(p, check);
		}
		return oldcheck;
	}

	public void setInCheck(int player, boolean check)
	{
		if (pstate[player].incheck != check)
		{
			pstate[player].incheck = check;
			addPlayerScore(player, check ? -CHECK_PENALTY : CHECK_PENALTY);
		}
	}

	public void checkmate()
	{
		stats.numCheckmates++;
		lose();
	}

	public void stalemate()
	{
		stats.numStalemates++;
		draw();
	}
	
	public String strpos(int index)
	{
		return board.i2x(index) + "," + board.i2y(index);
	}

	private static int other(int player)
	{
		return player ^ 1;
	}

	public int scoreFor(Piece p)
	{
		// TODO: positioning
		return CANONICAL_PIECE_VALUES[p.type.ordinal()];
	}

	@Override
	public void dump(PrintStream out)
	{
		board.dump(out);
	}

	@Override
	public String playerToString(int player)
	{
		return super.playerToString(player) + "\t" + pstate[player];
	}

	public String getMoveString(int srci, int desti)
	{
		// we gotta simulate the move to compute check
		Chess postMove = null;
		MoveResult opponentResult = MoveResult.Canceled;
		try {
			postMove = this.copy();
			// TODO: what about pawn promotions?
			postMove.makeMove(srci, desti, null);
			opponentResult = postMove.copy().playTurn(new RandomDecider());
		} catch (Exception e) {
			e.printStackTrace();
		}
		String s = "";
		int x1 = board.i2x(srci);
		int y1 = board.i2y(srci);
		int x2 = board.i2x(desti);
		int y2 = board.i2y(desti);
		Piece src = board.get(x1, y1);
		assert (src.type != PieceType._);
		Piece dest = board.get(x2, y2);
		boolean ambig_row = false;
		boolean ambig_col = false;
		if (src.type != PieceType.Pawn)
		{
			s += "<PNBRQK".charAt(src.type.ordinal());
		}
		for (int y = 0; y < BOARDY; y++)
		{
			for (int x = 0; x < BOARDX; x++)
			{
				if (x == x1 && y == y1)
					continue;
				Piece p = board.get(x, y);
				if (p.player == src.player && p.type == src.type)
				{
					if (y == y1)
						ambig_row = true;
					if (x == x1)
						ambig_col = true;
				}
			}
		}
		if ((ambig_row && y1 == y2) || (src.type == PieceType.Pawn && x1 != x2))
			s += (char)('a' + x1);
		if (ambig_col && x1 == x2)
			s += (char)('1' + y1);
		// capture move?
		if (board.get(x2, y2).type != PieceType._ && board.get(x2, y2).player != src.player)
			s += 'x';
		//if (x1 != x2)
		s += (char)('a' + x2);
		//if (y1 != y2)
		s += (char)('1' + y2);
		// check or checkmate?
		if (postMove != null && postMove.pstate[other(src.player)].incheck)
			s += (opponentResult == MoveResult.Ok) ? '+' : '#';
		return s;
	}

	public void flipHorizontally()
	{
		board.flipHorizontally();
		for (int i=0; i<pstate.length; i++)
		{
			PlayerState ps = pstate[i];
			ps.kingpos = flipindex(ps.kingpos);
			if (ps.enpassant > 0)
				ps.enpassant = flipindex(ps.enpassant);
			if (ps.castling != 0)
				throw new IllegalStateException("Cannot flip board unless castling flags set to 0");
		}
	}

	private static byte flipindex(byte i)
	{
		return (byte) ((7-(i&7)) | (i&~7));
	}

	@Override
	public long hashFor(int seekingPlayer)
	{
		return board.hash() + (pstate[0].hash() << 4) + (pstate[1].hash() << 20);
	}

	@Override
	public void enableHashing(boolean enable)
	{
		board.enableHashing(enable);
	}

	public PlayerState playerState(int player)
	{
		return pstate[player];
	}

}
