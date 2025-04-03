/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simona.api.data.em.EmMode;
import edu.ie3.simona.api.data.mapping.DataType;
import edu.ie3.simona.api.data.mapping.ExtEntityEntry;
import edu.ie3.simona.api.data.mapping.ExtEntityMapping;
import edu.ie3.simosaik.MosaikSimulator;
import edu.ie3.simosaik.RunSimosaik;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Class with helpful methods to couple SIMONA and MOSAIK */
public class SimosaikUtils {

  private static final Logger log = LoggerFactory.getLogger(SimosaikUtils.class);

  private SimosaikUtils() {}

  /**
   * Starts MOSAIK connection
   *
   * @param simonaSimulator Simulator that extends the MOSAIK API
   * @param mosaikIP IP address for the connection with MOSAIK
   */
  public static void startMosaikSimulation(MosaikSimulator simonaSimulator, String mosaikIP) {
    try {
      RunSimosaik simosaikRunner = new RunSimosaik(mosaikIP, simonaSimulator);
      new Thread(simosaikRunner, "Simosaik").start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<UUID, Class<Value>> buildAssetsToValueClasses(ExtEntityMapping entityMapping) {
    Map<UUID, Class<Value>> assetsToValueClasses = new HashMap<>();

    for (ExtEntityEntry extEntityEntry : entityMapping.getEntries(DataType.EXT_PRIMARY_INPUT)) {
      Optional<ColumnScheme> scheme = extEntityEntry.columnScheme();

      if (scheme.isPresent()) {
        assetsToValueClasses.put(
            extEntityEntry.uuid(), (Class<Value>) scheme.get().getValueClass());
      }
    }

    return assetsToValueClasses;
  }

  public static Optional<Map.Entry<EmMode, List<UUID>>> findEmMode(ExtEntityMapping entityMapping) {
    Set<DataType> dataTypes = entityMapping.getDataTypes();

    Function<DataType, Map.Entry<EmMode, List<UUID>>> fcn =
        dataType ->
            Map.entry(
                EmMode.fromDataType(dataType),
                entityMapping.getEntries(dataType).stream().map(ExtEntityEntry::uuid).toList());

    if (dataTypes.contains(DataType.EXT_EM_INPUT)) {
      return Optional.of(fcn.apply(DataType.EXT_EM_INPUT));

    } else if (dataTypes.contains(DataType.EXT_EM_COMMUNICATION)) {
      return Optional.of(fcn.apply(DataType.EXT_EM_COMMUNICATION));

    } else if (dataTypes.contains(DataType.EXT_EM_OPTIMIZER)) {
      return Optional.of(fcn.apply(DataType.EXT_EM_OPTIMIZER));

    } else {
      int count = 0;
      if (dataTypes.contains(DataType.EXT_EM_INPUT)) count++;
      if (dataTypes.contains(DataType.EXT_EM_COMMUNICATION)) count++;
      if (dataTypes.contains(DataType.EXT_EM_OPTIMIZER)) count++;

      log.warn("Em mapping for {} mode(s) were provided! Returning no em mode!", count);
      return Optional.empty();
    }
  }

  public static Map<DataType, List<UUID>> buildResultMapping(ExtEntityMapping entityMapping) {
    Map<DataType, List<UUID>> resultMapping = new HashMap<>();

    Consumer<DataType> consumer =
        type -> {
          List<UUID> assets =
              entityMapping.getEntries(type).stream().map(ExtEntityEntry::uuid).toList();

          if (!assets.isEmpty()) {
            resultMapping.put(type, assets);
          }
        };

    consumer.accept(DataType.EXT_GRID_RESULT);
    consumer.accept(DataType.EXT_PARTICIPANT_RESULT);
    consumer.accept(DataType.EXT_FLEX_OPTIONS_RESULT);

    return resultMapping;
  }

  public static void addResult(
      ExtResultContainer results, UUID id, String attr, Map<String, Object> outputMap) {
    if (equalsAny(attr, MOSAIK_VOLTAGE_DEVIATION_PU)) {
      if (results.getTick() == 0L) {
        outputMap.put(attr, 0d);
      } else {
        // grid related results are not sent in time step zero
        outputMap.put(attr, results.getVoltageDeviation(id));
      }
    }
    if (equalsAny(attr, MOSAIK_VOLTAGE_PU)) {
      if (results.getTick() == 0L) {
        outputMap.put(attr, 1d);
      } else {
        // grid related results are not sent in time step zero
        outputMap.put(attr, results.getVoltage(id));
      }
    }

    if (equalsAny(attr, MOSAIK_ACTIVE_POWER, MOSAIK_ACTIVE_POWER_IN)) {
      outputMap.put(attr, results.getActivePower(id));
    }
    if (equalsAny(attr, MOSAIK_REACTIVE_POWER, MOSAIK_REACTIVE_POWER_IN)) {
      outputMap.put(attr, results.getReactivePower(id));
    }
  }

  private static boolean equalsAny(String attr, String... units) {
    for (String unit : units) {
      if (attr.equals(unit)) {
        return true;
      }
    }
    return false;
  }
}
