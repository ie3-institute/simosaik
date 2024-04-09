package edu.ie3.simopsim;

import de.fhg.iee.opsim.DAO.AssetComparator;
import de.fhg.iee.opsim.DAO.ProxyConfigDAO;
import de.fhg.iee.opsim.abstracts.ConservativeSynchronizedProxy;
import de.fhg.iee.opsim.interfaces.ClientInterface;
import de.fhg.iwes.opsim.datamodel.dao.OpSimDataModelFileDao;
import de.fhg.iwes.opsim.datamodel.generated.asset.Asset;
import de.fhg.iwes.opsim.datamodel.generated.assetoperator.AssetOperator;
import de.fhg.iwes.opsim.datamodel.generated.flexforecast.OpSimFlexibilityElement;
import de.fhg.iwes.opsim.datamodel.generated.flexforecast.OpSimFlexibilityForecastMessage;
import de.fhg.iwes.opsim.datamodel.generated.realtimedata.*;
import de.fhg.iwes.opsim.datamodel.generated.scenarioconfig.ScenarioConfig;
import edu.ie3.simopsim.data.SimopsimPrimaryDataWrapper;
import edu.ie3.simopsim.data.SimopsimResultWrapper;
import org.apache.logging.log4j.Logger;
import org.joda.time.DateTime;

import javax.xml.bind.JAXBException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class SimonaProxy extends ConservativeSynchronizedProxy {
    private ClientInterface cli;
    private String componentDescription = "SIMONA";
    private Logger logger;
    private long delta = -1L;
    private long lastTimeStep = 0L;
    private Set<Asset> readable = new TreeSet(new AssetComparator());
    private Set<Asset> writable = new TreeSet(new AssetComparator());

    public final LinkedBlockingQueue<SimopsimPrimaryDataWrapper> receiveTriggerQueueForPrimaryData = new LinkedBlockingQueue();
    public final LinkedBlockingQueue<SimopsimResultWrapper> receiveTriggerQueueForResults = new LinkedBlockingQueue();

    public SimonaProxy(
            ClientInterface client, Logger logger
    ) {
        this.logger = logger;
        this.cli = client;
    }

    @Override
    public boolean initProxy(ProxyConfigDAO config) {
        logger.info(
                "Proxy {} is initialized!",
                componentDescription
        );
        this.setNrOfComponents(config.getNrOfComponents());
        return true;
    }

    @Override
    public void SetUp(String componentDescription, ClientInterface client, Logger logger) {
        this.logger = logger;
        this.cli = client;
        this.componentDescription = componentDescription;
    }

    @Override
    public boolean initComponent(String componentConfig) {
        if (componentConfig != null && !componentConfig.isEmpty()) {
            try {
                OpSimDataModelFileDao opsFile = new OpSimDataModelFileDao();
                ScenarioConfig scenarioConfig = opsFile.read(componentConfig);
                Iterator var4 = scenarioConfig.getAssetOperator().iterator();

                while(var4.hasNext()) {
                    AssetOperator ao = (AssetOperator)var4.next();
                    if (ao.getAssetOperatorName().equals(this.getComponentName())) {
                        this.readable.addAll(ao.getReadableAssets());
                        this.writable.addAll(ao.getControlledAssets());
                        this.delta = ao.getOperationInterval();
                    }
                }

                this.logger.info("Component {}, got Readables: {}, Writables: {} and Delta: {}", new Object[]{this.componentDescription, this.readable.size(), this.writable.size(), this.delta});
                return true;
            } catch (JAXBException var6) {
                this.logger.error("Problem with the Config Data not right format and or incomplete. ", var6);
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public Queue<OpSimMessage> step(Queue<OpSimMessage> inputFromClient, long timeStep) {
        logger.info(
                componentDescription + " step call at simulation time = " + cli.getClock().getActualTime().getMillis() + " present timezone = " + cli.getCurrentSimulationTime());
        if (timeStep < this.lastTimeStep + this.delta && timeStep != this.lastTimeStep) {
            return null;
        } else {
            // Get message from Netzbetriebsfuehrung
            this.lastTimeStep = timeStep;
            Iterator var4 = inputFromClient.iterator();
            logger.info("Received messages for " + this.cli.getCurrentSimulationTime().toString());

            SimopsimPrimaryDataWrapper primaryDataForSimona = new SimopsimPrimaryDataWrapper();

            while(var4.hasNext()) {
                OpSimMessage osm = (OpSimMessage) var4.next();
                if (osm instanceof OpSimScheduleMessage ossm) {
                    primaryDataForSimona.ossm().put(ossm.getAssetId(), ossm);
                }
                this.printMessage(osm);
            }

            try {
                receiveTriggerQueueForPrimaryData.put(primaryDataForSimona);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            // Trigger OpSimSimulator to provide result edu.ie3.simopsim.data

            try {
                // Wait for results from SIMONA!
                SimopsimResultWrapper results = receiveTriggerQueueForResults.take();

                List<OpSimAggregatedSetPoints> osmAggSetPoints = new ArrayList<>(Collections.emptyList());
                logger.debug("Send Aggregated SetPoints for " + this.cli.getCurrentSimulationTime().toString());
                writable.forEach(
                    asset -> {
                        List<OpSimSetPoint> osmSetPoints = new ArrayList<>(Collections.emptyList());
                        for (MeasurementValueType valueType : asset.getMeasurableQuantities()) {
                            osmSetPoints.add(
                                    new OpSimSetPoint(
                                            results.getActivePower(asset.getGridAssetId()),
                                            SetPointValueType.fromValue(valueType.value())
                                    )
                            );
                        }
                        osmAggSetPoints.add(
                                new OpSimAggregatedSetPoints(
                                        asset.getGridAssetId(),
                                        cli.getClock().getActualTime().plus(delta).getMillis(),
                                        osmSetPoints
                                )
                        );
                    }
                );
                osmAggSetPoints.forEach(this::printMessage);
                sendToOpSim(osmAggSetPoints);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return inputFromClient;
        }
    }

    @Override
    public String getComponentName() {
        return componentDescription;
    }

    @Override
    public void stop() {
        logger.info("stop() received.");
    }


    private void printMessage(OpSimMessage osm) {
        StringBuilder strb = new StringBuilder();
        String topic = osm.getAssetId();
        DateTime dt = new DateTime(osm.getDelta());
        strb.append(this.cli.getCurrentSimulationTime().toString());
        strb.append("|");
        strb.append(dt.toString());
        strb.append(" ");
        strb.append(topic);
        strb.append(";");
        Iterator var6;
        if (osm instanceof OpSimAggregatedMeasurements) {
            OpSimAggregatedMeasurements osmms = (OpSimAggregatedMeasurements)osm;
            var6 = osmms.getOpSimMeasurements().iterator();

            while(var6.hasNext()) {
                OpSimMeasurement osmm = (OpSimMeasurement)var6.next();
                strb.append(osmm.getMeasurementType());
                strb.append(";");
                strb.append(osmm.getMeasurementValue());
                strb.append(";");
            }
        } else if (osm instanceof OpSimAggregatedSetPoints) {
            OpSimAggregatedSetPoints ossp = (OpSimAggregatedSetPoints)osm;
            var6 = ossp.getOpSimSetPoints().iterator();

            while(var6.hasNext()) {
                OpSimSetPoint osp = (OpSimSetPoint)var6.next();
                strb.append(osp.getSetPointValueType());
                strb.append(";");
                strb.append(osp.getSetPointValue());
                strb.append(";");
            }
        } else if (osm instanceof OpSimFlexibilityForecastMessage) {
            OpSimFlexibilityForecastMessage off = (OpSimFlexibilityForecastMessage)osm;
            var6 = off.getForecastMessages().iterator();

            while(var6.hasNext()) {
                OpSimFlexibilityElement ofe = (OpSimFlexibilityElement)var6.next();
                strb.append(ofe.getLeadTimeInUTC());
                strb.append(";");
                strb.append(ofe.getType());
                strb.append(";");
                strb.append(ofe.getMax());
                strb.append(";");
                strb.append(ofe.getMin());
                strb.append(";");
            }
        } else if (osm instanceof OpSimScheduleMessage) {
            OpSimScheduleMessage osme = (OpSimScheduleMessage)osm;
            var6 = osme.getScheduleElements().iterator();

            while(var6.hasNext()) {
                OpSimScheduleElement ose = (OpSimScheduleElement)var6.next();
                strb.append(ose.getScheduleTimeInUTC());
                strb.append(";");
                strb.append(ose.getScheduledValueType());
                strb.append(";");
                strb.append(ose.getScheduledValue());
                strb.append(";");
            }
        }

        System.out.println(strb.toString());
    }

    public void queueResultsFromSimona(SimopsimResultWrapper data) throws InterruptedException {
        this.receiveTriggerQueueForResults.put(data);
    }

    public void queuePrimaryDataFromOpsim(SimopsimPrimaryDataWrapper data) throws InterruptedException {
        this.receiveTriggerQueueForPrimaryData.put(data);
    }


    private <T extends OpSimMessage> void sendToOpSim(
            List<T> inputFromComponent
    ) {
        if (inputFromComponent.isEmpty()) {
            logger.info("The component has not generated output to send.");
        } else {
            for (OpSimMessage msg : inputFromComponent) {
                cli.pushToMq(cli.getProxy(), msg);
            }
            logger.info("Results sent: {}", cli.getClock().getActualTime().toDateTimeISO());
        }
    }
}
