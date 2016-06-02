package org.pealfactory.bronze;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

/**
 * Manages the download and unpacking of a zipped Microsiril library file;
 * can also parse the contents to produce an HTML SELECT form element
 * listing every method in the library.
 * <p>
 * The Librarian is used by the {@link LibraryApplet} as follows:
 * <ol>
 * <li>getLibrary() is called to download and unpack a zipped Microsiril
 * library file. This method updates the Tracker progress counter, 0 to 100%.
 * <li>Next, emitLibrary() is called to produce the HTML body of a SELECT tag.
 * Methods are listed by name, in the order they appear in the library.
 * A separate 0 to 100% progress counter is updated during parsing - this could
 * take a few seconds on a slow machine, especially for the large Surprise
 * library.
 * <li>The webpage that is hosting the LibraryApplet can then use the two
 * calls getName() and getPN() to retrieve the full name and place notation of
 * any method in the list.
 * </ol>
 * <p>
 * The Librarian extends {@link Tracker} to provide progress monitoring
 * (getProgress call).
 *
 * @author MBD
 * @since Earwen
 */
class Librarian extends Tracker
{
	public final int kBLOCK_SIZE = 1024;

	private Vector fNames;
	private Vector fPNs;
	private byte[] fLibFile;

	public Librarian()
	{
		super(100, "Loading method library");
	}

	public String getName(int i)
	{
		return (String)fNames.elementAt(i);
	}

	public String getPN(int i)
	{
		return (String)fPNs.elementAt(i);
	}

	/**
	 * Returns null if error, otherwise a String containing the
	 * complete HTML &lt;select&gt; tag for this library
	 */
	public String getLibrary(URL base, String path)
	{
		setTotalDuration(100);
		setJobName("Downloading method library");
		setProgress(0);
		try
		{
			loadLib(base, path);
		}
		catch (IOException e)
		{
			setErrorMsg("Failed to load library "+path+"\n"+e);
			return null;
		}
		setJobName("Parsing library");
		setProgress(0);
		String select = emitLibrary();
		return select;
	}

	private void loadLib(URL base, String path) throws IOException
	{
		try
		{
			if (isAborted())
				return;
			URL url;
			url = new URL(base, path);
			URLConnection conn = url.openConnection();
			setProgress(1);
			if (isAborted())
				return;
			conn.setUseCaches(true);
			int length = conn.getContentLength();
			if (length<1)
				length = 50000;
			ZipInputStream zip = new ZipInputStream(conn.getInputStream());
			ZipEntry entry = zip.getNextEntry();
			setProgress(2);
			if (isAborted())
				return;
			int size = (int)entry.getSize();
			if (size<=0)
				throw new IOException("Zip entry is empty");
			Tracker loadJob = new Tracker(size);
			loadJob.setJobName(getJobName());
			startDelegateJob(loadJob, 100-2);
			fLibFile = new byte[size];
			int pos = 0;
			while (pos<size)
			{
				loadJob.setProgress(pos);
				int toRead = Math.min(kBLOCK_SIZE, size-pos);
				int bytesRead = zip.read(fLibFile, pos, toRead);
				if (bytesRead<0)
					break;
				pos+= bytesRead;
				if (isAborted())
					return;
			}
			loadJob.setProgress(pos);
		}
		finally
		{
			endDelegateJob();
		}
	}

	public String emitLibrary()
	{
		long time = System.currentTimeMillis();
		if (isAborted())
			return "";
		fNames = new Vector();
		fPNs = new Vector();
		int n = fLibFile.length;
		setTotalDuration(n);
		String libFile = new String(fLibFile);
		StringBuffer s = new StringBuffer(n);
		s.setLength(0);
		int listSize = 0;
		int pos = 0;
		while (true)
		{
			if (isAborted())
				return "";
			int i = libFile.indexOf("\n", pos);
			if (i<0)
				break;
			String line = libFile.substring(pos, i);
			pos = i+1;
			if (line.startsWith("<XMP>"))
				line = line.substring(5);
			if (line.startsWith("**"))
				continue;
			i = line.indexOf(' ');
			int i2 = line.indexOf(' ', i+1);
			if (i2>0)
			{
				String name = line.substring(0, i);
				if (!name.equals("Zzz"))
				{
					String code = line.substring(i+1, i2);
					String pn = line.substring(i2+1);
					fNames.addElement(name);
					fPNs.addElement(code+" "+pn);
					listSize++;
					if (name.length()>LibraryApplet.kMAX_NAME)
						name = name.substring(0, LibraryApplet.kMAX_NAME-1)+"...";
					s.append("<option value=\""+listSize+"\">"+name+" ("+code+")</option>");
				}
			}
			setProgress(pos);
		}
		time = System.currentTimeMillis()-time;
		return s.toString();
	}

}
