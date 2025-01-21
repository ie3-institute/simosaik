/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import static edu.ie3.datamodel.models.StandardUnits.*;

import edu.ie3.simosaik.exceptions.ConversionException;
import javax.measure.Quantity;
import javax.measure.Unit;

/** Conventions between SIMONA and MOSAIK */
public class SimosaikTranslation {
  public static final String MOSAIK_ACTIVE_POWER = "P[MW]";
  public static final String MOSAIK_REACTIVE_POWER = "Q[MVAr]";
  public static final String MOSAIK_VOLTAGE_DEVIATION_PU = "deltaU[pu]";

  /** Method to return the corresponding psdm unit to a mosaik unit. */
  @SuppressWarnings("unchecked")
  public static <Q extends Quantity<Q>> Unit<Q> getPSDMUnit(String mosaikUnit) {
    return switch (mosaikUnit) {
      case MOSAIK_ACTIVE_POWER -> (Unit<Q>) ACTIVE_POWER_IN.divide(1000);
      case MOSAIK_VOLTAGE_DEVIATION_PU -> (Unit<Q>) VOLTAGE_MAGNITUDE;
      case MOSAIK_REACTIVE_POWER -> (Unit<Q>) REACTIVE_POWER_IN.divide(1000);
      default ->
          throw new ConversionException("Cannot find psdm unit for mosaik unit: " + mosaikUnit);
    };
  }
}
