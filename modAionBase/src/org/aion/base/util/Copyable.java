package org.aion.base.util;

/**
 * Alternative to Cloneable from java read: http://www.artima.com/intv/bloch13.html
 *
 * @author yao
 * @param <T>
 */
public interface Copyable<T> {
    T copy();
}
