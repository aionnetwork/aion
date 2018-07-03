package org.aion.mcf.config.dynamic2;

import com.google.common.annotations.VisibleForTesting;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.applier.MiningApplier;
import org.aion.zero.impl.config.CfgAion;
import org.aion.zero.impl.config.CfgConsensusPow;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Representation of a 3-tuple (config_key, getter, applier), where:
 *   - config_key is a String that uniquely identifies a configuration key within config.xml
 *   - getter is a function that reads that config_key out of a given {@link Cfg}
 *   - applier is a {@link IDynamicConfigApplier}, which knows how to apply changes to Aion
 *     kernel when given an old cfg and new cfg.
 *
 * This class essentially gives names (config_key) to configuration keys of {@link Cfg} and
 * ties each to a getter (to retrieve the value from any Cfg object) and an applier (to perform
 * config modifications on the kernel).
 *
 * There is no restriction around how config_key is named, but the recommendation is to use a
 * form like "a.b.c" where a, b, c are based on the XML elements of config.xml (i.e. by the
 * parsing logic of {@link CfgAion}.  For example, the config for whether the kernel is mining is
 * represented by <code>"<aion><consensus><mining>true</mining></consensus></aion>"</code>
 * in XML, so use the config key "aion.consensus.mining".
 *
 * It is important to ensure that the getter returns a type that has a properly-defined equals
 * method.  Apart from this, there is no restriction, but it is generally a good idea for them
 * to return "atomic" types (i.e. primitives or their object equivalents).
 */
public class DynamicConfigKeyRegistry {
    private final Map<String, Function<Cfg,?>> key2Getter; // config_key to getter
    private final Map<String, IDynamicConfigApplier> key2Applier; // config_key to applier

    public DynamicConfigKeyRegistry() {
        this(DEFAULT_GETTERS, DEFAULT_APPLIERS);
    }

    @VisibleForTesting
    public DynamicConfigKeyRegistry(Map<String, Function<Cfg,?>> getters,
                                    Map<String, IDynamicConfigApplier> appliers) {
        this.key2Getter = getters;
        this.key2Applier = appliers;
    }

    public void bind(String key, Function<Cfg,Object> getter, IDynamicConfigApplier applier) {
        key2Getter.put(key, getter);
        key2Applier.put(key, applier);
    }

    public Set<String> getBoundKeys() {
        return key2Applier.keySet(); // should be identical to key2Getter.keySet()
    }

    public Function<Cfg,?> getGetter(String key) {
        return key2Getter.get(key);
    }

    public IDynamicConfigApplier getApplier(String key) {
        return key2Applier.get(key);
    }

    private static Map<String, Function<Cfg,?>> DEFAULT_GETTERS = new HashMap<>() {{
        put("aion.consensus.mining", cfg -> ((CfgConsensusPow)cfg.getConsensus()).getMining());

        // Below are sections where no config keys within are dynamically changeable
        put("aion.api", cfg -> cfg.getApi());
        put("aion.net.id", cfg -> cfg.getNet().getId());
        put("aion.net.p2p", cfg -> cfg.getNet().getP2p());
        put("aion.sync", cfg -> cfg.getSync());
        put("aion.log", cfg -> cfg.getLog());
        put("aion.db", cfg -> cfg.getDb());

    }};

    private static Map<String,IDynamicConfigApplier> DEFAULT_APPLIERS = new HashMap<>() {{
        put("aion.consensus.mining", new MiningApplier());

        // Below are sections where no config keys within are dynamically changeable
        put("aion.api", NotImplementedThrower.INST);
        put("aion.net.id", NotImplementedThrower.INST);
        put("aion.net.p2p", NotImplementedThrower.INST);
        put("aion.sync", NotImplementedThrower.INST);
        put("aion.log", NotImplementedThrower.INST);
        put("aion.db", NotImplementedThrower.INST);
    }};

    /**
     * An applyer that just throws an exception.  Used for sections of the config that don't have
     * implementation for dynamic config changes yet.
     */
    private static class NotImplementedThrower implements IDynamicConfigApplier {
        public static final NotImplementedThrower INST = new NotImplementedThrower();

        @Override
        public InFlightConfigChangeResult apply(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException {
            throw new InFlightConfigChangeNotAllowedException();
        }

        @Override
        public InFlightConfigChangeResult undo(Cfg oldCfg, Cfg newCfg) throws InFlightConfigChangeException {
            throw new InFlightConfigChangeNotAllowedException();
        }
    }
}
