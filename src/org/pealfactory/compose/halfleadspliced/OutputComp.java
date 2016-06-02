package org.pealfactory.compose.halfleadspliced;

/**
 * When the composing engine finds a true composition that meets the score
 * minimums, it creates an OutputComp instance and sends it to its ComposerHost.
 * The OutputComp class is initialised with a title and a Composition reference;
 * it queries the Composition to find the best rotation and to obtain the calling.
 * A tables of Leads is then built to represent the first part of the rotated
 * composition.
 *
 * @author MBD
 */
class OutputComp
{
	public final static String kNEWLINE = "\r\n";

	private String fTitle;
	private Lead[] fLeads;
	private int fNParts;
	private int fMusic;
	private int fScore;
	/** Must be held per part */
	private int fCOM;
	/** Method balance score 0-100% */
	private int fBalance;
	/** Counts for balance pruning */
	private int fUnbalance;
	private boolean fLHOnly;

	public OutputComp(String title, Composition comp, int score, RowNode start, boolean lhOnly)
	{
		fTitle = title;
		fScore = score;
		fNParts = comp.getNParts();
		fMusic = comp.getMusic();
		fCOM = comp.getCOM();
		fBalance = comp.getBalance();
		fUnbalance = comp.getUnbalanceCount();
		fLHOnly = lhOnly;
		int n = comp.getNLeadsPerPart();
		fLeads = new Lead[n];
		int j = comp.getBestRot();
		for (int i=0; i<n; i++)
		{
			Lead compLead = comp.getLead(j);
			Lead lead = new Lead();
			lead.setLead(start, compLead.getMethod(), compLead.getCall());
			start = lead.getLastRow();
			fLeads[i] = lead;
			if (++j>=n)
				j = 0;
		}
	}

	public Lead getLead(int i)
	{
		return fLeads[i];
	}

  public String toString(String titleExtra)
	{
		String s= fTitle+titleExtra+kNEWLINE;
		s+= " 2345678"+kNEWLINE;
		for (int i=0; i<fLeads.length; i++)
			s+= " "+fLeads[i].toString(fLHOnly)+kNEWLINE;
		if (fNParts>1)
			s+= fNParts+" part"+kNEWLINE;
		s+= "Music = "+fMusic+" COM = "+fCOM*fNParts+" Balance = "+fBalance+"%";
		return s;
	}

	/**
	 * Returns +1 if we score more, -1 if less
	 */
	public int compareTo(OutputComp comp)
	{
		if (fScore>comp.getScore())
			return 1;
		if (fScore<comp.getScore())
			return -1;
		return 0;
	}

	public String getTitle()
	{
		return fTitle;
	}

	public int getNParts()
	{
		return fNParts;
	}

	public int getMusic()
	{
		return fMusic;
	}

	public int getScore()
	{
		return fScore;
	}

	/**
	 * Changes of method per part - multiply by getNParts() to get total.
	 */
	public int getCOM()
	{
		return fCOM;
	}

	public int getBalance()
	{
		return fBalance;
	}

	public int getUnbalanceCount()
	{
		return fUnbalance;
	}

	public int getNLeads()
	{
		return fLeads.length;
	}

}
