package com.puzzlingplans.ai.test;

public abstract class PerformanceCounter
{
	protected abstract long currentCPUTimeMillis();
	
	protected abstract long currentCPUTimeMillis(Thread[] threads);

	public static PerformanceCounter getInstance()
	{
		try
		{
			return (PerformanceCounter) Class.forName(PerformanceCounter.class.getName() + "MX").newInstance();
		} catch (Exception e)
		{
			return new PerformanceCounterSE();
		}
	}
}
