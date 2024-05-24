package edu.ie3.simopsim;

import de.fhg.iee.opsim.client.Client;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import edu.ie3.simona.api.data.em.ExtEmDataSimulation;
import edu.ie3.simona.api.data.results.ExtResultDataSimulation;
import edu.ie3.simona.api.exceptions.ConvertionException;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simopsim.data.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeoutException;

public class OpsimEmSimulator extends ExtSimulation {

    private final Logger log = LogManager.getLogger("OpsimSimulator");

    private final ExtResultDataSimulation extResultDataSimulation;
    private final ExtEmDataSimulation extEmDataSimulation;

    private final long deltaT = 900L;

    private SimonaEmProxy simonaProxy;

    private final String urlToOpsim;

    private final Map<UUID, String> participantResultAssetMapping
            = Map.of(
                UUID.fromString("f9dc7ce6-658c-4101-a12f-d58bb889286b"),"EM_HH_Bus_81",
                UUID.fromString("957938b7-0476-4fab-a1b3-6ce8615857b3"), "EM_HH_Bus_110",
                UUID.fromString("c3a7e9f5-b492-4c85-af2d-1e93f6a25443"), "EM_HH_Bus_25");

    private final Map<UUID, String> gridResultAssetMapping
            = Collections.emptyMap();

    private final Map<String, UUID> emAgentMapping
            = Map.of(
                    "EM_HH_Bus_81/Schedule", UUID.fromString("f9dc7ce6-658c-4101-a12f-d58bb889286b"),
                    "EM_HH_Bus_110/Schedule", UUID.fromString("957938b7-0476-4fab-a1b3-6ce8615857b3"),
                    "EM_HH_Bus_25/Schedule", UUID.fromString("c3a7e9f5-b492-4c85-af2d-1e93f6a25443"));

    public OpsimEmSimulator(
            String urlString
    ) {
        this.urlToOpsim = urlString;
        this.extEmDataSimulation = new ExtEmDataSimulation(
                new OpsimEmDataFactory(),
                this.emAgentMapping.values().stream().toList()
        );
        this.extResultDataSimulation = new ExtResultDataSimulation(
                new OpsimResultDataFactory(),
                this.participantResultAssetMapping.keySet().stream().toList(),
                this.gridResultAssetMapping.keySet().stream().toList()
        );
        runSimopsim();
    }

    @Override
    protected Long initialize() {
        log.info("+++++++++++++++++++++++++++ initialization of the external simulation +++++++++++++++++++++++++++");
        return 0L;
    }

    @Override
    protected Optional<Long> doPreActivity(long tick) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("+++++++++++++++++++++++++++ PreActivities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);
        log.info("Current Simulation Time: " + extResultDataSimulation.getExtResultData().getSimulationTime(tick));
        log.info("Look for new EmData from OpSim...");
        try {
            SimopsimEmDataWrapper rawEmData = simonaProxy.receiveTriggerQueueForEmData.take();
            log.info("Received Em from OpSim... now convert them to PSDM-Value");

            // Primary Data that should be provided to SIMONA
            Map<String, Object> emDataFromExt = new HashMap<>();

            rawEmData.ossm().forEach(
                    (id, msg) -> {
                        if (emAgentMapping.containsKey(id)) {
                            emDataFromExt.put(
                                    emAgentMapping.get(id).toString(),
                                    msg
                            );
                        }
                    }
            );

            // send primary data for load1 and load2 to SIMONA
            extEmDataSimulation.getExtEmData().provideEmData(tick, emDataFromExt);
            log.info("Provide Em Data to SIMONA");
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return Optional.of( tick + deltaT);
    }

    @Override
    protected Optional<Long> doPostActivity(long tick) {
        log.info("+++++++++++++++++++++++++++ PostActivities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);
        ZonedDateTime currentTime = extResultDataSimulation.getExtResultData().getSimulationTime(tick);
        log.info("Request Results from SIMONA!");
        try {
            Map<String, Object> resultsFromSimona = extResultDataSimulation.getExtResultData().requestResultObjects(tick);
            log.info("Received results from SIMONA! " + resultsFromSimona);

            Map<String, ResultEntity> resultsToBeSend = new HashMap<>();

            resultsFromSimona.forEach(
                    (uuid, result) -> {
                        if (result instanceof SystemParticipantResult systemParticipantResult) {
                            resultsToBeSend.put(
                                    participantResultAssetMapping.get(UUID.fromString(uuid)),
                                    systemParticipantResult
                            );
                        } else {
                            log.error("Got a wrong result entity from SIMONA!");
                        }
                    }
            );
            simonaProxy.queueResultsFromSimona(new SimopsimResultWrapper(currentTime, resultsToBeSend));
            long nextTick = tick + deltaT;

            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
            return Optional.of(nextTick);
        } catch (ConvertionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    public void runSimopsim() {
        try {
            Logger logger = LogManager.getLogger(Client.class);
            Client client = new Client(logger);
            SimonaEmProxy proxy = new SimonaEmProxy(client, logger);
            this.simonaProxy = proxy;
            client.addProxy(proxy);
            client.reconnect(this.urlToOpsim);
        } catch (URISyntaxException | IOException | NoSuchAlgorithmException | KeyManagementException |
                TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public ExtResultDataSimulation getExtResultDataSimulation() {
        return extResultDataSimulation;
    }
    public ExtEmDataSimulation getExtEmDataSimulation() {
        return extEmDataSimulation;
    }
}
