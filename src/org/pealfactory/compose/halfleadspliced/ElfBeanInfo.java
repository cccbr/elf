package org.pealfactory.compose.halfleadspliced;

import java.beans.*;

/**
 * BeanInfo is required by applets (such as Elf) which are the target of
 * script communication in IE via LiveConnect. We supply a BeanInfo class
 * to prevent an HTTP 404 from IE's classloader.
 *
 * @author MBD
 */
public class ElfBeanInfo extends SimpleBeanInfo
{
}
