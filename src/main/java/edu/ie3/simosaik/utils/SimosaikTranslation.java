/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.datamodel.models.StandardUnits.*;

import edu.ie3.simosaik.exceptions.ConversionException;
import java.util.List;
import javax.measure.Quantity;
import javax.measure.Unit;

/** Conventions between SIMONA and MOSAIK */
public class SimosaikTranslation {

  // power units
  public static final String MOSAIK_ACTIVE_POWER = "P[MW]";
  public static final String MOSAIK_REACTIVE_POWER = "Q[MVAr]";
  public static final String MOSAIK_ACTIVE_POWER_IN = "Pin[MW]";
  public static final String MOSAIK_REACTIVE_POWER_IN = "Qin[MVAr]";

  // voltage units
  public static final String MOSAIK_VOLTAGE_DEVIATION_PU = "deltaU[pu]";
  public static final String MOSAIK_VOLTAGE_PU = "u[pu]";

  // soc unit
  public static final String MOSAIK_SOC = "SOC[percent]";

  // em communication
  public static final String FLEX_REQUEST = "Flex[request]"; // valueType = uuid
  public static final String FLEX_OPTIONS =
      "Flex[options]"; // valueType = Map{receiver=uuid, sender=uuid, PMin[MW]=double,
  // PRef[MW]=double, PMax[MW]=double}

  // flex options
  public static final String FLEX_OPTION_P_MIN = "PMin[MW]";
  public static final String FLEX_OPTION_P_REF = "PRef[MW]";
  public static final String FLEX_OPTION_P_MAX = "PMax[MW]";

  public static final String FLEX_OPTION_MAP_P_MIN = "idToPMin[MW]";
  public static final String FLEX_OPTION_MAP_P_REF = "idToPRef[MW]";
  public static final String FLEX_OPTION_MAP_P_MAX = "idToPMax[MW]";

  // all units
  public static final List<String> ALL_MOSAIK_UNITS =
      List.of(
          MOSAIK_ACTIVE_POWER,
          MOSAIK_REACTIVE_POWER,
          MOSAIK_VOLTAGE_DEVIATION_PU,
          MOSAIK_VOLTAGE_PU,
          MOSAIK_ACTIVE_POWER_IN,
          MOSAIK_REACTIVE_POWER_IN,
          MOSAIK_SOC,
          FLEX_OPTION_P_MIN,
          FLEX_OPTION_P_REF,
          FLEX_OPTION_P_MAX,
          FLEX_OPTION_MAP_P_MIN,
          FLEX_OPTION_MAP_P_REF,
          FLEX_OPTION_MAP_P_MAX);

  /** Method to return the corresponding psdm unit to a mosaik unit. */
  @SuppressWarnings("unchecked")
  public static <Q extends Quantity<Q>> Unit<Q> getPSDMUnit(String mosaikUnit) {
    return switch (mosaikUnit) {
      case MOSAIK_ACTIVE_POWER,
              MOSAIK_ACTIVE_POWER_IN,
              FLEX_OPTION_P_MIN,
              FLEX_OPTION_P_REF,
              FLEX_OPTION_P_MAX,
              FLEX_OPTION_MAP_P_MIN,
              FLEX_OPTION_MAP_P_REF,
              FLEX_OPTION_MAP_P_MAX ->
          (Unit<Q>) ACTIVE_POWER_IN.multiply(1000);
      case MOSAIK_REACTIVE_POWER, MOSAIK_REACTIVE_POWER_IN ->
          (Unit<Q>) REACTIVE_POWER_IN.multiply(1000);
      case MOSAIK_VOLTAGE_DEVIATION_PU, MOSAIK_VOLTAGE_PU -> (Unit<Q>) VOLTAGE_MAGNITUDE;
      case MOSAIK_SOC -> (Unit<Q>) SOC;
      default ->
          throw new ConversionException("Cannot find psdm unit for mosaik unit: " + mosaikUnit);
    };
  }
}
