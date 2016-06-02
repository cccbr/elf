package org.pealfactory.ring;

import java.util.*;

/**
 * Represents a set of place notation, parsed from a String which may be
 * in any recognised format. The place notation is held in an internal format
 * in which each change is represented by a byte array - see {@link #getChange}.
 * These byte arrays are suitable for application to Row objects using the call
 * {@link Row#applyPN}. Symmetric notations are expanded during parsing, so one
 * place notation change is available for every row of a method's lead.
 * <p>
 * For information about the notation formats supported by this class, see the
 * javadoc for the (private) {@link #parse} method. For the bell place characters
 * expected on higher numbers, see {@link Constants#kROUNDS}.
 * <p>
 * Various properties of the place notation are available for query once the
 * PN object has been constructed, including whether the notation is symmetric,
 * whether it is right-place, and the stage (highest bell number) it applies to.
 * The latter property is "guessed" by the parser and may not be accurate for
 * notation formats in which final external places are not given.
 * <p>
 * Some attention to performance issues has been paid in the design of this class,
 * but if performance is critical direct use of place notation permutations should
 * be avoided.
 *
 * @author MBD
 */
public class PN implements Constants
{
	/** Cross change X represented as a 0-length array */
	private static final byte[] kCROSSCHANGE = new byte[0];

	private String fOriginalString;
	/** Contains a byte[] for each change in original PN */
	private byte[][] fPN;
	/** The highest place made in the PN, used for guessing number of bells
	 *  (0 = lead) */
	private int fHighestPlace;
	/** The highest place made in the PN not at the half-lead, used for guessing
	 * number of bells (0 = lead) */
	private int fHighestPlaceNotHalfLead;
	/** True if PN contains cross notation - used for guessing number of bells */
	private boolean fContainsCross;

	public PN(String pn)
	{
		fOriginalString = pn.trim();
		parse();
	}

	public String toString()
	{
		return fOriginalString;
	}

	public int getLength()
	{
		return fPN.length;
	}

	/**
	 * Gets the place notation for a particular change.
	 * This is a parsed representation as a byte array which
	 * lists the places made, where 0 = lead.
	 * Initial external places (ie 0) are always present, but
	 * final external places (ie nbells-1) may not be.
	 * An empty byte[] represents the cross change X.
	 */
	public byte[] getChange(int i)
	{
		return fPN[i];
	}

	/**
	 * Tries to work out the stage of the method represented
	 * by the place notation. Not necessarily accurate if
	 * external places haven't been given!
	 */
	public int guessNBells()
	{
		int n = fHighestPlace+1;
		if ((n&1)!=0 && fContainsCross)
		{
			// If the pn contains a cross notation, the method is likely
			// to be at an even stage; in this case, if the highest place
			// is odd, we assume we haven't been given the external places
			// and guess that the number of bells is 1 higher than the highest
			// place made, or 3 higher than the highest place made not at
			// the halflead, whichever is higher.
			n = Math.max(fHighestPlaceNotHalfLead+1+3, n+1);
		}
		return n;
	}

	/**
	 * Returns the highest place made in the pn.
	 */
	public int highestPlace()
	{
		return fHighestPlace+1;
	}

	/**
	 * A right-place method has an even-length lead, and every even change
	 * is a cross.
	 */
	public boolean isRightPlace()
	{
		int n = getLength();
		if ((n&1)!=0)
			return false;
		for (int i=0; i<n; i+=2)
			if (getChange(i)!=kCROSSCHANGE)
				return false;
		return true;
	}

	/**
	 * Returns true if place notation is symmetrical about the halfway point.
	 */
	public boolean isSymmetric()
	{
		int l = getLength();
		if ((l&1)!=0)
			return false;
		for (int i=0; i<l/2-1; i++)
			if (!changesEqual(getChange(i), getChange(l-i-2)))
				return false;
		return true;
	}

	/**
	 * Returns true if the place notation has double-method symmetry
	 * (to see whether it represents a double method, test isSymmetric()
	 * as well).
	 */
	public boolean isRotationallySymmetric(int nbells)
	{
		int l = getLength();
		if ((l&3)!=0)
			return false;
		for (int i=0; i<=l/4; i++)
		{
			if (!changesEqual(reverseChange(getChange(i), nbells), getChange(l/2-2-i)))
				return false;
			if (!changesEqual(reverseChange(getChange(l/2+i), nbells), getChange(l-2-i)))
				return false;
		}
		// Check halflead is reverse of leadhead.
		if (!changesEqual(reverseChange(getChange(l/2-1), nbells), getChange(l-1)))
			return false;
		return true;
	}

	/**
	 * Returns true if two place notation changes are equal - note
	 * that the final place is important, so if one change specifies "n"
	 * and the other doesn't, they won't match.
	 */
	private boolean changesEqual(byte[] pn1, byte[] pn2)
	{
		int l = pn1.length;
		if (l!=pn2.length)
			return false;
		for (int i=0; i<l; i++)
			if (pn1[i]!=pn2[i])
				return false;
		return true;
	}

	/**
	 * Swap change front to back, e.g. for double method testing.
	 */
	private byte[] reverseChange(byte[] pn1, int nbells)
	{
		int l = pn1.length;
		if (l==0)
			return pn1;

		// Ensure final place
		if ((pn1[l-1]&1)!=(nbells-1&1))
		{
			byte[] pn2 = new byte[l+1];
			System.arraycopy(pn1, 0, pn2, 0, l);
			pn1 = pn2;
			pn1[l++] = (byte)(nbells-1);
		}
		byte[] pn2 = new byte[l];
		for (int i=0; i<l; i++)
			pn2[i] = (byte)(nbells-1-pn1[i]);
		return pn2;
	}

	/**
	 * Parses place notation String, placing results into internal
	 * format array ready for use. It automatically handles any of
	 * the following standard notations:
	 * <ul>
	 * <li>CC library - a list of place notations, with external places
	 * given and '.' separators. Symmetric methods are indicated by
	 * giving the leadhead notation immediately after the halflead,
	 * separated only by a space.
	 * <li>Mark's MLIB format - similar to CC library, but symmetrical
	 * methods are given by prefixing the leadhead notation with "l"
	 * or "lh".
	 * <li>MicroSiril - this uses blocks of place notation, separated by
	 * ',' and prefixed by '&' or '+'. Blocks prefixed by '&' are taken
	 * to be symmetrical. In this format external places are not usually given.
	 * <li>A variation of MicroSiril in which the leadhead code (e.g. 'a', 'j1'',
	 * 'mx', 'z2' etc) is given immediately before the symmetric portion of the
	 * notation. The final leadhead is implied by the leadhead code.
	 * </ul>
	 * In any format, either 'x' or '-' can be used to represent the cross
	 * change. Other standard characters are '.' to separate adjacent
	 * non-cross notations, and of course letters from the Rounds string
	 * "1234567890ETABCDFGHJ" to represent places made. Spaces are normally
	 * ignored (except in the special case before a leadhead in the CC format)
	 * and upper or lower case characters are always treated the same.
	 */
	private void parse()
	{
		// A Vector is used as a temporary repository for the place notation
		// changes as they are generated. This is copied into the fPN array at
		// the end of the method.
		Vector pn = new Vector();

		fHighestPlace = 0;
		fHighestPlaceNotHalfLead = 0;
		int highestInBlock = 0;
		int lastHighest = 0;
		fContainsCross = false;

		String pnString = fOriginalString;
		if (pnString.length()==0)
		{
			System.out.println(" No place notation given!");
			return;
		}

		// See if we start with a leadhead code.
		char c = pnString.charAt(0);
		int i = pnString.indexOf(' ');
		if (i>0 && pnString.charAt(i-1)=='z')
			c = 'z';
		if (c=='z' || (c>='a' && c<='m'))
		{
			// Check we don't already finish with leadhead pn
			int j = pnString.lastIndexOf('+');
			if (j<0 || pnString.length()-j>4)
			{
				if (c=='z')
					pnString = pnString.substring(i+1)+", +"+pnString.substring(0, i-1);
				else if (i>=1 && i<=2)
				{
					pnString = pnString.substring(i+1);
					if (c<='f')
						pnString+= ", +2";
					else
						pnString+= ", +1";
				}
			}
		}

		char[] source = pnString.toUpperCase().toCharArray();
		byte[] pnbuf = new byte[kMAXNBELLS];
		int n = 0;
		int blockStart = 0;
		boolean blockEnded = false;
		boolean symmetric = false;
		boolean hadSeparator = true;

		for (i = 0; i<source.length; i++)
		{
			// First see if it is a bell character (place)
			// If so, we'll copy change place notation to char array,
			// and add to PN vector.
			int place = kROUNDS.indexOf(source[i]);
			if (place>=0)
			{
				// If first place even, set b to 1, then later add extra
				// external 1st's place to notation.
				int b = (place&1);
				int j = i;
				// Could apply more checks for validity of PN here
				do
				{
					pnbuf[i-j] = (byte)place;
					i++;
					if (i>=source.length)
						break;
					place = kROUNDS.indexOf(source[i]);
				}	while (place>=0 && i-j<kMAXNBELLS);
				byte[] change = new byte[b+i-j];
				// Add any missing external place at lead.
				if (b>0)
					change[0] = 0;
				System.arraycopy(pnbuf, 0, change, b, i-j);
				// Highest place
				place = pnbuf[i-j-1];

				// At this point we check whether this new PN was separated
				// from the previous one only by whitespace - in CC library
				// format this means we have reached the leadhead of a
				// symmetric method. This also only occurs at the end of
				// the PN string, so check for that too.
				if (!hadSeparator && i>=source.length)
				{
					n+= reflectSymmetricNotation(blockStart, pn);
					blockStart = n;
					symmetric = false;
					if (lastHighest>fHighestPlaceNotHalfLead)
						fHighestPlaceNotHalfLead = lastHighest;
					lastHighest = highestInBlock = 0;
				}
				else
				{
					lastHighest = highestInBlock;
				}
				if (place>highestInBlock)
				{
					highestInBlock = place;
					if (place>fHighestPlace)
						fHighestPlace = place;
				}
				hadSeparator = false;

				// Finally add the parsed PN to the list
				pn.addElement(change);
				n++;
				i--;	// Needed because outer loop always does i++
			}
			// Cross PN represented as an empty byte array
			else if (source[i]=='X' || source[i]=='-')
			{
				pn.addElement(kCROSSCHANGE);
				n++;
				fContainsCross = true;
				hadSeparator = true;
				lastHighest = highestInBlock;
			}
			// Keep track of . separators, because two non-cross PNs
			// not separated by a . implies symmetric halflead/leadhead
			// in CC library notation.
			else if (source[i]=='.')
			{
				hadSeparator = true;
			}
			// Ignore whitespace
			else if (source[i]==' ' || source[i]=='\t')
			{
			}
			// Look for mlib-format "LH" at end of PN
			else if (source[i]=='L')
			{
				// Always means previous block symmetric
				n+= reflectSymmetricNotation(blockStart, pn);
				symmetric = false;
				// Skip over L or LH
				if (source[i+1]=='H')
					i++;
				blockEnded = true;
			}
			// & signifies end of one block and beginning of a symmetric block
			else if (source[i]=='&')
			{
				// Do any previous symmetric block
				if (symmetric)
					n+= reflectSymmetricNotation(blockStart, pn);
				symmetric = true;
				blockEnded = true;
			}
			// + signifies end of one block and beginning of a non-symmetric block
			// Treat block separator , the same.
			else if (source[i]=='+' || source[i]==',')
			{
				// Do any previous symmetric block
				if (symmetric)
					n+= reflectSymmetricNotation(blockStart, pn);
				symmetric = false;
				blockEnded = true;
			}
			else
			{
				System.out.println(" Unrecognised character "+source[i]+" in place notation");
			}

			// Note this code is also embedded into the place parser above when
			// a CC leadhead is reached!
			if (blockEnded)
			{
				blockStart = n;
				hadSeparator = true;
				if (lastHighest>fHighestPlaceNotHalfLead)
					fHighestPlaceNotHalfLead = lastHighest;
				lastHighest = highestInBlock = 0;
				blockEnded = false;
			}
		}

		// All done - copy temporary pn Vector into fPN array.
		fPN = new byte[pn.size()][];
		pn.copyInto(fPN);
	}

	/**
	 * Adds reflected notation between <code>fromHere</code> up
	 * to but not including the current PN entry, which is taken as
	 * the pivot point.
	 *
	 * @return the number of relected changes added.
	 */
	private int reflectSymmetricNotation(int fromHere, Vector pn)
	{
		int n = 0;
		int i = pn.size()-2;
		while (i>=fromHere)
		{
			pn.addElement(pn.elementAt(i));
			i--;
			n++;
		}
		return n;
	}
}
