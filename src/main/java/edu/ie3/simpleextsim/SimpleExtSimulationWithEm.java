package edu.ie3.simpleextsim;

import ch.qos.logback.classic.Logger;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.EmResult;
import edu.ie3.datamodel.models.result.system.PvResult;
import edu.ie3.simona.api.data.*;
import edu.ie3.simona.api.data.em.ExtEmData;
import edu.ie3.simona.api.data.em.ExtEmDataSimulation;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.data.results.ExtResultDataSimulation;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simpleextsim.data.SimpleEmDataFactory;
import edu.ie3.simpleextsim.data.SimpleExtSimValue;
import org.slf4j.LoggerFactory;

import java.util.*;

import static edu.ie3.simpleextsim.grid.SimpleExtSimulationGridData.*;

/**
 * Simple example for an external simulation, that calculates power for two loads, and gets power for two pv plants from SIMONA.
 */

public class SimpleExtSimulationWithEm extends ExtSimulation implements ExtEmDataSimulation, ExtResultDataSimulation {

    private final Logger log = (Logger) LoggerFactory.getLogger("SimpleExtSimulationWithEm");

    private final ExtEmData extEmData;
    private final ExtResultData extResultData;

    private final long deltaT = 900L;

    public SimpleExtSimulationWithEm() {
        this.extEmData = new ExtEmData(
                new SimpleEmDataFactory(),
                Map.of(
                        EM_3, EM_3_UUID,
                        EM_4, EM_4_UUID
                )
        );
        this.extResultData = new ExtResultData(
                Map.of(
                        PV_1_UUID, PV_1,
                        PV_2_UUID, PV_2
                ),
                Collections.emptyMap()
        );
    }

    @Override
    protected Long initialize() {
        log.info("Main args handed over to external simulation: {}", Arrays.toString(getMainArgs()));
        return 0L;
    }

    @Override
    protected Optional<Long> doPreActivity(long tick) {
        log.info("+++++++++++++++++++++++++++ PreActivities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);

        Map<String, ExtInputDataValue> extSimData = new HashMap<>();

        long phase = (tick / 2000) % 4;

        long nextTick = tick + deltaT;

        extSimData.put(
                EM_CONTROLLER_3.getId(),
                new SimpleExtSimValue(EM_CONTROLLER_3.getSetPoint(phase))
        );

        extSimData.put(
                EM_CONTROLLER_4.getId(),
                new SimpleExtSimValue(EM_CONTROLLER_4.getSetPoint(phase))
        );

        ExtInputDataPackage extInputDataPackage = new ExtInputDataPackage(
                extSimData
        );


        // send primary data for load1 and load2 to SIMONA
        extEmData.provideEmData(tick, extEmData.createExtEmDataMap(
                extInputDataPackage
        ));
        log.info("Provide Primary Data to SIMONA for "
                + EM_CONTROLLER_3.getId()
                + " ("
                + EM_CONTROLLER_3.getUuid()
                + ") with "
                + EM_CONTROLLER_3.getSetPoint(phase)
                + " and "
                + EM_CONTROLLER_4.getId()
                + " ("
                + EM_CONTROLLER_4.getUuid()
                + ") with "
                + EM_CONTROLLER_4.getSetPoint(phase)
                + ".");
        return Optional.of(nextTick);
    }

    @Override
    protected Optional<Long> doPostActivity(long tick) {
        log.info("+++++++++++++++++++++++++++ PostActivities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);

        log.info("Request Results from SIMONA!");
        // request results for pv1 and pv2 from SIMONA
        try {
            Map<String, ResultEntity> resultsFromSimona = extResultData.requestResults(tick);

            log.info("Received results from SIMONA!");

            resultsFromSimona.forEach(
                    (id, result) -> {
                        log.info("(uuid = " + id + ", result = " + result);
                        if (result instanceof PvResult spResult) {
                            if (PV_1.equals(id)) {
                                log.debug("Tick " + tick + ": SIMONA calculated the power of pv1 (" + PV_1 + ") with " + spResult);
                                log.info("SIMONA calculated the power of pv1 (" + PV_1 + ") with p = " + spResult.getP());
                            } else if (PV_2.equals(id)) {
                                log.debug("Tick " + tick + ": SIMONA calculated the power of pv2 (" + PV_2 + ") with " + spResult);
                                log.info("SIMONA calculated the power of pv2 (" + PV_2 + ") with p = " + spResult.getP());
                            } else {
                                log.error("Received a result from SIMONA for uuid {}, but I don't expect this entity!", id);
                            }
                        } else if (result instanceof EmResult emResult){
                            if (EM_3.equals(id)) {
                                log.debug("Tick " + tick + ": SIMONA calculated the power of em3 (" + EM_3 + ") with " + emResult);
                                log.info("SIMONA calculated the power of em3 (" + EM_3 + ") with p = " + emResult.getP());
                            } else if (EM_4.equals(id)) {
                                log.debug("Tick " + tick + ": SIMONA calculated the power of em4 (" + EM_4 + ") with " + emResult);
                                log.info("SIMONA calculated the power of em4 (" + EM_4 + ") with p = " + emResult.getP());
                            } else {
                                log.error("Received a result from SIMONA for uuid {}, but I don't expect this entity!", id);
                            }
                        } else {
                            log.error("Received wrong results from SIMONA!");
                        }
                    }
            );

            long nextTick = tick + deltaT;

            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
            return Optional.of(nextTick);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public List<ExtData> getDataConnections() {
        return List.of(extEmData, extResultData);
    }
}