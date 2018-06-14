package com.puzzlingplans.ai.test;

import java.io.PrintStream;
import java.util.Random;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.HashedPosition;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.RandomChoice;
import com.puzzlingplans.ai.util.ZobristTable;

public class SampleGames
{
	public static abstract class TestGame<T> extends GameState
	{
		public TestGame()
		{
			super(2);
		}

		@Override
		public void dump(PrintStream out)
		{
		}
	}
	
	public static class BinaryGame extends TestGame<BinaryGame>
	{
		@Override
		public MoveResult playTurn(Decider decider) throws MoveFailedException
		{
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					nextPlayer();
					return MoveResult.Ok;
				}

				@Override
				public long getPotentialMoves()
				{
					return 1|2;
				}
			});
		}
	};

	public static class NonBranchingGame extends TestGame<BinaryGame>
	{
		@Override
		public MoveResult playTurn(Decider decider) throws MoveFailedException
		{
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					nextPlayer();
					return MoveResult.Ok;
				}

				@Override
				public long getPotentialMoves()
				{
					return 1;
				}
			});
		}
	}

	public static class TwoLevelInvalidMoves extends TestGame<BinaryGame>
	{
		int turn;
		
		@Override
		public MoveResult playTurn(Decider decider) throws MoveFailedException
		{
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					if (choice < turn)
						return MoveResult.NoMoves;
					nextPlayer();
					turn++;
					return MoveResult.Ok;
				}

				@Override
				public long getPotentialMoves()
				{
					return 1|2|4;
				}
			});
		}
	};

	public static class OneLevelOneValidMove extends TestGame<BinaryGame>
	{
		int turn;
		
		@Override
		public MoveResult playTurn(final Decider decider) throws MoveFailedException
		{
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					if (choice == (turn%2))
						return MoveResult.NoMoves;
					nextPlayer();
					turn++;
					return MoveResult.Ok;
				}

				@Override
				public long getPotentialMoves()
				{
					return 1|2;
				}
			});
		}
	};

	public static class TwoLevelOneValidMove extends TestGame<BinaryGame>
	{
		int turn;
		
		@Override
		public MoveResult playTurn(final Decider decider) throws MoveFailedException
		{
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					return decider.choose(new Choice()
					{
						@Override
						public MoveResult choose(int choice) throws MoveFailedException
						{
							if (choice == (turn%2))
								return MoveResult.NoMoves;
							nextPlayer();
							turn++;
							return MoveResult.Ok;
						}

						@Override
						public long getPotentialMoves()
						{
							return 1|2;
						}
					});
				}

				@Override
				public long getPotentialMoves()
				{
					return 1|2;
				}
			});
		}
	};

	public static class TwoLevelVariableResults extends TestGame<BinaryGame>
	{
		int turn;
		
		@Override
		public MoveResult playTurn(final Decider decider) throws MoveFailedException
		{
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					final int bias = choice;
					MoveResult result = decider.choose(new Choice()
					{
						@Override
						public MoveResult choose(int choice) throws MoveFailedException
						{
							if (choice < turn + bias)
								return MoveResult.NoMoves;
							nextPlayer();
							turn++;
							return MoveResult.Ok;
						}

						@Override
						public long getPotentialMoves()
						{
							return 1|2|4;
						}
					});
					if (result == MoveResult.NoMoves)
					{
						assert(!isGameOver());
						win();
						return MoveResult.Ok;
					} else
						return result;
				}

				@Override
				public long getPotentialMoves()
				{
					return 1|2;
				}
			});
		}
	};

	public static class BinaryRandomScoringGame extends TestGame<BinaryGame> implements HashedPosition
	{
		int seed = 1;
		
		@Override
		public MoveResult playTurn(Decider decider) throws MoveFailedException
		{
			return decider.choose(new Choice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					Random rnd = new Random(seed);
					if (choice == rnd.nextInt(2))
						addPlayerScore(getCurrentPlayer(), rnd.nextInt(50)-25);
					seed = ((seed<<1) + choice) & 0xff;
					nextPlayer();
					return MoveResult.Ok;
				}

				@Override
				public long getPotentialMoves()
				{
					return 1|2;
				}
			});
		}

		@Override
		public long hashFor(int seekingPlayer)
		{
			long hash = ((defaultHash() + 0x1000000) * 257L) + seed;
			assert(hash >= 0);
			return hash;
		}

		@Override
		public void enableHashing(boolean enable)
		{
		}
	};

	public static class Binary50PercentWinGame extends TestGame<BinaryGame>
	{
		int seed = 0;
		int turn;
		int numturns;
		ZobristTable zob;
		
		public Binary50PercentWinGame(int numturns)
		{
			this.numturns = numturns;
			assert(numturns<=16);
			zob = new ZobristTable(1<<(numturns+1), seed);
		}
		
		@Override
		public MoveResult playTurn(Decider decider) throws MoveFailedException
		{
			return decider.choose(new RandomChoice()
			{
				@Override
				public MoveResult choose(int choice) throws MoveFailedException
				{
					seed = (seed<<1) + choice;
					if (turn++ >= numturns)
					{
						if ((zob.get(seed) & 1) == 0)
						{
							win();
						} else {
							lose();
						}
					}
					nextPlayer();
					return MoveResult.Ok;
				}

				@Override
				public long getPotentialMoves()
				{
					return 1|2;
				}

				@Override
				public float getProbability(int choice)
				{
					return 1f / 2;
				}
			});
		}
	};

}
