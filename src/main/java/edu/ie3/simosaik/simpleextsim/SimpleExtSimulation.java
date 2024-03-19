package edu.ie3.simosaik.simpleextsim;

import ch.qos.logback.classic.Logger;
import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.result.system.PvResult;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryData;
import edu.ie3.simona.api.data.primarydata.ExtPrimaryDataSimulation;
import edu.ie3.simona.api.data.primarydata.PrimaryDataFactory;
import edu.ie3.simona.api.data.results.ExtResultData;
import edu.ie3.simona.api.data.results.ExtResultDataSimulation;
import edu.ie3.simona.api.data.results.ResultDataFactory;
import edu.ie3.simona.api.exceptions.ConvertionException;
import edu.ie3.simona.api.simulation.ExtSimulation;
import org.slf4j.LoggerFactory;
import tech.units.indriya.quantity.Quantities;

import java.util.*;

/**
 * Simple example for an external simulation, that calculates power for two loads, and gets power for two pv plants from SIMONA.
 */

public class SimpleExtSimulation extends ExtSimulation implements ExtPrimaryDataSimulation, ExtResultDataSimulation {

    private final Logger log = (Logger) LoggerFactory.getLogger("SimpleExtSimulation");

    private ExtPrimaryData extPrimaryData;
    private ExtResultData extResultData;

    private final SimplePrimaryDataFactory primaryDataFactory;
    private final SimpleResultDataFactory resultDataFactory;


    private final UUID load1 = UUID.fromString("fd1a8de9-722a-4304-8799-e1e976d9979c");
    private final UUID load2 = UUID.fromString("ff0b995a-86ff-4f4d-987e-e475a64f2180");

    private final List<UUID> primaryDataAssets = Arrays.asList(load1, load2);

    private final UUID pv1 = UUID.fromString("de8cfef5-7620-4b9e-9a10-1faebb5a80c0");
    private final UUID pv2 = UUID.fromString("2560c371-f420-4c2a-b4e6-e04c11b64c03");

    private final List<UUID> resultDataAssets = Arrays.asList(pv1, pv2);

    private final SimpleLoadModel loadModel1 = new SimpleLoadModel(
            load1,
            "Load 1",
            createTimeSeries1()
    );

    private final SimpleLoadModel loadModel2 = new SimpleLoadModel(
            load2,
            "Load 2",
            createTimeSeries2()
    );

    private static HashMap<Long, PValue> createTimeSeries1() {
        HashMap<Long, PValue> ts = new HashMap<>();
        ts.put(0L, new PValue(Quantities.getQuantity(10.0, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(1L, new PValue(Quantities.getQuantity(15.0, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(2L, new PValue(Quantities.getQuantity(10.0, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(3L, new PValue(Quantities.getQuantity(5.0, StandardUnits.ACTIVE_POWER_IN)));
        return ts;
    }

    private static HashMap<Long, PValue> createTimeSeries2() {
        HashMap<Long, PValue> ts = new HashMap<>();
        ts.put(0L, new PValue(Quantities.getQuantity(5.0, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(1L, new PValue(Quantities.getQuantity(10.0, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(2L, new PValue(Quantities.getQuantity(15.0, StandardUnits.ACTIVE_POWER_IN)));
        ts.put(3L, new PValue(Quantities.getQuantity(10.0, StandardUnits.ACTIVE_POWER_IN)));
        return ts;
    }

    public SimpleExtSimulation() {
        this.primaryDataFactory = new SimplePrimaryDataFactory();
        this.resultDataFactory = new SimpleResultDataFactory();
    }


    @Override
    protected Optional<Long> initialize() {
        log.info("Main args handed over to external simulation: {}", Arrays.toString(getMainArgs()));
        return Optional.of(0L);
    }

    @Override
    protected Optional<Long> doActivity(long tick) {
        try {
            log.info("+++++++++++++++++++++++++++ External simulation: Tick {} has been triggered. +++++++++++++++++++++++++++", tick);

            // Primary Data that should be provided to SIMONA
            Map<String, Object> primaryDataFromExt = new HashMap<>();

            long phase = (tick / 2000) % 4;

            primaryDataFromExt.put(
                    load1.toString(), loadModel1.getPower(phase)
            );
            primaryDataFromExt.put(
                    load2.toString(), loadModel2.getPower(phase)
            );

            // send primary data for load1 and load2 to SIMONA
            extPrimaryData.providePrimaryData(tick, primaryDataFromExt);
            log.info("Provide Primary Data to SIMONA for load1 ("
                            + load1
                            + ") with "
                            + loadModel1.getPower(phase)
                            + " and load2 ("
                            + load2
                            + ") with "
                            + loadModel2.getPower(phase)
                            + ".");

            log.debug("Request Results from SIMONA!");
            // request results for pv1 and pv2 from SIMONA
            Map<String, Object> resultsFromSimona = extResultData.requestResultObjects(tick);

            log.debug("Received results from SIMONA!");

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
                    } else {
                        log.error("Received wrong results from SIMONA!");
                    }
                }
            );

            long nextTick = tick + 900;

            log.info("***** External simulation for tick " + tick + " completed. Next simulation tick = " + nextTick + " *****");
            return Optional.of(nextTick);
        } catch (InterruptedException | ConvertionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setExtPrimaryData(ExtPrimaryData extPrimaryData) {
        this.extPrimaryData = extPrimaryData;
    }

    @Override
    public PrimaryDataFactory getPrimaryDataFactory() {
        return primaryDataFactory;
    }

    @Override
    public List<UUID> getPrimaryDataAssets() {
        return primaryDataAssets;
    }


    @Override
    public void setExtResultData(ExtResultData extResultData) {
        this.extResultData = extResultData;
    }

    @Override
    public ResultDataFactory getResultDataFactory() {
        return resultDataFactory;
    }

    @Override
    public List<UUID> getResultDataAssets() {
        return resultDataAssets;
    }
}
