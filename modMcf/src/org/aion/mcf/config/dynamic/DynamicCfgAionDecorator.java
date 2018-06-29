package org.aion.mcf.config.dynamic;

import com.google.common.io.CharSource;
import org.aion.mcf.config.Cfg;
import org.aion.mcf.config.CfgApi;
import org.aion.mcf.config.CfgDb;
import org.aion.mcf.config.CfgGui;
import org.aion.mcf.config.CfgLog;
import org.aion.mcf.config.CfgNet;
import org.aion.mcf.config.CfgReports;
import org.aion.mcf.config.CfgSync;
import org.aion.mcf.config.CfgTx;
import org.aion.mcf.config.ConfigProposalResult;
import org.aion.zero.impl.AionGenesis;
import org.aion.zero.impl.config.CfgAion;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import java.io.FileInputStream;
import java.util.LinkedList;
import java.util.List;

/**
 * Decorates a {@link CfgAion} to provide Dynamic Config functionality.
 *
 * Initially, all the methods inherited from CfgAion will just redirect down to the backing
 * CfgAion that is being decorated.  As we add handlers to dynamically update the kernel, replace
 * these methods with implementations that actually does the dynamic logic.  For methods like
 * {@link #getNet()} we will probably need to create a dynamic version of the config pojo i.e.
 * create a "DynamicCfgNet" for {@link org.aion.mcf.config.CfgNet}.
 *
 */
public class DynamicCfgAionDecorator
extends CfgAion
implements IDynamicConfig, DynamicCfgAionDecoratorMBean {
    private final Cfg activeCfg;
    private final List<ConfigObserver> observers;

    public DynamicCfgAionDecorator(Cfg cfg) {
        this.activeCfg = cfg;
        this.observers = new LinkedList<>();
    }

    @Override
    public Cfg getActiveCfg() {
        return this.activeCfg;
    }

    @Override
    public ConfigProposalResult proposeCfg(String newCfgXml) {
        CfgAion newCfg = new CfgAion();
        try {
            XMLStreamReader xmlStream = XMLInputFactory.newInstance()
                    .createXMLStreamReader(CharSource.wrap(newCfgXml).openStream());
            newCfg.fromXml(xmlStream);
        } catch (Exception e) {
            e.printStackTrace(); //FIXME
            return null;
        }

        System.out.println("Got new proposal.  Cfg.id = " + newCfg.getId());





        for(ConfigObserver co : observers) {
            // notify them somehow
        }

        return new ConfigProposalResult("it's all good!");
    }

    @Override
    public void register(ConfigObserver observer) {
        observers.add(observer);
    }

    @Override
    public void unregister(ConfigObserver observer) {
        observers.remove(observer);
    }

    // -- Methods inherited from CfgAion.  --------------------------------------------------------
    // These are the ones that we will just redirect to the decorated object, until we add support
    // for handling each ones' config update.

    @Override
    public void setId(String _id) {
        getActiveCfg().setId(_id);
    }

    @Override
    public void setNet(CfgNet _net) {
        getActiveCfg().setNet(_net);
    }

    @Override
    public void setApi(CfgApi _api) {
        getActiveCfg().setApi(_api);
    }

    @Override
    public void setDb(CfgDb _db) {
        getActiveCfg().setDb(_db);
    }

    @Override
    public void setLog(CfgLog _log) {
        getActiveCfg().setLog(_log);
    }

    @Override
    public void setTx(CfgTx _tx) {
        getActiveCfg().setTx(_tx);
    }

    @Override
    public String getId() {
        return getActiveCfg().getId();
    }

    @Override
    protected String getMode() {
        // CfgAion#getMode() is protected so can't call it.  The class was final at the
        // time of implementing this, so don't think anything was ever calling it.  If
        // we end up needing this, make CfgAion#getMode public and then uncomment the
        // below line.
        //return getActiveCfg().getMode();
        throw new IllegalStateException("Not implemented.");
    }

    @Override
    public CfgNet getNet() {
        return getActiveCfg().getNet();
    }

    @Override
    public CfgSync getSync() {
        return getActiveCfg().getSync();
    }

    @Override
    public CfgApi getApi() {
        return getActiveCfg().getApi();
    }

    @Override
    public CfgDb getDb() {
        return getActiveCfg().getDb();
    }

    @Override
    public CfgLog getLog() {
        return getActiveCfg().getLog();
    }

    @Override
    public CfgTx getTx() {
        return getActiveCfg().getTx();
    }

    @Override
    public CfgReports getReports() {
        return getActiveCfg().getReports();
    }

    @Override
    public CfgGui getGui() {
        return getActiveCfg().getGui();
    }

    @Override
    public String[] getNodes() {
        return getActiveCfg().getNodes();
    }

    @Override
    public String getBasePath() {
        return getActiveCfg().getBasePath();
    }

    @Override
    public boolean fromXML() {
        return getActiveCfg().fromXML();
    }

    @Override
    public void toXML(String[] args) {
        getActiveCfg().toXML(args);
    }

    @Override
    public void setGenesis() {
        getActiveCfg().setGenesis();
    }

    @Override
    public AionGenesis getGenesis() {
        return (AionGenesis)getActiveCfg().getGenesis();
    }

}
