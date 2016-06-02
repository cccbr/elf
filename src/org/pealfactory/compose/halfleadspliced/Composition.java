package org.pealfactory.compose.halfleadspliced;

import org.pealfactory.ring.*;

/**
 * Tightly bound to the Composer class, a Composition instance holds the leads
 * of a spliced composition as it is generated. It is also home to analysis
 * methods such as checkRots() and isTrue().
 * <p>
 * Note that this class has been heavily tuned for performance, and should not
 * be modified without extensive benchmarking on a range of searches.
 *
 * @author MBD
 */
class Composition
{
	private int fNParts;
	private int fNLeadsPerPart;
	private Method[] fMethods;
	/** Set if tenors must also be home at part end */
	private boolean fTenorsHomePE;
	private boolean fNicePartEnds;
	/** Set if neither fTenorsHomePE nor fNicePartEnds are set */
	private boolean fAllRotsGood;

	private int[] fCOMs;
	private Lead[] fLeads;
	private RowNode fPartEnd;
	private int fFirstRot;
	private Tables fTables;
	private boolean[] fTruthTable;
	private boolean[] fZeroTable;
	private int[] fRowNumBuf;
	/** @since Undomiel-B */
	private int[] fLength;

	private RowNode fRounds;
	private int fBestRot;
	private int f1stPartFalseLead;
	/** Cache last calculated values */
	private int fMusic;
	/** Method balance 0-100% */
	private int fBalance;
	/** Count for balance pruning */
	private int fUnbalance;

	public Composition(Method[] methods, Tables tables, int nleads, boolean tenorsHomePE, boolean nicePE)
	{
		fMethods = methods;
		fTables = tables;
		fNLeadsPerPart = nleads;
		fTenorsHomePE = tenorsHomePE;
		fNicePartEnds = nicePE;
		fAllRotsGood = !fTenorsHomePE && !fNicePartEnds;
		fNParts = 0;
		fCOMs = new int[nleads];
		fLength = new int[nleads];
		fLeads = new Lead[nleads];
		for (int i=0; i<nleads; i++)
			fLeads[i] = new Lead();
		fTruthTable = new boolean[fTables.getNNodes()];
		fZeroTable = new boolean[fTables.getNNodes()];
		fRounds = fTables.getNode(ImmutableRow.kROUNDS_ROW);

		int maxMethodLength=0;
		for (int i=0; i<methods.length; i++)
		{
			int l = methods[i].nRowsInFirstHalf()+methods[i].nRowsInSecondHalf();
			if (l>maxMethodLength)
				maxMethodLength = l;
		}
		fRowNumBuf = new int[maxMethodLength];
		int n = fTables.getNBells();
	}

	/**
	 * Produces a newly-constructed OutputComp instance populated with the
	 * leadheads from the best rotation of the current composition.
	 */
	public OutputComp getOutputComp(int score, boolean lhOnly)
	{
		int length = 0;
		for (int i=0; i<fNLeadsPerPart; i++)
			length+= fLeads[i].getMethod().getLeadLength();
		length*= fNParts;
		String title = length+" "+fMethods.length+"-spliced";
		return new OutputComp(title, this, score, fRounds, lhOnly);
	}

	/**
	 * Final for speed (inlining)
	 */
	public final int getNLeadsPerPart()
	{
		return fNLeadsPerPart;
	}

	/**
	 * Final for speed (inlining)
	 */
	public final Lead getLead(int n)
	{
		return fLeads[n];
	}

	/**
	 * Final for speed (inlining)
	 */
	public final RowNode setLead(int n, RowNode start, Method composite, int call)
	{
		Lead lead = fLeads[n];
		lead.setLead(start, composite, call);
		// Keep track of length of compositions and number of changes of method so far, for pruning purposes
		if (n==0)
		{
			fCOMs[n] = composite.getCOM();
			fLength[n] = composite.getLeadLength();
		}
		else
		{
			fCOMs[n] = fCOMs[n-1] + composite.getCOM();
			if (composite.getIndex1()!=fLeads[n-1].getMethod().getIndex2())
				fCOMs[n]++;
			fLength[n] = fLength[n-1] + composite.getLeadLength();
		}
		return lead.getLastRow();
	}

	/**
	 * Get changes of method in composition up to and including this lead
	 * (does not include end-start wraparound).
	 * Final for speed.
	 */
	public final int getCOM(int lead)
	{
		return fCOMs[lead];
	}

	/**
	 * Get length of part up to and including this lead
	 *
	 * @since Undomiel-B
	 */
	public final int getPartLength(int lead)
	{
		return fLength[lead];
	}

	/**
	 * Changes of method per part
	 */
	public int getCOM()
	{
		int n = fNLeadsPerPart;
		// Can use fCOMs table, but have to take account of extra COM
		// if first method of part different to last.
		int com = fCOMs[n-1];
		if (fLeads[0].getMethod().getIndex1()!=fLeads[n-1].getMethod().getIndex2())
			com++;
		return com;
	}

	/**
	 * Calculates number of parts from part end. Has the side effect of
	 * setting fPartEnd for subsequent methods such as checkRots().
	 * Final for speed.
	 */
	public final int getNParts()
	{
		fPartEnd = fLeads[fNLeadsPerPart-1].getLastRow();
		fNParts = fPartEnd.getNParts();
		return fNParts;
	}

	public int getBestRot()
	{
		return fBestRot;
	}

	/**
	 * If the composition was false in the first part, returns the lead it
	 * went false at, otherwise -1. This is used to backtrack the composition
	 * to prune false parts.
	 * Final for speed.
	 */
	public final int get1stPartFalseLead()
	{
		return f1stPartFalseLead;
	}

	/**
	 * Returns true if there is at least one rotation of the composition with
	 * an acceptable part end; in this case, fFirstRot is also set to the
	 * rotation number of the first acceptable rotation.
	 * Final for speed, but on the borderline of bytecode size for inlining.
	 */
	public final boolean checkRots()
	{
		// If we're not looking for nice part-ends, and the tenors don't have to
		// be home at the part-end, then fAllRotsGood will be set and we can
		// return true immediately.
		if (fAllRotsGood)
		{
			fFirstRot = 0;
			return true;
		}
		// Tinuviel-C bugfix 3.11.2009
		// Check nice part ends for rotation 0
		if ((!fTenorsHomePE || fPartEnd.isTenorsHome()) && (!fNicePartEnds || fPartEnd.isNicePartEnd()))
		{
			fFirstRot = 0;
			return true;
		}
		for (fFirstRot=1; fFirstRot<fNLeadsPerPart; fFirstRot++)
			if (isGoodRotPartEnd(fFirstRot))
				return true;
		return false;
	}

	/**
	 * Returns true if the composition rotation starting from lead rot
	 * has an acceptable part end; rot MUST BE GREATER THAN 0!
	 * This method also assumes that one of fTenorsHomePE or fNicePartEnds is set,
	 * i.e. that fAllRotsGood is false.
	 * Final for speed.
	 */
	private final boolean isGoodRotPartEnd(int rot)
	{
		// Tinuviel-C bugfix 3.11.2009
		// A fast check for a tenors-home part end is to check that the bells in 7,8 in the starting row for the rotation
		// are in their home positions at the (unrotated) part end.
		if (fTenorsHomePE)
		{
			RowNode rotStart = fLeads[rot-1].getLastRow();
			int bellInSeventhsPlace = rotStart.bellAt(Composer.kNBELLS-1);
			int bellInEighthsPlace = rotStart.bellAt(Composer.kNBELLS);
			if (fPartEnd.bellAt(bellInSeventhsPlace)!=bellInSeventhsPlace || fPartEnd.bellAt(bellInEighthsPlace)!=bellInEighthsPlace)
				return false;
		}
		// If nice part-ends set, we must calculate the actual rotated part-end.
		if (fNicePartEnds)
		{
			/*
			 * On the face of it this looks faster - use the permutations from
			 * the intermediate row to calculate the rotated part end with just two
			 * permutations. In practice calculating permutations is much slower
			 * than using the permnums, so it is quicker to step through every lead,
			 * as below.
			 *
			RowNode intermediate = fLeads[rot-1].getLastRow();
			intermediate.calcPermutation(fPartEnd, fTempPerm);
			fTempRow.setRow(fRounds);
			fTempRow.applyPermutation(fTempPerm);
			fTempRow.applyPermutation(intermediate.toBytes());
			partEnd = fTables.getNode(fTempRow);
			*/
			RowNode partEnd = fRounds;
			int j = rot;
			for (int i=0; i<fNLeadsPerPart; i++)
			{
				Lead lead = fLeads[j];
				partEnd = partEnd.permute(lead.getMethod().getLeadPermNum(lead.getCall()));
				if (++j>=fNLeadsPerPart)
					j = 0;
			}
			return partEnd.isNicePartEnd();
		}
		return true;
	}

	/**
	 * Checks all acceptable rotations of the composition for music score.
	 * If a rotation is found better or equal to minMusic, the composition is
	 * also checked for truth.
	 *
	 * @return best music score, or -1 if composition false in the first part,
	 * or 0 if composition is otherwise false or no rotations meet music minimum.
	 */
	public int calcMusicRots(int minMusic)
	{
		fNParts = fLeads[fNLeadsPerPart-1].getLastRow().getNParts();
		fMusic = 0;
		int rot = fFirstRot;
		int music = calcMusic(rot);
		while (true)
		{
			if (music>minMusic)
			{
				// It is best to check for truth as soon as we have found the first
				// rotation with good music; we're going to have to prove anyway, and
				// if the composition is false we'll save time by not checking the
				// music of the remaining rotations.
				if (fMusic==0 && !isTrue())
				{
					// If false in first part, return -1 to tell Composer to backtrack.
					if (f1stPartFalseLead>=0)
						return -1;
					return 0;
				}
				if (music>fMusic)
				{
					fMusic = music;
					fBestRot = rot;
				}
			}
			if (fAllRotsGood)
			{
				if (++rot>=fNLeadsPerPart)
					return fMusic;
			}
			else
			{
				do
				{
					if (++rot>=fNLeadsPerPart)
						return fMusic;
				} while (!isGoodRotPartEnd(rot));
			}
			music = calcMusic(rot);
		}
	}

	/**
	 * Calculates the total music score of the entire composition starting
	 * from the given lead rotation. This is relatively slow, although the
	 * node lead-music tables are used to ensure only one count operation
	 * is required per lead.
	 * <p>
	 * Final for speed
	 */
	private final int calcMusic(int rot)
	{
		int music = 0;
		RowNode node = fRounds;
		int j = rot;
		for (int part=0; part<fNParts; part++)
		{
			for (int i=0; i<fNLeadsPerPart; i++)
			{
				Lead lead = fLeads[j];
				Method method = lead.getMethod();
				music+= node.getLeadMusic(method.getMethodIndex());
				node = node.permute(method.getLeadPermNum(lead.getCall()));
				if (++j>=fNLeadsPerPart)
					j = 0;
			}
		}
		return music;
	}

	/**
	 * Returns music score of best rotation.
	 * Final for speed.
	 */
	public final int getMusic()
	{
		return fMusic;
	}

	/**
	 * Called by Composer whenever a comp is checked to ensure getBalance() etc
	 * is up-to-date
	 */
	public void setBalance(int balance, int unbalanceCount)
	{
		fBalance = balance;
		fUnbalance = unbalanceCount;
	}

	/**
	 * Relies on setBalance() being called by the Composer
	 */
	public int getBalance()
	{
		return fBalance;
	}

	public int getUnbalanceCount()
	{
		return fUnbalance;
	}

	/**
	 * Checks the composition is true. For multiparts, we can save time by only
	 * checking (one more than) half of the parts. If the composition proves to
	 * be false in the first part, this is fed back into the main composing loop
	 * in order to prune the search tree.
	 */
	public boolean isTrue()
	{
		System.arraycopy(fZeroTable, 0, fTruthTable, 0, fTruthTable.length);
		RowNode node = fRounds;
		int partsToCheck = (fNParts+2)/2;
		int[] rows = fRowNumBuf;
		f1stPartFalseLead = -1;
		for (int part=0; part<partsToCheck; part++)
		{
			for (int i=0; i<fNLeadsPerPart; i++)
			{
				Lead lead = fLeads[i];
				Method method = lead.getMethod();
				int nodeNum;
				method.generateLead(node, rows);
				for (int k=method.getLeadLength()-1; k>=0; k--)
				{
					nodeNum = rows[k];
					if (fTruthTable[nodeNum])
					{
						if (part==0)
							f1stPartFalseLead = i;
						return false;
					}
					fTruthTable[nodeNum] = true;
				}
				node = node.permute(method.getLeadPermNum(lead.getCall()));
			}
		}
		return true;
	}
}
