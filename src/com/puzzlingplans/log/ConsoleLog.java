package com.puzzlingplans.log;

public class ConsoleLog implements Log
{
	private int logLevel = LOG_DEBUG;
	
	//

	private void _log(String message, Throwable exception)
	{
		System.out.println(message);
		if (exception != null)
			exception.printStackTrace();
	}

	@Override
	public void log(String message)
	{
		if (logLevel >= LOG_INFO)
			_log(message, null);
	}

	@Override
	public void log(String message, Throwable exception)
	{
		if (logLevel >= LOG_INFO)
			_log(message, exception);
	}

	@Override
	public void error(String message)
	{
		if (logLevel >= LOG_ERROR)
			_log(message, null);
	}

	@Override
	public void error(String message, Throwable exception)
	{
		if (logLevel >= LOG_ERROR)
			_log(message, exception);
	}

	@Override
	public void debug(String message)
	{
		if (logLevel >= LOG_DEBUG)
			_log(message, null);
	}

	@Override
	public void debug(String message, Throwable exception)
	{
		if (logLevel >= LOG_DEBUG)
			_log(message, exception);
	}

	@Override
	public void setLogLevel(int logLevel)
	{
		this.logLevel = logLevel;
	}

	@Override
	public int getLogLevel()
	{
		return logLevel;
	}

	@Override
	public boolean canDebug()
	{
		return logLevel >= LOG_DEBUG;
	}
}
