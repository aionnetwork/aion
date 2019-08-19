package org.aion.zero.impl.cli;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import java.util.List;
import junitparams.JUnitParamsRunner;
import junitparams.Parameters;
import org.aion.zero.impl.cli.AccountCli.Composite;
import org.aion.zero.impl.cli.Cli.ReturnType;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(JUnitParamsRunner.class)
public class AccountCliTest {

    @Test
    public void checkOptionsTest() {
        AccountCli accountCli = new AccountCli();
        accountCli.setHelp(true);
        accountCli.checkOptions();

        accountCli = new AccountCli();
        accountCli.setList(true);
        accountCli.checkOptions();

        accountCli = new AccountCli();
        Composite group = new Composite();
        group.setImportAcc("0x");
        accountCli.setGroup(group);
        accountCli.checkOptions();

        accountCli = new AccountCli();
        group = new Composite();
        group.setExport("0x");
        accountCli.setGroup(group);
        accountCli.checkOptions();

        accountCli = new AccountCli();
        group = new Composite();
        group.setCreate(true);
        accountCli.setGroup(group);
        accountCli.checkOptions();

        try{
            accountCli = new AccountCli();
            accountCli.checkOptions();
            fail();
        }catch (IllegalArgumentException e){

        }
        try{
            accountCli = new AccountCli();
            accountCli.setGroup(new Composite());
            accountCli.checkOptions();
            fail();
        }catch (IllegalArgumentException e){

        }

    }

    @Parameters(method = "parametersForReturnType")
    @Test
    public void testReturnType(AccountCli cli, ReturnType returnType) {
        assertThat(cli.runCommand(null)).isEqualTo(returnType);
    }

    public Object parametersForReturnType() {
        AccountCli cli = new AccountCli();
        cli.setHelp(true);
        return List.of(
                new Object[] {new AccountCli(), ReturnType.ERROR}, new Object[] {cli, ReturnType.EXIT});
    }
}
