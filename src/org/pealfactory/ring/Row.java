package org.pealfactory.ring;

/**
 * Represents a single row of bells, at any stage between kMINNBELLS and
 * kMAXNBELLS (inclusive). The current number of bells in the row is kept,
 * as well as the row itself as a character array. Methods are provided to
 * permute rows (including applying place notation changes), and to analyse them,
 * for example to discover whether the tenors are together, or whether the row
 * is found in the plain course of Plain Bob at the appropriate stage.
 * <p>
 * Rows can be constructed from Strings of bell characters.
 * Normally these will look like the kROUNDS constant ("12345678"),
 * but we can use anything - e.g. "1--------" to draw just the treble
 * path. Rows constructed from kROUNDS can be converted to a byte array
 * representation using numbers 1...nbells - see {@link #toBytes}.
 * <p>
 * Since Rows are mutable, we allow cloning for clients who want
 * to preserve a copy of a particular row. See also {@link ImmutableRow}.
 * <p>
 * Efficiency is a prime concern of this class, so methods are provided
 * which avoid slow String operations and deal directly with Row and byte array
 * objects. Performance advice for each method is given in the method javadoc.
 *
 * @author MBD
 */
public class Row implements Constants, Cloneable
{
	/** Internal representation of the row */
	protected char[] fRow = new char[kMAXNBELLS];
	/** The 'size' (number of bells) of the row */
	protected int fNBells;

	/**
	 * Construct a row containing rounds on <code>nbells</code>.
	 */
	public Row(int nbells)
	{
		this(kROUNDS);
		setNBells(nbells);
	}

	/**
	 * Make a Row from another Row (just like clone really).
	 * This is much faster than new Row(r.toString()) because slow string
	 * conversion is avoided.
	 */
	public Row(Row r)
	{
		fNBells = r.getNBells();
		System.arraycopy(r.fRow, 0, fRow, 0, fNBells);
	}

	/**
	 * Construct a row from the String s, setting the number
	 * of bells from the string length.
	 */
	public Row(String s)
	{
		// !!! Cannot use setRow() because this is not allowed
		// for immutable rows
		int n = s.length();
		if (n>kMAXNBELLS)
			n = kMAXNBELLS;
		s.getChars(0, n, fRow, 0);
		fNBells = n;
	}

	/**
	 * Resets the row to the contents of the given String.
	 * Note that String processing is relatively inefficient, and it
	 * better to use setRow(r) if you already have a Row r.
	 */
	public void setRow(String s)
	{
		int n = s.length();
		if (n>kMAXNBELLS)
			n = kMAXNBELLS;
		else if (n<kMINNBELLS)
			return;
		s.getChars(0, n, fRow, 0);
		fNBells = n;
	}

	/**
	 * This is an efficient way to assign the contents of another Row to ourselves.
	 * It uses a direct array copy, so is much faster than the equivalent
	 * setRow(r.toString()).
	 */
	public void setRow(Row r)
	{
		fNBells = r.fNBells;
		System.arraycopy(r.fRow, 0, fRow, 0, fNBells);
	}

	/**
	 * The number of bells in the row can be reduced but not increased.
	 * To create a row with more bells, construct a new object.
	 */
	protected void setNBells(int n)
	{
		if (n>kMAXNBELLS)
			throw new IllegalArgumentException("Maximum number of bells is "+kMAXNBELLS+", tried to set "+n);
		if (n<kMINNBELLS)
			throw new IllegalArgumentException("Minimum number of bells is "+kMINNBELLS+", tried to set "+n);
		if (n>fNBells)
			throw new IllegalArgumentException("Can't increase number of bells from "+fNBells+" to "+n+" - row may not be set!");
		fNBells = n;
	}

	/**
	 * Return the row as a String whose length is the current number of bells.
	 * Creating Strings is fairly slow, so do not use this in performance-critical code.
	 */
	public String toString()
	{
		return new String(fRow, 0, fNBells);
	}

	/**
	 * Returns the row as a byte[] with bells represented by
	 * bytes 1..nbells, not bell characters. Useful for creating permutations
	 * from rows.
	 * Final for speed.
	 */
	public final byte[] toBytes()
	{
		byte[] ret = new byte[fNBells];
		for (int i=1; i<=fNBells; i++)
			ret[i-1] = (byte)bellAt(i);
		return ret;
	}

	/**
	 * As well as comparing Row with Row, we can compare
	 * with a String - in this case, it doesn't matter if
	 * the String is longer than us, as the extra characters
	 * are dropped. This helps easy comparison with kROUNDS.
	 * <p>
	 * Optimisation: slow toString() conversions avoided.
	 */
	public boolean equals(Object o)
	{
		if (o instanceof Row)
		{
			Row r = (Row)o;
			for (int i=fNBells-1; i>=0; i--)
				if (fRow[i]!=r.fRow[i])
					return false;
			return true;
		}
		else if (o instanceof String)
		{
			String s = (String)o;
			if (s.length()<fNBells)
				return false;
			for (int i=fNBells-1; i>=0; i--)
				if (fRow[i]!=s.charAt(i))
					return false;
			return true;
		}
		return false;
	}

	/**
	 * The hashcode is based on that of the underlying row string, however the
	 * calculation is performed directly, to avoid the slow toString()
	 * conversion.
	 */
	public int hashCode()
	{
		int hash = fRow[0];
		for (int i=1; i<fNBells; i++)
			hash = 31*hash + fRow[i];
		return hash;
	}

	/**
	 * Returns the current number of bells defined for this row.
	 * Final for speed.
	 */
	public final int getNBells()
	{
		return fNBells;
	}

	public Object clone()
	{
		Row r = new Row(this);
		return r;
	}

	/**
	 * Given a bell character from kROUNDS, returns the index at which the
	 * bell occurs in the row, 1..nbells.
	 * Returns 0 if not found.
	 */
	public int findBell(char bell)
	{
		for (int i=0; i<fNBells; i++)
		{
			if (fRow[i]==bell)
				return i+1;
		}
		return 0;
	}

	/**
	 * Place is 1..nbells. Returns bell number 1..nbells at
	 * that position in row, or 0 if not found.
	 * Final for speed.
	 */
	public final int bellAt(int place)
	{
		return 1+kROUNDS.indexOf(fRow[place-1]);
	}

	/**
	 * Swap bells at i and j in this row, numbering from 1 as usual.
	 */
	public void swap(int i, int j)
	{
		char c = fRow[i-1];
		fRow[i-1] = fRow[j-1];
		fRow[j-1] = c;
	}

	public void test(PN pn)
	{
		Row r = new Row(pn.guessNBells());
		for (int i=0; i<pn.getLength(); i++)
		{
			r.applyPN(pn.getChange(i));
			if (r.equals(kROUNDS))
				break;
		}

		ImmutableRow row = new ImmutableRow(pn.guessNBells());
		for (int i=0; i<pn.getLength(); i++)
		{
			row = row.change(pn.getChange(i));
			if (row.isRounds())
				break;
		}
	}

	/**
	 * Permutes this row by the given Row transformation.
	 * The Row should be a valid change formed from kROUNDS
	 * characters and of the right length.
	 * Then, if e.g. permutation = reverse rounds, the effect is
	 * to reverse this row.
	 */
	public void applyPermutation(Row permutation)
	{
		applyPermutation(permutation.toBytes());
	}

	/**
	 * Permutes this row by the given change.
	 * Bells in the byte array are represented by numbers 1..nbells.
	 */
	public void applyPermutation(byte[] permutation)
	{
		//String oldRow = toString();
		char[] oldRow = new char[fNBells];
		System.arraycopy(fRow, 0, oldRow, 0, fNBells);
		try
		{
			for (int i=0; i<fNBells; i++)
			{
				int index = permutation[i]-1;
				fRow[i] = oldRow[index];
			}
		}
		catch (ArrayIndexOutOfBoundsException e)
		{
			System.out.println("FATAL: bad permutation "+new String(permutation));
			System.arraycopy(oldRow, 0, fRow, 0, fNBells);
		}
	}

	/**
	 * Given a Row r, calculates the permutation (as a byte[]) which would
	 * permute us to that row. This is the reverse of applyPermutation().
	 * Note the permutation array must be pre-allocated and passed to this method
	 * as a parameter.
	 * <p>
	 * Final for speed.
	 */
	public final void calcPermutation(Row r, byte[] perm)
	{
		for (int i=0; i<fNBells; i++)
			perm[i] = (byte)findBell(r.fRow[i]);
	}

	/**
	 * Permute the row by a single change of place notation. The place notation
	 * is represented as a byte array, and should be in the format provided by
	 * {@link PN#getChange}.
	 */
	public void applyPN(byte[] pn)
	{
		int j = 0;
		// Get first place from the notation - note if the array is
		// empty, this implies a X change, so set the next place above
		// the end of the change.
		int nextPlace;
		if (j<pn.length)
			nextPlace = pn[j++];
		else
			nextPlace = fNBells;
		for (int i=0; i<fNBells; )
		{
			// Have we reached the next place?
			// Double-check for malformed notation - i.e. odd place where
			// an even is expected, or vice versa. In this case assume an
			// extra place immediately beforehand.
			if (i==nextPlace || i+1==nextPlace)
			{
				i = nextPlace+1;
				// Get the next place
				if (j<pn.length)
					nextPlace = pn[j++];
				else
					nextPlace = fNBells;
			}
			else
			{
				// Swap pair of bells and move on two.
				swap(i+1, i+2);
				i+= 2;
			}
		}
	}

	/**
	 * Returns true if the tenors (bells 7..n) are home.
     * Calculates the answer every time, so cache if you want speed.
	 */
	public boolean isTenorsHome()
	{
		for (int i=7; i<=fNBells; i++)
			if (bellAt(i)!=i)
				return false;
		return true;
	}

	/**
	 * Returns true if the tenors (bells 7..n) are in a PB leadhead position
     * Calculates the answer every time, so cache if you want speed.
	 */
	public boolean isTenorsTogether()
	{
		int bell = 8;
		int pos = findBell(kROUNDS.charAt(bell-1));
		if (pos==0)
			return false;
		do
		{
			bell = nextCourseBell(bell, true);
			pos = nextCourseBellNoTreble(pos, true);
			if (bell!=bellAt(pos))
				return false;
		} while (bell!=7);
		return true;
	}

	/**
	 * Finds the next bell in Plain Bob coursing order from i
	 * (where i = 1..nbells).
	 * If <code>evensUp</code> set, we move even bells up two
	 * and odd bells down two, and vice versa.
	 * For evensUp, the effect is to incrementally generate
	 * (on 8 bells) the numbers 2 4 6 8 7 5 3 1 2 4 6 8...
	 */
	public int nextCourseBell(int i, boolean evensUp)
	{
		// If we're at an even place and evensUp is true,
		// or we're at an odd place and evensUp is false,
		// go up two
		if (((i&1)!=0) ^ evensUp)
		{
			i+=2;
			// If we've reached the tenor, start again at highest odd bell
			// (for evensUp), or the highest even bell (for evensUp false).
			if (i>fNBells)
			{
				if (evensUp)
					i = (fNBells-1)|1;
				else
					i = fNBells&~1;
			}
		}
		else
		{
			// Go down two
			i-=2;
			// If we've reached the front, start again with the two
			// (for evensUp), or the treble (for evensUp false).
			if (i<1)
			{
				if (evensUp)
					i = 2;
				else
					i = 1;
			}
		}
		return(i);
	}

	/**
	 * Just like nextCourseBell() above, but always skips the
	 * treble (i.e. never returns 1).
	 */
	public int nextCourseBellNoTreble(int i, boolean evensUp)
	{
		i = nextCourseBell(i, evensUp);
		if (i==1)
			i = nextCourseBell(i, evensUp);
		return i;
	}

	/**
	 * Returns true if the current row occurs in a plain course of
	 * Plain Bob at this stage.
	 * We use the isPBrow(boolean) method to check for either
	 * backstroke (direction=true) or handstroke (direction=false)
	 * PB rows.
	 */
	public boolean isPBrow()
	{
		return isPBrow(true) || isPBrow(false);
	}

	/**
	 * Returns true if the current row occurs in a plain course of
	 * Plain Bob at backstroke (when direction=true) or handstroke
	 * (direction=false).
	 * This is accomplished by stepping through places 1,2,4,6... down
	 * to 5,3,1, skipping any place that contains the treble. For each
	 * step, we make sure that the bell found is the expected course
	 * (or after) bell of the one before.
	 */
	private boolean isPBrow(boolean direction)
	{
		int place, bell, expected;
		// Start in 1st's place
		place = 1;
		// Flag to say don't check the first bell!
		expected = -1;
		do
		{
			// Skip this place if it has the treble in it
			if (fRow[place-1]==kROUNDS.charAt(0))
			{
				place = nextCourseBell(place, true);
				if (place==1)
				 break;
			}
			// Find which bell number is in this place
			bell = 1+kROUNDS.indexOf(fRow[place-1]);
			// Was it what we expected?
			if (expected>0 && bell!=expected)
				return false;
			// Work out the next expected bell, according to direction,
			// remembering to skip the treble.
			expected = nextCourseBellNoTreble(bell, direction);
			// Work out the next place - notice here the treble (1st's place)
			// is OK, and indeed signifies we've done the entire row.
			place = nextCourseBell(place, true);
		} while (place!=1);

		return true;
	}

    /**
     * For lead heads only - returns a bitmap indicating which pairs of bells are in a coursing position at this lead.
     * The tenors (7,8 for Major) are always at bit 0 in the map. Bit 0 will therefore be set if the tenors are in
     * a (plain-bob)
     *
     * @return
     */
    public int getCoursingPairBitmap()
    {
			return 0;
    }
}
