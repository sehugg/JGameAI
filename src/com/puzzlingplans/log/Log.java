package com.puzzlingplans.log;

public interface Log
{
	public static final int LOG_DEBUG = 3;
	public static final int LOG_INFO = 2;
	public static final int LOG_ERROR = 1;
	public static final int LOG_NONE = 0;

	/** Logs a message to the console or logcat */
	public void log (String message);

	/** Logs a message to the console or logcat */
	public void log (String message, Throwable exception);

	/** Logs an error message to the console or logcat */
	public void error (String message);

	/** Logs an error message to the console or logcat */
	public void error (String message, Throwable exception);

	/** Logs a debug message to the console or logcat */
	public void debug (String message);

	/** Logs a debug message to the console or logcat */
	public void debug (String message, Throwable exception);

	/** Sets the log level. {@link #LOG_NONE} will mute all log output. {@link #LOG_ERROR} will only let error messages through.
	 * {@link #LOG_INFO} will let all non-debug messages through, and {@link #LOG_DEBUG} will let all messages through.
	 * @param logLevel {@link #LOG_NONE}, {@link #LOG_ERROR}, {@link #LOG_INFO}, {@link #LOG_DEBUG}. */
	public void setLogLevel (int logLevel);

	/** Gets the log level. */
	public int getLogLevel ();
	
	/** Is debug level set? */
	public boolean canDebug();
}
