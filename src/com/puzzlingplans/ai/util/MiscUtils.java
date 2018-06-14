package com.puzzlingplans.ai.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

public class MiscUtils
{
	public static String format(String format, Object... args)
	{
		return String.format(format, args);
	}

	public static long nanoTime()
	{
		return System.nanoTime();
	}

	public static <T> T[] newArray(Class<?> componentType, int i)
	{
		return (T[]) Array.newInstance(componentType, i);
	}

	public static <T> T[][] new2DArray(Class<?> componentType, int h, int w)
	{
		return (T[][]) Array.newInstance(componentType, h, w);
	}

	public static <T> void sumFields(T total, T[] stats)
	{
		Field[] fields = total.getClass().getFields();
		for (Field f : fields)
		{
			try
			{
				int y = Math.round((Integer) f.get(total));
				for (int i = 0; i < stats.length; i++)
				{
					int x = (Integer) f.get(stats[i]);
					if (f.getName().startsWith("max"))
						y = Math.max(x, y);
					else if (f.getName().startsWith("min"))
						y = Math.min(x, y);
					else
						y += x;
				}
				f.set(total, y);
			} catch (Exception e)
			{
				e.printStackTrace();
			}
		}
	}

	public static boolean isAssignableFrom(Class<?> parentClass, Class<?> childClass)
	{
		return parentClass.isAssignableFrom(childClass);
	}

	public static String readToString(Reader reader) throws IOException
	{
		BufferedReader sreader = new BufferedReader(reader);
		String line;
		StringBuffer st = new StringBuffer();
		while ((line = sreader.readLine()) != null)
		{
			st.append(line);
		}
		String result = st.toString();
		return result;
	}


}
