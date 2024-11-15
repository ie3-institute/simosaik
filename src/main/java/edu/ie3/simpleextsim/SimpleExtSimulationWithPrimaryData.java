package edu.ie3.simpleextsim;

import ch.qos.logback.classic.Logger;
import edu.ie3.datamodel.models.result.ModelResultEntity;
import edu.ie3.datamodel.models.result.system.EmResult;
import edu.ie3.datamodel.models.result.system.PvResult;
import edu.ie3.simona.api.data.ExtData;
import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.ExtInputDataValue;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryData;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.simulation.ExtSimulation;
import edu.ie3.simpleextsim.data.SimpleExtSimValue;
import edu.ie3.simpleextsim.data.SimplePrimaryDataFactory;
import org.slf4j.LoggerFactory;

import java.util.*;

import static edu.ie3.simpleextsim.grid.SimpleExtSimulationGridData.*;

/**
 * Simple example for an external simulation, that calculates set points for two em agents, and gets power for two pv plants from SIMONA.
 */
public class SimpleExtSimulationWithPrimaryData extends ExtSimulation {

    private final Logger log = (Logger) LoggerFactory.getLogger("SimpleExtSimulationWithPrimaryData");

    private final ExtPrimaryData extPrimaryData;
    private final ExtResultData extResultData;

    private final long deltaT = 900L;

    public SimpleExtSimulationWithPrimaryData() {
        this.extPrimaryData = new ExtPrimaryData(
                new SimplePrimaryDataFactory(),
                Map.of(
                        LOAD_1, LOAD_1_UUID,
                        LOAD_2, LOAD_2_UUID
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
    public List<ExtData> getDataConnections() {
        return List.of(extPrimaryData, extResultData);
    }

    @Override
    protected Long initialize() {
        log.info("+++ Main args handed over to external simulation: {} +++", Arrays.toString(getMainArgs()));
        return 0L;
    }

    @Override
    protected Optional<Long> doActivity(long tick) {
        log.info("+++++++++++++++++++++++++++ Activities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);

        Map<String, ExtInputDataValue> extSimData = new HashMap<>();

        long phase = (tick / 2000) % 4;

        long nextTick = tick + deltaT;

        extSimData.put(
                LOAD_MODEL_1.getId(),
                new SimpleExtSimValue(LOAD_MODEL_1.getPower(phase))
        );

        extSimData.put(
                LOAD_MODEL_2.getId(),
                new SimpleExtSimValue(LOAD_MODEL_2.getPower(phase))
        );

        ExtInputDataContainer extInputDataPackage = new ExtInputDataContainer(
                tick,
                extSimData,
                Optional.of(nextTick)
        );


        // send primary data for load1 and load2 to SIMONA
        extPrimaryData.providePrimaryData(
                tick,
                extPrimaryData.createExtPrimaryDataMap(extInputDataPackage),
                extInputDataPackage.getMaybeNextTick()
        );
        log.info("[" + tick + "] Provide Primary Data to SIMONA for "
                + LOAD_MODEL_1.getId()
                + " ("
                + LOAD_MODEL_1.getUuid()
                + ") with "
                + LOAD_MODEL_1.getPower(phase)
                + " and "
                + LOAD_MODEL_2.getId()
                + " ("
                + LOAD_MODEL_2.getUuid()
                + ") with "
                + LOAD_MODEL_2.getPower(phase)
                + ".");


        log.info("[" + tick + "] Request Results from SIMONA!");

        try {
            Map<String, ModelResultEntity> resultsFromSimona = extResultData.requestResults(tick);

            log.info("[" + tick + "] Received results from SIMONA for " + resultsFromSimona.keySet());

            resultsFromSimona.forEach(
                    (id, result) -> {
                        //log.info("uuid = " + id + ", result = " + result);
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
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
        return Optional.of(nextTick);
    }
}