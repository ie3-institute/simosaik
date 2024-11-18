/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import static edu.ie3.simosaik.SimosaikTranslation.*;

import edu.ie3.simona.api.data.ExtInputDataContainer;
import edu.ie3.simona.api.data.results.ExtResultContainer;
import edu.ie3.simosaik.data.SimosaikValue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Class with helpful methods to couple SIMONA and MOSAIK */
public class SimosaikUtils {

  private SimosaikUtils() {}

  /**
   * Starts MOSAIK connection
   *
   * @param simonaSimulator Simulator that extends the MOSAIK API
   * @param mosaikIP IP address for the connection with MOSAIK
   */
  public static void startMosaikSimulation(SimonaSimulator simonaSimulator, String mosaikIP) {
    try {
      RunSimosaik simosaikRunner = new RunSimosaik(mosaikIP, simonaSimulator);
      new Thread(simosaikRunner, "Simosaik").start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** Converts input data from MOSAIK to a data format that can be read by SIMONA API */
  public static ExtInputDataContainer createSimosaikPrimaryDataWrapper(
      long currentTick, Map<String, Object> mosaikInput, long nextTick) {
    ExtInputDataContainer extInputDataPackage = new ExtInputDataContainer(currentTick, nextTick);
    mosaikInput.forEach(
        (assetId, inputValue) -> extInputDataPackage.addValue(assetId, getSimosaikValue(inputValue)));
    return extInputDataPackage;
  }

  private static SimosaikValue getSimosaikValue(Object inputValue) {
    Map<String, Float> convertedInputValueMap = new HashMap<>();
    Map<String, Object> attrs = (Map<String, Object>) inputValue;
    for (Map.Entry<String, Object> attr : attrs.entrySet()) {
      Object[] values = ((Map<String, Object>) attr.getValue()).values().toArray();
      float value = 0;
        for (Object o : values) {
            value += ((Number) o).floatValue();
        }
      convertedInputValueMap.put(attr.getKey(), value);
    }
    return new SimosaikValue(convertedInputValueMap);
  }

  /**
   * Converts the results sent by SIMONA for the requested entities and attributes in a format that
   * can be read by MOSAIK
   */
  public static Map<String, Object> createSimosaikOutputMap(
      Map<String, List<String>> mosaikRequestedAttributes, ExtResultContainer simonaResults) {
    Map<String, Object> outputMap = new HashMap<>();
    mosaikRequestedAttributes.forEach(
        (id, attrs) -> {
          HashMap<String, Object> values = new HashMap<>();
          for (String attr : attrs) {
            addResult(simonaResults, id, attr, values);
          }
          outputMap.put(id, values);
        });
    return outputMap;
  }

  private static void addResult(
      ExtResultContainer results, String id, String attr, Map<String, Object> outputMap) {
    if (attr.equals(MOSAIK_VOLTAGE_DEVIATION)) {
      if (results.getTick() == 0L) {
        outputMap.put(attr, 0d);
      } else { // grid related results are not sent in time step zero
        outputMap.put(attr, results.getVoltageDeviation(id));
      }
    }
    if (attr.equals(MOSAIK_ACTIVE_POWER)) {
      outputMap.put(attr, results.getActivePower(id));
    }
    if (attr.equals(MOSAIK_REACTIVE_POWER)) {
      outputMap.put(attr, results.getReactivePower(id));
    }
  }
}
