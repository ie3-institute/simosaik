/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.simosaik.MosaikSimulator;
import edu.ie3.simosaik.RunSimosaik;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Class with helpful methods to couple SIMONA and MOSAIK */
public class SimosaikUtils {

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

  public static ExtInputDataContainer createInputDataContainer(
      long tick, long nextTick, Map<String, Object> inputs, ExtEntityMapping mapping) {
    return createInputDataContainer(tick, nextTick, MosaikMessageParser.parse(inputs), mapping);
  }

  public static ExtInputDataContainer createInputDataContainer(
      long tick,
      long nextTick,
      List<MosaikMessageParser.MosaikMessage> mosaikMessages,
      ExtEntityMapping mapping) {
    ExtInputDataContainer container = new ExtInputDataContainer(tick, nextTick);

    // primary data
    Map<String, UUID> primaryMapping = mapping.getExtId2UuidMapping(DataType.EXT_PRIMARY_INPUT);
    PrimaryUtils.getPrimary(mosaikMessages, primaryMapping).forEach(container::addPrimaryValue);

    // em data
    Map<String, UUID> idToUuid = mapping.getExtId2UuidMapping(DataType.EXT_EM_INPUT);
    FlexUtils.getFlexRequests(mosaikMessages, idToUuid).forEach(container::addRequest);
    FlexUtils.getFlexOptions(mosaikMessages, idToUuid).forEach(container::addFlexOptions);
    FlexUtils.getSetPoint(mosaikMessages, idToUuid).forEach(container::addSetPoint);

    return container;
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
