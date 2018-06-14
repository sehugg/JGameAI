package com.puzzlingplans.log;

public class LogAdapter implements Log
{
	protected Log log;
	protected String prefix = "";
	
	public LogAdapter(Log log) {
		this.log = log;
	}
	
	public LogAdapter(Log log, Class<?> clazz) {
		this.log = log;
		this.prefix = getLastComponent(clazz.getName(), '.') + ": ";
	}
	
	public LogAdapter(Class<?> clazz) {
		this(LogUtils.log, clazz);
	}
	
	private String getLastComponent (String name, char ch) {
		int pos = name.lastIndexOf(ch);
		if (pos >= 0)
			return name.substring(pos+1);
		else
			return name;
	}

	public boolean canDebug() {
		return getLogLevel() >= LOG_DEBUG;
	}

	@Override
	public void log (String message) {
		log.log(prefix + message);
	}

	@Override
	public void log (String message, Throwable exception) {
		log.log(prefix + message, exception);
	}

	@Override
	public void error (String message) {
		log.error(prefix + message);
	}

	@Override
	public void error (String message, Throwable exception) {
		log.error(prefix + message, exception);
	}

	@Override
	public void debug (String message) {
		if (canDebug())
			log.debug(prefix + message);
	}

	@Override
	public void debug (String message, Throwable exception) {
		if (canDebug())
			log.debug(prefix + message, exception);
	}

	@Override
	public void setLogLevel (int logLevel) {
		log.setLogLevel(logLevel);
	}

	@Override
	public int getLogLevel () {
		return log.getLogLevel();
	}
}
