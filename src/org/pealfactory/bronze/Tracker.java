package org.pealfactory.bronze;

/**
 * Part of the BronzeAge Java support system, this class provides an implementation
 * of the {@link Trackable} interface to provide asynchronous management of
 * lengthy jobs. Support for delegate jobs and worker Thread creation is also
 * provided.
 * <p>
 * For an example of how Tracker is used, see the {@link Librarian} class used by
 * the {@link LibraryApplet} to download method libraries. Librarian extends Tracker,
 * allowing it to be used as a Trackable "task" by the applet. Librarian provides
 * the synchronous method getLibrary() which performs the downloading work.
 * The synchronous "worker code" must perform these duties to work effectively as
 * a Trackable:
 * <ol>
 * <li>At the beginning of the task, set the job name {@link #setJobName},
 * set the progress to zero with {@link #setProgress} and set the total duration of the
 * job {@link #setTotalDuration}. (The total duration can be any integer, and
 * represents the amount of progress reached when the job is finished.)
 * <li>The code that performs the task must be interspersed with checks for abort
 * and pause status {@link #isAborted} and {@link #isPaused}. These checks should
 * be performed at least every {@link #kRESPONSE_TIME} milliseconds, but more
 * often is better - usually there is a suitable inner loop which can be used.
 * <li>To track progress, {@link #setProgress} should also be called frequently
 * within the task code, again preferrably in an inner loop. It should monotonically
 * and as evenly as possible increment progress from 0 up to the total duration
 * set at the start of the job.
 * <li>No special code is needed at the end of the job, except that progress should
 * have reached the total duration value set at the beginning. However if an
 * error occurs during task processing, the {@link #setErrorMsg} method should be
 * called before returning.
 * </ol>
 * In the case of the Librarian, the getLibrary() code contains subtasks. If you
 * examine the loadLib() method you will see a delegate Tracker object is created.
 * This allows one job to be split into subtasks of different durations; the master
 * Tracker handles the calculation of total progress. Here's a simple example
 * where we want to track a job that comprises two subtasks:
 * <pre>
 setJobName("Master task");
 // We'll give 1 progress point to each subtask, so a total of 2.
 setTotalDuration(2);
 setProgress(0);

 // Start 1st subtask - has a total duration of 255
 Tracker job1 = new Tracker(255);
 job1.setJobName("Subtask 1");
 // Start the delegate job, allocating it 1 out of 2 "master job" progress points.
 startDelegateJob(job1, 1);
 // Here's the "worker" code
 for (int i=0; i<255; i++)
 {
  job1.setProgress(i);
  if (isAborted())
    return;
  // Do stuff
 }
 // Delegate job finishes - must call this to ensure correct update of master job
 endDelegateJob();

 // Now start 2nd subtask - has a total duration of 1000
 Tracker job2 = new Tracker(1000);
 job2.setJobName("Subtask 2");
 startDelegateJob(job2, 1);
 for (int i=0; i<1000; i++)
 {
  job2.setProgress(i);
  if (isAborted())
    return;
  // Do stuff
 }
 endDelegateJob();
</pre>
 *
 * Of course it would have been easy enough in this case to have a single task
 * with a duration of 1255. However, using the delegate system makes for simpler
 * code, and also helps in cases where the length of the second subtask can't be
 * determined until the first is complete.
 * <p>
 * The final service offered by Tracker is the provision of worker threads to
 * help asynchronous monitoring of tasks. Again the LibraryApplet provides a good
 * example of this. The Tracker method {@link #startWorker} is called by client
 * code to start a worker thread. A Runnable object must be given as a parameter,
 * whose run() method contains the synchronous worker code, and of course usually
 * implements Trackable. The asynchronous Tracker methods {@link #isFinished},
 * {@link #pause}, {@link #resume} and {@link #abortWorker} may then be used from
 * client code whilst the task is running in its separate Thread.
 *
 * @author MBD
 * @version 2.0
 */
public class Tracker implements Trackable, Runnable
{
	private void test()
	{
		setJobName("Master task");
		// We'll give 1 progress point to each subtask, so a total of 2.
		setTotalDuration(2);
		setProgress(0);

		// Start 1st subtask - has a total duration of 255
		Tracker job1 = new Tracker(255);
		job1.setJobName("Subtask 1");
		// Start the delegate job, allocating it 1 out of 2 "master job" progress points.
		startDelegateJob(job1, 1);
		// Here's the "worker" code
		for (int i=0; i<255; i++)
		{
			job1.setProgress(i);
			if (isAborted())
				return;
			// Do stuff
		}
		// Delegate job finishes - must call this to ensure correct update of master job
		endDelegateJob();

		// Now start 2nd subtask - has a total duration of 1000
		Tracker job2 = new Tracker(1000);
		job2.setJobName("Subtask 2");
		startDelegateJob(job2, 1);
		for (int i=0; i<1000; i++)
		{
			job2.setProgress(i);
			if (isAborted())
				return;
			// Do stuff
		}
		endDelegateJob();

		// All done!
	}

	public static final String kDEFAULT_JOB_NAME = "busy...";

	/** The task should check abort and pause status more often than this */
	public static final int kRESPONSE_TIME = 300;

	private int fTotalDuration;
	/** 0-fTotalDuration */
	private int fProgress;
	private boolean fAbort;
	private boolean fPause;
	private String fJobName;
	private boolean fError;
	private String fErrorMsg;
	private Trackable fDelegateJob;
	private int fDelegateDuration;

	/** Asynchronous support. */
	private Thread fWorker;
	private Runnable fTask;

	public Tracker(int total)
	{
		fProgress = 0;
		fAbort = false;
		fErrorMsg = kERROR_NONE;
		fError = false;
		fDelegateJob = null;
		fJobName = kDEFAULT_JOB_NAME;
		setTotalDuration(total);
	}

	public Tracker(int total, String name)
	{
		this(total);
		setJobName(name);
	}

	public String getJobName()
	{
		if (fDelegateJob!=null && fDelegateJob instanceof Tracker)
			return ((Tracker)fDelegateJob).getJobName();
		return fJobName;
	}

	public void setJobName(String name)
	{
		fJobName = name;
	}

	public void setTotalDuration(int total)
	{
		fTotalDuration = total;
	}

	public synchronized boolean isError()
	{
		return fError;
	}

	public synchronized String getErrorMsg()
	{
		return fErrorMsg;
	}

	public void setErrorMsg(String msg)
	{
		synchronized(this)
		{
			fError = true;
			fErrorMsg = msg;
		}
	}

	/** 0..100.0 */
	public synchronized double getProgress()
	{
		double p = fProgress*100.0;
		if (fDelegateJob!=null)
			p = p + fDelegateJob.getProgress()*fDelegateDuration;
		p/= fTotalDuration;
		if (p>100.0)
			p = 100.0;
		return p;
	}

	/**
	 * Final for speed
	 */
	public final String getProgress(int sigFigs)
	{
		double p = getProgress();
		int intPart = (int)p;
		if (sigFigs==0)
			return ""+intPart;
		p-= intPart;
		p*= Math.pow(10.0, (double)sigFigs);
		int fracPart = (int)p;
		String s = ""+fracPart;
		while (s.length()<sigFigs)
			s = "0"+s;
		return intPart+"."+s;
	}

	/**
	 * Terminates any delegate job!
	 * 0...total duration
	 */
	public synchronized void setProgress(int progress)
	{
		endDelegateJob();
		if (progress>=fTotalDuration)
			fProgress = fTotalDuration;
		else
		{
			fProgress = progress;
			if (progress==0)
				reset();
		}
	}

	public synchronized void startDelegateJob(Trackable job, int duration)
	{
		fDelegateJob = job;
		fDelegateJob.reset();
		fDelegateDuration = duration;
	}

	public synchronized void endDelegateJob()
	{
		if (fDelegateJob!=null)
		{
			fProgress+= fDelegateDuration;
			if (!isError() && fDelegateJob.isError())
			{
				fError = true;
				fErrorMsg = fDelegateJob.getErrorMsg();
			}
			fDelegateJob = null;
		}
	}

	/**
	 * Abort status treated like an error condition
	 */
	public synchronized void abort()
	{
		fAbort = true;
		if (fDelegateJob!=null)
			fDelegateJob.abort();
		setErrorMsg("Aborted");
	}

	public synchronized void reset()
	{
		fAbort = false;
		fPause = false;
		fError = false;
		if (fDelegateJob!=null)
			fDelegateJob.reset();    // This will reset any aborted status too
	}

	/**
	 * Final but not synchronized for speed - access is atomic so
	 * shouldn't cause a problem.
	 */
	public final /*synchronized*/ boolean isAborted()
	{
		return fAbort;
	}

	// --------------------------------------------------
	// Methods for asynchronous worker support
	// --------------------------------------------------

	/**
	 * Only valid after startWorker() called.
	 */
	public boolean isFinished()
	{
		return fWorker==null;
	}

	public synchronized void pause()
	{
		fPause = true;
		if (fDelegateJob!=null)
			fDelegateJob.pause();
	}

	public synchronized void resume()
	{
		fPause = false;
		if (fDelegateJob!=null)
			fDelegateJob.resume();
		notifyAll();
	}

	/**
	 * Final but not synchronized for speed - access is atomic so
	 * shouldn't cause a problem.
	 */
	public final /*synchronized*/ boolean isPaused()
	{
		return fPause;
	}

	/**
	 * Remains responsive to aborts whilst paused - should therefore
	 * check for abort in caller after this returns.
	 */
	public synchronized void waitForResume()
	{
		while (isPaused())
		{
			try
			{
				if (isAborted())
					return;
				wait(kRESPONSE_TIME/2);
			}
			catch (InterruptedException e)
			{
			}
		}
	}

	/**
	 * Use this to start a worker thread to run a task asynchronously.
	 * The task is assumed to start and run a synchronous Tracker job.
	 */
	public void startWorker(Runnable task, String threadName)
	{
		abortWorker(kRESPONSE_TIME);
		reset();
		fTask = task;
		fWorker = new Thread(this, threadName);
		fWorker.start();
	}

	public synchronized void abortWorker(int millisToWait)
	{
		if (fWorker!=null)
		{
			abort();
			if (fWorker.isAlive())
			{
				try
				{
					wait(millisToWait);
				}
				catch (InterruptedException e)
				{
				}
				if (fWorker!=null)
					fWorker.stop();
			}
			fWorker = null;
		}
	}

	/**
	 * Only Runnable for private use by worker Thread.
	 */
	public void run()
	{
		try
		{
			if (isAborted())
				return;
			fTask.run();
			synchronized (this)
			{
				fWorker = null;
				notifyAll();
			}
		}
		catch (Throwable t)
		{
			if (t instanceof ThreadDeath)
				throw (ThreadDeath)t;
			t.printStackTrace(System.out);
			setErrorMsg("FATAL: "+t);
			synchronized (this)
			{
				fWorker = null;
				notifyAll();
			}
		}
	}
}