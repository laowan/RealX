package com.ycloud.utils;
public interface ILog
{
	public void verbose(String tag, String msg);
	public void debug(String tag, String msg);
	public void info(String tag, String msg);
	public void warn(String tag, String msg);
	public void error(String tag, String msg);
	public void error(String tag, String msg, Throwable t);
}
