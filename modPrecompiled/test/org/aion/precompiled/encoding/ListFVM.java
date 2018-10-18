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

package org.aion.precompiled.encoding;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.aion.precompiled.PrecompiledUtilities;

public class ListFVM extends BaseTypeFVM {

    List<BaseTypeFVM> params;

    public ListFVM() {
        this.params = new ArrayList<>();
    }

    public ListFVM(@Nonnull final BaseTypeFVM... params) {
        this.params = new ArrayList<>(Arrays.asList(params));
    }

    public void add(BaseTypeFVM param) {
        this.params.add(param);
    }

    @Override
    public byte[] serialize() {
        ByteBuffer bb = ByteBuffer.allocate(params.size() * params.get(0).serialize().length + 16);
        int elementLength = params.size();
        bb.put(PrecompiledUtilities.pad(BigInteger.valueOf(elementLength).toByteArray(), 16));

        for (BaseTypeFVM p : params) {
            bb.put(p.serialize());
        }
        return bb.array();
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public Optional<List<BaseTypeFVM>> getEntries() {
        return Optional.of(this.params);
    }
}
