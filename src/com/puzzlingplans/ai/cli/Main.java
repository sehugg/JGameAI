package com.puzzlingplans.ai.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Stack;

import com.puzzlingplans.ai.Choice;
import com.puzzlingplans.ai.Decider;
import com.puzzlingplans.ai.GameState;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.games.Cluedo;
import com.puzzlingplans.ai.search.AIDecider;
import com.puzzlingplans.ai.search.MCTS;
import com.puzzlingplans.ai.search.MCTS.Option;
import com.puzzlingplans.ai.util.BitUtils;

public class Main implements Decider
{
	private GameState<?> game;
	private BufferedReader in;
	private Stack<GameState<?>> undo = new Stack();
	private MCTS mcts;
	
	//
	
	Main()
	{
		in = new BufferedReader(new InputStreamReader(System.in));
		mcts = new MCTS(300);
		//mcts.setGoodMoveProbability(75);
		mcts.setOption(Option.UseAbsoluteScores, true);
	}
	
	public static void main(String[] args)
	{
		Main main = new Main();
		main.run();
	}


	private void run()
	{
		System.out.println("Started");
		boolean done = false;
		while (!done)
		{
			try
			{
				doCommand();
			} catch (IOException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private void doCommand() throws IOException
	{
		if (game != null)
			System.out.print("P" + game.getCurrentPlayer() + "(" + undo.size() + ")>");
		else
			System.out.print(">");
		String l = in.readLine();
		String[] toks = l.split("\\s+");
		String cmd = toks[0];
		if ("newgame".equals(cmd))
		{
			game = new Cluedo(Integer.parseInt(toks[1]), 0);
			game.dump();
		}
		else if ("play".equals(cmd))
		{
			undo.add(game.copy());
			try
			{
				game.playTurn(this);
				game.dump();
			} catch (MoveFailedException e)
			{
				e.printStackTrace();
			}
		}
		else if ("solve".equals(cmd))
		{
			undo.add(game.copy());
			System.out.println("Solving");
			try
			{
				MCTS mcts = new MCTS(this.mcts);
				mcts.setNumIters(500000);
				AIDecider solver = mcts.newSolver(game);
				game.playTurn(solver);
				game.dump();
			} catch (MoveFailedException e)
			{
				e.printStackTrace();
			}
		}
		else if ("undo".equals(cmd))
		{
			if (!undo.isEmpty())
			{
				game = undo.pop();
				game.dump();
			}
		}
	}

	@Override
	public MoveResult choose(Choice choice) throws MoveFailedException
	{
		long moves = choice.getPotentialMoves();
		System.out.println("Player " + game.getCurrentPlayer() + ", moves = " + BitUtils.toBitSet(moves));
		try
		{
			String line = in.readLine().trim();
			if (line.equals(""))
				return MoveResult.NoMoves;
			else
			{
				int index = Integer.parseInt(line);
				choice.choose(index);
				return MoveResult.Ok;
			}
		} catch (IOException e)
		{
			throw new MoveFailedException(e.toString());
		}
	}

	@Override
	public int getSeekingPlayer()
	{
		return RealLife;
	}
}
