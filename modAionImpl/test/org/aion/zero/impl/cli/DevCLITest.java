package org.aion.zero.impl.cli;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class DevCLITest {

    private static Long toLong(Object object) {
        return Optional.ofNullable(object).map(Objects::toString).map(Long::parseLong).orElse(null);
    }

    private static Boolean toBool(Object object) {
        return Optional.ofNullable(object)
                .map(Objects::toString)
                .map(Boolean::parseBoolean)
                .orElse(null);
    }

    @Test
    public void testPrintUsage() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(os);
        DevCLI.printUsage(ps, new DevCLI());

        String output = os.toString();
        System.out.println(output);

        assertThat(output).isNotEmpty();
    }

    @Test(expected = RuntimeException.class)
    public void testEmptyComposite() {
        DevCLI.Composite composite = new DevCLI.Composite();
        composite.checkOptions();
    }

    @Test
    public void testCompositeWithValue() {
        final long testNumber = 5;
        final String[] testStringArr = new String[] {"valid", "data"};
        final String testString = "valid";
        DevCLI.Composite composite = new DevCLI.Composite();
        composite.setDumpBlocksParam(testNumber);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setDumpForTestParams(testStringArr);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setDumpStateParam(testNumber);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setDumpStateSizeParam(testNumber);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setHelp(true);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setHelp(false);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setQueryAccountParams(testString);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setQueryBlockParams(testNumber);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setQueryTxParams(testString);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setStopAtParam(testNumber);
        composite.checkOptions();

        composite = new DevCLI.Composite();
        composite.setFullSync(true);
        composite.checkOptions();
    }

    @Parameters(method = "paramatersForTestRunCommand")
    @Test
    public void testRunCommand(Object[] params, Cli.ReturnType expectedReturn) {
        DevCLI.Composite composite = new DevCLI.Composite();
        composite.setStopAtParam(toLong(params[0]));
        composite.setQueryTxParams((String) params[1]);
        composite.setQueryBlockParams(toLong(params[2]));
        composite.setQueryAccountParams((String) params[3]);
        composite.setHelp(toBool(params[4]));
        composite.setDumpStateSizeParam(toLong(params[5]));
        composite.setDumpStateParam(toLong(params[6]));
        composite.setDumpForTestParams((String[]) params[7]);
        composite.setDumpBlocksParam(toLong(params[8]));
        composite.setFullSync(toBool(params[9]));
        DevCLI devCLI = new DevCLI();
        devCLI.setArgs(composite);
        assertThat(devCLI.runCommand()).isEqualTo(expectedReturn);
    }

    public Object[] paramatersForTestRunCommand() {
        Object[] empty = new Object[] {null, null, null, null, null, null, null, null, null, null};
        Object[] help = new Object[] {null, null, null, null, true, null, null, null, null, null};
        return List.of(
                        new Object[] {empty, Cli.ReturnType.ERROR},
                        new Object[] {help, Cli.ReturnType.EXIT})
                .toArray();
    }
}
