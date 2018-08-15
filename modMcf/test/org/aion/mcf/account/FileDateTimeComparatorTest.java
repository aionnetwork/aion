/*
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
 * Contributors:
 *     Aion foundation.
 */
package org.aion.mcf.account;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;

public class FileDateTimeComparatorTest {
    // note: all invalid names are equal, invalid > valid
    private static final FileDateTimeComparator COMPARE = new FileDateTimeComparator();
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

    private File generateFile(int timeOffset, String timeZoneSymbol){
        long time = System.currentTimeMillis();
        TimeZone tz = TimeZone.getTimeZone("UTC");
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        df.setTimeZone(tz);
        String date = df.format(new Date(time - timeOffset));
        String fileName = timeZoneSymbol + "--" + date + "--" + "blah";
        return new File(fileName);
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

    @Test
    public void testComparatorWithGarbage2() {
        List<File> l = generateValidFiles();

        List<String> expectedFileNames = new ArrayList<String>();

        for (int i = (l.size() - 1); i >= 0; i--) {
            expectedFileNames.add(l.get(i).getName());
        }

        File garbageFile1 = new File("blargh");
        File garbageFile2 = new File("UTC--cats--blah");
        File garbageFile3 = null;
        File garbageFile4 = generateFile(2000, "EST"); //include diff time zone
        File garbageFile5 = generateFile(2000, "PST");

        l.add(3, garbageFile5);
        l.add(4, garbageFile4);
        l.add(5, garbageFile1);
        l.add(6, garbageFile2);
        l.add(7, garbageFile3);

        expectedFileNames.add(garbageFile2.getName());
        expectedFileNames.add(garbageFile5.getName());
        expectedFileNames.add(garbageFile4.getName());
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
                //System.out.println("Expected: " + b + "     Generated: " + a);
            }
        }
    }

    @Test
    public void testInvalidNameInputLength(){
        File invalidLength = new File("part1--part2");
        File validLength = new File("part1--part2--part3");

        // both valid
        assertEquals(0, COMPARE.compare(invalidLength, invalidLength));

        // first file name does not have enough components
        assertEquals(1, COMPARE.compare(invalidLength, validLength));

        // second file name does not have enough components
        assertEquals(-1, COMPARE.compare(validLength, invalidLength));
    }

    @Test
    public void testInvalidNameDifferentTimeZone(){
        File invalidTZ = generateFile(0, "EST");
        File validTZ= generateFile(4000, "UTC");

        // both valid
        assertEquals(0, COMPARE.compare(invalidTZ, invalidTZ));

        // first file name is invalid
        assertEquals(1, COMPARE.compare(invalidTZ, validTZ));

        // second file name is invalid
        assertEquals(-1, COMPARE.compare(validTZ, invalidTZ));
    }

    @Test
    public void testInvalidNameTimeFormat() {
        File invalidFormat = new File("UTC--invalid format--blah");
        File validFormat = generateFile(0,"UTC");

        // try both files with invalid time format
        assertEquals(0, COMPARE.compare(invalidFormat,invalidFormat));

        // try file1 with invalid time format
        assertEquals(1, COMPARE.compare(invalidFormat,validFormat));

        // try file2 with invalid time format
        assertEquals(-1, COMPARE.compare(validFormat,invalidFormat));
    }

    @Test
    public void testNullInput(){
        File testFile = new File("test");

        // both are null
        assertEquals(0, COMPARE.compare(null, null));

        // first file is null
        assertEquals(1, COMPARE.compare(null, testFile));

        // second file is null
        assertEquals(-1, COMPARE.compare(testFile, null));
    }

    @Test
    public void testExactlySameFileName(){
        File file = generateFile(0, "UTC");

        // check
        assertEquals(0, COMPARE.compare(file,file));
    }

    @Test
    public void testComparator2(){
        // create some files
        File valid1 = generateFile(0, "UTC");
        File valid2 = generateFile(-4000, "UTC");
        File valid3 = generateFile(-8000, "UTC");

        File invalid1 = new File("UTC--blah--blah");
        File invalid2 = generateFile(-8000, "PST");
        File invalid3 = new File("part1--part2");
        File invalid4 = null;

        // put files into sorted order
        List<String> expectedFileNames = new ArrayList<>();
        expectedFileNames.add(valid1.getName());
        expectedFileNames.add(valid2.getName());
        expectedFileNames.add(valid3.getName());
        expectedFileNames.add(invalid1.getName());
        expectedFileNames.add(invalid2.getName());
        expectedFileNames.add(invalid3.getName());
        expectedFileNames.add(null);

        // compare files with unsorted order
        // you can change orders of the code below and see the result
        // but make sure all 7 files are still all added to the list, and only once each
        List<File> generatedFiles = new ArrayList<>();
        generatedFiles.add(invalid3);
        generatedFiles.add(valid2);
        generatedFiles.add(valid3);
        generatedFiles.add(invalid4);
        generatedFiles.add(invalid1);
        generatedFiles.add(valid1);
        generatedFiles.add(invalid2);


        // sort
        generatedFiles.sort(COMPARE);

        // check that the sort is correct
        System.out.println(
                "generatedFiles size: " + generatedFiles.size() + " expectedFileName size: " + expectedFileNames.size());

        for (int i = 0; i < generatedFiles.size(); i++) {
            if (generatedFiles.get(i) != null) { // avoid null pointer exception
                String a = generatedFiles.get(i).getName();
                String b = expectedFileNames.get(i);
                assertThat(a, is(equalTo(b)));
               // System.out.println("Expected: " + b + "     Generated: " + a);
            }
        }
    }

    @Test
    public void testInvalidTimestamp(){
        // test that invalid timestamps are handled

        // create valid file name
        File validFile = generateFile(0, "UTC");
        // invalid date, s: 100
        File invalid1 = new File("UTC--2018-08-15T14:59:100.364SS--bbb");
        // invalid date, month: 100
        File invalid2 = new File("UTC--2018-100-15T14:59:35.364Z--bbb");
        // invalid date, min: 100
        File invalid3 = new File("UTC--2018-08-15T14:100:35.364Z--bbb");

        List<String> expectedFileNames = new ArrayList<>();
        expectedFileNames.add(validFile.getName());
        expectedFileNames.add(invalid1.getName());
        expectedFileNames.add(invalid2.getName());
        expectedFileNames.add(invalid3.getName());

        // put in random order
        List<File> generatedFiles = new ArrayList<>();
        generatedFiles.add(invalid1);
        generatedFiles.add(invalid2);
        generatedFiles.add(invalid3);
        generatedFiles.add(validFile);

        // sort
        generatedFiles.sort(COMPARE);

        // check that the sort is correct
        System.out.println(
                "generatedFiles size: " + generatedFiles.size() + " expectedFileName size: " + expectedFileNames.size());

        for (int i = 0; i < generatedFiles.size(); i++) {
            if (generatedFiles.get(i) != null) { // avoid null pointer exception
                String a = generatedFiles.get(i).getName();
                String b = expectedFileNames.get(i);
                assertThat(a, is(equalTo(b)));
                //System.out.println("Expected: " + b + "     Generated: " + a);
            }
        }
    }
}
