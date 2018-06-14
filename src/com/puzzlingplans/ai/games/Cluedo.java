package com.puzzlingplans.ai.games;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.UniformRandomChoice;
import com.puzzlingplans.ai.board.HashKeepingGrid;
import com.puzzlingplans.ai.util.BitUtils;
import com.puzzlingplans.ai.util.CloningObject;
import com.puzzlingplans.ai.util.RandomXorshift128;

public class Cluedo extends GameState<Cluedo>
{
	interface Card
	{
		public int index();
	}
	
	enum Suspect implements Card
	{
		Scarlet,
		Plum,
		Mustard,
		Peacock,
		Green,
		White;

		public int index()
		{
			return ordinal();
		}
	}
	
	enum Room implements Card
	{
		Study,
		Hall,
		Lounge,
		Library,
		DiningRoom,
		BilliardRoom,
		Conservatory,
		Ballroom,
		Kitchen;
	
		public int index()
		{
			return ordinal() + Suspect.values().length;
		}
	}
	
	enum Weapon implements Card
	{
		Candlestick,
		Dagger,
		Pipe,
		Revolver,
		Rope,
		Wrench;

		public int index()
		{
			return ordinal() + Suspect.values().length + Room.values().length;
		}
	}
	
	static abstract class Piece
	{
		int index;
		public Piece(int index) { this.index = index; }
	}
	
	static class Empty extends Piece
	{
		public Empty()
		{
			super(0);
		}
		@Override
		public String toString()
		{
			return ".";
		}
	}
	
	static class Wall extends Piece
	{
		public Wall()
		{
			super(1);
		}
		@Override
		public String toString()
		{
			return "x";
		}
	}
	
	static class Door extends Piece
	{
		Room room;
		
		public Door(Room room)
		{
			super(2 + Suspect.values().length + room.ordinal());
			this.room = room;
		}
		@Override
		public String toString()
		{
			return room.toString().substring(0,3);
		}
	}
	
	class PlayerPiece extends Piece
	{
		public PlayerPiece(int player)
		{
			super(2 + player);
		}
		@Override
		public String toString()
		{
			return "[" + (index-2) + "]";
		}
	}
	
	class Player extends CloningObject
	{
		Suspect suspect;
		int player;
		
		int pos;
		Room room;
		long cards;			// cards the player holds
		int numCardsToDeal;
		long seen;			// cards the player has seen
		long revealed;		// which cards has another player revealed
		boolean outofgame;	// guessed wrong, player is out of the game

		public Player(int player)
		{
			this.player = player;
			this.suspect = Suspect.values()[player];
		}
		@Override
		public Player clone() throws CloneNotSupportedException
		{
			return (Player) super.clone();
		}
		// which cards has this player not seen?
		public long unseenChoices(Card[] list)
		{
			long mask = 0;
			for (int i=0; i<list.length; i++)
			{
				long m = 1L << list[i].index();
				if ((m & (cards|seen)) == 0)
					mask |= (1L << i);
			}
			return mask;
		}
	}
	
	static Piece EMPTY = new Empty();
	static Piece WALL = new Wall();
	static final int numCards = Suspect.values().length + Room.values().length + Weapon.values().length;
	static final int numPieceTypes = 2 + Suspect.values().length + Room.values().length;
	
	//

	HashKeepingGrid<Piece> board;
	Player[] players;
	long solution;
	int[][] roomExits;
	
	public boolean debug = false;
	private boolean determinized;
	
	//
	
	public Cluedo(int numPlayers, long seed)
	{
		super(numPlayers);
		
		players = new Player[Suspect.values().length];
		for (int i=0; i<players.length; i++)
			players[i] = new Player(i);
		board = new HashKeepingGrid<Cluedo.Piece>(24, 25, EMPTY, 1, numPieceTypes, 0)
		{
			@Override
			public int getPieceTypeIndex(Piece t)
			{
				return t.index;
			}
		};
		fillMap(DEFAULT_MAP);
		dealCards(new RandomXorshift128(seed));
	}
	
	@Override
	protected Cluedo clone() throws CloneNotSupportedException
	{
		Cluedo copy = super.clone();
		copy.board = board.clone();
		copy.players = new Player[players.length];
		for (int i=0; i<players.length; i++)
			copy.players[i] = players[i].clone();
		return copy;
	}
	
	private void dealCards(Random rnd)
	{
		solution |= chooseEnum(Suspect.values(), rnd);
		solution |= chooseEnum(Room.values(), rnd);
		solution |= chooseEnum(Weapon.values(), rnd);
		
		long cards = choiceMask(numCards) & ~solution;
		int player = 0;
		while (cards != 0)
		{
			int cardi = BitUtils.choose_bit(cards, rnd);
			cards &= ~(1L << cardi);
			players[player].cards |= (1L << cardi);
			player = (player + 1) % getNumPlayers();
		}
	}
	
	private long chooseEnum(Card[] values, Random rnd)
	{
		int i = rnd.nextInt(values.length);
		return 1L << values[i].index();
	}

	private void fillMap(String[] map)
	{
		this.roomExits = new int[Room.values().length][];
		int y = 0;
		for (String line : map)
		{
			for (int x=0; x<line.length(); x++)
			{
				char ch = line.charAt(x);
				switch (ch)
				{
					case '.':
						board.set(x, y, EMPTY, -1);
						break;
					default:
						int index = board.xy2i(x, y);
						if (ch >= '1' && ch <= '9')
						{
							int ri = ch - '1';
							Room room = Room.values()[ri];
							board.set(x, y, new Door(room), -1);
							if (roomExits[ri] != null)
							{
								roomExits[ri] = Arrays.copyOf(roomExits[ri], roomExits[ri].length + 1);
								roomExits[ri][roomExits[ri].length - 1] = index;
							} else
								roomExits[ri] = new int[] { index };
						}
						else if (ch >= 'A' && ch <= 'F')
						{
							Player pstate = players[ch-'A'];
							pstate.pos = index;
							board.set(x, y, new PlayerPiece(pstate.player), -1);
						}
						else
						{
							board.set(x, y, WALL, -1);
						}
						break;
				}
			}
			y++;
		}
	}

	static final int Noop = 0;
	static final int Roll = 1;
	static final int Teleport = 2;
	static final int Suggest = 2;
	static final int Accuse = 4;
	
	@Override
	public MoveResult playTurn(final Decider decider) throws MoveFailedException
	{
		final Player pstate = players[getCurrentPlayer()];
		if (debug)
			System.out.println("Player " + getCurrentPlayer());
		
		// roll, suggest, accuse, teleport
		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				long moves = 0;
				moves |= choiceIndex(Roll); // roll
				moves |= choiceIndex(Accuse); // accuse
				// TODO: suggest if we just got teleported into a room 
				// moves |= choiceIndex(Suggest); // suggest
				return moves;
			}
			
			@Override
			public MoveResult choose(int choice) throws MoveFailedException
			{
				switch (choice)
				{
					case Roll:
						return decider.choose(new UniformRandomChoice()
						{
							@Override
							public long getPotentialMoves()
							{
								return choiceMask(6);
							}
							
							@Override
							public MoveResult choose(int choice) throws MoveFailedException
							{
								if (debug)
									System.out.println("Roll " + (choice + 1));
								if (pstate.room != null)
									return exitRoom(decider, choice + 1);
								else
									return moveNumberOfSpaces(decider, choice + 1, -1);
							}
						});
					case Accuse:
						return accuse(decider);
				}
				throw new IllegalArgumentException();
			}
		});
	}

	MoveResult exitRoom(final Decider decider, final int numSpaces) throws MoveFailedException
	{
		if (debug)
			System.out.println("Exit room " + numSpaces);
		final Player pstate = players[getCurrentPlayer()];
		assert(pstate.room != null);
		final int[] exits = roomExits[pstate.room.ordinal()];
		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return choiceMask(exits.length);
			}
			
			@Override
			public MoveResult choose(int choice) throws MoveFailedException
			{
				// can't exit room?
				int exitx = board.i2x(exits[choice]);
				int exity = board.i2y(exits[choice]);
				if (0 == getPossibleMovesFor(exitx, exity))
				{
					if (debug)
						System.out.println("Cannot exit " + pstate.room + " @ " + choice);
					return MoveResult.NoMoves;
				}
				
				assert(pstate.room != null);
				pstate.room = null;
				movePlayer(getCurrentPlayer(), exits[choice]);
				MoveResult result = moveNumberOfSpaces(decider, numSpaces, -1);
				return result;
			}
		});
	}
	
	void movePlayer(int player, int index)
	{
		final Player pstate = players[player];
		assert(pstate.room == null);
		assert(index >= 0);
		if (pstate.pos >= 0)
			board.set(pstate.pos, EMPTY, -1);
		pstate.pos = index;
		if (index >= 0)
			board.set(index, new PlayerPiece(player), -1);
		if (debug)
			System.out.println("Move player " + player + " to " + board.i2x(index) + "," + board.i2y(index));
	}

	private void movePlayerToRoom(int player, Room room)
	{
		final Player pstate = players[player];
		if (pstate.pos >= 0)
			board.set(pstate.pos, EMPTY, -1);
		pstate.room = room;
		pstate.pos = -1;
	}

	MoveResult moveNumberOfSpaces(final Decider decider, final int numSpaces, final int backDir) throws MoveFailedException
	{
		final Player pstate = players[getCurrentPlayer()];
		final int x = board.i2x(pstate.pos);
		final int y = board.i2y(pstate.pos);
		if (debug)
			System.out.println("Move " + numSpaces + " from " + x + "," + y);
		long mask = getPossibleMovesFor(x, y); // + skip
		if (backDir >= 0)
			mask &= ~(1L<<backDir); // can't go backwards
		if (mask == 0)
			mask = 16; // skip
		
		final long finalmask = mask;
		
		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return finalmask;
			}
			
			@Override
			public MoveResult choose(int choice) throws MoveFailedException
			{
				switch (choice)
				{
					case 0: return moveTo(decider, x-1, y, numSpaces, 1);
					case 1: return moveTo(decider, x+1, y, numSpaces, 0);
					case 2: return moveTo(decider, x, y-1, numSpaces, 3);
					case 3: return moveTo(decider, x, y+1, numSpaces, 2);
					case 4: return endTurn(decider); // skip
					default: throw new IllegalArgumentException();
				}
			}
		});
	}

	protected MoveResult moveTo(Decider decider, int x, int y, int numSpaces, int backDir) throws MoveFailedException
	{
		Piece piece = board.get(x, y);
		// enter room?
		if (piece instanceof Door)
		{
			Room room = ((Door) piece).room;
			movePlayerToRoom(getCurrentPlayer(), room);
			return canSuggest(decider);
		} else {
			// move one more space
			movePlayer(getCurrentPlayer(), board.xy2i(x, y));
			if (numSpaces == 1)
			{
				return endTurn(decider);
			}
			else
				return moveNumberOfSpaces(decider, numSpaces - 1, backDir);
		}
	}

	private MoveResult endTurn(Decider decider) throws MoveFailedException
	{
		nextPlayer();
		// if any players have concealed information, determinize it (shuffle their cards)
		if (decider.getSeekingPlayer() != Decider.RealLife && !determinized)
		{
			determinized = true;
			return redealCards(decider);
		} else
			return MoveResult.Ok;
	}

	private MoveResult redealCards(Decider decider) throws MoveFailedException
	{
		long allCards = solution;
		long seen = players[decider.getSeekingPlayer()].seen;
		for (int i=0; i<getNumPlayers(); i++)
		{
			if (i == decider.getSeekingPlayer())
				continue;
			// which cards are we not sure of? we have to redeal these
			long cards = players[i].cards & ~(players[i].revealed | seen);
			allCards |= cards;
			players[i].numCardsToDeal = BitUtils.countBits(cards);
			players[i].cards &= ~cards;
		}
		//System.out.println("Determinizing " + cardsFromMask(allCards));
		//return redealNextCard(decider, allCards);
		solution = 0;
		return redealSolution(decider, allCards);
	}

	private MoveResult redealSolution(final Decider decider, final long allCards) throws MoveFailedException
	{
		if (BitUtils.countBits(solution) == 3)
		{
			return redealNextCard(decider, allCards);
		} else {
			return decider.choose(new UniformRandomChoice()
			{
				@Override
				public long getPotentialMoves()
				{
					return allCards;
				}
				
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					long leftoverCards = allCards & ~(1L << choice);
					solution |= (1L << choice);
					return redealSolution(decider, leftoverCards);
				}
			});
		}
	}

	private MoveResult redealNextCard(final Decider decider, final long allCards) throws MoveFailedException
	{
		for (int i=0; i<getNumPlayers(); i++)
		{
			if (i == decider.getSeekingPlayer())
				continue;
			if (players[i].numCardsToDeal > 0)
			{
				players[i].numCardsToDeal--;
				return decider.choose(new UniformRandomChoice()
				{
					@Override
					public long getPotentialMoves()
					{
						return allCards;
					}
					
					@Override
					public MoveResult choose(int choice) throws MoveFailedException
					{
						long leftoverCards = allCards & ~(1L << choice);
						// more cards to deal
						if (leftoverCards != 0)
						{
							return redealNextCard(decider, leftoverCards);
						}
						else
						{
							return MoveResult.Ok;
						}
					}
				});
			}
		}
		throw new IllegalStateException();
	}

	private MoveResult canSuggest(final Decider decider) throws MoveFailedException
	{
		final Player pstate = players[getCurrentPlayer()];
		if (pstate.room == null)
		{
			return endTurn(decider);
		}
		
		long mask = choiceIndex(Noop) | choiceIndex(Suggest);
		final long finalmask = mask;
		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return finalmask;
			}
			
			@Override
			public MoveResult choose(int choice) throws MoveFailedException
			{
				if (debug)
					System.out.println("choice " + choice);
				switch (choice)
				{
					case Noop:
						return endTurn(decider);
					case Suggest:
						return suggest(decider, pstate.room, false);
					default:
						throw new IllegalArgumentException();
				}
			}
		});
	}

	protected MoveResult suggest(final Decider decider, final Room room, final boolean accuse) throws MoveFailedException
	{
		final long possibleSuspects = players[getCurrentPlayer()].unseenChoices(Suspect.values());
		final long possibleWeapons = players[getCurrentPlayer()].unseenChoices(Weapon.values());

		return decider.choose(new Choice() // TODO: really random?
		{
			@Override
			public long getPotentialMoves()
			{
				return possibleSuspects;
			}
			
			@Override
			public MoveResult choose(final int suspecti) throws MoveFailedException
			{
				return decider.choose(new Choice()
				{
					@Override
					public long getPotentialMoves()
					{
						return possibleWeapons;
					}

					@Override
					public MoveResult choose(int weaponi) throws MoveFailedException
					{
						Suspect suspect = Suspect.values()[suspecti];
						Weapon weapon = Weapon.values()[weaponi];
						return suggest(decider, suspect, weapon, room, accuse);
					}
				});
			}
		});
	}

	protected MoveResult accuse(final Decider decider) throws MoveFailedException
	{
		final long possibleRooms = players[getCurrentPlayer()].unseenChoices(Room.values());

		return decider.choose(new Choice()
		{
			@Override
			public long getPotentialMoves()
			{
				return possibleRooms;
			}
			
			@Override
			public MoveResult choose(int choice) throws MoveFailedException
			{
				Room room = Room.values()[choice];
				return suggest(decider, room, true);
			}
		});
	}

	private MoveResult suggest(final Decider decider, Suspect suspect, Weapon weapon, Room room, boolean accuse) throws MoveFailedException
	{
		if (debug)
			System.out.println("Player " + getCurrentPlayer() + " suggests " + suspect + " " + weapon + " " + room);
		final long suggestion = (1L<<suspect.index()) | (1L<<weapon.index()) | (1L<<room.index());
		
		// accusation?
		if (accuse)
		{
			if (suggestion == solution)
			{
				if (debug)
					System.out.println("Player " + getCurrentPlayer() + " wins!");
				win();
				return MoveResult.Ok;
			} else {
				if (debug)
					System.out.println("Player " + getCurrentPlayer() + " gets the accusation wrong");
				setPlayerScore(getCurrentPlayer(), LOSE);
				movePlayerToRoom(getCurrentPlayer(), Room.Study);
				return endTurn(decider);
			}
		}
		
		// ask each player
		for (int p = 1; p < getNumPlayers(); p++)
		{
			final int showingPlayer = (p + getCurrentPlayer()) % getNumPlayers();
			// do they have any of them?
			if ((players[showingPlayer].cards & suggestion) != 0)
			{
				if (debug)
					System.out.println("Player " + p + " has some of the cards");
				// choose one of them
				return decider.choose(new Choice()
				{
					@Override
					public long getPotentialMoves()
					{
						return suggestion;
					}
					
					@Override
					public MoveResult choose(int cardi) throws MoveFailedException
					{
						if (debug)
							System.out.println("Player " + showingPlayer + " shows card " + cardi);
						long cardmask = 1L << cardi;
						if ((players[getCurrentPlayer()].seen & cardmask) == 0)
						{
							players[getCurrentPlayer()].seen |= cardmask;
							addPlayerScore(getCurrentPlayer(), 100);
						}
						players[showingPlayer].revealed |= cardmask;
						return endTurn(decider);
					}
				});
			}
		}
		// must have guessed right, the player wins
		if (debug)
			System.out.println("Player " + getCurrentPlayer() + " suggested correctly");
		win();
		return MoveResult.Ok;
	}
	
	@Override
	public void nextPlayer()
	{
		// skip losing players
		for (int i=0; i<getNumPlayers(); i++)
		{
			super.nextPlayer();
			if (getAbsoluteScore(getCurrentPlayer()) > LOSE)
				return;
		}
		// everyone out, end of game
		setGameOver(true);
	}

	private long getPossibleMovesFor(final int x, final int y)
	{
		Piece left = board.getOrNull(x-1, y);
		Piece right = board.getOrNull(x+1, y);
		Piece up = board.getOrNull(x, y-1);
		Piece down = board.getOrNull(x, y+1);
		long mask = 0;
		if (isPassable(left)) mask |= 1;
		if (isPassable(right)) mask |= 2;
		if (isPassable(up)) mask |= 4;
		if (isPassable(down)) mask |= 8;
		if (debug)
			System.out.println("Moves from " + x + "," + y + " = " + left + " " + right + " " + up + " " + down + ": " + mask);
		return mask;
	}

	private boolean isPassable(Piece p)
	{
		if (p == null)
			return false;
		else
			return (p instanceof Door) || (p == EMPTY);
	}

	@Override
	public void dump(PrintStream out)
	{
		System.out.println("Case: " + cardsFromMask(solution));
		board.dump(out);
	}
	
	@Override
	public String playerToString(int player)
	{
		Player pstate = players[player];
		return super.playerToString(player) + "\t" + "room " + pstate.room + " " + cardsFromMask(pstate.cards);
	}
	
	Collection<Enum<?>> cardsFromMask(long mask)
	{
		ArrayList<Enum<?>> arr = new ArrayList();
		for (int i=BitUtils.nextBit(mask, 0); i>=0; i=BitUtils.nextBit(mask, i+1))
		{
			arr.add(cardToEnum(i));
		}
		return arr;
	}
	
	Enum<?> cardToEnum(int i)
	{
		if (i < Suspect.values().length)
			return Suspect.values()[i];
		i -= Suspect.values().length;
		if (i < Room.values().length)
			return Room.values()[i];
		i -= Room.values().length;
		return Weapon.values()[i];
	}

	String[] DEFAULT_MAP = {
		"xxxxxxx.        Axxxxxxx",
		"xxxxxxx..xxxxxx..xxxxxxx",
		"xxxxxxx..xxxxxx..xxxxxxx",
		"xxxxx1x..xxxxxx..xxxxxxx",
		" ........2xxxxx..xxxxxxx",
		"B........xxxxxx..x3xxxxx",
		" xxxxx...xx22xx........ ",
		"xxxxxxx................C",
		"xxxxxx4..xxxxx......... ",
		"xxxxxxx..xxxxx..x5xxxxxx",
		" xx4xx...xxxxx..xxxxxxxx",
		" ........xxxxx..xxxxxxxx",
		" 6xxxx...xxxxx..5xxxxxxx",
		"xxxxxx...xxxxx..xxxxxxxx",
		"xxxxxx...xxxxx..xxxxxxxx",
		"xxxxxd.............xxxxx",
		"xxxxxx................. ",
		" .......x8xxxx8x........",
		"D.......xxxxxxxx..x9xxxx",
		" xxx7...8xxxxxx8..xxxxxx",
		"xxxxxx..xxxxxxxx..xxxxxx",
		"xxxxxx..xxxxxxxx..xxxxxx",
		"xxxxxx..xxxxxxxx..xxxxxx",
		"xxxxxx ...xxxx... xxxxxx",
		"         E    F         ",
	};
}
