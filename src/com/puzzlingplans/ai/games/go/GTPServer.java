package com.puzzlingplans.ai.games.go;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import com.puzzlingplans.ai.Line;
import com.puzzlingplans.ai.MoveFailedException;
import com.puzzlingplans.ai.MoveResult;
import com.puzzlingplans.ai.search.AIDecider;
import com.puzzlingplans.ai.search.MCRAVE;
import com.puzzlingplans.ai.util.MiscUtils;

public class GTPServer implements Runnable
{
	private ServerSocket welcomeSocket;
	private Thread thread;
	private Go canonicalGame;
	private MCRAVE playerAI;
	private boolean done;
	private PrintStream out;

	int nodesLog2 = 20;
	int numIters = 20000;

	public static final int BLACK = 0;
	public static final int WHITE = 1;
	
	//
	
	public GTPServer()
	{
		newGame(19);
		//playerAI = new MCRAVE[2];
	}

	private void newGame(int size)
	{
		canonicalGame = size <= 7 ? new Go7x7(size, 2) : new Go(size, 2);
		playerAI = null;
	}

	private AIDecider getSolver(int player)
	{
		MCRAVE ai = playerAI;
		if (ai == null)
		{
			ai = new MCRAVE(nodesLog2, canonicalGame.getBoard().getNumCells() * 2, numIters);
			//ai.setRAVEBias(0.5f);
			ai.useMultipleThreads = true;
			ai.resetBeforeSolve = false;
			ai.preferredMoveProb = 0x100; // avoid passing
			playerAI = ai;
		}
		return ai.newSolver(canonicalGame);
	}

	public void start(int port) throws IOException
	{
		welcomeSocket = new ServerSocket(port);
		thread = new Thread(this);
		thread.start();
	}
	
	public void stop()
	{
		if (thread != null)
		{
			thread.interrupt();
			thread = null;
		}
	}

	@Override
	public void run()
	{
		while (true)
		{
			try
			{
				handleConnection();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private void handleConnection() throws IOException
	{
		Socket connectionSocket = welcomeSocket.accept();
		BufferedReader sin = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));
		PrintStream sout = new PrintStream(connectionSocket.getOutputStream());
		session(sin, sout);
		connectionSocket.close();
	}

	private void session(BufferedReader in, PrintStream out) throws IOException
	{
		this.out = out;
		String l;
		while (!done && (l = in.readLine()) != null)
		{
			String[] toks = l.split("\\s+");
			if (toks.length > 0)
			{
				String cmd = toks[0];
				Class<?>[] parameterTypes = new Class[toks.length-1];
				Arrays.fill(parameterTypes, String.class);
				try
				{
					Method method = getClass().getMethod("do_" + cmd.toUpperCase(), parameterTypes);
					Object result = method.invoke(this, (Object[])Arrays.copyOfRange(toks, 1, toks.length));
					if (result != null)
						out.println("= " + result);
				} catch (InvocationTargetException e)
				{
					e.printStackTrace();
					out.println("? " + e.getCause());
				} catch (Exception e)
				{
					e.printStackTrace();
					out.println("? " + e);
				}
				out.println();
			}
		}
	}
	
	public void do_PLAY(String color, String coord)
	{
		MoveResult result;
		int player = color2player(color);
		int index = canonicalGame.coord2index(coord);
		if (index < 0)
		{
			result = canonicalGame.pass();
		} else {
			int x = canonicalGame.getBoard().i2x(index);
			int y = canonicalGame.getBoard().i2y(index);
			canonicalGame.setCurrentPlayer(player);
			result = canonicalGame.makeMove(x, y);
		}
		if (result == MoveResult.Ok)
		{
			dump();
			out.println("=");
		} else {
			out.println("? illegal move");
		}
	}

	private int color2player(String color)
	{
		int player;
		if (color.toLowerCase().startsWith("b"))
			player = BLACK;
		else if (color.toLowerCase().startsWith("w"))
			player = WHITE;
		else
			throw new IllegalArgumentException(color);
		return player;
	}
	
	public void do_GENMOVE(String color) throws MoveFailedException
	{
		int player = color2player(color);
		canonicalGame.setCurrentPlayer(player);

		Line<?> completeMove;
		AIDecider solver = getSolver(player);
		// TODO: set to System.err
		canonicalGame.playTurn(solver);
		completeMove = solver.getCompleteMove();
		dump();
		out.println("= " + canonicalGame.move2coord(completeMove));
	}
	
	private void dump()
	{
		canonicalGame.dump(System.err);
		System.err.println(playerAI);
	}

	public void do_SHOWBOARD()
	{
		canonicalGame.dump(out);
	}

	public void do_QUIT()
	{
		done = true;
		out.println("=");
	}

	public void do_ECHO(String s)
	{
		out.println("= " + s);
	}

	public void do_NAME()
	{
		out.println("= " + this.getClass().getName());
	}
	
	public void do_PROTOCOL_VERSION()
	{
		out.println("= 2");
	}

	public void do_VERSION()
	{
		out.println("= 0.0");
	}

	public void do_BOARDSIZE(String bsstr)
	{
		int bs = Integer.parseInt(bsstr);
		newGame(bs);
		out.println("=");
	}
	
	public void do_CLEAR_BOARD()
	{
		newGame(canonicalGame.getBoard().getWidth());
		out.println("=");
	}

	public String do_FINAL_SCORE()
	{
		// http://www.red-bean.com/sgf/properties.html
		canonicalGame.final_scoring();
		int score = canonicalGame.getModifiedScore(BLACK);
		if (score > 0)
			return MiscUtils.format("B%+d", canonicalGame.getAbsoluteScore(BLACK));
		else if (score < 0)
			return MiscUtils.format("W%+d", canonicalGame.getAbsoluteScore(WHITE));
		else
			return "0"; // draw
	}
	
	public String do_NUM_ITERS(String str)
	{
		this.numIters = Integer.parseInt(str);
		playerAI = null;
		return "";
	}

	public String do_NODES_LOG2(String str)
	{
		this.nodesLog2 = Integer.parseInt(str);
		playerAI = null;
		return "";
	}

	public void do_LIST_COMMANDS()
	{
		out.print("= ");
		Method[] methods = getClass().getMethods();
		for (Method m : methods)
		{
			if (m.getName().startsWith("do_"))
				out.println(m.getName().substring(3));
		}
	}
	
	public static void main(String[] args)
	{
		GTPServer server = new GTPServer();
		if (args.length == 0)
		{
			try
			{
				server.session(new BufferedReader(new InputStreamReader(System.in)), System.out);
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		} else {
			try
			{
				int port = Integer.valueOf(args[0]);
				server.start(port);
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}
