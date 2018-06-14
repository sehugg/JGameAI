package com.puzzlingplans.ai.test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class PerformanceCounterMX extends PerformanceCounter
{
	ThreadMXBean mx = ManagementFactory.getThreadMXBean();

	@Override
	protected long currentCPUTimeMillis()
	{
		if (mx != null && mx.isCurrentThreadCpuTimeSupported())
			return mx.getCurrentThreadCpuTime() / 1000000L;
		else
			return System.currentTimeMillis();
	}

	@Override
	protected long currentCPUTimeMillis(Thread[] threads)
	{
		if (mx != null && mx.isThreadCpuTimeEnabled() && threads != null && threads.length > 0)
		{
			long n = 0;
			for (Thread t : threads)
				n += mx.getThreadCpuTime(t.getId());
			return n / (threads.length * 1000000L);
		} else
			return currentCPUTimeMillis();
	}

}
