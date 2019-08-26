// == Rpc.java == 
package org.aion.api.server.rpc2.autogen;
import org.aion.api.server.rpc2.autogen.pod.*;
import org.aion.api.server.rpc2.autogen.errors.*;

/******************************************************************************
*
* AUTO-GENERATED SOURCE FILE.  DO NOT EDIT MANUALLY -- YOUR CHANGES WILL
* BE WIPED OUT WHEN THIS FILE GETS RE-GENERATED OR UPDATED.
*
*****************************************************************************/
public interface Rpc {

    byte[] getseed(
    );

    byte[] submitseed(
        byte[] var0, 
        byte[] var1, 
        byte[] var2
    );

    boolean submitsignature(
        byte[] var0, 
        byte[] var1
    );

}
