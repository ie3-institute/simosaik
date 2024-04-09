package edu.ie3.simopsim;

import de.fhg.iee.opsim.client.Client;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataSimulation;
import edu.ie3.simona.api.data.results.ExtResultDataSimulation;
import edu.ie3.simona.api.exceptions.ConvertionException;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simopsim.data.OpsimPrimaryDataFactory;
import edu.ie3.simopsim.data.OpsimResultDataFactory;
import edu.ie3.simopsim.data.SimopsimPrimaryDataWrapper;
import edu.ie3.simopsim.data.SimopsimResultWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class OpsimSimulator extends ExtSimulation {

    private final Logger log = LogManager.getLogger("OpsimSimulator");

    private final ExtResultDataSimulation extResultDataSimulation;
    private final ExtPrimaryDataSimulation extPrimaryDataSimulation;

    private final long deltaT = 900L;

    private SimonaProxy simonaProxy;

    private final String urlToOpsim;

    private final Map<UUID, String> resultAssetMapping
            = Map.of(
                UUID.fromString("de8cfef5-7620-4b9e-9a10-1faebb5a80c0"),"EM_HH_Bus_81",
                UUID.fromString("a1eb7fc1-3bee-4b65-a387-ef3046644bf0"), "EM_HH_Bus_110",
                UUID.fromString("2560c371-f420-4c2a-b4e6-e04c11b64c03"), "EM_HH_Bus_25");

    private final Map<String, UUID> primaryDataAssetMapping
            = Map.of(
            "EM_HH_Bus_81/Schedule", UUID.fromString("c3434742-e4f0-49e5-baa7-c1e3045c732c"),
            "EM_HH_Bus_110/Schedule", UUID.fromString("fd2e19b6-d5e3-4776-9456-8787a2160d9d"),
            "EM_HH_Bus_25/Schedule", UUID.fromString("98c1a2ab-bd09-4c77-a389-d088aed894b1"));

    public OpsimSimulator(
            String urlString
    ) {
        this.urlToOpsim = urlString;
        this.extPrimaryDataSimulation = new ExtPrimaryDataSimulation(
                new OpsimPrimaryDataFactory(),
                this.primaryDataAssetMapping.values().stream().toList()
        );
        this.extResultDataSimulation = new ExtResultDataSimulation(
                new OpsimResultDataFactory(),
                this.resultAssetMapping.keySet().stream().toList()
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
        log.info("Look for new PrimaryData from OpSim...");
        try {
            SimopsimPrimaryDataWrapper rawPrimaryData = simonaProxy.receiveTriggerQueueForPrimaryData.take();
            log.info("Received Primary from OpSim... now convert them to PSDM-Value");

            // Primary Data that should be provided to SIMONA
            Map<String, Object> primaryDataFromExt = new HashMap<>();

            rawPrimaryData.ossm().forEach(
                    (id, msg) -> {
                        if (primaryDataAssetMapping.containsKey(id)) {
                            primaryDataFromExt.put(
                                    primaryDataAssetMapping.get(id).toString(),
                                    msg
                            );
                        }
                    }
            );

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
        log.info("+++++++++++++++++++++++++++ PostActivities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);
        ZonedDateTime currentTime = extResultDataSimulation.getExtResultData().getSimulationTime(tick);
        log.info("Request Results from SIMONA!");
        try {
            Map<String, Object> resultsFromSimona = extResultDataSimulation.getExtResultData().requestResultObjects(tick);
            log.info("Received results from SIMONA!");

            Map<String, ResultEntity> resultsToBeSend = new HashMap<>();

            resultsFromSimona.forEach(
                    (uuid, result) -> {
                        if (result instanceof SystemParticipantResult systemParticipantResult) {
                            resultsToBeSend.put(
                                    resultAssetMapping.get(UUID.fromString(uuid)),
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
            SimonaProxy proxy = new SimonaProxy(client, logger);
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
    public ExtPrimaryDataSimulation getExtPrimaryDataSimulation() {
        return extPrimaryDataSimulation;
    }
}
