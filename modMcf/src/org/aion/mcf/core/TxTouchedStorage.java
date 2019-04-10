package org.aion.mcf.core;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.aion.interfaces.functional.Functional;
import org.aion.mcf.vm.types.DataWordImpl;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.collections4.keyvalue.AbstractKeyValue;

public class TxTouchedStorage {

    public static class Entry extends AbstractKeyValue<DataWordImpl, DataWordImpl> {

        private boolean changed;

        public Entry(DataWordImpl key, DataWordImpl value, boolean changed) {
            super(key, value);
            this.changed = changed;
        }

        public Entry() {
            super(null, null);
        }

        @Override
        protected DataWordImpl setKey(DataWordImpl key) {
            return super.setKey(key);
        }

        @Override
        protected DataWordImpl setValue(DataWordImpl value) {
            return super.setValue(value);
        }

        public boolean isChanged() {
            return changed;
        }

        public void setChanged(boolean changed) {
            this.changed = changed;
        }
    }

    private Map<DataWordImpl, Entry> entries = new HashMap<>();

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

    private Entry add(Map.Entry<DataWordImpl, DataWordImpl> entry, boolean changed) {
        return add(new Entry(entry.getKey(), entry.getValue(), changed));
    }

    public void addReading(Map<DataWordImpl, DataWordImpl> entries) {
        if (MapUtils.isEmpty(entries)) return;

        for (Map.Entry<DataWordImpl, DataWordImpl> entry : entries.entrySet()) {
            if (!this.entries.containsKey(entry.getKey())) add(entry, false);
        }
    }

    public void addWriting(Map<DataWordImpl, DataWordImpl> entries) {
        if (MapUtils.isEmpty(entries)) return;

        for (Map.Entry<DataWordImpl, DataWordImpl> entry : entries.entrySet()) {
            add(entry, true);
        }
    }

    private Map<DataWordImpl, DataWordImpl> keyValues(Functional.Function<Entry, Boolean> filter) {
        Map<DataWordImpl, DataWordImpl> result = new HashMap<>();
        for (Entry entry : getEntries()) {
            if (filter == null || filter.apply(entry)) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    public Map<DataWordImpl, DataWordImpl> getChanged() {
        return keyValues(
                new Functional.Function<Entry, Boolean>() {
                    @Override
                    public Boolean apply(Entry entry) {
                        return entry.isChanged();
                    }
                });
    }

    public Map<DataWordImpl, DataWordImpl> getReadOnly() {
        return keyValues(
                new Functional.Function<Entry, Boolean>() {
                    @Override
                    public Boolean apply(Entry entry) {
                        return !entry.isChanged();
                    }
                });
    }

    public Map<DataWordImpl, DataWordImpl> getAll() {
        return keyValues(null);
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
