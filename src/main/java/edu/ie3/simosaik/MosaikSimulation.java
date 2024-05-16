package edu.ie3.simosaik;

import ch.qos.logback.classic.Logger;
import de.offis.mosaik.api.SimProcess;
import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import edu.ie3.simona.api.data.ExtDataSimulation;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryData;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataSimulation;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.data.results.ExtResultDataSimulation;
import edu.ie3.simona.api.exceptions.ConvertionException;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simosaik.data.MosaikPrimaryDataFactory;
import edu.ie3.simosaik.data.MosaikResultDataFactory;
import edu.ie3.simosaik.data.SimosaikPrimaryDataWrapper;
import edu.ie3.simosaik.data.SimosaikResultWrapper;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MosaikSimulation extends ExtSimulation implements ExtDataSimulation {

    private final ch.qos.logback.classic.Logger log = (Logger) LoggerFactory.getLogger("MosaikSimulation");
    private final ExtPrimaryDataSimulation extPrimaryDataSimulation;
    private final ExtResultDataSimulation extResultDataSimulation;
    private final long deltaT = 900L;
    public ExtPrimaryData extPrimaryData;
    public ExtResultData extResultsData;

    private SimonaSimulator simonaSimulator; //extends Simulator

/*
    private final Map<UUID, String> resultAssetMapping
            = Map.of(
            UUID.fromString("dfae9806-9b44-4995-ba27-d66d8e4a43e0"),"Node_HS_2");
 */
    private final Map<UUID, String> resultAssetMapping
            = Map.of(
            UUID.fromString("9abe950d-362e-4efe-b686-500f84d8f368"),"Node_HS_2");



    private final Map<String, UUID> primaryAssetMapping
            = Map.of(
            "Load_HS_2", UUID.fromString("9c5991bc-24df-496b-b4ce-5ec27657454c"));

    public MosaikSimulation(String mosaikIP) {
        this.extPrimaryDataSimulation = new ExtPrimaryDataSimulation(
                new MosaikPrimaryDataFactory(),
                this.primaryAssetMapping.values().stream().toList()
        );
        this.extResultDataSimulation = new ExtResultDataSimulation(
                new MosaikResultDataFactory(),
                this.resultAssetMapping.keySet().stream().toList()
        );
        runSimosaik(mosaikIP);
    }


    @Override
    protected Long initialize() {
        log.info("+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
        return 0L;
    }

    @Override
    protected Optional<Long> doPreActivity(long tick) {
        try {
            log.info("Current Simulation Time: " + extResultDataSimulation.getExtResultData().getSimulationTime(tick));
            log.info("Look for new PrimaryData from Mosaik...");
            SimosaikPrimaryDataWrapper rawPrimaryData = simonaSimulator.receiveTriggerQueueForPrimaryData.take();
            log.info("Received Primary from OpSim... now convert them to PSDM-Value");

            Map<String, Object> primaryDataFromExt = new HashMap<>();
            rawPrimaryData.dataMap().forEach(
                    (id, dataMapEntry) -> {
                        if (primaryAssetMapping.containsKey(id)) {
                            primaryDataFromExt.put(
                                    primaryAssetMapping.get(id).toString(),
                                    dataMapEntry.get("P[MW]").doubleValue()
                            );
                        }
                    }
            );

            log.info("New Map = " + primaryDataFromExt);

            // send primary data for load1 and load2 to SIMONA
            extPrimaryDataSimulation.getExtPrimaryData().providePrimaryData(tick, primaryDataFromExt);
            log.info("Provide Primary Data to SIMONA");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Optional.of( tick + deltaT);
    }

    @Override
    protected Optional<Long> doPostActivity(long tick) {
        ZonedDateTime currentTime = extResultDataSimulation.getExtResultData().getSimulationTime(tick);

            try {
                Map<String, Object> resultsFromSimona = extResultDataSimulation.getExtResultData().requestResultObjects(tick);
                log.info("Received results from SIMONA!");

                Map<String, ResultEntity> resultsToBeSend = new HashMap<>();

                resultsFromSimona.forEach(
                        (uuid, result) -> {
                            if (result instanceof NodeResult nodeResult) {
                                resultsToBeSend.put(
                                        resultAssetMapping.get(UUID.fromString(uuid)),
                                        nodeResult
                                );
                            } else if (result instanceof SystemParticipantResult systemParticipantResult) {
                                resultsToBeSend.put(
                                        resultAssetMapping.get(UUID.fromString(uuid)),
                                        systemParticipantResult
                                );
                            }
                                else {
                                log.error("Got a wrong result entity from SIMONA!");
                            }
                        }
                );
                log.info("Send converted results to SIMOSAIK!");
                simonaSimulator.queueResultsFromSimona(new SimosaikResultWrapper(currentTime, resultsToBeSend));
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } catch (ConvertionException e) {
                throw new RuntimeException(e);
            }

        long nextTick = tick + deltaT;

        log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
        return Optional.of(nextTick);
    }


    public ExtPrimaryDataSimulation getExtPrimaryDataSimulation() {
        return extPrimaryDataSimulation;
    }

    public ExtResultDataSimulation getExtResultDataSimulation() {
        return extResultDataSimulation;
    }

    public void runSimosaik(String mosaikIP) {
        try {
            this.simonaSimulator = new SimonaSimulator();
            SimProcess.startSimulation(new String[]{mosaikIP}, simonaSimulator);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
