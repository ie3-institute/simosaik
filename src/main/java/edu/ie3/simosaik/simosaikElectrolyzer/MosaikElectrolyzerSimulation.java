package edu.ie3.simosaik.simosaikElectrolyzer;

import ch.qos.logback.classic.Logger;
import edu.ie3.simona.api.data.ExtData;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryData;
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

public class MosaikElectrolyzerSimulation extends ExtCoSimulation {

    private final Logger log = (Logger) LoggerFactory.getLogger("MosaikSimulation");
    private final ExtPrimaryData extPrimaryData;
    private final ExtResultData extResultData;
    private final long deltaT = 900L;
    private final String mosaikIP;

    private SimonaElectrolyzerSimulator simonaElectrolyzerSimulator; //extends Simulator in Mosaik

    private final ExtEntityMapping mapping;

    public MosaikElectrolyzerSimulation(
            String mosaikIP,
            Path mappingPath
    ) {
        super("MosaikElectrolyzerSimulation", "mosaik");
        this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(mappingPath);
        this.extPrimaryData = new ExtPrimaryData(
                mapping.getExtId2UuidMapping(ExtEntityEntry.EXT_INPUT)
        );
        this.extResultData = new ExtResultData(
                mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_PARTICIPANT),
                mapping.getExtUuid2IdMapping(ExtEntityEntry.EXT_RESULT_GRID)
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
        this.simonaElectrolyzerSimulator = new SimonaElectrolyzerSimulator();
        simonaElectrolyzerSimulator.setConnectionToSimonaApi(mapping, dataQueueExtCoSimulatorToSimonaApi, dataQueueSimonaApiToExtCoSimulator);
        SimosaikUtils.startMosaikSimulation(simonaElectrolyzerSimulator, mosaikIP);
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
        try {
            sendPrimaryDataToSimona(
                    extPrimaryData,
                    tick,
                    log
            );
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long nextTick = tick + deltaT;
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
        return Optional.of(nextTick);
    }
}
