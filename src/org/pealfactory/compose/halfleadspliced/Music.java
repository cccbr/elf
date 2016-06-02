package org.pealfactory.compose.halfleadspliced;

import java.util.*;

/**
 * Represents one music definition: a score plus list of matching rows.
 *
 * @author MBD
 * @since Earwen
 */
class Music
{
	public final static char kSEPARATOR = '|';

	public final static char kWILDCARD = 'x';
	public final static int kNBELLS = 8;

	private final static String[] kBACK_ROLLUPS = {"xxxx5678", "xxxx6578", "xxxx8765"};
	private final static String[] kFRONT_ROLLUPS = {"5678xxxx", "8765xxxx"};
	private final static String[] kLB = {"2345xxxx", "5432xxxx", "xxxx2345", "xxxx5432"};
	private final static String[] k468 = {"xxxx2468", "xxxx3468"};
	private final static String[] kQUEENS = {"13572468"};
	private final static String[] kWHITT = {"12753468"};
	public final static Music[] kDEFAULT = {
			new Music("Back rollups", 1, kBACK_ROLLUPS),
			new Music("Front rollups", 1, kFRONT_ROLLUPS),
			new Music("Little-bell", 1, kLB),
			new Music("468s", 1, k468),
			new Music("Queens", 2, kQUEENS),
			new Music("Whittingtons", 2, kWHITT)};

	private final static String[] k1_3PART = {"1xxx5678"};
	private final static String[] k2_6PART = {"1xxx5678", "1xxx6578", "1xxx8765", "1xxx5768", "1xxx7856"};
	private final static String[] k5PART = {"13526478", "15634278", "16452378", "14263578"};
	private final static String[] k7PART = {"13527486", "15738264", "17856342", "18674523", "16482735", "14263857", "13456782", "14567823", "15678234", "16782345", "17823456", "18234567"};
	private final static String[] k10PART = {"13257486", "13278564", "13286745", "13264857"};
	/**
	 * Corrected and increased for Undomiel-B
	 */
	private final static String[] k4_12PART = {"1xxx6857", "1xxx7586", "13524xxx", "14253xxx", "15234xxx", "13452xxx", "14532xxx", "15423xxx"};
	public final static Music[] kPARTS = {
			new Music("1 & 3", 1, k1_3PART),
			new Music("2 & 6", 1, k2_6PART),
			new Music("4 & 12", 1, k4_12PART),
			new Music("5", 1, k5PART),
			new Music("7", 1, k7PART),
			new Music("10", 1, k10PART)};

	private String fName;
	private int fScore;
	private int fMinScore = 0;
	private String[] fMatches;

	public Music(String name, int score, String[] matches)
	{
		fName = name;
		fScore = score;
		fMatches = matches;
	}

	/**
	 * !!! Assumes 8 bells !!!
	 */
	public Music(String name, int score, String matches)
	{
		fName = name.replace(kSEPARATOR, ' ');
		fScore = score;
		Vector v = new Vector();
		StringTokenizer tok = new StringTokenizer(matches.trim(), ",; ");
		while (tok.hasMoreTokens())
		{
			String row = tok.nextToken();
			int l = row.length();
			if (l>kNBELLS)
				row = row.substring(0, kNBELLS);
			else if (l<kNBELLS)
				row = row+"xxxxxxxxxxxx".substring(0, kNBELLS-l);
			row = row.replace('l', '1');
			row = row.replace('O', '0');
			row = row.replace('-', kWILDCARD);
			row = row.replace('*', kWILDCARD);
			row = row.replace('.', kWILDCARD);
			v.addElement(row);
		}
		fMatches = new String[v.size()];
		v.copyInto(fMatches);
	}

	public String toString()
	{
		String s = fName+kSEPARATOR+fScore+kSEPARATOR;
		for (int i = 0; i<fMatches.length; i++)
			s += " "+fMatches[i];
		return s;
	}

	public String getName()
	{
		return fName;
	}

	public int getScore()
	{
		return fScore;
	}

	public int getMinScore()
	{
		return fMinScore;
	}

	public String[] getMatches()
	{
		return fMatches;
	}

}
