package com.puzzlingplans.ai.test;

public class PerformanceCounterSE extends PerformanceCounter
{

	@Override
	protected long currentCPUTimeMillis()
	{
		return System.currentTimeMillis();
	}

	@Override
	protected long currentCPUTimeMillis(Thread[] threads)
	{
		return System.currentTimeMillis();
	}

}
