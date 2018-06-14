package com.puzzlingplans.ai.board;

import java.io.PrintStream;
import java.lang.reflect.Array;
import java.util.Arrays;

import com.puzzlingplans.ai.util.CloningObject;
import com.puzzlingplans.ai.util.MiscUtils;


public class Grid<T> extends CloningObject
{
	protected final int w;
	protected final int h;
	protected T[] board;
	protected T defaultValue;
	
	@SuppressWarnings("unchecked")
	public Grid(int width, int height, T defaultValue)
	{
		this.w = width;
		this.h = height;
		assert (w > 0);
		assert (h > 0);
		this.board = (T[]) new Object[width * height];
		for (int i = 0; i < board.length; i++)
			board[i] = defaultValue;
		this.defaultValue = defaultValue;
	}

	public T get(int x, int y)
	{
		int i = x + y * w;
		return board[i];
	}

	public T getOrNull(int x, int y)
	{
		return inBounds(x, y) ? get(x, y) : null;
	}

	public void set(int x, int y, T val)
	{
		int i = x + y * w;
		board[i] = val;
	}
	
	public T get(int i)
	{
		return board[i];
	}

	public void set(int i, T val)
	{
		board[i] = val;
	}

	public boolean inBounds(int x, int y)
	{
		return x>=0 && y>=0 && x<w && y<h;
	}

	public boolean matchRow(int y, int n, T p)
	{
		int t = 0;
		for (int i = 0; i < w; i++)
			if (get(i, y) == p)
			{
				if (++t >= n)
					return true;
			} else {
				t = 0;
			}
		return false;
	}

	public boolean matchColumn(int x, int n, T p)
	{
		int t = 0;
		for (int i = 0; i < h; i++)
			if (get(x, i) == p)
			{
				if (++t >= n)
					return true;
			} else {
				t = 0;
			}
		return false;
	}

	public boolean matchDiagonal(int x, int y, int n, T p)
	{
		int t = 0;
		for (int i=-n+1; i<n; i++)
		{
			int xx = x+i;
			int yy = y+i;
			if (inBounds(xx,yy) && get(xx,yy) == p)
			{
				if (++t >= n)
					return true;
			} else
				t = 0;
		}
		t = 0;
		for (int i=-n+1; i<n; i++)
		{
			int xx = x+i;
			int yy = y-i;
			if (inBounds(xx,yy) && get(xx,yy) == p)
			{
				if (++t >= n)
					return true;
			} else
				t = 0;
		}
		return false;
	}

	// TODO: use Log
	public void dump(PrintStream out, boolean yflip)
	{
		out.print('\t');
		for (int x=0; x<w; x++)
		{
			out.print((char)('A' + x + (x >= 8 ? 1 : 0))); // skip I
			out.print('\t');
		}
		out.println();
		for (int yy=0; yy<h; yy++)
		{
			int y = yflip ? h-1-yy : yy;
			out.print(y+1);
			out.print('\t');
			for (int x=0; x<w; x++)
			{
				T cell = get(x, y);
				out.print(cellToString(xy2i(x, y), cell));
				out.print('\t');
			}
			out.println();
		}
		out.println();
	}

	public void dump(PrintStream out)
	{
		dump(out, false);
	}

	protected String cellToString(int index, T cell)
	{
		return cell != null ? cell.toString() : "-";
	}

	public final int i2x(int index)
	{
		return index % w;
	}

	public final int i2y(int index)
	{
		return index / w;
	}

	public final int xy2i(int x, int y)
	{
		return x + y*w;
	}

	public Grid<T> clone() throws CloneNotSupportedException
	{
		Grid<T> copy = (Grid<T>) super.clone();
		copy.board = Arrays.copyOf(board, board.length);
		return copy;
	}

	public final int getWidth()
	{
		return w;
	}

	public final int getHeight()
	{
		return h;
	}

	public final int getNumCells()
	{
		return w * h;
	}

	public T[] copyElements(Class<T[]> clazz)
	{
		return (T[]) Arrays.copyOf(board, board.length, clazz);
	}
	
	public T[][] copyElements2D(Class<T[]> clazz)
	{
		T[][] rows = MiscUtils.new2DArray(clazz.getComponentType(), h, w);
		for (int y=0; y<h; y++)
		{
			rows[y] = Arrays.copyOfRange(board, y*w, (y+1)*w, clazz);
		}
		return rows;
	}

	public static <T> T[] to1D(T[][] rows, Class<T[]> clazz)
	{
		int h = rows.length;
		int w = rows[0].length;
		T[] arr = MiscUtils.newArray(clazz.getComponentType(), h * w);
		for (int y=0; y<h; y++)
		{
			System.arraycopy(rows[y], 0, arr, y*w, w);
		}
		return arr;
	}

	public T getDefaultValue()
	{
		return defaultValue;
	}
}
