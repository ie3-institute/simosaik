package edu.ie3.simosaik.simosaikOptimizer;

import ch.qos.logback.classic.Logger;
import edu.ie3.simona.api.data.ExtData;
import edu.ie3.simona.api.data.em.ExtEmData;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.simulation.ExtCoSimulation;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingCsvSource;
import edu.ie3.simosaik.SimosaikUtils;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class MosaikOptimizerSimulation extends ExtCoSimulation {

    private final Logger log = (Logger) LoggerFactory.getLogger("MosaikSimulation");
    private final ExtEmData extEmData;
    private final ExtResultData extResultData;
    private final long deltaT = 900L;
    private final String mosaikIP;

    private SimonaOptimizerSimulator simonaOptimizerSimulator; //extends Simulator in Mosaik

    private final ExtEntityMapping mapping;

    public MosaikOptimizerSimulation(
            String mosaikIP,
            Path mappingPath
    ) {
        super("MosaikOptimizerSimulation", "mosaik");
        this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(mappingPath);
        this.extEmData = new ExtEmData(
                mapping.getExtId2UuidMapping(ExtEntityEntry.EXT_INPUT)
        );
        this.extResultData = new ExtResultData(
                mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_PARTICIPANT),
                mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_GRID),
                mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_FLEX_OPTIONS)
        );
        this.mosaikIP = mosaikIP;
    }


    @Override
    public List<ExtData> getDataConnections() {
        return List.of(extEmData, extResultData);
    }

    @Override
    protected Long initialize() {
        log.info("+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
        this.simonaOptimizerSimulator = new SimonaOptimizerSimulator();
        simonaOptimizerSimulator.setConnectionToSimonaApi(mapping, dataQueueExtCoSimulatorToSimonaApi, dataQueueSimonaApiToExtCoSimulator);
        SimosaikUtils.startMosaikSimulation(simonaOptimizerSimulator, mosaikIP, log);
        log.info("+++++++++++++++++++++++++++ initialization of the external simulation completed +++++++++++++++++++++++++++");
        return 0L;
    }

    @Override
    protected Optional<Long> doActivity(long tick) {
        log.info("+++++++++++++++++++++++++++ Activities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long nextTick = tick + deltaT;
        try {
            sendFlexOptionResultsToExtCoSimulator(
                    extResultData,
                    tick,
                    Optional.of(nextTick),
                    log
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            sendEmDataToSimona(
                    extEmData,
                    tick,
                    nextTick,
                    log
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        try {
            sendResultsToExtCoSimulator(
                    extResultData,
                    tick,
                    Optional.of(nextTick),
                    log
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("+++++++++++++++++++++++++++ Activities in External simulation: Tick {} completed. +++++++++++++++++++++++++++\n", tick);
        return Optional.of(nextTick);
    }
}
