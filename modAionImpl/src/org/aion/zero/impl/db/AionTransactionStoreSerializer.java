/*******************************************************************************
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
 *     
 ******************************************************************************/

package org.aion.zero.impl.db;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.aion.mcf.ds.Serializer;
import org.aion.rlp.RLP;
import org.aion.rlp.RLPList;
import org.aion.zero.impl.types.AionTxInfo;

public class AionTransactionStoreSerializer {
    public final static Serializer<List<AionTxInfo>, byte[]> serializer = new Serializer<List<AionTxInfo>, byte[]>() {
        @Override
        public byte[] serialize(List<AionTxInfo> object) {
            byte[][] txsRlp = new byte[object.size()][];
            for (int i = 0; i < txsRlp.length; i++) {
                txsRlp[i] = object.get(i).getEncoded();
            }
            return RLP.encodeList(txsRlp);
        }

        @Override
        public List<AionTxInfo> deserialize(byte[] stream) {
            try {
                RLPList params = RLP.decode2(stream);
                RLPList infoList = (RLPList) params.get(0);
                List<AionTxInfo> ret = new ArrayList<>();
                for (int i = 0; i < infoList.size(); i++) {
                    ret.add(new AionTxInfo(infoList.get(i).getRLPData()));
                }
                return ret;
            } catch (Exception e) {
                // fallback to previous DB version
                e.printStackTrace();
                return Collections.singletonList(new AionTxInfo(stream));
            }
        }
    };
}
