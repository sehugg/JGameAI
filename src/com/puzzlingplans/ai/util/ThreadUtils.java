package com.puzzlingplans.ai.util;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;

public class ThreadUtils
{
	static final int availableProcessors = Runtime.getRuntime().availableProcessors();

	static ThreadPoolExecutor threadPool = newFixedThreadPool();

	//
	
	public static Thread[] getThreadPoolExecutorThreads()
	{
		return getThreadPoolExecutorThreads(threadPool);
	}

	public static Thread[] getThreadPoolExecutorThreads(ThreadPoolExecutor threadPool)
	{
		threadPool.prestartAllCoreThreads();
		int numThreads = threadPool.getCorePoolSize();
		if (numThreads == 0 || numThreads > 100)
			return null;
					
		Future<Thread>[] futures = new Future[numThreads];
		for (int i=0; i<numThreads; i++)
		{
			futures[i] = threadPool.submit(new Callable<Thread>()
			{
				@Override
				public Thread call() throws Exception
				{
					return Thread.currentThread();
				}
			});
		}
		Thread[] threads = new Thread[numThreads];
		for (int i=0; i<numThreads; i++)
		{
			try
			{
				threads[i] = futures[i].get();
			} catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		}
		return threads;
	}

	public static ThreadPoolExecutor newFixedThreadPool()
	{
		return (ThreadPoolExecutor) Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
	}

	public static int numThreadsPerPool()
	{
		return availableProcessors;
	}

	public static void submitAndWait(Runnable[] tasks) throws ExecutionException, InterruptedException
	{
		Future<?>[] futures = new Future[tasks.length];
		for (int i = 0; i < tasks.length; i++)
		{
			futures[i] = threadPool.submit(tasks[i]);
		}
		boolean canceled = false;
		for (int i = 0; i < futures.length; i++)
		{
			try
			{
				futures[i].get();
			} catch (InterruptedException e)
			{
				e.printStackTrace(); // TODO?
				futures[i].cancel(true);
				canceled = true;
			}
		}
		if (canceled)
			throw new InterruptedException();
	}

}
