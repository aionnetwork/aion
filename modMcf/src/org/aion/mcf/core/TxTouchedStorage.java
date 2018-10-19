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
package org.aion.mcf.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.aion.base.util.Functional;
import org.aion.mcf.vm.types.DataWord;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.keyvalue.AbstractKeyValue;

public class TxTouchedStorage {

    public static class Entry extends AbstractKeyValue<DataWord, DataWord> {

        private boolean changed;

        public Entry(DataWord key, DataWord value, boolean changed) {
            super(key, value);
            this.changed = changed;
        }

        public Entry() {
            super(null, null);
        }

        @Override
        protected DataWord setKey(DataWord key) {
            return super.setKey(key);
        }

        @Override
        protected DataWord setValue(DataWord value) {
            return super.setValue(value);
        }

        public boolean isChanged() {
            return changed;
        }

        public void setChanged(boolean changed) {
            this.changed = changed;
        }
    }

    private Map<DataWord, Entry> entries = new HashMap<>();

    public TxTouchedStorage() {}

    public TxTouchedStorage(Collection<Entry> entries) {
        for (Entry entry : entries) {
            add(entry);
        }
    }

    public Collection<Entry> getEntries() {
        return entries.values();
    }

    public Entry add(Entry entry) {
        return entries.put(entry.getKey(), entry);
    }

    private Entry add(Map.Entry<DataWord, DataWord> entry, boolean changed) {
        return add(new Entry(entry.getKey(), entry.getValue(), changed));
    }

    public void addReading(Map<DataWord, DataWord> entries) {
        if (MapUtils.isEmpty(entries)) return;

        for (Map.Entry<DataWord, DataWord> entry : entries.entrySet()) {
            if (!this.entries.containsKey(entry.getKey())) add(entry, false);
        }
    }

    public void addWriting(Map<DataWord, DataWord> entries) {
        if (MapUtils.isEmpty(entries)) return;

        for (Map.Entry<DataWord, DataWord> entry : entries.entrySet()) {
            add(entry, true);
        }
    }

    private Map<DataWord, DataWord> keyValues(Functional.Function<Entry, Boolean> filter) {
        Map<DataWord, DataWord> result = new HashMap<>();
        for (Entry entry : getEntries()) {
            if (filter == null || filter.apply(entry)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public Map<DataWord, DataWord> getChanged() {
        return keyValues(
                new Functional.Function<Entry, Boolean>() {
                    @Override
                    public Boolean apply(Entry entry) {
                        return entry.isChanged();
                    }
                });
    }

    public Map<DataWord, DataWord> getReadOnly() {
        return keyValues(
                new Functional.Function<Entry, Boolean>() {
                    @Override
                    public Boolean apply(Entry entry) {
                        return !entry.isChanged();
                    }
                });
    }

    public Map<DataWord, DataWord> getAll() {
        return keyValues(null);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
