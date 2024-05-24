package edu.ie3.simpleextsim;

import ch.qos.logback.classic.Logger;
import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.result.system.EmResult;
import edu.ie3.datamodel.models.result.system.PvResult;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.simona.api.data.ExtDataSimulation;
import edu.ie3.simona.api.data.em.ExtEmDataSimulation;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataSimulation;
import edu.ie3.simona.api.data.results.ExtResultDataSimulation;
import edu.ie3.simona.api.exceptions.ConvertionException;
import edu.ie3.simona.api.simulation.ExtSimulation;
import org.slf4j.LoggerFactory;
import tech.units.indriya.quantity.Quantities;

import java.util.*;

/**
 * Simple example for an external simulation, that calculates power for two loads, and gets power for two pv plants from SIMONA.
 */

public class SimpleExtSimulationWithEm extends ExtSimulation {

    private final Logger log = (Logger) LoggerFactory.getLogger("SimpleExtSimulationWithEm");

    private final ExtEmDataSimulation extEmDataSimulation;
    private final ExtResultDataSimulation extResultDataSimulation;

    private final List<ExtDataSimulation> dataConnections;

    /*
    private final UUID em3 = UUID.fromString("c40d5386-d2ab-49f8-a1b4-02991b68f502");
    private final UUID em4 = UUID.fromString("5f4c7c32-0e14-4f88-b727-467f270624e2");

     */

    private final UUID em3 = UUID.fromString("fd1a8de9-722a-4304-8799-e1e976d9979c");
    private final UUID em4 = UUID.fromString("ff0b995a-86ff-4f4d-987e-e475a64f2180");


    private final List<UUID> emDataAssets = Arrays.asList(em3, em4);

    private final UUID pv1 = UUID.fromString("a1eb7fc1-3bee-4b65-a387-ef3046644bf0");
    private final UUID pv2 = UUID.fromString("9d7cd8e2-d859-4f4f-9c01-abba06ef2e2c");

    private final List<UUID> resultDataAssets = Arrays.asList(em3, em4);

    private final long deltaT = 900L;

    private final static String EM_3 = "EM_3";
    private final static String EM_4 = "EM_4";

    private final SimpleFlexibilityController emController3 = new SimpleFlexibilityController(
            em3,
            EM_3,
            createTimeSeries1()
    );

    private final SimpleFlexibilityController emController4 = new SimpleFlexibilityController(
            em4,
            EM_4,
            createTimeSeries2()
    );

    private static HashMap<Long, PValue> createTimeSeries1() {
        HashMap<Long, PValue> ts = new HashMap<>();
        ts.put(0L, new PValue(Quantities.getQuantity(-0.5, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(1L, new PValue(Quantities.getQuantity(-0.25, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(2L, new PValue(Quantities.getQuantity(0.25, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(3L, new PValue(Quantities.getQuantity(0.5, StandardUnits.ACTIVE_POWER_IN)));
        return ts;
    }

    private static HashMap<Long, PValue> createTimeSeries2() {
        HashMap<Long, PValue> ts = new HashMap<>();
        ts.put(0L, new PValue(Quantities.getQuantity(0.5, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(1L, new PValue(Quantities.getQuantity(0.25, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(2L, new PValue(Quantities.getQuantity(-0.25, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(3L, new PValue(Quantities.getQuantity(-0.5, StandardUnits.ACTIVE_POWER_IN)));
        return ts;
    }

    public SimpleExtSimulationWithEm() {
        dataConnections = new ArrayList<>();
        this.extEmDataSimulation =  new ExtEmDataSimulation(
                new SimpleEmDataFactory(),
                emDataAssets
        );
        this.extResultDataSimulation = new ExtResultDataSimulation(
                new SimpleResultDataFactory(),
                resultDataAssets,
                Collections.emptyList()
        );
        dataConnections.add(
           this.extEmDataSimulation
        );
        dataConnections.add(
            this.extResultDataSimulation
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

        // Primary Data that should be provided to SIMONA
        Map<String, Object> emDataFromExt = new HashMap<>();

        long phase = (tick / 2000) % 4;

        long nextTick = tick + deltaT;

        emDataFromExt.put(
                em3.toString(), emController3.getSetPoint(phase)
        );
        emDataFromExt.put(
                em4.toString(), emController4.getSetPoint(phase)
        );

        // send primary data for load1 and load2 to SIMONA
        extEmDataSimulation.getExtEmData().provideEmData(tick, emDataFromExt);
        log.info("Provide Primary Data to SIMONA for "
                + EM_3
                + " ("
                + em3
                + ") with "
                + emController3.getSetPoint(phase)
                + " and "
                + EM_4
                + " ("
                + em4
                + ") with "
                + emController4.getSetPoint(phase)
                + ".");
        return Optional.of(nextTick);
    }

    @Override
    protected Optional<Long> doPostActivity(long tick) {
        log.info("+++++++++++++++++++++++++++ PostActivities in External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);

        log.info("Request Results from SIMONA!");
        // request results for pv1 and pv2 from SIMONA
        try {
            Map<String, Object> resultsFromSimona = extResultDataSimulation.getExtResultData().requestResultObjects(tick);

            log.info("Received results from SIMONA!");

            resultsFromSimona.forEach(
                    (uuid, result) -> {
                        if (result instanceof PvResult spResult) {
                            if (pv1.equals(UUID.fromString(uuid))) {
                                log.debug("Tick " + tick + ": SIMONA calculated the power of pv1 (" + pv1 + ") with " + spResult);
                                log.info("SIMONA calculated the power of pv1 (" + pv1 + ") with p = " + spResult.getP());
                            } else if (pv2.equals(UUID.fromString(uuid))) {
                                log.debug("Tick " + tick + ": SIMONA calculated the power of pv2 (" + pv2 + ") with " + spResult);
                                log.info("SIMONA calculated the power of pv2 (" + pv2 + ") with p = " + spResult.getP());
                            } else {
                                log.error("Received a result from SIMONA for uuid {}, but I don't expect this entity!", uuid);
                            }
                        } else if (result instanceof EmResult emResult){
                            if (em3.equals(UUID.fromString(uuid))) {
                                log.debug("Tick " + tick + ": SIMONA calculated the power of em3 (" + em3 + ") with " + emResult);
                                log.info("SIMONA calculated the power of em3 (" + em3 + ") with p = " + emResult.getP());
                            } else if (em4.equals(UUID.fromString(uuid))) {
                                log.debug("Tick " + tick + ": SIMONA calculated the power of em4 (" + em4 + ") with " + emResult);
                                log.info("SIMONA calculated the power of em4 (" + em4 + ") with p = " + emResult.getP());
                            } else {
                                log.error("Received a result from SIMONA for uuid {}, but I don't expect this entity!", uuid);
                            }
                        } else {
                            log.error("Received wrong results from SIMONA!");
                        }
                    }
            );

            long nextTick = tick + deltaT;

            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
            return Optional.of(nextTick);
        } catch (ConvertionException | InterruptedException e) {
            throw new RuntimeException(e);
        }


        /*
        long nextTick = tick + deltaT;

        log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
        return Optional.of(nextTick);

         */
    }

    public ExtEmDataSimulation getExtEmDataSimulation() {
        return extEmDataSimulation;
    }

    public ExtResultDataSimulation getExtResultDataSimulation() {
        return extResultDataSimulation;
    }

    public List<ExtDataSimulation> getDataConnections() {
        return dataConnections;
    }
}