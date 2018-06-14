package com.puzzlingplans.ai.test;

public abstract class HangDetector implements Runnable
{
	Thread thread;
	int interval = 3000;
	String lastval;
	
	public HangDetector(int interval)
	{
		this.interval = interval;
		lastval = getCanaryString();
		thread = new Thread(this, "HangDetector");
		thread.setDaemon(true);
		thread.start();
	}
	
	public void finalize()
	{
		destroy();
	}
	
	public abstract String getCanaryString();

	public abstract void hangDetected();

	@Override
	public void run()
	{
		while (!thread.isInterrupted())
		{
			try
			{
				Thread.sleep(interval);
			} catch (InterruptedException e)
			{
				return;
			}
			String thisval = getCanaryString();
			//System.err.println(thisval);
			if (lastval.equals(thisval))
			{
				System.err.println("HANG DETECTED! " + thisval);
				hangDetected();
				return;
			}
			lastval = thisval;
		}
	}

	public void destroy()
	{
		thread.interrupt();
	}
}
