package org.pealfactory.compose.halfleadspliced;

import org.pealfactory.ring.*;

/**
 * Represents a method: holds name, abbreviation, place notation and leadhead, as well
 * as internal data used to increase performance, such as table permutation numbers for
 * leadhead and place notation permutations. Methods are provided to calculate
 * these perm numbers, and to use them to generate and count music in a lead of
 * the method from a giving starting row.
 * <p>
 * Since Indis, an alternate constructor is available which supports "composite"
 * methods. These contain the first-half place notation for one method (e.g. Cambridge)
 * and the second half of another (e.g. Yorkshire).
 * Such methods are automatically assigned a two-letter abbreviation (e.g. "CY").
 * It is no longer possible to generate or count music for individual half-leads.
 * <p>
 * Much of the code within this class has been heavily tuned for performance, and
 * should not be modified without extensive benchmarking on a range of searches.
 *
 * @author MBD
 */
class Method
{
	public final static int kNCALLTYPES = 3;
	public final static byte[] kBOB_PN = new PN("14").getChange(0);
	public final static byte[] kSINGLE_PN = new PN("1234").getChange(0);

	private int fMethodIndex;
	private int fIndex1;
	private int fIndex2;
	private String fName;
	private String fAbbrev;
	private PN fPN;
	private PN fPN2;
	private int[] fPNPermNums;
	private ImmutableRow fLeadhead;
	private ImmutableRow fHalflead;
	private ImmutableRow fCallPerms[];
	private int fLeadLength;
	private int f1stHalfLength;
	private int f2ndHalfLength;
	private int[] fLeadPermNums;
	/** =1 if two halves of composite are different methods */
	private int fCOM;

	public Method(String name, String pn)
	{
		this(name, name.substring(0, 1), pn);
	}

	/**
	 * Note calcPerms() and updateLeadPerms() MUST be called prior to use.
	 * The latter can only be called after all methods have been added.
	 */
	public Method(String name, String abbrev, String pn)
	{
		PN p = new PN(pn);
		fCOM = 0;
		init(name, abbrev, p, p);
	}

	/**
	 * Create composite method with first half pn from m1 and second half from m2.
	 * Note that the methods indices of m1 and m2 must already have been set up.
	 *
	 * @since Indis
	 */
	public Method(Method m1, Method m2, int index)
	{
		fMethodIndex = index;
		fIndex1 = m1.getMethodIndex();
		fIndex2 = m2.getMethodIndex();
		if (fIndex1==fIndex2)
			fCOM = 0;
		else
			fCOM = 1;
		String name = m1.getName()+"/"+m2.getName();
		String abbrev = m1.getAbbrev()+m2.getAbbrev();
		init(name, abbrev, m1.getPN(), m2.getPN());
	}

	private void init(String name, String abbrev, PN pn1, PN pn2)
	{
		fName = name;
		fAbbrev = abbrev;
		fPN = pn1;
		fPN2 = pn2;
		/** @todo Should take account of asymmetric treble paths? */
		f1stHalfLength = fPN.getLength()/2;
		f2ndHalfLength = fPN2.getLength()/2;
		fLeadLength = f1stHalfLength+f2ndHalfLength;

		// Calculate halflead, leadhead and the permutations from halflead to
		// plained, bobbed and singled leadheads.
		ImmutableRow r = ImmutableRow.kROUNDS_ROW;
		byte[] pn;
		int i;
		for (i=0; i<f1stHalfLength; i++)
		{
			pn = fPN.getChange(i);
			r = r.change(pn);
		}
		fHalflead = r;
		for (i=0; i<f2ndHalfLength-1; i++)
		{
			pn = fPN2.getChange(i+f2ndHalfLength);
			r = r.change(pn);
		}
		pn = fPN2.getChange(i+f2ndHalfLength);
		fLeadhead = r.change(pn);
		fCallPerms = new ImmutableRow[kNCALLTYPES];
		fCallPerms[0] = r.change(pn);
		fCallPerms[1] = r.change(kBOB_PN);
		fCallPerms[2] = r.change(kSINGLE_PN);
	}

	/**
	 * Two methods are equal if their names are equal
	 */
	public boolean equals(Object o)
	{
		if (o instanceof Method)
		{
			Method m = (Method)o;
			return fName.equals(m.getName());
		}
		return false;
	}

	public String getName()
	{
		return fName;
	}

	public String getAbbrev()
	{
		return fAbbrev;
	}

	public PN getPN()
	{
		return fPN;
	}

	/**
	 * Final for speed
	 */
	public final int getMethodIndex()
	{
		return fMethodIndex;
	}

	public void setMethodIndex(int methodIndex)
	{
		fMethodIndex = methodIndex;
		fIndex1 = fMethodIndex;
		fIndex2 = fMethodIndex;
	}

	public final int getIndex1()
	{
		return fIndex1;
	}

	public final int getIndex2()
	{
		return fIndex2;
	}

	/**
	 * Final for speed
	 */
	public final int getLeadLength()
	{
		return fLeadLength;
	}

	/**
	 */
	public int nRowsInFirstHalf()
	{
		return f1stHalfLength;
	}

	/**
	 */
	public int nRowsInSecondHalf()
	{
		return f2ndHalfLength;
	}

	/**
	 * Returns 0 if this method has the same 1st & 2nd halves, or 1
	 * if it is a composite made out of two different halfleads.
	 */
	public int getCOM()
	{
		return fCOM;
	}

	/**
	 * Final for speed (inlining)
	 */
	public final int getLeadPermNum(int call)
	{
		return fLeadPermNums[call];
	}

	/**
	 * Final for speed.
	 */
	public final ImmutableRow getLeadhead()
	{
		return fLeadhead;
	}

	/**
	 * Final for speed.
	 */
	public final ImmutableRow getHalflead()
	{
		return fHalflead;
	}

	/**
	 * Calculates music score for one lead of method, from leadhead up to but NOT
	 * INCLUDING following leadhead.
	 * Optimised - uses RowNodes
	 */
	public final int leadMusic(RowNode start)
	{
		int score = 0;
		RowNode r = start;
		int n = fLeadLength-1;
		score+= r.getMusic();
		for (int i=0; i<n; i++)
		{
			r = r.permute(fPNPermNums[i]);
			score+= r.getMusic();
		}
		return score;
	}

	/**
	 * Generates node numbers for entire lead of method, from leadhead up to leadend.
	 * Optimised - uses RowNodes
	 */
	public final void generateLead(RowNode start, int[] rowNums)
	{
		RowNode r = start;
		int n = fLeadLength-1;
		rowNums[0] = r.getNodeNumber();
		for (int i=0; i<n; )
		{
			r = r.permute(fPNPermNums[i]);
			i++;
			rowNums[i] = r.getNodeNumber();
		}
	}

	/**
	 * Calculates place notation, and leadhead permutations for this method.
	 * !! VERY IMPORTANT - leadhead perm numbers are at this stage temporary;
	 * they must be incremented by the total number of PN perms before use!!
	 * @see #updateLeadPerms
	 */
	public void calcPerms(Tables tables)
	{
		fPNPermNums = new int[fLeadLength];
		ImmutableRow rounds = new ImmutableRow(tables.getNBells());
		for (int i=0; i<f1stHalfLength; i++)
			fPNPermNums[i] = tables.addPNPerm(rounds.change(fPN.getChange(i)).toBytes());
		for (int i=0; i<f2ndHalfLength; i++)
			fPNPermNums[i+f1stHalfLength] = tables.addPNPerm(rounds.change(fPN2.getChange(i+f2ndHalfLength)).toBytes());

		fLeadPermNums = new int[kNCALLTYPES];
		for (int i=0; i<kNCALLTYPES; i++)
		{
			// !! Temporary numbers - must later add on total number of PN perms
			fLeadPermNums[i] = tables.addLeadheadPerm(fCallPerms[i].toBytes());
		}
	}

	/**
	 * Before use the leadhead perm numbers must be incremented so they
	 * correctly point into the node permtables. To do this, we increment by
	 * the total number of PN perms for all methods
	 */
	public void updateLeadPerms(Tables tables)
	{
		int inc = tables.getNPnPerms();
		for (int i=0; i<kNCALLTYPES; i++)
			fLeadPermNums[i]+= inc;
	}
}
