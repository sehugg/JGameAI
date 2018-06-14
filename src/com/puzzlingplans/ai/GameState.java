package com.puzzlingplans.ai;

import java.io.PrintStream;
import java.util.Arrays;

import com.puzzlingplans.ai.util.CloningObject;

public abstract class GameState<T extends GameState<T>> extends CloningObject
{
	public static final int SCORE_MIN = Integer.MIN_VALUE;
	public static final int SCORE_MAX = Integer.MAX_VALUE;
	public static final int WIN = 1000000;
	public static final int DRAW = 0;
	public static final int LOSE = -1000000;
	public static final int WINLOSE_THRESHOLD = WIN/10; // still a win/loss if it comes within this threshold

	int numPlayers;
	int currentPlayer;
	int[] playerScores;
	boolean gameOver;

	//

	public abstract MoveResult playTurn(Decider decider) throws MoveFailedException;

	public abstract void dump(PrintStream out);

	//
	
	public GameState(int numPlayers)
	{
		this.numPlayers = numPlayers;
		this.playerScores = new int[numPlayers];
	}

	// TODO: use Log
	public void dump()
	{
		dumpScores(System.out);
		dump(System.out);
	}

	public void dumpScores(PrintStream out) 
	{
		for (int i=0; i<numPlayers; i++)
		{
			out.println(playerToString(i));
		}
	}
	
	public String playerToString(int player)
	{
		String playerChar;
		if (gameOver && (getWinners() & (1<<player)) != 0)
			playerChar = "+";
		else
			playerChar = (currentPlayer==player)?"*":" ";
		return (playerChar + "PLAYER " + player + ": " + getAbsoluteScore(player)); // TODO: MiscUtils.format("%8d", getAbsoluteScore(player)));
	}

	public int getNumPlayers()
	{
		return numPlayers;
	}

	public int getCurrentPlayer()
	{
		return currentPlayer;
	}

	public void setCurrentPlayer(int player)
	{
		assert (player >= 0 && player < numPlayers);
		this.currentPlayer = player;
	}

	public void nextPlayer()
	{
		setCurrentPlayer((currentPlayer + 1) % numPlayers);
	}

	public void prevPlayer()
	{
		setCurrentPlayer((currentPlayer + numPlayers - 1) % numPlayers);
	}

	public int getModifiedScore(int player)
	{
		int n = playerScores[player] * 2;
		for (int i = 0; i < numPlayers; i++)
			n -= playerScores[i];
		return n;
	}

	public int getAbsoluteScore(int player)
	{
		return playerScores[player];
	}

	public void addPlayerScore(int player, int delta)
	{
		playerScores[player] += delta;
	}
	
	public void setPlayerScore(int player, int score)
	{
		playerScores[player] = score;
	}
	
	public void setAllPlayerScores(int score)
	{
		for (int p=0; p<playerScores.length; p++)
			playerScores[p] = 0;
	}
	
	public int[] getPlayerScores()
	{
		return Arrays.copyOf(playerScores, playerScores.length);
	}

	public void setGameOver(boolean b)
	{
		this.gameOver = b;
	}
	
	public boolean isGameOver()
	{
		return gameOver;
	}

	protected void win()
	{
		draw();
		setPlayerScore(getCurrentPlayer(), WIN);
	}

	protected void lose()
	{
		draw();
		setPlayerScore(getCurrentPlayer(), LOSE);
	}

	protected void draw()
	{
		setAllPlayerScores(0);
		setGameOver(true);
	}

	protected boolean highestScoreWins()
	{
		return highestScoreWins(GameState.WIN);
	}

	protected boolean highestScoreWins(int winningScore)
	{
		int bestval = Integer.MIN_VALUE;
		int bestplayer = -1;
		int ndraws = 0;
		for (int i=0; i<numPlayers; i++)
		{
			int playerscore = getAbsoluteScore(i);
			if (playerscore > bestval)
			{
				bestval = playerscore;
				bestplayer = i;
				ndraws = 0;
			}
			else if (playerscore == bestval)
			{
				ndraws++;
			}
		}
		draw();
		if (ndraws == 0)
		{
			setPlayerScore(bestplayer, winningScore);
			return true;
		} else
			return false;
	}

	public long getWinners()
	{
		long winners = 0;
		for (int i=0; i<numPlayers; i++)
			if (getModifiedScore(i) >= getWinningScore())
				winners |= 1L<<i;
		return winners;
	}

	public int getWinningScore()
	{
		return WIN - WINLOSE_THRESHOLD;
	}

	public int getLosingScore()
	{
		return LOSE + WINLOSE_THRESHOLD;
	}

	public static final long choiceMask(int nchoices)
	{
		return (1L << nchoices) - 1;
	}

	public static final long choiceIndex(int i)
	{
		return (1L << i);
	}

	public static final long choice(Enum<?> e)
	{
		return choiceIndex(e.ordinal());
	}


	//


	/**
	 * Overridable clone function.
	 */
	protected T clone() throws CloneNotSupportedException
	{
		T copy = (T) super.clone();
		copy.playerScores = Arrays.copyOf(playerScores, playerScores.length);
		return copy;
	}
	
	/**
	 * Non-overridable clone() function that converts CloneNotSupportedException to RuntimeException.
	 */
	public final T copy()
	{
		try
		{
			return clone();
		} catch (CloneNotSupportedException e)
		{
			throw new RuntimeException(e);
		}
	}

	public long defaultHash()
	{
		final int prime = 31;
		long result = Arrays.hashCode(playerScores) * prime;
		return result;
	}

	public GameState<?> nullMoveEndsGame() throws MoveFailedException
	{
		GameState<?> copy = copy();
		copy.playTurn(new InOrderDecider());
		return copy.isGameOver() ? copy : null;
	}

}
