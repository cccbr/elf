/*
Elf half-lead spliced composer
Copyright (C) 2002-2003 Mark B. Davies
Author contact: elf@bronze-age.org

This program is free software; you can redistribute it and/or
modify it under the terms of the GNU General Public License
as published by the Free Software Foundation; either version 2
of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

Please note that the GNU General Public License does not permit
incorporation of any part of this work into proprietary programs.

See the GNU General Public License for more details:
http://www.gnu.org/licenses/gpl.html
*/

package org.pealfactory.compose.halfleadspliced;

import org.pealfactory.bronze.*;
import org.pealfactory.ring.*;

import java.applet.*;
import java.util.*;

/**
 * Elf, the Online Half-lead Spliced Composing Engine.
 * The Elf applet provides a public API, accessible from client-side javascript,
 * to manage method libraries, music and composition parameters, and to
 * initiate and monitor searches. The search algorithm itself is handled
 * by the {@link Composer} class.
 * <p>
 * Note that the applet should be scripted with a 0x0 visible size, since it doesn't
 * provide any external user interface. The UI is the sole responsibility of DHTML
 * in the Elf website. All Elf functionality is accessed from Javascript on the
 * invoking web page via calls to public methods in the applet. For this to function
 * correctly a Javascript to Java communication protocol must be supported by the
 * browser, such as LiveConnect. Browsers such as IE for the Macintosh and Netscape 6.0
 * do not support such communication.
 * <p>
 * Many methods in this class are public so that they can be accessed from script.
 *
 * @author MBD
 */
public class Elf extends Applet implements ComposerHost, Runnable, StandardMethods
{
	public final static String kOPEN_SOURCE_ANNOUNCE =
			"Elf comes with ABSOLUTELY NO WARRANTY.\nIt is free software, and you are welcome to redistribute it under certain conditions.\nFor more details see the GNU General Public Licence at http://www.gnu.org/licenses/gpl.html";
	public final static String kHEAD_LINE = "======================================";

	public final static int kMAX_NAME = 15;
	public final static String kNEWLINE = "\r\n";
	public final static char kSEPARATOR = '|';

	/** Used by the website to check Java VM with LiveConnect available */
	public boolean available = true;

	private Composer fComposer;
	private Tracker fTracker;
	private Vector fMethodLibrary;
	private Vector fMethods;
	private Vector fMusic;
	private int fCOMScore = 2;
	private int fBalanceScore = 1;
	private Tables fTables;
	private Vector fBestComps;
	private int fNCompsToKeep = 10;

	private int fNComps = 0;
	private int fCompsPerSec = 0;
	private int fNodesPerSec = 0;
	private int fBestScore = 0;
	private int fBestMusic = 0;
	private int fBestCOM = 0;
	private int fBestBalance = 0;
	private String fTimeLeft = "";
	private boolean fOutputChanged = false;
	private String fOutput = "";

	/**
	 * Made public for use with Java plugin (Undomiel build).
	 */
	public Elf()
	{
		fTracker = new Tracker(100);

		fMethods = new Vector();
		fMethods.addElement(kCAMBRIDGE);
		fMethods.addElement(kYORKSHIRE);
		fMethods.addElement(kLINCOLNSHIRE);
		fMethods.addElement(kSUPERLATIVE);

		resetLibrary();
		resetMusic();
	}

	public void init()
	{
		System.out.println("\n"+kHEAD_LINE);
		System.out.println(deAccent(getVersionString()));
		System.out.println(Composer.kCOPYRIGHT);
		System.out.println(kOPEN_SOURCE_ANNOUNCE);
		System.out.println(kHEAD_LINE);

		fTables = new Tables();
		fTables.buildNodeTable();
		methodsChanged();
	}

	public void start()
	{
		System.out.println("Elf ready");
	}

	/**
	 * Part of the public script interface.
	 */
	public String getVersionString()
	{
		return Composer.kVERSION;
	}

	/**
	 * Converts any HTML accent characters (e.g. <code>&uacute;</code>) into
	 * normal unaccented 7-bit ASCII (e.g. 'u').
	 *
	 * @since Tinuviel
	 */
	private String deAccent(String s)
	{
		StringBuffer ret = new StringBuffer();
		int i1 = 0;
		while (i1<s.length())
		{
			int i2 = s.indexOf('&', i1);
			if (i2<0)
				break;
			int i3 = s.indexOf(';', i2);
			if (i3<0)
				break;
			ret.append(s.substring(i1, i2));
			ret.append(s.charAt(i2+1));
			i1 = i3+1;
		}
		ret.append(s.substring(i1));
		return ret.toString();
	}

	/**
	 * Allows number of comps in "top ten" list to be configured from the website
	 *
	 * @since Tinuviel B
	 */
	public int getNCompsToKeep()
	{
		return fNCompsToKeep;
	}

	/**
	 * Allows number of comps in "top ten" list to be configured from the website
	 *
	 * @since Tinuviel B
	 */
	public void setNCompsToKeep(int NCompsToKeep)
	{
		fNCompsToKeep = NCompsToKeep;
	}

	/**
	 * Allows COM score to be configured from the website
	 *
	 * @since Tinuviel
	 */
	public int getCOMScore()
	{
		return fCOMScore;
	}

	/**
	 * Allows COM score to be configured from the website
	 *
	 * @since Tinuviel
	 */
	public void setCOMScore(int COMScore)
	{
		fCOMScore = COMScore;
	}

	/**
	 * Allows method balance score to be configured from the website
	 *
	 * @since Tinuviel
	 */
	public int getBalanceScore()
	{
		return fBalanceScore;
	}

	/**
	 * Allows method balance score to be configured from the website
	 *
	 * @since Tinuviel
	 */
	public void setBalanceScore(int balanceScore)
	{
		fBalanceScore = balanceScore;
	}

	public void resetMusic()
	{
		fMusic = new Vector();
		for (int i=0; i<Music.kDEFAULT.length; i++)
			fMusic.addElement(Music.kDEFAULT[i]);
		// Don't need to do this the first time, before tables constructed
		if (fTables!=null)
			musicChanged();
	}

	public int getNMusicDefs()
	{
		return fMusic.size();
	}

	public String getMusic(int i)
	{
		return fMusic.elementAt(i).toString();
	}

	public void removeMusic(int i)
	{
		fMusic.removeElementAt(i);
		musicChanged();
	}

	public String addMusic(String name, int score, String matches)
	{
		Music m = new Music(name, score, matches);
		boolean existing = false;
		for (int i=0; i<fMusic.size(); i++)
			if (name.equalsIgnoreCase(((Music)fMusic.elementAt(i)).getName()))
			{
				fMusic.setElementAt(m, i);
				existing = true;
				break;
			}
		if (!existing)
			fMusic.addElement(m);
		musicChanged();
		return "";
	}

	private void musicChanged()
	{
		Music[] music = new Music[fMusic.size()];
		fMusic.copyInto(music);
		fTables.setMusic(music);
	}

	public void resetLibrary()
	{
		fMethodLibrary = new Vector();
		// Must use newLibraryMethod() call to keep vector in sorted order (by abbreviation)
		newLibraryMethod(kCAMBRIDGE);
		newLibraryMethod(kYORKSHIRE);
		newLibraryMethod(kLINCOLNSHIRE);
		newLibraryMethod(kSUPERLATIVE);
		newLibraryMethod(kPUDSEY);
		newLibraryMethod(kRUTLAND);
		newLibraryMethod(kBRISTOL);
		newLibraryMethod(kLONDON);
		newLibraryMethod(kASHTEAD);
		newLibraryMethod(kUXBRIDGE);
		newLibraryMethod(kCASSIOBURY);
		newLibraryMethod(kBELFAST);
		newLibraryMethod(kGLASGOW);
	}

	public int getLibrarySize()
	{
		return fMethodLibrary.size();
	}

	/**
	 * Returns one String with name, abbreviation, PN and leadhead separated
	 * by '|' characters
	 */
	public String getLibraryMethod(int i)
	{
		Method m = (Method)fMethodLibrary.elementAt(i);
		String s = m.getName()+kSEPARATOR+m.getAbbrev()+kSEPARATOR+m.getPN()+kSEPARATOR+m.getLeadhead();
		return s;
	}

	/**
	 * Returns empty string if successful, otherwise an error message.
	 * Also updates existing methods if "name" already in use.
	 */
	public String addMethodToLibrary(String name, String abbrev, String pn)
	{
		if (name==null || name.length()==0)
			return "The method must be given a name";
		name = name.replace(kSEPARATOR, ' ');
		if (abbrev==null || abbrev.length()!=1 || abbrev.charAt(0)==kSEPARATOR)
			return "The method must be given a one-letter abbreviation";
		if (pn==null || pn.length()==0)
			return "No place notation specified!";
		pn = pn.replace(kSEPARATOR, '.');
		Method newMethod = new Method(name, abbrev, pn);
		PN p = newMethod.getPN();
		if (p.highestPlace()>8)
			return "Sorry - only Major methods are currently supported";
		if (!p.isSymmetric())
			return "Method must be symmetric for half-lead splicing";
		if (newMethod.getLeadhead().bellAt(1)!=1)
			return "The treble must be the hunt bell";
		if (newMethod.getHalflead().bellAt(8)!=1)
			return "The treble must be in 8ths place at the half-lead";
		int old = fMethodLibrary.indexOf(newMethod);
		for (int i=0; i<fMethodLibrary.size(); i++)
		{
			Method m = (Method)fMethodLibrary.elementAt(i);
			if (i!=old && m.getAbbrev().equals(abbrev))
			{
				StringBuffer avail = new StringBuffer(26);
				char c = 'A';
				for (int j=0; j<fMethodLibrary.size(); j++)
				{
					char abb = ((Method)fMethodLibrary.elementAt(j)).getAbbrev().charAt(0);
					while (c<abb)
						avail.append(c++);
					c = (char)(abb+1);
				}
				while (c<='Z')
					avail.append(c++);
				return "The abbreviation "+abbrev+" is already used.\nAvailable abbreviations are:\n  "+avail;
			}
		}
		System.out.println("OLD = "+old);
		if (old>=0)
		{
			int i = fMethods.indexOf(newMethod);
			if (i>=0)
			{
				fMethods.setElementAt(newMethod, i);
				methodsChanged();
			}
			fMethodLibrary.removeElementAt(old);
		}
		newLibraryMethod(newMethod);
		return "";
	}

	/**
	 * Inserts new method into library, sorted on abbreviation.
	 * Method must not already exist.
	 */
	private void newLibraryMethod(Method newMethod)
	{
		String abbrev = newMethod.getAbbrev();
		int i;
		for (i=0; i<fMethodLibrary.size(); i++)
		{
			Method m = (Method)fMethodLibrary.elementAt(i);
			if (abbrev.compareTo(m.getAbbrev())<0)
				break;
		}
		fMethodLibrary.insertElementAt(newMethod, i);
	}

	public void removeMethodFromLibrary(int i)
	{
		fMethodLibrary.removeElementAt(i);
	}

	public int getNMethods()
	{
		return fMethods.size();
	}

	/**
	 * Return the lead length of the shortest method in the current composition method list.
	 *
	 * @since Undomiel-B
	 * @return
	 */
	public int getShortestLead()
	{
		int len = 5000;
		for (int i=fMethods.size()-1; i>=0; i--)
		{
			Method m = (Method)fMethods.elementAt(i);
			if (m.getLeadLength()<len)
				len = m.getLeadLength();
		}
		return len;
	}

	/**
	 * Return the lead length of the longest method in the current composition method list.
	 *
	 * @since Undomiel-B
	 * @return
	 */
	public int getLongestLead()
	{
		int len = 0;
		for (int i=fMethods.size()-1; i>=0; i--)
		{
			Method m = (Method)fMethods.elementAt(i);
			if (m.getLeadLength()>len)
				len = m.getLeadLength();
		}
		return len;
	}

	/**
	 */
	public boolean isMethodInComp(String name)
	{
		for (int i=fMethods.size()-1; i>=0; i--)
		{
			Method m = (Method)fMethods.elementAt(i);
			if (m.getName().equals(name))
				return true;
		}
    return false;
	}

	public String getMethod(int i)
	{
		Method m = (Method)fMethods.elementAt(i);
		String name = m.getName();
		if (name.length()>kMAX_NAME)
			name = name.substring(0, kMAX_NAME-1)+"...";
		return m.getAbbrev()+" "+name;
	}

	public void removeMethod(int i)
	{
		fMethods.removeElementAt(i);
		methodsChanged();
	}

	/**
	 * Parameter <code>i</code> is index in method library
	 */
	public void addMethod(int i)
	{
		fMethods.addElement(fMethodLibrary.elementAt(i));
		methodsChanged();
	}

	private void methodsChanged()
	{
		Method[] methods = new Method[fMethods.size()];
		fMethods.copyInto(methods);
		fTables.setMethods(methods);
	}


	/**
	 * Start composition, using the passed parameter values.
	 * Returns false if composer fails to start for some reason (usually because tables are still building).
	 */
	public boolean compose(int nleads, int nparts, boolean tenorsTogether, boolean nicePE, boolean optimumBalance, boolean maxCOM, int calls, boolean LHonly)
	{
		int minPartLength = getShortestLead()*nleads;
		int maxPartLength = getLongestLead()*nleads;
		return compose(nleads, nparts, tenorsTogether, nicePE, optimumBalance, maxCOM, calls, LHonly, minPartLength, maxPartLength);
	}

	/**
	 * Start composition, using the passed parameter values, including min and max part length.
	 * Returns false if composer fails to start for some reason (usually because tables are still building).
	 *
	 * @since Undomiel-B
	 */
	public boolean compose(int nleads, int nparts, boolean tenorsTogether, boolean nicePE, boolean optimumBalance, boolean maxCOM, int calls, boolean LHonly, int minPartLength, int maxPartLength)
	{
		if (fTables==null || !fTables.isBuilt())
		{
			System.out.println("Cannot compose yet - table building still in progress");
			return false;
		}

		fTracker.abortWorker(300);

		fComposer = Composer.create(fTables, nleads, nparts, tenorsTogether, nicePE, optimumBalance, LHonly, calls);

		fNComps = 0;
		fCompsPerSec = 0;
		fNodesPerSec = 0;
		fBestScore = 0;
		fBestMusic = 0;
		fBestCOM = 0;
		fBestBalance = 0;
		fTimeLeft = "";
		fOutput = "";
		fOutputChanged = true;

		fComposer.setMinBalance(1);
		fComposer.setMinScore(0);
		fComposer.setCOMScore(fCOMScore);
		fComposer.setBalanceScore(fBalanceScore);
		fComposer.setMinPartLength(minPartLength);
		fComposer.setMaxPartLength(maxPartLength);

		Method[] m = fTables.getMethods();
		int nmethods = m.length;
		int minCOM;
		/**
		 * Set initial minimum required changes-of-method.
		 * If maxCOM isn't set, this is the minimum possible value that allows every
		 * method to be rung in the part.
		 * If maxCOM is set, it is the maximum possible COM for the number of
		 * methods and number of leads in the part. A complication for LH spliced
		 * is that 2-spliced searches with an odd number of leads in the part cannot
		 * avoid having a repeated lead, so the maximum COM is one less than otherwise.
		 */
		if (nmethods<2)
			maxCOM = false;
		if (fComposer.isLHSpliced())
		{
			if (maxCOM)
			{
				minCOM = nleads;
				if (nmethods==2 && (nleads&1)==1)
					minCOM--;
			}
			else
				minCOM = nmethods-1;
		}
		else
		{
			if (maxCOM)
				minCOM = nleads*2;
			else
				minCOM = Math.min(nmethods, nleads+2);
		}
		fComposer.setMinCOM(minCOM);
		// Ensure we start e.g. CC YC not CC CC ...
		if (!fComposer.isLHSpliced() && m.length>1)
			fComposer.setStartComp(m[0].getAbbrev()+m[0].getAbbrev()+" "+m[1].getAbbrev()+m[0].getAbbrev());
		fBestComps = new Vector();

		fTracker.startWorker(this, "Composer");
		return true;
	}

	public String getStatus()
	{
		String s;
		if (fTracker.isFinished() && !fTracker.isError())
		{
			fNComps = fComposer.getNComps();
			fBestScore = fComposer.getBestScore();
			fBestMusic = fComposer.getBestMusic();
			fBestCOM = fComposer.getBestCOM();
			fBestBalance = fComposer.getBestBalance();
			s = "<b>Search Complete</b>";
			s+= "<br>Search took: "+fComposer.getSearchTime();
			s+= "<br><table><tr><td colspan=2>Comps found&nbsp;</td><td colspan=2>Leads searched</td></tr>";
			s+= "<tr><td align='middle'>"+fNComps+"</td><td></td><td align='middle'>"+fComposer.getNNodes()+"</td></tr>";
			s+= "<tr><td colspan=4 align='left'><b>Best scores</b></td></tr>";
			// Force "no results" to be shown if necessary
			fOutputChanged = true;
		}
		else
		{
			if (fTracker.isFinished())
			{
				s = "<b>Composer idle</b>";
			}
			else
			{
				s = "<b>"+fTracker.getJobName()+"</b> ";
				if (fComposer.isComposing())
				{
					s+= fComposer.getProgress(3)+"%";
					fNComps = fComposer.getNComps();
					fCompsPerSec = fComposer.getCompsPerSec();
					fNodesPerSec = fComposer.getNodesPerSec();
					fBestScore = fComposer.getBestScore();
					fBestMusic = fComposer.getBestMusic();
					fBestCOM = fComposer.getBestCOM();
					fBestBalance = fComposer.getBestBalance();
					fTimeLeft = fComposer.estimateTimeLeft();
				}
				else
					s+= (int)fTables.getProgress()+"%";
			}
			s+= "<br>Estimated time to completion: "+fTimeLeft;
			s+= "<br><table><tr><td colspan=2>Comps found&nbsp;</td><td>Comps/s</td><td>&nbsp;Leads/s</td></tr>";
			s+= "<tr><td align='middle'>"+fNComps+"</td><td></td><td align='middle'>"+fCompsPerSec+"</td><td align='middle'>"+fNodesPerSec+"</td></tr>";
			s+= "<tr><td colspan=4 align='left'><b>Best scores so far</b></td></tr>";
		}
		s+= "<tr><td>Score</td><td>Music</td><td>&nbsp;&nbsp;COM</td><td>Balance</td></tr>";
		s+= "<tr><td align='middle'>"+fBestScore+"</td><td align='middle'>"+fBestMusic+"</td><td align='middle'>"+fBestCOM+"</td><td align='middle'>"+fBestBalance+"%</td></tr></table>";

		return s;
	}

	public boolean isFinished()
	{
		return fTracker.isFinished();
	}

	/**
	 * Returns true if a (non-abort) error occurred.
	 */
	public boolean isError()
	{
		return fTracker.isError() && !fTracker.isAborted();
	}

	public String getErrorMsg()
	{
		return fTracker.getErrorMsg();
	}

	public boolean isThereNewOutput()
	{
		return fOutputChanged;
	}

	/**
	 * Now just gets the one best composition - see getAllComps().
	 */
	public String getOutput()
	{
		synchronized (this)
		{
			if (fOutputChanged)
			{
				int ncomps = fBestComps.size();
				if (ncomps>0)
					fOutput = getComp(0);
				else if (fTracker.isFinished())
					fOutput = "No compositions found";
				else
					fOutput = "Awaiting results...";
				fOutputChanged = false;
			}
		}
		return fOutput;
	}

	public String getAllComps()
	{
		int ncomps = fBestComps.size();
		String s = "";
		if (ncomps==0)
			s = "No results";
		else
			for (int i=0; i<ncomps; i++)
				s+= getComp(i)+kNEWLINE+kNEWLINE;
		return s;
	}

	private String getComp(int i)
	{
		OutputComp comp = (OutputComp)fBestComps.elementAt(i);
		return comp.toString(", gen. Elf (No. "+(i+1)+")");
	}


	/**
	 * Pass 1 is table build (methods - only if method table dirty).
	 * Pass 2 is music preparation (only if music table dirty).
	 * Pass 3 is lead-music prep (always)
	 * Pass 4 is the search!
	 */
	public void run()
	{
		if (fTracker.isAborted())
			return;
		fTracker.setProgress(0);
    fTracker.startDelegateJob(fTables, 1);
		fTables.prepareMethods();
		fTracker.endDelegateJob();

		if (fTracker.isAborted())
			return;
		fTracker.startDelegateJob(fTables, 1);
		fTables.prepareMusic();
		fTracker.endDelegateJob();

		if (fTracker.isAborted())
			return;
		fTracker.startDelegateJob(fTables, 1);
		fTables.prepareLeadMusic();
		fTracker.endDelegateJob();

		if (fTracker.isAborted())
			return;
		fTracker.startDelegateJob(fComposer, 98);
		fComposer.compose(this);
		fTracker.endDelegateJob();
	}

	/**
	 * Called by the Composer when a new composition meeting the score minimums
	 * is produced. We compare it against the current top ten to see whether it
	 * is worth keeping. If it is, we may also update the Composer's minimum
	 * score values to implement "heuristic pruning".
	 */
	public void outputComp(OutputComp latest)
	{
		synchronized (this)
		{
			int ncomps = fBestComps.size();
			for (int i=0; i<ncomps; i++)
			{
				OutputComp comp = (OutputComp)fBestComps.elementAt(i);
				if (latest.compareTo(comp)>=1)
				{
					fBestComps.insertElementAt(latest, i);
					if (ncomps>=fNCompsToKeep)
					{
						fBestComps.removeElementAt(ncomps);
						comp = (OutputComp)fBestComps.elementAt(ncomps-1);
						if (fComposer!=null)
						{
							// Update min scores to keep search pruned
							int minScore = Integer.MAX_VALUE;
							int minCOM = Integer.MAX_VALUE;
							int minBalance = Integer.MAX_VALUE;
							int maxUnbalance = 0;
							for (int j=0; j<ncomps; j++)
							{
								comp = (OutputComp)fBestComps.elementAt(j);
								if (comp.getScore()<minScore)
									minScore = comp.getScore();
								if (comp.getCOM()<minCOM)
									minCOM = comp.getCOM();
								if (comp.getBalance()<minBalance)
									minBalance = comp.getBalance();
								if (comp.getUnbalanceCount()>maxUnbalance)
									maxUnbalance = comp.getUnbalanceCount();
							}
							fComposer.setMinScore(minScore);
							fComposer.setMinCOM(minCOM);
							fComposer.setMinBalance(minBalance);
							fComposer.setRepeatLimits(maxUnbalance);
						}
					}
					fOutputChanged = true;
					return;
				}
			}
			if (ncomps<fNCompsToKeep)
			{
				fBestComps.addElement(latest);
				fOutputChanged = true;
			}
		}
		return;
	}

	public boolean isPaused()
	{
		return fComposer!=null && fComposer.isPaused();
	}

	public boolean pause()
	{
		if (fComposer!=null && fComposer.isComposing())
		{
			fComposer.pause();
			return true;
		}
		return false;
	}

	public void resume()
	{
		if (fComposer!=null && fComposer.isPaused())
			fComposer.resume();
	}

	public void stop()
	{
		System.out.println("Elf stopped");
		synchronized (this)
		{
			fTracker.abort();
		}
	}

	public void destroy()
	{
	}
}
