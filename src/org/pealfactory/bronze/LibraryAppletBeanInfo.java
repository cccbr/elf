package org.pealfactory.bronze;

import java.beans.*;

/**
 * A BeanInfo class is provided for the LibraryApplet because this applet
 * is used by LiveConnect communication from clientside Javascript.
 * The Microsoft Java VM attempts to load a BeanInfo class for any applet
 * involved in LiveConnect communication; if we don't supply a class, an
 * expensive and uncacheable web request and 404 response occurs.
 *
 * @author MBD
 * @since Earwen
 */
public class LibraryAppletBeanInfo extends SimpleBeanInfo
{
}
