package edu.ie3.simopsim.simopsimEm;

import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.result.ModelResultEntity;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.simona.api.data.*;
import edu.ie3.simona.api.data.em.ExtEmData;
import edu.ie3.simona.api.data.em.ExtEmDataSimulation;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.data.results.ExtResultDataSimulation;
import edu.ie3.simona.api.data.results.ExtResultPackage;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingCsvSource;
import edu.ie3.simopsim.SimopsimUtils;
import edu.ie3.simopsim.data.OpsimEmDataFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import tech.units.indriya.quantity.Quantities;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class OpsimEmSimulation extends ExtSimulation implements ExtEmDataSimulation, ExtResultDataSimulation {

    private final Logger log = LogManager.getLogger("OpsimEmSimulation");

    private final ExtResultData extResultData;
    private final ExtEmData extEmData;

    private final long deltaT = 900L;

    private final SimonaEmProxy simonaProxy;

    private final ExtEntityMapping mapping;

    public OpsimEmSimulation(
            String urlToOpsim,
            Path mappingPath
    ) {
        this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(mappingPath);
        this.extEmData = new ExtEmData(
                new OpsimEmDataFactory(),
                mapping.getExtIdUuidMapping(ExtEntityEntry.EXT_INPUT)
        );
        this.extResultData = new ExtResultData(
                mapping.getExtUuidIdMapping(ExtEntityEntry.EXT_RESULT_PARTICIPANT),
                mapping.getExtUuidIdMapping(ExtEntityEntry.EXT_RESULT_GRID)
        );
        this.simonaProxy = new SimonaEmProxy();
        SimopsimUtils.runSimopsim(simonaProxy, urlToOpsim);
    }

    @Override
    public List<ExtData> getDataConnections() {
        return List.of(
                extEmData,
                extResultData
        );
    }

    @Override
    protected Long initialize() {
        log.info("+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
        return 0L;
    }

    @Override
    protected Optional<Long> doActivity(long tick) {
        try {
            log.info("+++++ [Phase 1-Activity] Tick = " + tick + ", current simulation time = " + extResultData.getSimulationTime(tick) + " +++++");
            log.info("Wait for new EmData from OpSim...");
            ExtInputDataPackage rawEmData = simonaProxy.dataQueueOpsimToSimona.takeData();
            // send primary data for load1 and load2 to SIMONA
            extEmData.provideEmData(
                    tick,
                    extEmData.createExtEmDataMap(rawEmData)
            );
            log.info("Provided EmData to SIMONA!");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long nextTick = tick + deltaT;
        try {
            log.info("Request Results from SIMONA!");
            Map<String, ModelResultEntity> resultsToBeSend = extResultData.requestResults(tick);
            log.info("Received results from SIMONA! Now convert them and send them to OpSim!");

            simonaProxy.dataQueueSimonaToOpsim.queueData(new ExtResultPackage(tick, resultsToBeSend));
            //log.info("+++++ [Phase 2-Activity] Tick = " + tick + " finished +++++");
            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Optional.of(nextTick);
    }

    /*
    @Override
    protected Optional<Long> doPreActivity(long tick) {
        try {
            log.info("+++++ [Phase 1-Activity] Tick = " + tick + ", current simulation time = " + extResultData.getSimulationTime(tick) + " +++++");
            log.info("Wait for new EmData from OpSim...");
            ExtInputDataPackage rawEmData = simonaProxy.dataQueueOpsimToSimona.takeData();
            // send primary data for load1 and load2 to SIMONA
            extEmData.provideEmData(
                    tick,
                    extEmData.createExtEmDataMap(rawEmData)
            );
            log.info("Provided EmData to SIMONA!");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Optional.of( tick + deltaT);
    }

    @Override
    protected Optional<Long> doPostActivity(long tick) {
        log.info("+++++ [Phase 2-Activity] Tick = " + tick + ", current simulation time = " + extResultData.getSimulationTime(tick) + " +++++");
        try {
            log.info("Request Results from SIMONA!");
            Map<String, ModelResultEntity> resultsToBeSend = extResultData.requestResults(tick);
            log.info("Received results from SIMONA! Now convert them and send them to OpSim!");

            simonaProxy.dataQueueSimonaToOpsim.queueData(new ExtResultPackage(tick, resultsToBeSend));
            long nextTick = tick + deltaT;
            log.info("+++++ [Phase 2-Activity] Tick = " + tick + " finished +++++");
            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
            return Optional.of(nextTick);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

     */
}
