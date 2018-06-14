package com.puzzlingplans.ai.board;


public class GridHelper64
{
	private final int width;
	private final int height;
	private final long allcells;
	private final long[] eleft;
	private final long[] eright;
	private final long[] etop;
	private final long[] ebottom;
	private final long[] distmasks;
	
	//
	
	public GridHelper64(int width, int height)
	{
		if (width < 0 || height < 0 || width*height > 64)
			throw new IllegalArgumentException("width*height must be <= 64");
		
		this.width = width;
		this.height = height;
		this.allcells = RANGEMASK(0, width*height);
		
		long left = line(0, 0, 0, 1, height);
		long right = line(width-1, 0, 0, 1, height);
		long top = line(0, 0, 1, 0, width);
		long bottom = line(0, height-1, 1, 0, width);
		eleft = new long[width];
		eright = new long[width];
		etop = new long[height];
		ebottom = new long[height];
		for (int i = 0; i < width; i++)
		{
			eleft[i] = left;
			eright[i] = right;
			left |= (left << 1);
			right |= (right >>> 1);
		}
		for (int i = 0; i < height; i++)
		{
			etop[i] = top;
			ebottom[i] = bottom;
			top |= (top << width);
			bottom |= (bottom >>> width);
		}
		int j = 0;
		long m = BM(3,3);
		distmasks = new long[4*2-1];
		while (j < distmasks.length)
		{
			m |= adjacent(m);
			distmasks[j++] = erode(m);
			//System.out.println(Long.toBinaryString(erode(m)));
			if (j >= distmasks.length)
				break;
			distmasks[j++] = m;
			//System.out.println(Long.toBinaryString(m));
		}
	}
	
	private long RANGEMASK(int i, int j)
	{
		assert(i >= 0 && j > i && i + j <= 64);
		if (i == 0 && j == 64)
			return -1;
		else
			return (CHOICE(j - i) - 1) << i;
	}

	private static final long CHOICE(int i)
	{
		return 1L << i;
	}

	private final int BI(int x, int y)
	{
		return x + y * width;
	}

	private final long BM(int x, int y)
	{
		if (x >= 0 && y >= 0 && x < width && y < height)
			return CHOICE(BI(x, y));
		else
			return 0;
	}

	public long line(int x0, int y0, int xd, int yd, int n)
	{
		long m = 0;
		while (n-- > 0)
		{
			m |= BM(x0 + xd * n, y0 + yd * n);
		}
		return m;
	}

	public long adjacent(long m)
	{
		// TODO: speedup
		return offset(m, -1, 0) | offset(m, 1, 0) | offset(m, 0, -1) | offset(m, 0, 1);
	}

	public long adjacent(long m, int n)
	{
		// TODO: speedup
		return offset(m, -n, 0) | offset(m, n, 0) | offset(m, 0, -n) | offset(m, 0, n);
	}

	public long diagonals(long m)
	{
		// TODO: speedup
		return offset(m, -1, -1) | offset(m, 1, 1) | offset(m, 1, -1) | offset(m, -1, 1);
	}

	public long diagonals(long m, int n)
	{
		// TODO: speedup
		return offset(m, -n, -n) | offset(m, n, n) | offset(m, n, -n) | offset(m, -n, n);
	}

	public long erode(long m)
	{
		long m1 = offset(m, -1, 0);
		long m2 = offset(m, 1, 0);
		long m3 = offset(m, 0, -1);
		long m4 = offset(m, 0, 1);
		return m & ((m1 & m2) | (m1 & m3) | (m1 & m4) | (m2 & m3) | (m2 & m4) | (m3 & m4));
	}

	public final long offset(long mask, int dx, int dy)
	{
		if (dx < 0)
			mask = (mask & ~eleft[-dx - 1]) >>> -dx;
		else if (dx > 0)
			mask = (mask & ~eright[dx - 1]) << dx;
		if (dy < 0)
			mask >>>= -dy * width;
		else if (dy > 0)
			mask <<= dy * width;
		return mask & allcells;
	}

	public long project(int x, int y, long us, long them, int dx, int dy)
	{
		long m = 0;
		long p = BM(x + dx, y + dy);
		while (p != 0)
		{
			if ((p & us) != 0)
				break; // one of our pieces
			m |= p;
			if ((p & them) != 0)
				break; // capture piece
			p = offset(p, dx, dy);
		}
		return m;
	}

	public int fire(int x, int y, long us, long them, int dx, int dy)
	{
		int i = BI(x, y);
		do {
			x += dx;
			y += dy;
			long p = BM(x, y);
			if (p == 0)
				return i; // out of bounds
			if ((p & us) != 0)
				return i; // one of our pieces
			i = BI(x, y);
			if ((p & them) != 0)
				return i; // capture piece
		} while (true);
	}

	public long distmask(int x, int y, int range)
	{
		return offset(distmasks[range], x - 3, y - 3);
	}

	/**
	 * @param occupied - the bitmask of squares to flood fill (inverse of boundary)
	 * @param index - the initial seed of the flood fill
	 * @return the final flood-filled mask
	 */
	public long floodfill(long occupied, int index)
	{
		long m = CHOICE(index);
		assert(m != 0);
		do {
			long next = adjacent(m) & occupied & ~m;
			if (next == 0)
				return m;
			m |= next;
		} while (true);
	}

	/**
	 * @param occupied - the bitmask of squares to flood fill (inverse of boundary)
	 * @param index - the initial seed of the flood fill
	 * @return the final flood-filled mask
	 */
	public long floodfilldiag(long occupied, int index)
	{
		long m = CHOICE(index);
		assert(m != 0);
		do {
			long next = (adjacent(m)|diagonals(m)) & occupied & ~m;
			if (next == 0)
				return m;
			m |= next;
		} while (true);
	}

}
