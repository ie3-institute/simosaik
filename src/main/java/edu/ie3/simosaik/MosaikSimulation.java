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
import java.util.*;

public class MosaikSimulation extends ExtSimulation implements ExtDataSimulation {

    private final ch.qos.logback.classic.Logger log = (Logger) LoggerFactory.getLogger("MosaikSimulation");
    private final ExtPrimaryDataSimulation extPrimaryDataSimulation;
    private final ExtResultDataSimulation extResultDataSimulation;
    private final long deltaT = 900L;
    public ExtPrimaryData extPrimaryData;
    public ExtResultData extResultsData;

    private String mosaikIP;

    private SimonaSimulator simonaSimulator; //extends Simulator

    private boolean startedMosasik = false;


    private final Map<UUID, String> gridResultAssetMapping
            = Map.of(
            UUID.fromString("dfae9806-9b44-4995-ba27-d66d8e4a43e0"),"Node_HS_2",
            UUID.fromString("33f29587-f63e-45b7-960b-037bda37a3cb"),"Node_HS_3"
            );

    private final Map<UUID, String> participantResultAssetMapping
            = Collections.emptyMap();

    private final Map<String, UUID> primaryAssetMapping
            = Map.of(
            "Load_HS_2", UUID.fromString("9c5991bc-24df-496b-b4ce-5ec27657454c"),
            "Load_HS_3", UUID.fromString("58b9f934-f7c4-4335-9894-3c80d9e6b852")
    );

    public MosaikSimulation(String mosaikIP) {
        this.extPrimaryDataSimulation = new ExtPrimaryDataSimulation(
                new MosaikPrimaryDataFactory(),
                this.primaryAssetMapping.values().stream().toList()
        );
        this.extResultDataSimulation = new ExtResultDataSimulation(
                new MosaikResultDataFactory(),
                this.participantResultAssetMapping.keySet().stream().toList(),
                this.gridResultAssetMapping.keySet().stream().toList()
        );
        this.mosaikIP = mosaikIP;
    }


    @Override
    protected Long initialize() {
        log.info("Main args handed over to external simulation: {}", Arrays.toString(getMainArgs()));
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
            log.info("+++++ [Phase 1-Activity] Tick = " + tick + ", current simulation time = " + extResultDataSimulation.getExtResultData().getSimulationTime(tick) + " +++++");
            SimosaikPrimaryDataWrapper rawPrimaryData = simonaSimulator.receiveTriggerQueueForPrimaryData.take();
            log.debug("Received Primary Data from Mosaik = " + rawPrimaryData);

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

            log.debug("New Map = " + primaryDataFromExt);

            // send primary data for load1 and load2 to SIMONA
            extPrimaryDataSimulation.getExtPrimaryData().providePrimaryData(tick, primaryDataFromExt);
            log.info("Provided Primary Data to SIMONA");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("+++++ [Phase 1-Activity] Tick = " + tick + " finished +++++");
        return Optional.of( tick + deltaT);
    }

    @Override
    protected Optional<Long> doPostActivity(long tick) {
        log.info("+++++ [Phase 2-Activity] Tick = " + tick + ", current simulation time = " + extResultDataSimulation.getExtResultData().getSimulationTime(tick) + " +++++");
        ZonedDateTime currentTime = extResultDataSimulation.getExtResultData().getSimulationTime(tick);
        try {
            log.info("Request results from SIMONA!");
            Map<String, Object> resultsFromSimona = extResultDataSimulation.getExtResultData().requestResultObjects(tick);
            log.debug("Received results from SIMONA! resultsFromSimona = " + resultsFromSimona);
            log.info("Received results from SIMONA! Now convert them and send them to MOSAIK!");

            Map<String, ResultEntity> resultsToBeSend = new HashMap<>();

            resultsFromSimona.forEach(
                    (uuid, result) -> {
                        if (result instanceof NodeResult nodeResult) {
                            resultsToBeSend.put(
                                    gridResultAssetMapping.get(UUID.fromString(uuid)),
                                    nodeResult
                            );
                        } else if (result instanceof SystemParticipantResult systemParticipantResult) {
                            resultsToBeSend.put(
                                    participantResultAssetMapping.get(UUID.fromString(uuid)),
                                    systemParticipantResult
                            );
                        }
                            else {
                            log.error("Got a wrong result entity from SIMONA!");
                        }
                    }
            );
            log.info("Send converted results to SIMOSAIK!");
            simonaSimulator.queueResultsFromSimona(new SimosaikResultWrapper(tick, currentTime, resultsToBeSend));
            long nextTick = tick + deltaT;
            log.info("+++++ [Phase 2-Activity] Tick = " + tick + " finished +++++");
            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
            return Optional.of(nextTick);
        } catch (InterruptedException | ConvertionException e) {
            throw new RuntimeException(e);
        }
    }


    public ExtPrimaryDataSimulation getExtPrimaryDataSimulation() {
        return extPrimaryDataSimulation;
    }

    public ExtResultDataSimulation getExtResultDataSimulation() {
        return extResultDataSimulation;
    }

    public void startMosaikSimulation(String mosaikIP) {
        try {
            this.simonaSimulator = new SimonaSimulator();
            RunSimosaik simosaikRunner = new RunSimosaik(
                    mosaikIP, simonaSimulator
            );
            new Thread(simosaikRunner, "Simosaik").start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
