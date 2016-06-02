package org.pealfactory.bronze;

import java.applet.*;
import java.net.*;

/**
 * This applet speeds up the processing of Microsiril library files.
 * It delegates to the {@link Librarian} class to provide download and
 * unpacking of zipped Microsiril libraries, and can also emit a complete
 * HTML &lt;select&gt; tag listing all methods in the downloaded library.
 *
 * @author MBD
 * @since Earwen
 */
public class LibraryApplet extends Applet implements Runnable
{
	public final static int kMAX_NAME = 20;

	private URL fContext;
	private String fPath;
	private Librarian fTask;
	public String fResult;

	/**
	 * Public constructor for use with Java plugin.
	 *
	 * @since Undomiel
	 */
	public LibraryApplet()
	{
	}

	public void init()
	{
		fContext = getCodeBase();
		fTask = new Librarian();
	}

	/**
	 * This is only valid once library has loaded
	 */
	public String getName(int i)
	{
		return fTask.getName(i);
	}

	/**
	 * This is only valid once library has loaded
	 */
	public String getPN(int i)
	{
		return fTask.getPN(i);
	}

	public void loadLibrary(String path)
	{
		fPath = path;
		fTask.startWorker(this, "Library loader");
	}

	public String getProgress()
	{
		return fTask.getJobName()+" "+fTask.getProgress(0)+"%";
	}

	public boolean isFinished()
	{
		return fTask.isFinished();
	}

	public boolean isAborted()
	{
		return fTask.isAborted();
	}

	public boolean isError()
	{
		return fTask.isError();
	}

	public String getErrorMsg()
	{
		if (fTask!=null)
			return fTask.getErrorMsg();
		return "";
	}

	public String getResult()
	{
		return fResult;
	}

	public void run()
	{
		fResult = fTask.getLibrary(fContext, fPath);
	}

	public void start()
	{
	}

	public void stop()
	{
		synchronized (this)
		{
			fTask.abort();
		}
	}

	public void destroy()
	{
	}
}
