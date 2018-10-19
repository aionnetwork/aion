package org.aion.zero.impl.config.dynamic;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.aion.mcf.config.Cfg;
import org.aion.zero.impl.config.CfgAion;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Representation of a 3-tuple (config_key, getter, applier), where: - config_key is a String that
 * uniquely identifies a configuration key within config.xml - getter is a function that reads that
 * config_key out of a given {@link Cfg} - applier is a {@link IDynamicConfigApplier}, which knows
 * how to apply changes to Aion kernel when given an old cfg and new cfg.
 *
 * <p>This class essentially gives names (config_key) to configuration keys of {@link Cfg} and ties
 * each to a getter (to retrieve the value from any Cfg object) and an applier (to perform config
 * modifications on the kernel).
 *
 * <p>There is no restriction around how config_key is named, but the recommendation is to use a
 * form like "a.b.c" where a, b, c are based on the XML elements of config.xml (i.e. by the parsing
 * logic of {@link CfgAion}. For example, the config for whether the kernel is mining is represented
 * by <code>"<aion><consensus><mining>true</mining></consensus></aion>"</code> in XML, so use the
 * config key "aion.consensus.mining".
 *
 * <p>It is important to ensure that the getter returns a type that has a properly-defined equals
 * method. Apart from this, there is no restriction, but it is generally a good idea for them to
 * return "atomic" types (i.e. primitives or their object equivalents).
 */
public class DynamicConfigKeyRegistry {
    private final Map<String, Pair<Function<Cfg, ?>, Optional<IDynamicConfigApplier>>>
            key2GetterApplier; // config_key -> (getter,applier)

    public DynamicConfigKeyRegistry() {
        this(DEFAULT_GETTERS_APPLIERS);
    }

    @VisibleForTesting
    DynamicConfigKeyRegistry(
            Map<String, Pair<Function<Cfg, ?>, Optional<IDynamicConfigApplier>>>
                    key2GetterApplier) {
        this.key2GetterApplier = key2GetterApplier; // note: not a deep copy
    }

    public void bind(String key, Function<Cfg, ?> getter, IDynamicConfigApplier applier) {
        key2GetterApplier.put(key, new ImmutablePair<>(getter, Optional.of(applier)));
    }

    public Set<String> getBoundKeys() {
        return key2GetterApplier.keySet();
    }

    public Function<Cfg, ?> getGetter(String key) {
        return key2GetterApplier.get(key).getLeft();
    }

    public Optional<IDynamicConfigApplier> getApplier(String key) {
        return key2GetterApplier.get(key).getRight();
    }

    private static Map<String, Pair<Function<Cfg, ?>, Optional<IDynamicConfigApplier>>>
            DEFAULT_GETTERS_APPLIERS =
                    new HashMap<>() {
                        {
                            put(
                                    "aion.consensus",
                                    ImmutablePair.of(cfg -> cfg.getConsensus(), Optional.empty()));
                            // When MiningApplier changes reviewed, delete above line and uncomment
                            // below line.
                            //        put("aion.consensus.mining", ImmutablePair.of(
                            //                cfg ->
                            // ((CfgConsensusPow)cfg.getConsensus()).getMining(),
                            //                new MiningApplier()));

                            // Below are sections where no config keys within are dynamically
                            // changeable
                            put(
                                    "aion.api",
                                    ImmutablePair.of(cfg -> cfg.getApi(), Optional.empty()));
                            put(
                                    "aion.net.id",
                                    ImmutablePair.of(
                                            cfg -> cfg.getNet().getId(), Optional.empty()));
                            put(
                                    "aion.net.p2p",
                                    ImmutablePair.of(
                                            cfg -> cfg.getNet().getP2p(), Optional.empty()));
                            put(
                                    "aion.sync",
                                    ImmutablePair.of(cfg -> cfg.getSync(), Optional.empty()));
                            put(
                                    "aion.log",
                                    ImmutablePair.of(cfg -> cfg.getLog(), Optional.empty()));
                            put("aion.db", ImmutablePair.of(cfg -> cfg.getDb(), Optional.empty()));
                        }
                    };
}
