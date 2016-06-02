package org.pealfactory.compose.halfleadspliced;

/**
 * An interface which must be implemented by applications or applets
 * hosting a Composer instance. It is the means by which the Composer
 * communicates with its hosting environment. Currently the only
 * required API is outputComp(), which the Composer calls to supply
 * newly-produced compositions.
 *
 * @author MBD
 * @since Indis
 */
public interface ComposerHost
{
	/**
	 * Must be implemented to supply a callback used by the {@link Composer}
	 * to output compositions found by the search.
	 */
	void outputComp(OutputComp comp);
}
