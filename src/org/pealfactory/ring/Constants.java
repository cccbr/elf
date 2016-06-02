package org.pealfactory.ring;

/**
 * Basic constants for ringing software. Defined here are:
 * <ol>
 * <li>Min and max acceptable number of bells
 * <li>Place notation characters
 * <li>Call indices
 * <li>The Rounds string containing characters for each bell up to stage 20.
 * </ol>
 *
 * @author MBD
 */
public interface Constants
{
	public static final int kMINNBELLS = 3;
	public static final int kMAXNBELLS = 20;

	public static final char kCHAR_CROSS = 'x';
	public static final char kCHAR_LH = 'l';

	public static final int kCALL_PLAIN = 0;
	public static final int kCALL_BOB = 1;
	public static final int kCALL_SINGLE = 2;
	public static final int kCALL_EXTREME = 3;
	public static final int kCALL_USER = 4;

	public static final String kROUNDS = "1234567890ETABCDFGHJ";
}
