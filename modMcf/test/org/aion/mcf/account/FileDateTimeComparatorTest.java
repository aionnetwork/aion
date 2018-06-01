/*
 ******************************************************************************
 * Copyright (c) 2017-2018 Aion foundation.
 *
 *     This file is part of the aion network project.
 *
 *     The aion network project is free software: you can redistribute it
 *     and/or modify it under the terms of the GNU General Public License
 *     as published by the Free Software Foundation, either version 3 of
 *     the License, or any later version.
 *
 *     The aion network project is distributed in the hope that it will
 *     be useful, but WITHOUT ANY WARRANTY; without even the implied
 *     warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *     See the GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with the aion network project source files.
 *     If not, see <https://www.gnu.org/licenses/>.
 *
 *     The aion network project leverages useful source code from other
 *     open source projects. We greatly appreciate the effort that was
 *     invested in these projects and we thank the individual contributors
 *     for their work. For provenance information and contributors
 *     please see <https://github.com/aionnetwork/aion/wiki/Contributors>.
 *
 * Contributors to the aion source files in decreasing order of code volume:
 *     Aion foundation.
 *     <ether.camp> team through the ethereumJ library.
 *     Ether.Camp Inc. (US) team through Ethereum Harmony.
 *     John Tromp through the Equihash solver.
 *     Samuel Neves through the BLAKE2 implementation.
 *     Zcash project team.
 *     Bitcoinj team.
 *****************************************************************************
 */
package org.aion.mcf.account;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

public class FileDateTimeComparatorTest {

    /**
     * Generates a list that should go from latest date to earliest
     *
     * @return
     */
    public static List<File> generateValidFiles() {
        List<File> nameList = new ArrayList<File>();
        long date = System.currentTimeMillis();
        for (int i = 0; i < 10; i++) {
            TimeZone tz = TimeZone.getTimeZone("UTC");
            DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            df.setTimeZone(tz);
            String iso_date = df.format(new Date(date - (i * 1000)));
            String fileName = "UTC--" + iso_date + "--" + "blah";
            nameList.add(new File(fileName));
        }

        return nameList;
    }

    @Test
    public void testComparator() {
        List<File> l = generateValidFiles();

        List<String> expectedFileNames = new ArrayList<String>();

        for (int i = (l.size() - 1); i >= 0; i--) {
            expectedFileNames.add(l.get(i).getName());
        }

        // note: this will fail if we use a odd number of files!
        for (int i = 0; i < l.size(); i++) {
            assertThat(l.get(i).getName(), is(not(equalTo(expectedFileNames.get(i)))));
        }

        l.sort(new FileDateTimeComparator());

        for (int i = 0; i < l.size(); i++) {
            assertThat(l.get(i).getName(), is(equalTo(expectedFileNames.get(i))));
        }
    }

    /**
     * Throw in some garbage files Expected behaviour is that these garbage files get moved to the
     * back
     */
    @Test
    public void testComparatorWithGarbage() {
        List<File> l = generateValidFiles();

        List<String> expectedFileNames = new ArrayList<String>();

        for (int i = (l.size() - 1); i >= 0; i--) {
            expectedFileNames.add(l.get(i).getName());
        }

        File garbageFile1 = new File("blargh");
        File garbageFile2 = new File("UTC--cats--blah");
        File garbageFile3 = null;

        l.add(5, garbageFile1);
        l.add(6, garbageFile2);
        l.add(7, garbageFile3);

        expectedFileNames.add(garbageFile2.getName());
        expectedFileNames.add(garbageFile1.getName());
        expectedFileNames.add(null);

        l.sort(new FileDateTimeComparator());

        System.out.println(
                "l size: " + l.size() + " expectedFileName size: " + expectedFileNames.size());

        for (int i = 0; i < l.size(); i++) {
            if (l.get(i) != null) {
                String a = l.get(i).getName();
                String b = expectedFileNames.get(i);
                assertThat(a, is(equalTo(b)));
            }
        }
    }
}
