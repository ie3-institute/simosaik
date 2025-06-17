/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import static edu.ie3.datamodel.models.StandardUnits.*;

import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.simosaik.exceptions.ConversionException;
import java.util.List;
import javax.measure.Quantity;
import javax.measure.Unit;

public final class SimosaikUnits {

  // scheduling units -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
  public static final String SIMONA_NEXT_TICK = "Simona[nextTick]";

  // grid units -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
  public static final String VOLTAGE_MAG = "u[pu]";
  public static final String VOLTAGE_ANG = "u[RAD]";

  public static final String CURRENT_MAG = "I[A]";
  public static final String CURRENT_ANG = "I[RAD]";

  // participant units -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
  public static final String ACTIVE_POWER = "P[MW]";
  public static final String REACTIVE_POWER = "Q[MVAr]";
  public static final String THERMAL_POWER = "P_th[MW]";

  public static final String SOC = "SOC[%]";

  // em units -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
  public static final String FLEX_REQUEST = "Flex[request]";
  public static final String FLEX_OPTIONS = "Flex[options]";
  public static final String FLEX_SET_POINT = "EM[setPoint]";
  public static final String FLEX_OPTIONS_DISAGGREGATED = "Flex[disaggregated]";

  public static final String DELAY = "delay[ms]";

  public static final String FLEX_OPTION_P_MIN = "PMin[MW]";
  public static final String FLEX_OPTION_P_REF = "PRef[MW]";
  public static final String FLEX_OPTION_P_MAX = "PMax[MW]";

  public static final String FLEX_OPTION_MAP_P_MIN = "idToPMin[MW]";
  public static final String FLEX_OPTION_MAP_P_REF = "idToPRef[MW]";
  public static final String FLEX_OPTION_MAP_P_MAX = "idToPMax[MW]";

  public static final List<String> ALL_GRID_UNITS =
      List.of(VOLTAGE_MAG, VOLTAGE_ANG, CURRENT_MAG, CURRENT_ANG);

  public static final List<String> ALL_PARTICIPANT_UNITS =
      List.of(ACTIVE_POWER, REACTIVE_POWER, THERMAL_POWER, SOC);

  /** Method to return the corresponding psdm unit to a mosaik unit. */
  @SuppressWarnings("unchecked")
  public static <Q extends Quantity<Q>> Unit<Q> getPSDMUnit(String mosaikUnit) {
    return switch (mosaikUnit) {
      case ACTIVE_POWER,
              THERMAL_POWER,
              FLEX_OPTION_P_MIN,
              FLEX_OPTION_P_REF,
              FLEX_OPTION_P_MAX,
              FLEX_OPTION_MAP_P_MIN,
              FLEX_OPTION_MAP_P_REF,
              FLEX_OPTION_MAP_P_MAX ->
          (Unit<Q>) ACTIVE_POWER_IN.multiply(1000);
      case REACTIVE_POWER -> (Unit<Q>) REACTIVE_POWER_IN.multiply(1000);
      case VOLTAGE_MAG -> (Unit<Q>) VOLTAGE_MAGNITUDE;
      case SOC -> (Unit<Q>) StandardUnits.SOC;
      default ->
          throw new ConversionException("Cannot find psdm unit for mosaik unit: " + mosaikUnit);
    };
  }
}
