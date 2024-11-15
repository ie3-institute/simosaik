package edu.ie3.simosaik.simosaikElectrolyzer;

import ch.qos.logback.classic.Logger;
import edu.ie3.datamodel.models.result.ModelResultEntity;
import edu.ie3.simona.api.data.ExtData;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryData;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.data.results.ExtResultContainer;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simona.api.simulation.mapping.ExtEntityEntry;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMappingCsvSource;
import edu.ie3.simosaik.SimosaikUtils;
import edu.ie3.simosaik.data.MosaikPrimaryDataFactory;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MosaikElectrolyzerSimulation extends ExtSimulation {

    private final ch.qos.logback.classic.Logger log = (Logger) LoggerFactory.getLogger("MosaikSimulation");
    private final ExtPrimaryData extPrimaryData;
    private final ExtResultData extResultData;
    private final long deltaT = 900L;
    private final String mosaikIP;

    private SimonaElectrolyzerSimulator simonaElectrolyzerSimulator; //extends Simulator in Mosaik

    private boolean startedMosasik = false;

    private final ExtEntityMapping mapping;

    public MosaikElectrolyzerSimulation(
            String mosaikIP,
            Path mappingPath
    ) {
        this.mapping = ExtEntityMappingCsvSource.createExtEntityMapping(mappingPath);
        this.extPrimaryData = new ExtPrimaryData(
                new MosaikPrimaryDataFactory(),
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
        if (!startedMosasik) {
            startedMosasik = true;
            this.simonaElectrolyzerSimulator = new SimonaElectrolyzerSimulator();
            simonaElectrolyzerSimulator.setMapping(mapping);
            SimosaikUtils.startMosaikSimulation(simonaElectrolyzerSimulator, mosaikIP);
        }
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
            ExtInputDataContainer rawPrimaryData = simonaElectrolyzerSimulator.dataQueueMosaikToSimona.takeData();
            log.debug("Received Primary Data from Mosaik = " + rawPrimaryData);

            extPrimaryData.providePrimaryData(
                    tick,
                    extPrimaryData.createExtPrimaryDataMap(
                            rawPrimaryData
                    ),
                    rawPrimaryData.getMaybeNextTick()
            );
            log.info("Provided Primary Data to SIMONA");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        long nextTick = tick + deltaT;
         try {
            log.info("Request Results from SIMONA!");
            Map<String, ModelResultEntity> resultsToBeSend = extResultData.requestResults(tick);
            log.info("Received results from SIMONA! Now convert them and send them to Mosaik!");

            simonaElectrolyzerSimulator.dataQueueSimonaToMosaik.queueData(new ExtResultContainer(tick, resultsToBeSend));
            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Optional.of(nextTick);
    }
}
