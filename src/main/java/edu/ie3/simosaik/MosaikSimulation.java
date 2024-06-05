package edu.ie3.simosaik;

import ch.qos.logback.classic.Logger;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.simona.api.data.ExtData;
import edu.ie3.simona.api.data.ExtDataSimulation;
import edu.ie3.simona.api.data.ExtInputDataPackage;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryData;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.data.results.ExtResultPackage;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingCsvSource;
import edu.ie3.simosaik.data.MosaikPrimaryDataFactory;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MosaikSimulation extends ExtSimulation implements ExtDataSimulation {

    private final ch.qos.logback.classic.Logger log = (Logger) LoggerFactory.getLogger("MosaikSimulation");
    private final ExtPrimaryData extPrimaryData;
    private final ExtResultData extResultData;
    private final long deltaT = 900L;
    private final String mosaikIP;

    private SimonaSimulator simonaSimulator; //extends Simulator in Mosaik

    private boolean startedMosasik = false;

    private final ExtEntityMapping mapping;

    public MosaikSimulation(
            String mosaikIP,
            Path mappingPath
    ) {
        this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(mappingPath);
        this.extPrimaryData = new ExtPrimaryData(
                new MosaikPrimaryDataFactory(),
                mapping.getExtIdUuidMapping(ExtEntityEntry.EXT_INPUT)
        );
        this.extResultData = new ExtResultData(
                mapping.getExtUuidIdMapping(ExtEntityEntry.EXT_RESULT_PARTICIPANT),
                mapping.getExtUuidIdMapping(ExtEntityEntry.EXT_RESULT_GRID)
        );
        this.mosaikIP = mosaikIP;
    }

    @Override
    public List<ExtData> getDataConnections() {
        return List.of(extPrimaryData, extResultData);
    }

    @Override
    protected Long initialize() {
        log.info("+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
        if (!startedMosasik) {
            startedMosasik = true;
            startMosaikSimulation(mosaikIP);
        }
        return 0L;
    }

    @Override
    protected Optional<Long> doPreActivity(long tick) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            log.info("+++++ [Phase 1-Activity] Tick = " + tick + ", current simulation time = " + extResultData.getSimulationTime(tick) + " +++++");
            ExtInputDataPackage rawPrimaryData = simonaSimulator.dataQueueMosaikToSimona.takeData();
            log.debug("Received Primary Data from Mosaik = " + rawPrimaryData);

            extPrimaryData.providePrimaryData(
                    tick,
                    extPrimaryData.createExtPrimaryDataMap(
                            rawPrimaryData
                    )
            );
            log.info("Provided Primary Data to SIMONA");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("+++++ [Phase 1-Activity] Tick = " + tick + " finished +++++");
        return Optional.of( tick + deltaT);
    }

    @Override
    protected Optional<Long> doPostActivity(long tick) {
        log.info("+++++ [Phase 2-Activity] Tick = " + tick + ", current simulation time = " + extResultData.getSimulationTime(tick) + " +++++");
        try {
            log.info("Request Results from SIMONA!");
            Map<String, ResultEntity> resultsToBeSend = extResultData.requestResults(tick);
            log.info("Received results from SIMONA! Now convert them and send them to OpSim!");

            simonaSimulator.dataQueueSimonaToMosaik.queueData(new ExtResultPackage(tick, resultsToBeSend));
            long nextTick = tick + deltaT;
            log.info("+++++ [Phase 2-Activity] Tick = " + tick + " finished +++++");
            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
            return Optional.of(nextTick);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public ExtPrimaryData getExtPrimaryData() {
        return extPrimaryData;
    }

    public ExtResultData getExtResultData() {
        return extResultData;
    }

    public void startMosaikSimulation(String mosaikIP) {
        try {
            this.simonaSimulator = new SimonaSimulator(mapping);
            RunSimosaik simosaikRunner = new RunSimosaik(
                    mosaikIP, simonaSimulator
            );
            new Thread(simosaikRunner, "Simosaik").start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
