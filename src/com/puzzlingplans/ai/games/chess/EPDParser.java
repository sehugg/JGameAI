package com.puzzlingplans.ai.games.chess;

import com.puzzlingplans.ai.games.chess.Chess.Piece;
import com.puzzlingplans.ai.games.chess.Chess.PieceType;

public class EPDParser
{
	private Chess game;
	private String epd_bestmove;

	//

	public EPDParser(Chess game)
	{
		this.game = game;
	}

	public void parse(String s)
	{
		String[] toks = s.split("\\s+", 5);
		String pieces = toks[0];
		String side2move = toks[1];
		String castling = toks[2];
		String enpass = toks[3];
		String operations = toks.length > 4 ? toks[4] : "";

		// parse castling flags  
		{
			for (char ch : castling.toCharArray())
			{
				Piece p = charToPiece(ch);
				if (p != null)
				{
					if (p.type == PieceType.Queen)
						game.playerState(p.player).castling |= 1<<0;
					if (p.type == PieceType.King)
						game.playerState(p.player).castling |= 1<<7;
				}
			}
		}

		// piece placement
		String[] ranks = pieces.split("/");
		if (ranks.length < 8)
			throw new IllegalArgumentException("Not enough ranks: " + ranks.length);
		for (int y = 7; y >= 0; y--)
		{
			String rank = ranks[7 - y];
			char ch;
			int x = 0;
			for (int i = 0; i < rank.length(); i++)
			{
				ch = rank.charAt(i);
				if (x >= 8)
					EPDERR("X coord out of bounds");
				switch (ch)
				{
					case '1':
					case '2':
					case '3':
					case '4':
					case '5':
					case '6':
					case '7':
					case '8':
						x += (ch - '0');
						break;
					default:
					{
						Piece piece = charToPiece(ch);
						if (piece != null)
						{
							game.initSquare(x, y, piece);
							x++;
						} else
							EPDERR("Invalid piece character");
						break;
					}
				}
			}
			if (x != 8)
				EPDERR("Did not finish filling rank");
		}

		// side to move
		if (side2move.equals("w"))
			game.setCurrentPlayer(Chess.WHITE);
		else if (side2move.equals("b"))
			game.setCurrentPlayer(Chess.BLACK);
		else
			EPDERR("Side to move invalid: " + side2move);

		// en passant
		if (!enpass.equals("-"))
		{
			if (enpass.length() != 2)
				EPDERR("Bad en passant string: " + enpass);
			int x = enpass.charAt(0) - 'a';
			int y = enpass.charAt(1) - '1';
			game.playerState(game.getCurrentPlayer()^1).enpassant = (byte) game.getBoard().xy2i(x, y);
		}

		// TODO: operations
		epd_bestmove = operations;

		game.init();
	}

	private void EPDERR(String string)
	{
		throw new RuntimeException("EPD Error: " + string);
	}

	static final String EPD_PIECE_CHARS = "<PNBRQK>pnbrqk";

	private Piece charToPiece(char ch)
	{
		int pos = EPD_PIECE_CHARS.indexOf(ch);
		if (pos < 0)
			return null;
		int player = 0;
		if (pos >= 7)
		{
			pos -= 7;
			player = 1;
		}
		return new Piece(player, PieceType.values()[pos]);
	}

	public String getBestMoveString()
	{
		return epd_bestmove;
	}
}
