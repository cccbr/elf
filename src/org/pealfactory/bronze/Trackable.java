package org.pealfactory.bronze;

/**
 * Part of the BronzeAge Java support system, this interface helps manage
 * lengthy tasks by allowing an implementing class to provide progress information,
 * and to allow the user to pause or abort the task. Error reporting on task
 * completion is also provided.
 *
 * @see Tracker
 * @author MBD
 * @version 2.0
 */
public interface Trackable
{
	public final static String kERROR_NONE = "OK";

	/** Starts a new job */
	public void reset();

	/** 0..100.0 */
	public double getProgress();

	public boolean isError();

	public String getErrorMsg();

	public void abort();

	public void pause();

	public void resume();
}