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

import java.util.*;

/**
 * The Composer is the heart of Elf, and contains the inner Search Loops which
 * find and score compositions. It works closely with a delegate {@link Composition}
 * instance to handle composition statistics counting and truth and music checking.
 * It is designed for asynchronous use by a hosting application or applet, and
 * so subclasses {@link Tracker} to provide abort and pause functions as well as
 * progress and status information.
 * <p>
 * Note that the base class Composer only handles the half-lead no-calls search.
 * The subclasses {@link ComposerCalls} and {@link ComposerLH} contain half-lead
 * calls and leadhead-only searches. To create the correct Composer class, it is
 * essential to use the static factory method {@link #create}.
 * <p>
 * Here's how to use a Composer to perform a search:
 * <ol>
 * <li>First a {@link Tables} instance must be created with the methods and music
 * definitions required for the search; however, the table builds do not have
 * to be performed yet.
 * <li>Next construct a Composer instance using the factory method Composer.create(),
 * passing the Tables instance together with parameters specifying the number of leads
 * and parts for the search, four boolean composing options (tenors-together,
 * nice part-ends, optimum HL balance and LH-only spliced), and an integer indicating
 * whether calls are to be used (0 = no calls, 1 = bobs only, 2 = bobs and singles).
 * <li>Appropriate setter methods can now be used to set other options - e.g.
 * setMinScore(), setStartComp() etc.
 * <li>Up to this point execution can proceed synchronously. However it is now best
 * to spawn a worker Thread to perform the slow build and search operations, and
 * use the Tracker interface to monitor progress of the Composer and if necessary
 * ask it to pause or abort.
 * <li>If necessary, the table build passes should now be performed. The tables
 * instance must be fully populated before composing can begin!
 * <li>Finally, the compose() method can be called to begin the search. Note that
 * you must supply an object implementing the {@link ComposerHost} interface - this
 * is effectively a callback which the Composer will use to output compositions found.
 * </ol>
 * <p>
 * Whilst composing is underway, the following methods can be called asynchronously
 * to follow the progress of the search:
 * <table>
 * <tr><td width="150">isComposing()</td><td>returns true if the search is underway</td></tr>
 * <tr><td>isFinished()</td><td>returns true when the search is complete</td></tr>
 * <tr><td>getProgress()</td><td>returns % complete to 3 decimal places</td></tr>
 * <tr><td>estimateTimeLeft()</td><td>returns an estimate of the time till completion</td></tr>
 * <tr><td>getNComps()</td><td>the number of true compositions found so far</td></tr>
 * <tr><td>getNNodes()</td><td>the number of leads generated so far</td></tr>
 * <tr><td>getCompsPerSec()</td><td>the number of compositions checked in the last second</td></tr>
 * <tr><td>getNodesPerSec()</td><td>the number of leads generated in the last second</td></tr>
 * <tr><td>getBestScore()</td><td>the best score found from a composition so far</td></tr>
 * <tr><td>getBestMusic()</td><td>the best music found from a composition so far</td></tr>
 * <tr><td>getBestCOM()</td><td>the maximum number of COM found so far</td></tr>
 * <tr><td>getBestBalance()</td><td>the best method balance found in a composition so far</td></tr>
 * <tr><td>setMinScore</td><td>sets minimum score a composition must achieve before
 * being accepted; can be called during composing to tighten search</td></tr>
 * <tr><td>setMinBalance</td><td>set minimum method balance allowed, 0-100;
 * call this during composing to tighten search</td></tr>
 * <tr><td>setMinCOM</td><td>set minimum COM allowed; call this during composing
 * to tighten search</td></tr>
 * <tr><td>abort()</td><td>to abort a search</td></tr>
 * <tr><td>pause()</td><td>to pause a search</td></tr>
 * <tr><td>resume()</td><td>to resume a paused search</td></tr>
 * </table>
 * <p>
 * Once the search has finished, the following methods can also be used:
 * <table>
 * <tr><td width="150">getSearchTime()</td><td>total time spent in search</td></tr>
 * <tr><td>isError()</td><td>returns true if an internal error (exception) occurred,
 * other than abort.</td></tr>
 * <tr><td>getErrorMsg()</td><td>if an error terminated the search, this returns
 * a descriptive error message.</td></tr>
 * </table>
 * <p>
 * Much of the Composer code has been heavily tuned for performance, and should not
 * be modified without extensive benchmarking on a range of searches. The inner
 * composing loop doCompose() is triplicated - see ComposerCalls and ComposerLH
 * classes for versions of doCompose() which are optimised for
 * half-lead with-calls and leadhead-only with-calls searches.
 * <p>
 * A description of the rotationally-sorted search algorithm used by Elf is contained
 * in the Elf White Paper.
 *
 * @author MBD
 */
public class Composer extends Tracker implements StandardMethods
{
	//public final static String kVERSION = "Elf early-access version (E&auml;rwen build)";
	//public final static String kVERSION = "Elf preview edition (Indis build)";
	//public final static String kVERSION = "Elf release edition (Tin&uacute;viel build)";
	public final static String kVERSION = "Elf release edition (Und&oacute;miel-D)";
	public final static String kCOPYRIGHT = "Copyright 2002-2011 Mark B. Davies";

	public static final int kNBELLS = 8;

	private final static long kDISPLAY_INTERVAL = 500;	// milliseconds
	private final static int kTOTAL_DURATION = 1000000;
	/** Number of nodes between time/abort checks */
	public final static int kCHECK_FREQ = 2000;

	Tables fTables;
	boolean fComposing;

	/** The original list of methods to splice; note these do NOT have tables/perms built for them.  */
	Method[] fMethods;
	/** The number of methods to splice, equals fMethods.length */
	int fNMethods;
	/** Composite methods - size is fNMethods squared - these DO have tables/perms built */
	Method[] fCompositeMethods;
	/** Pointers into fCompositeMethods for LH-only search - size = fNMethods */
	Method[] fLHOnlyMethods;
	/** Number of composite methods - equals fNMethods squared for HL search */
	int fNCompMethods;
	/** Number of leads per part of composition */
	int fLeadsPerPart;
	/** Desired number of parts for composition */
	int fNParts;
	/** Set to true if doing LH-only spliced */
	boolean fLHSpliced = false;
	/** Set to true if tenors must be together at every leadhead */
	boolean fTenorsTogether;
	/** Set to true if only "nice" part ends are allowed */
	boolean fNicePartEnds;
	/** =0 for no calls, 1 for bobs-only and 2 for bobs and singles */
	int fAllowCalls = 0;
	/**
	 * Minimum part length to be produced by search.
	 * @since Undomiel-B
	 */
	private int fMinPartLength;
	/**
	 * Maximum part length to be produced by search.
	 * @since Undomiel-B
	 */
	private int fMaxPartLength;


	/** Current minimum score a composition must achieve before being output */
	int fMinScore;
	/** Current minimum method balance (0-100) a composition must achieve before being output */
	int fMinBalance;
	/** Current minimum changes of method (per part) a composition must achieve before being output */
	int fMinCOM;
	/** The COM weighting - how many points a composition is awarded for each COM per part */
	int fCOMScore;
	/** The balance weighting - how many points a composition is awarded for each method balance percentage point 0-100 */
	int fBalanceScore;
	/** The maximum number of allowed repeated occurrences of a method (for HL spliced, in each half) */
	int fMethodRepeatLimit;
	/** The maximum number of methods that are allowed to reach fMaxMethodRepeat occurrences */
	int fMaxMethodsAtRepeatLimit;

	/** One Composition reference is held for the entire search */
	Composition fComp;
	/** The ComposerHost is where output compositions get sent */
	ComposerHost fHost;
	/** This is kept as a fast way to reference the rounds RowNode */
	RowNode fRounds;
	/** The current state of the composition during the search; one composite method index per lead  */
	int[] fMethodIndices;
	/** The current state of calls in each lead of the search (0=plain) */
	int[] fCalls;
	/** For HL searches, the first-half method index for each lead in the current search comp */
	int[] f1stHalfMethodIndices;
	/** For HL searches, the second-half method index for each lead in the current search comp */
	int[] f2ndHalfMethodIndices;
	/** For each method, the number of times it has appeared in the first half-leads of the composition */
	int[] f1stHalfMethodCounts;
	/** For each method, the number of times it has appeared in the second half-leads of the composition */
	int[] f2ndHalfMethodCounts;
	/** The number of methods that have occurred fMaxMethodRepeat times in the first half-leads of the composition */
	int fN1stHalfAtMaxRepeat;
	/** The number of methods that have occurred fMaxMethodRepeat times in the second half-leads of the composition */
	int fN2ndHalfAtMaxRepeat;
	/** Temporary use by method balance calculator */
	int[] fMethodCounts;
	/** Progress scale values for each possible value of the first node (not inc calls) */
	double[] fProgressRatios;
	double[] fProgressCumulatives;

	double fInitialProgress;
	/** Only set when search finished */
	long fSearchTime;
	long fInitialTime;
	long fNodesSearched;
	int fNComps;
	int fNodesPerSec;
	int fCompsPerSec;
	int fBestScore;
	int fBestMusic;
	int fBestCOM;
	int fBestBalance;
	int fRegenPtr;

	long fStartTime;
	long fLastTime;
	long fLastNodes;
	long fCompsChecked;
	long fLastComps;
	int fCounter;

	/** Used for quick check of leadhead proof during search */
	boolean[] fTruthTable;

	/**
	 * This is a factory method which must be used by client code to construct
	 * Composer instances - the individual constructors should not be used.
	 * To use, you must supply an initialised
	 * Tables instance (methods and music must be specified, but the tables do not have
	 * to be built yet) together with the following search parameters.
	 * <ul>
	 * <li>leadsPerPart - the number of leads in the part to search.
	 * <li>nParts - the number of parts.
	 * <li>tenorsTogether - set to true to prevent the tenors being split (note - for
	 * 7 parts and above, this will keep one coursing pair together in each part).
	 * <li>nicePartEnds - if set, only consider compositions with nice part-ends
	 * as defined in the Tables instance.
	 * <li>ATW - if set, ensure optimum half-lead balance, i.e. that as near as
	 * possible equals numbers of each method appear in both the first and second
	 * half-leads. Note for HL searches this is different from method balance, because
	 * it treats the first and second half-leads separately; for LH-only spliced, it
	 * does ensure perfect method balance.
	 * <li>LHonly - if set the half-lead COM is fixed and a LH-only spliced search
	 * is performed.
	 * <li>calls - 0 = no calls, 1 = bobs only, 2 = bobs and singles.
	 * </ul>
	 * <p>
	 * The constructors initialise a number of private fields, but do not
	 * perform any lengthy or error-prone operations, so this method is appropriate for
	 * synchronous use before the search begins.
	 *
	 * @since Tinuviel
	 */
  public static Composer create(Tables tables, int leadsPerPart, int nParts, boolean tenorsTogether, boolean nicePartEnds, boolean ATW, boolean LHonly, int calls)
	{
		Composer comp;
		if (LHonly)
			comp = new ComposerLH(tables, leadsPerPart, nParts, tenorsTogether, nicePartEnds, ATW, calls);
		else if (calls>0)
			comp = new ComposerCalls(tables, leadsPerPart, nParts, tenorsTogether, nicePartEnds, ATW, calls);
		else
			comp = new Composer(tables, leadsPerPart, nParts, tenorsTogether, nicePartEnds, ATW);
		return comp;
	}

	/**
	 * This constructor should not be used by client code - instead see the
	 * {@link #create} static factory method.
	 */
	Composer(Tables tables, int leadsPerPart, int nParts, boolean tenorsTogether, boolean nicePartEnds, boolean ATW)
	{
		super(100, "Composing...");
		fComposing = false;
		fLHSpliced = false;
		fAllowCalls = 0;
		fTables = tables;
		fMethods = tables.getMethods();
		fNMethods = fMethods.length;
		fLeadsPerPart = leadsPerPart;
		fNParts = nParts;
		fTenorsTogether = tenorsTogether;
		fNicePartEnds = nicePartEnds;
		// Work out whether tenors must be home at the part end.
		// For tenors-together and parts less than 7, this is true.
		// For tenors-together in 7-parts and above, it's false, which means we can
		// search for compositions with a different coursing pair unaffected in each part.
		boolean tenorsHomePE = fTenorsTogether;
		if (fNParts>6)
			tenorsHomePE = false;
		fComp = new Composition(fMethods, fTables, fLeadsPerPart, tenorsHomePE, fNicePartEnds);
		fMethodIndices = new int[fLeadsPerPart+1];
		fCalls = new int[fLeadsPerPart+1];
		f1stHalfMethodCounts = new int[fNMethods];
		f2ndHalfMethodCounts = new int[fNMethods];
		fMethodCounts = new int[fNMethods];
		fN1stHalfAtMaxRepeat = 0;
		fN2ndHalfAtMaxRepeat = 0;
		fMinBalance = 1;
		fMinScore = 0;
		fMinCOM = 0;
		fCOMScore = 2;
		fBalanceScore = 1;
		initRepeatLimits(ATW);
		fRounds = fTables.getNode(ImmutableRow.kROUNDS_ROW);
		fTruthTable = new boolean[fTables.getNLeadheadNodes()];
	}

	/**
	 * Determines whether calls will be allowed; the parameter should be
	 * set to 0 for no calls (the default), 1 for bobs, and 2 for bobs and singles.
	 * Note that 4th's place bobs and 1234 singles are assumed.
	 * This method should be called immediately after construction.
	 * If LH-only spliced has been specified, an implicit setAllowCalls(1) is made.
	 */
	public void setAllowCalls(int allowCalls)
	{
		fAllowCalls = allowCalls;
	}

	/**
	 * Determines the score weighting given to changes of method.
	 * For example, if a composition has 10 changes of method (per part),
	 * points worth 10*COMScore will be added to its overall score.
	 *
	 * @since Tinuviel
	 */
	public void setCOMScore(int COMScore)
	{
		fCOMScore = COMScore;
	}

	/**
	 * Determines the score weighting given to method balance.
	 * For example, if a composition has a method balance of 85%, points worth
	 * 85*BalanceScore will be added to its overall score.
	 *
	 * @since Tinuviel
	 */
	public void setBalanceScore(int balanceScore)
	{
		fBalanceScore = balanceScore;
	}

	/**
	 * Allows a minimum composition length to be set.
	 * Only makes sense if varying-length compositions can be produced, which requires methods of
	 * different lead lengths to be present.
	 *
	 * @param minLength
	 */
	public void setMinPartLength(int minLength)
	{
		fMinPartLength = minLength;
	}

	/**
	 * Allows a maximum composition length to be set.
	 * Only makes sense if varying-length compositions can be produced, which requires methods of
	 * different lead lengths to be present.
	 *
	 * @param maxLength
	 */
	public void setMaxPartLength(int maxLength)
	{
		fMaxPartLength = maxLength;
	}

	/**
	 * Initialises method repeat-count limits;
	 * if "atw" set, enforces perfect method balance in both first and second half-leads.
	 */
	public void initRepeatLimits(boolean atw)
	{
		if (atw)
		{
			fMethodRepeatLimit = fLeadsPerPart/fNMethods;
			fMaxMethodsAtRepeatLimit = fLeadsPerPart%fNMethods;
			if (fMaxMethodsAtRepeatLimit==0)
				fMaxMethodsAtRepeatLimit = fNMethods;
			else
				fMethodRepeatLimit++;
		}
		else
		{
			fMethodRepeatLimit = fLeadsPerPart;
			fMaxMethodsAtRepeatLimit = fNMethods;
		}
	}

	/**
	 * Given an "unbalance" count, sets the repeat limits for methods
	 * to prune compositions where methods occur too many times.
	 * This method can be called asynchronously
	 * whilst composing is in progress to tighten down the search. It is used
	 * by Elf as part of the heuristic pruning system: the repeat limits are
	 * kept set so that compositions are only considered if they have a method
	 * balance sufficient to place them in the current top ten.
	 *
	 * @since Tinuviel
	 */
	public void setRepeatLimits(int unbalance)
	{
		if (unbalance>fNMethods)
		{
			fMethodRepeatLimit = unbalance-fNMethods;
			fMaxMethodsAtRepeatLimit = fNMethods;
		}
		else
		{
			fMethodRepeatLimit = fLeadsPerPart/fNMethods;
			fMaxMethodsAtRepeatLimit = unbalance;
			if (unbalance==0)
				fMaxMethodsAtRepeatLimit = fNMethods;
			else
				fMethodRepeatLimit++;
		}
	}

	/**
	 * Returns true if composing is underway, or is paused.
	 * Suitable for asynchronous use.
	 * See also isFinished().
	 */
	public boolean isComposing()
	{
		return fComposing;
	}

	/**
	 * Returns the number of true compositions found so far.
	 * Suitable for asynchronous use.
	 */
	public int getNComps()
	{
		return fNComps;
	}

	/**
	 * Returns an instantaneous measure of the number of leads being processed
	 * per second.
	 * Suitable for asynchronous use.
	 */
	public int getNodesPerSec()
	{
		return fNodesPerSec;
	}

	/**
	 * Returns an instantaneous measure of the number of compositions being
	 * checked (for music and proof) per second.
	 * Note that only compositions with good part-ends and who meet the minimum COM
	 * and balance criteria are checked.
	 * Suitable for asynchronous use.
	 */
	public int getCompsPerSec()
	{
		return fCompsPerSec;
	}

	/**
	 * Returns the score of the best composition found so far.
	 * Suitable for asynchronous use.
	 */
	public int getBestScore()
	{
		return fBestScore;
	}

	/**
	 * Returns the music count of the most musical composition found so far.
	 * Note that this may not be the highest-scoring composition, or even in the
	 * top ten, because the total composition score also includes factors such
	 * as method balance and COM.
	 * Suitable for asynchronous use.
	 */
	public int getBestMusic()
	{
		return fBestMusic;
	}

	/**
	 * Returns the highest number of changes of method (in one part) of any composition
	 * found so far.
	 * Note that this may not be from the highest-scoring compositions,
	 * because the total composition score also includes factors such
	 * as method balance and music.
	 * Suitable for asynchronous use.
	 */
	public int getBestCOM()
	{
		return fBestCOM;
	}

	/**
	 * Returns the best method balance of any composition found so far.
	 * Method balance is a measure of how evenly the number of half-leads of each
	 * method are distributed. A balance of 0% means not every method is present -
	 * such compositions are automatically rejected. A balance of 100% would mean
	 * that every method has equal numbers of half-leads in the part. Note that
	 * for half-lead spliced this also takes into account the distribution of
	 * methods between first and second half-leads.
	 * <p>
	 * Note that the best balance number may not be achieved by the highest-scoring
	 * compositions, because the total composition score also includes factors such
	 * as music and COM.
	 * <p>
	 * Suitable for asynchronous use.
	 */
	public int getBestBalance()
	{
		return fBestBalance;
	}

	/**
	 * Total number of leads searched.
	 * Suitable for asynchronous use.
	 */
	public long getNNodes()
	{
		return fNodesSearched;
	}

	/**
	 * Returns true if the LH-only flag is set.
	 *
	 * @since Indis
	 */
	public boolean isLHSpliced()
	{
		return fLHSpliced;
	}

	/**
	 * Sets the minimum score that a composition must achieve before being
	 * output by the Composer. This method can be called asynchronously whilst
	 * composing is in progress to tighten down a search.
	 */
	public void setMinScore(int min)
	{
		fMinScore = min;
	}

	/**
	 * Sets the minimum method balance that a composition must achieve before
	 * being considered for checking. This method can be called asynchronously
	 * whilst composing is in progress to tighten down the search. It is used
	 * by Elf as part of the heuristic pruning system: the minimum balance is
	 * kept set so that compositions are only considered if they have a method
	 * balance sufficient to place them in the current top ten.
	 *
	 * @see #setRepeatLimits
	 */
	public void setMinBalance(int min)
	{
		fMinBalance = min;
	}

	/**
	 * Sets the minimum number of changes of method that a composition must achieve
	 * before being considered for checking. This method can be called asynchronously
	 * whilst composing is in progress to tighten down the search. It is used
	 * by Elf as part of the heuristic pruning system: the minimum COM is
	 * kept set so that compositions are only considered if they have a COM
	 * sufficient to place them in the current top ten.
	 */
	public void setMinCOM(int min)
	{
		fMinCOM = min;
	}

	/**
	 * Returns (as a String) an estimate of the time left till search completion,
	 * in hours and minutes. If a search has made no measurable progress, returns
	 * "forever". If the search is paused, returns "&gt;paused&lt;".
	 */
	public String estimateTimeLeft()
	{
		if (isPaused())
			return ">paused<";
		if (fInitialProgress<0.0)
			return "";
		double proportionDone = (getProgress()-fInitialProgress)/(100.0-fInitialProgress);
		if (proportionDone==0.0)
			return "forever";
		long time = (System.currentTimeMillis()-fInitialTime)/1000;
		long totalTime = (long)(time/proportionDone);
		long minsLeft = (totalTime-time+30)/60;
		int hours = (int)(minsLeft/60);
		minsLeft = minsLeft%60;
		String s = ""+minsLeft;
		if (s.length()==1)
			s = "0"+s;
		return hours+"h"+s;
	}

	/**
	 * Only valid if isFinished() - returns the total search time, in hours,
	 * minutes and seconds, as a String.
	 */
	public String getSearchTime()
	{
		long secs = fSearchTime;
		long mins = secs/60;
		secs-= mins*60;
		long hours = mins/60;
		mins-= hours*60;
		return hours+":"+(mins<10? "0":"")+mins+":"+(secs<10? "0":"")+secs;
	}

	/**
	 * The composing engine can be seeded with a "start" composition. This could
	 * be used to implement checkpointed searches. The composition is passed as
	 * a String, and should be in a form such as:
	 * <pre>  CY NB RS ...</pre>
	 * or for LH-only spliced:
	 * <pre>  C Y B- C ...</pre>
	 * The string does not have to be as many leads as the part; missing leads
	 * are padded. Remember however that the start comp should be rotationally
	 * sorted, i.e. if X represents the entire start comp, it must not contain any
	 * infix Y such that X>Y.
	 */
	public void setStartComp(String comp)
	{
		int i = 0;
		StringTokenizer tok = new StringTokenizer(comp, " ");
		while (tok.hasMoreTokens())
		{
			int j = 0;
			String lead = tok.nextToken();
			int m1 = findMethod(""+lead.charAt(j));
			j++;
			if (!fLHSpliced && lead.length()>1)
			{
				int m2 = findMethod(""+lead.charAt(j));
				j++;
				fMethodIndices[i] = m1*fNMethods+m2;
			}
			else
				fMethodIndices[i] = m1;
			if (lead.length()>j)
			{
				char c = comp.charAt(j);
				if (c=='-' || c=='s')
				{
					if (c=='-')
						fCalls[i] = 1;
					else
						fCalls[i] = 2;
				}
			}
			i++;
		}
	}

	/**
	 * Used privately by setStartComp() - looks up a Method reference from
	 * its abbreviation String.
	 */
	private int findMethod(String abbrev)
	{
		for (int i=0; i<fNMethods; i++)
			if (fMethods[i].getAbbrev().equals(abbrev))
				return i;
		return 0;
	}

	/**
	 * Starts the search. Note that the inner search loop is contained within
	 * the doCompose() method; there are three different implementations of this
	 * method, depending on whether the Composer, ComposerCalls or ComposerLH
	 * classes are in use.
	 *
	 * Tables MUST be fully-populated with current methods!
	 */
	public void compose(ComposerHost host)
	{
		fComposing = true;
		setTotalDuration(kTOTAL_DURATION);
		setProgress(0);
		setJobName("Composing");
		java.util.Date date = new java.util.Date();
		System.out.println("Composing starts at "+date.toString());

		fHost = host;
		fTables.prepareRegenPtrs(fTenorsTogether);
		fCompositeMethods = fTables.getCompositeMethods();
		if (fLHSpliced)
		{
			fNCompMethods = fMethods.length;
		}
		else
		{
			fNCompMethods = fCompositeMethods.length;
			// These are tables which turn a composite index into the single-method
			// first & second half indices.
			f1stHalfMethodIndices = new int[fNCompMethods];
			f2ndHalfMethodIndices = new int[fNCompMethods];
			for (int i=0; i<fNCompMethods; i++)
			{
				f1stHalfMethodIndices[i] = i/fNMethods;
				f2ndHalfMethodIndices[i] = i%fNMethods;
			}
		}

		calcProgressRatios();

		for (int i=fTables.getNLeadheadNodes()-1; i>=0; i--)
			fTruthTable[i] = false;
		if (fNParts>1)
			fTruthTable[fRounds.getLeadheadNumber()] = true;

		fNComps = 0;
		fBestScore = 0;
		fBestBalance = 0;
		fBestCOM = 0;
		fBestMusic = 0;
		fNodesPerSec = 0;
		fCompsPerSec = 0;
		fNodesSearched = 0;
		fInitialProgress = -1.0;
		fLastTime = System.currentTimeMillis();
		fStartTime = fLastTime;
		fLastNodes = 0;
		fCompsChecked = 0;
		fLastComps = 0;
		// Counts nodes, up to kCHECK_FREQ
		fCounter = 0;
		// -ve to ensure start comp is used intially
		fRegenPtr = -fLeadsPerPart;

		/** Virtual method - see also ComposerCalls and ComposerLH methods */
		doCompose();

		fNodesSearched+= fCounter;
		fSearchTime = (System.currentTimeMillis()-fStartTime)/1000;
		if (!isAborted())
		{
			// All finished - print final stats
			System.out.println("Found: "+fNComps+" bal="+fBestBalance+" com="+fBestCOM+" score="+fBestScore);
			System.out.println("Search took "+getSearchTime()+" and covered "+fNodesSearched+" nodes");
		}
		fComposing = false;
	}

	/**
	 * This one doesn't do calls!
	 */
	void doCompose()
	{
		RowNode start = fRounds;
		int minCOMconstant = fMinCOM+1-2*fLeadsPerPart;
		int i = 0;

		while (true)
		{
			if (i>=fLeadsPerPart)
			{
				// Reached end of part - check if we have a good composition.
				int regenMod = fRegenPtr-1;
				int j = fLeadsPerPart - 1;
				// The number of parts generated by the prospective part end must be correct,
				// and also we reject touches from the rotational sort with "non-lowest" postfixes,
				// i.e. where the last backtrack was not an integral division of the part.
				if (isLengthGood() && (regenMod<=0 || regenMod*2>=fLeadsPerPart) && fComp.getNParts()==fNParts)
					j = checkComp();

				// Backtrack from the part end to continue the search
				do
				{
					i--;
					int leadNum = fComp.getLead(i).getLastRow().getLeadheadNumber();
					fTruthTable[leadNum] = false;
					int index = fMethodIndices[i];
					reduceMethodCounts(f1stHalfMethodIndices[index], f2ndHalfMethodIndices[index]);
				} while (i>j);
			}
			else
			{
				// Not reached part end - generate new lead

				// Increment nodes-visited counter. Also check if it's time to drop out to update stats on screen.
				if (++fCounter>=kCHECK_FREQ)
					if (checkStats())
						break;

				int index = fMethodIndices[i];
				// "Index" is the composite index. Use pre-built index tables to find the individual method
				// indices for the first and second halfleads.
				int one = f1stHalfMethodIndices[index];
				int two = f2ndHalfMethodIndices[index];
				// Now check the method counts to make sure we are allowed these two new methods.
				// If not, we'll immediately move on to the next composite method choice.
				if (f1stHalfMethodCounts[one]>=fMethodRepeatLimit)
				{
					// Already have too many of the first halflead method - move on to next first halflead choice by
					// forcing a backtrack from final second halflead choice. (See backtrack code at bottom of loop).
					fMethodIndices[i]+= fNMethods-two-1;
				}
				else if (f2ndHalfMethodCounts[two]<fMethodRepeatLimit)
				{
					// Both first and second half methods are within allowed maximums.
					// Next check to see whether they are actually at maximum, and if so, whether we now
					// have too many methods present to maximum number.
					if (++f1stHalfMethodCounts[one] >= fMethodRepeatLimit)
						fN1stHalfAtMaxRepeat++;
					if (fN1stHalfAtMaxRepeat>fMaxMethodsAtRepeatLimit)
					{
						f1stHalfMethodCounts[one]--;
						fN1stHalfAtMaxRepeat--;
						fMethodIndices[i]+= fNMethods-two-1;
					}
					else
					{
						// First half method is OK - check second half to see whether it is at maximum, and if so
						// whether we now have too many second-half methods at maximum count.
						if (++f2ndHalfMethodCounts[two] >= fMethodRepeatLimit)
							fN2ndHalfAtMaxRepeat++;
						if (fN2ndHalfAtMaxRepeat>fMaxMethodsAtRepeatLimit)
						{
							f2ndHalfMethodCounts[two]--;
							fN2ndHalfAtMaxRepeat--;
						}
						else
						{
							// We are allowed these two new methods - add the lead to the composition.
							// Note this updates the Composition COM count.
							RowNode next = fComp.setLead(i, start, fCompositeMethods[index], 0);
							int leadNum = next.getLeadheadNumber();
							// Check leadhead truth and prune for tenors together
							if (!fTruthTable[leadNum] && (!fTenorsTogether || next.isTenorsTogether()))
							{
								// Prune branches where min COM not achievable.
								if (fComp.getCOM(i) >= 2*i + minCOMconstant)
								{
									start = next;
									fTruthTable[leadNum] = true;
									i++;
									// The new lead has been successfully added.
									// Move on to the next - use the regen pointer to copy the next methods to use from
									// the start of the composition.
									if (fRegenPtr<0)
									{
										// If RegenPtr is less than 0, we are copying "plain leads" (i.e. first method choice nodes)
										// until the course end is reached. Only when RegenPtr reaches 0 do we start copying nodes
										// from the start of the composition.
										if (fRegenPtr<-100)
										{
											// RegenPtr less than -100 is a special flag set after a backtrack to indicate we must copy
											// some "plain leads" until a course end comes up. Here we calculate exactly how many plain nodes
											// are needed, and set the RegenPtr to be minus that number.
											fRegenPtr = start.getRegenOffset();
											if (fRegenPtr>=0)
												fMethodIndices[i] = fMethodIndices[fRegenPtr];
										}
									}
									else
									{
										fMethodIndices[i] = fMethodIndices[fRegenPtr];
									}
									fRegenPtr++;
									continue;
								}
								else
								{
									reduceMethodCounts(one, two);
									// If 1st & 2nd methods different, min COM must have been
									// violated by 1st method (compared to previous lead)
									if (one!=two)
										fMethodIndices[i]+= fNMethods-two-1;
								}
							}
							else
								reduceMethodCounts(one, two);
						}
					}
				}
			}
			i = backtrack(i);
			if (i>0)
				start = fComp.getLead(i-1).getLastRow();
			else if (i==0)
				start = fRounds;
			else
				break;
			fRegenPtr = -1000;
		}
	}

	/**
	 * This method doesn't do calls!
	 */
	int backtrack(int i)
	{
		fMethodIndices[i]++;
		if (fMethodIndices[i]>=fNCompMethods)
		{
			fMethodIndices[i] = 0;
			if (i==0)
				return -1;
			i--;
			int leadNum = fComp.getLead(i).getLastRow().getLeadheadNumber();
			fTruthTable[leadNum] = false;
			int index = fMethodIndices[i];
			// Must reduce method counts every time we backtrack.
			reduceMethodCounts(f1stHalfMethodIndices[index], f2ndHalfMethodIndices[index]);
			// Recursively backtrack up the tree.
			i = backtrack(i);
		}
		return i;
	}

	/**
	 */
	void reduceMethodCounts(int one, int two)
	{
		if (f1stHalfMethodCounts[one]-- >= fMethodRepeatLimit)
			fN1stHalfAtMaxRepeat--;
		if (f2ndHalfMethodCounts[two]-- >= fMethodRepeatLimit)
			fN2ndHalfAtMaxRepeat--;
	}

	/**
	 * Check length of part is between min and max constraints - must only call this if comp has reached part end.
	 *
	 * @return
	 */
	boolean isLengthGood()
	{
    int partLen = fComp.getPartLength(fLeadsPerPart-1);
		return partLen>=fMinPartLength && partLen<=fMaxPartLength;
	}

	/**
	 * Calculates a percentage score (as an integer 0-100) representing the
	 * method balance of the current composition. For half-lead compositions,
	 * the balance figure is a combination (in a ratio 2:1) of the overall method
	 * distribution plus the distribution of methods between the first and second half-leads.
	 * A balance of 0% is always awarded if there are any methods missing.
	 * A 100% balance means each method occurs the same number of times in
	 * both the first and second half-leads.
	 * <p>
	 * This method must call setBalance() on the Composition instance
	 * to ensure the composition knows the current method balance values.
	 */
	int calcMethodBalance()
	{
		// First of all calculate overall method balance, ignoring half-lead distribution
		// (Overall method distribution is the sum of the half-lead distributions)
		for (int i=fNMethods-1; i>=0; i--)
			fMethodCounts[i] = f1stHalfMethodCounts[i]+f2ndHalfMethodCounts[i];
		int balance = calcMethodDistribution(fMethodCounts, (double)fLeadsPerPart*2);

		if (balance>0)
		{
			// Now find out the half-lead distributions.
			// This is done in a different way to the overall distribution calculated above,
			// because we want to use this value to feed back into the balance pruner,
			// which works on method counts (fMaxMethodRepeat and fMaxMethodsAtMaxRepeat).
			int minRep = 1 + fLeadsPerPart/fNMethods;
			int unbalance = calcUnbalance(f1stHalfMethodCounts, minRep);
			int unbalance2 = calcUnbalance(f2ndHalfMethodCounts, minRep);
			// Use the worst half, i.e. the half with the highest unbalance count
			if (unbalance2>unbalance)
				unbalance = unbalance2;
			// Now turn the worst half's unbalance count into a balance percentage -
			// this provides one third of the overall score
			int minAtMinRep = fLeadsPerPart%fNMethods;
			int balMax = fLeadsPerPart-minRep + fNMethods-minAtMinRep;
			int halfLeadbalance;
			halfLeadbalance = fLeadsPerPart+fNMethods-unbalance;
			if (unbalance<=fNMethods)
				halfLeadbalance-= minRep;
			balance = balance*67/100 + halfLeadbalance*33/balMax;
			fComp.setBalance(balance, unbalance);
		}
		return balance;
	}

	/**
	 * Provides an overall measure of method distribution, by finding the
	 * mean deviation of method counts from the optimum.
	 */
	int calcMethodDistribution(int[] methodCounts, double maxMethods)
	{
		double perfect = maxMethods/fMethods.length;
		double balance = 1.0;
		for (int i=fNMethods-1; i>=0; i--)
		{
			int count = methodCounts[i];
			if (count==0)
			{
				balance = 0.0;
				break;
			}
			double score = perfect-count;
			if (score<0)
				score = -score;
			score = 1.0 - score/maxMethods;
			balance*= score;
		}
		int b = (int)(balance*100.0);
		return b;
	}

	/**
	 * Returns an "unbalance" value based on the method counts in the counts[] array.
	 * The higher the count value, the WORSE the balance.
	 * The count is based on the number of occurrences of the most-repeated method,
	 * as follows:
	 * <ol>
	 * <li>If no method occurs more than the minimum possible number of times
	 * (minRep), the count value is equal to the <i>number</i> of methods which occur
	 * minRep times.
	 * <li>If one or more methods occur more than minRep times, the count value
	 * is equal to fNMethods plus the repeat count of the most common method(s).
	 * Because fNMethods is added to this value, it will always be more than
	 * the value from (1) above.
	 * </ol>
	 */
	int calcUnbalance(int[] counts, int minRep)
	{
		int max = 0;
		int nAtMax = 0;
		for (int i=counts.length-1; i>=0; i--)
		{
			int count = counts[i];
			if (count>0)
				if (count==max)
					nAtMax++;
				else if (count>max)
				{
					max = count;
					nAtMax = 1;
				}
		}
		int unbalance;
		// Catch boundary case of even method distribution
		if (max<minRep)
			unbalance = 0;
		else if (max==minRep)
			unbalance = nAtMax;
		else
			unbalance = fNMethods+max;
		return unbalance;
	}

	/**
	 * Checks composition for music and truth; if it also meets the current
	 * minimum scores, it gets outputted. The return value is the lead number
	 * to backtrack to - normally this is nLeadsInPart-1, but if the composition
	 * was found to be false in the first part, we can force the engine
	 * to backtrack further.
	 * <p>
	 * Final for speed.
	 */
	final int checkComp()
	{
		// CalcMethodBalance() has the side-effect of updating fComp.fBalance.
		int balance = calcMethodBalance();
		int com = fComp.getCOM();
		if (balance>=fMinBalance && com>=fMinCOM && fComp.checkRots())
		{
			fCompsChecked++;
			int score = com*fCOMScore + balance*fBalanceScore;
			int minMusic = fMinScore-score;
			int music = fComp.calcMusicRots(minMusic);
			if (music>0)
			{
				score+= music;
				if (score>fBestScore)
					fBestScore = score;
				if (music>fBestMusic)
					fBestMusic = music;
				if (balance>fBestBalance)
					fBestBalance = balance;
				if (com>fBestCOM)
					fBestCOM = com;
				fNComps++;
				fHost.outputComp(fComp.getOutputComp(score, fLHSpliced));
			}
			else if (music<0)
			{
				// This tells us composition is false in the first part - backtrack.
				return fComp.get1stPartFalseLead();
			}
		}
		return fLeadsPerPart-1;
	}

	/**
	 * Should be called every few centiseconds by the main composing loop.
	 * Updates stats, checks for pause and abort.
	 * Final for speed
	 */
	final boolean checkStats()
	{
		fNodesSearched+= fCounter;
		fCounter = 0;
		if (isPaused())
		{
			fLastTime = System.currentTimeMillis();
			waitForResume();
			fInitialTime+= System.currentTimeMillis()-fLastTime;
		}
		if (isAborted())
		{
			return true;
		}
		else
		{
			// Update stats display
			long dur = System.currentTimeMillis()-fLastTime;
			if (dur>kDISPLAY_INTERVAL)
			{
				setProgress((int)(getComposingProgress()*kTOTAL_DURATION));
				fNodesPerSec = (int)((fNodesSearched-fLastNodes)*1000/dur);
				fLastNodes = fNodesSearched;
				// This is comps checked, not true comps
				fCompsPerSec = (int)((fCompsChecked-fLastComps)*1000/dur);
				fLastComps = fCompsChecked;
				fLastTime = System.currentTimeMillis();
				if (fInitialProgress<0.0)
				{
					fInitialProgress = getProgress();
					fInitialTime = fLastTime;
				}
			}
		}
		return false;
	}

	private double getComposingProgress()
	{
		double progress = 0.0;
		double scale = 1.0;

		// First node is scaled asymmetrically for rotational sort.
		int j = fMethodIndices[0];
		scale = fProgressRatios[j];
		progress+= fProgressCumulatives[j];
		scale/= fAllowCalls+1;
		progress+= fCalls[0]*scale;

		for (j=1; j<fLeadsPerPart; j++)
		{
			scale/= fNCompMethods;
			progress+= fMethodIndices[j]*scale;
			scale/= fAllowCalls+1;
			progress+= fCalls[j]*scale;
			if (scale*kTOTAL_DURATION<=1.0)
				break;
		}
		return progress;
	}

	/**
	 * For the rotational sort, progress through the search space speeds
	 * up exponentially as the possibilities for the initial node are exhausted.
	 * This method calculates a table of ratios for the initial node in order
	 * to produce a constant progress value.
	 */
	private void calcProgressRatios()
	{
		fProgressRatios = new double[fNCompMethods];
		fProgressCumulatives = new double[fNCompMethods];
		// 1-spliced is special case - revert to ordinary calculation
		if (fNCompMethods==1)
		{
			fProgressRatios[0] = 1.0;
			fProgressCumulatives[0] = 0.0;
			return;
		}
		double n = (double)(fLeadsPerPart-1);
		int i;
		// First calculate series of ratios n^(i+1)/n^i.
		for (i=0; i<fNCompMethods-1; i++)
		{
			double x = fNCompMethods-i-1;
			double scale = 1.0 + (n/x + n*(n-1)/(2*x*x));
			scale = 1.0 - 1.0/scale;
			fProgressRatios[i] = scale;
		}
		fProgressRatios[i] = fProgressRatios[i-1];

		// Collapse subsequent ratios
		int boundary = fNMethods;
		if (fTenorsTogether)
			boundary = fNCompMethods;
		else if (fLHSpliced)
			boundary = 1;
		double scale = 1.0;
		for (i=0; i<fNCompMethods; i+=boundary)
		{
			int j;
			// reverse ratios within boundary
			for (j=0; j<boundary/2; j++)
			{
				double x = fProgressRatios[i+j];
				fProgressRatios[i+j] = fProgressRatios[i+boundary-j-1];
				fProgressRatios[i+boundary-j-1] = x;
			}
			// scale boundary
			for (j=0; j<boundary; j++)
				fProgressRatios[i+j]*= scale;
			double cum = fProgressRatios[i+j-1];
			// Calculate scale-down for next boundary
			scale*= cum;
		}

		// Find total sum of all ratios
		double total = 0.0;
		for (i=0; i<fNCompMethods; i++)
			total+= fProgressRatios[i];

		// Finally normalise entire range to 0..1.0, and calculate cumulative totals.
		double cum = total;
		for (i=fNCompMethods-1; i>=0; i--)
		{
			cum-= fProgressRatios[i];
			fProgressCumulatives[i] = cum/total;
			fProgressRatios[i]/= total;
		}
		fProgressCumulatives[0] = 0.0;
	}

}
