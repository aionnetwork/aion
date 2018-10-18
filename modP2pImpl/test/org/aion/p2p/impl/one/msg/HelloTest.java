/*
 * Copyright (c) 2017-2018 Aion foundation.
 *      This file is part of the aion network project.
 *
 *      The aion network project is free software: you can redistribute it
 *      and/or modify it under the terms of the GNU General Public License
 *      as published by the Free Software Foundation, either version 3 of
 *      the License, or any later version.
 *
 *      The aion network project is distributed in the hope that it will
 *      be useful, but WITHOUT ANY WARRANTY; without even the implied
 *      warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *      See the GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the aion network project source files.
 *      If not, see <https://www.gnu.org/licenses/>.
 *
 *  Contributors:
 *      Aion foundation.
 */

package org.aion.p2p.impl.one.msg;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.UnsupportedEncodingException;
import org.junit.Test;

public class HelloTest {
    @Test
    public void testHello() {
        String msg = "Hello";
        Hello hello = new Hello(msg);
        assertNotNull(hello);

        String m = hello.getMsg();
        assertNotNull(m);
        assertEquals(msg, m);
    }

    @Test
    public void testEncode() {
        String msg = "Hello";
        Hello hello = new Hello(msg);
        assertNotNull(hello);

        byte[] b = hello.encode();
        assertNotNull(b);
        assertArrayEquals(b, msg.getBytes());
    }

    @Test
    public void testDecode() throws UnsupportedEncodingException {
        String msg = "Hello";
        Hello hello = Hello.decode(msg.getBytes());
        assertNotNull(hello);

        String m = hello.getMsg();
        assertNotNull(m);
        assertEquals(msg, m);
    }
}
