package org.aion.mcf.config.dynamic;

import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.ConfigProposalResult;
import org.aion.zero.impl.config.CfgConsensusPow;
import org.aion.zero.impl.config.CfgEnergyStrategy;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class DynamicCfgConsensusPowDecorator
extends CfgConsensusPow
implements IDynamicConfig
{
    CfgConsensusPow cfg;

    DynamicCfgConsensusPowDecorator() {
        super();
    }

    // -- IDynamicConfig interface ----------------------------------------------------------------
    @Override
    public Cfg getActiveCfg() {
        return null;
    }

    @Override
    public ConfigProposalResult proposeCfg(String xml) {
        return null;
    }

    @Override
    public void register(ConfigObserver observer) {

    }

    @Override
    public void unregister(ConfigObserver observer) {

    }


    // -- Decorator redirects ---------------------------------------------------------------------
    @Override
    public void fromXML(XMLStreamReader sr) throws XMLStreamException {
        cfg.fromXML(sr);
    }

    @Override
    public void setExtraData(String _extraData) {
        cfg.setExtraData(_extraData);
    }

    @Override
    public void setMining(boolean value) {
        cfg.setMining(value);
    }

    @Override
    public boolean getMining() {
        return cfg.getMining();
    }

    @Override
    public byte getCpuMineThreads() {
        return cfg.getCpuMineThreads();
    }

    @Override
    public String getExtraData() {
        return cfg.getExtraData();
    }

    @Override
    public String getMinerAddress() {
        return cfg.getMinerAddress();
    }

    @Override
    public CfgEnergyStrategy getEnergyStrategy() {
        return cfg.getEnergyStrategy();
    }

    @Override
    public boolean isSeed() {
        return cfg.isSeed();
    }


}
