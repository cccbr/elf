package org.pealfactory.ring;

import org.pealfactory.compose.halfleadspliced.*;

/**
 * This is an immutable version of the basic class which represents rows -
 * {@link Row}.
 * It is often more convenient and less error-prone to deal with immutable rows,
 * especially when you need to keep semi-permanent references to them.
 * When applying permutations to ImmutableRows, the Row methods applyPermutation()
 * and applyPN() cannot be used. Instead, {@link #permute} and {@link #change}
 * methods are provided, which return a new ImmutableRow and do not alter the
 * current one.
 * <p>
 * Performance is critical for this class, so some methods are final to allow
 * inlining by compiling VMs. The hashcode, rounds, tenorsTogether, tenorsHome
 * and PBrow properties provided by the Row class are also cached.
 * <p>
 * In an ideal class design ImmutableRow and Row would share a common interface
 * and have different implementations. However these classes are used in applet
 * code where download size is crucial, so to keep the code size and number
 * of classes as small as possible, ImmutableRow extends Row directly.
 * All Row methods which alter internal state are overridden and will throw
 * RuntimeExceptions - a somewhat messy way to provide immutability, but it works.
 *
 * @author MBD
 */
public class ImmutableRow extends Row
{
	public static final ImmutableRow kROUNDS_ROW = new ImmutableRow(Composer.kNBELLS);

	private boolean fRounds = false;
	private transient boolean fHashcodeSet = false;
	private transient int fHashcode;
	private transient boolean fPBRowSet = false;
	private transient boolean fPBRow;
	private transient boolean fTTSet = false;
	private transient boolean fTenorsTogether;
	private transient boolean fTHSet = false;
	private transient boolean fTenorsHome;

	/**
	 * Construct a row containing rounds on <code>nbells</code>.
	 */
	public ImmutableRow(int nbells)
	{
		super(nbells);
		fRounds = true;
	}

	/**
	 * Construct a row from another Row - faster than doing
	 * new ImmutableRow(row.toString()).
	 */
	public ImmutableRow(Row row)
	{
		super(row);
		fRounds = super.equals(kROUNDS);
	}

	/**
	 * Construct a row from the String s, setting the number
	 * of bells from the string length.
	 */
	public ImmutableRow(String row)
	{
		super(row);
		fRounds = super.equals(kROUNDS);
	}

	/**
	 * Hashcode same as underlying string. Cached for speed.
	 */
	public final int hashCode()
	{
		if (!fHashcodeSet)
		{
			fHashcode = super.hashCode();
			fHashcodeSet = true;
		}
		return fHashcode;
	}

	/**
	 * Final for speed
	 */
	public final boolean isRounds()
	{
		return fRounds;
	}

	/**
	 * Cache value for speed
	 */
	public final boolean isTenorsTogether()
	{
		if (!fTTSet)
		{
			fTenorsTogether = super.isTenorsTogether();
			fTTSet = true;
		}
		return fTenorsTogether;
	}

	/**
	 * Cache value for speed
	 */
	public final boolean isTenorsHome()
	{
		if (!fTHSet)
		{
			fTenorsHome = super.isTenorsHome();
			fTHSet = true;
		}
		return fTenorsHome;
	}

	/**
	 * Cache value for speed
	 */
	public final boolean isPBrow()
	{
		if (!fPBRowSet)
		{
			fPBRow = super.isPBrow();
			fPBRowSet = true;
		}
		return fPBRow;
	}

	/**
	 * Permutes this row by the given transformation.
	 * The ImmutableRow r should be a valid change formed from kROUNDS
	 * characters and of the right length.
	 * Then, if e.g. permutation = reverse rounds, the effect is
	 * to reverse this row.
	 */
	public ImmutableRow permute(ImmutableRow r)
	{
		return permute(r.toBytes());
	}

	/**
	 * Permutes this row by the given change.
	 * Bells in the byte array are represented by numbers 1..nbells.
	 */
	public ImmutableRow permute(byte[] perm)
	{
		Row newRow = (Row)clone();
		newRow.applyPermutation(perm);
		ImmutableRow ret = new ImmutableRow(newRow.toString());
		return ret;
	}

	/**
	 * Permute the row by a single change of place notation. The place notation
	 * is represented as a byte array, and should be in the format provided by
	 * {@link PN#getChange}.
	 */
	public ImmutableRow change(byte[] pn)
	{
		Row newRow = new Row(toString());
		newRow.applyPN(pn);
		ImmutableRow ret = new ImmutableRow(newRow.toString());
		return ret;
	}

	/**
	 * Not allowed - we are immutable
	 */
	public void setRow(String s)
	{
		throw new RuntimeException("Cannot setRow() on ImmutableRows");
	}

	/**
	 * Not allowed - we are immutable
	 */
	public void setRow(Row r)
	{
		throw new RuntimeException("Cannot setRow() on ImmutableRows");
	}

	/**
	 * Not allowed - we are immutable
	 */
	public void swap(int i, int j)
	{
		throw new RuntimeException("Cannot setRow() on ImmutableRows");
	}

	/**
	 * Not allowed because we are immutable - use permute() instead.
	 */
	public void applyPermutation(Row permutation)
	{
		throw new RuntimeException("Cannot setRow() on ImmutableRows");
	}

	/**
	 * Not allowed because we are immutable - use permute() instead.
	 */
	public void applyPermutation(byte[] permutation)
	{
		throw new RuntimeException("Cannot setRow() on ImmutableRows");
	}

	/**
	 * Not allowed because we are immutable - use change() instead.
	 */
	public void applyPN(byte[] pn)
	{
		throw new RuntimeException("Cannot setRow() on ImmutableRows");
	}
}
