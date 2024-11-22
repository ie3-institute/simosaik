/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.datamodel.models.StandardUnits;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.exceptions.ConversionException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.measure.Quantity;
import javax.measure.Unit;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

/** Conventions between SIMONA and MOSAIK */
public class SimosaikTranslation {
  public static final String MOSAIK_ACTIVE_POWER = "P[MW]";
  public static final String MOSAIK_REACTIVE_POWER = "Q[MVAr]";
  public static final String MOSAIK_VOLTAGE_DEVIATION = "deltaU[kV]";

  /**
   * Method to translate a MOSAIK input value into a {@link Value}.
   *
   * @param inputValue map: mosaik field name to value map
   * @return a new value
   */
  public static Value convertMosaikDataToValue(Map<String, Map<String, Number>> inputValue)
      throws ConversionException {
    Map<String, Double> valueMap = new HashMap<>();

    for (Map.Entry<String, Map<String, Number>> attr : inputValue.entrySet()) {
      valueMap.put(
          attr.getKey(),
          attr.getValue().values().stream().map(Number::doubleValue).reduce(0d, Double::sum));
    }

    Set<String> keySet = valueMap.keySet();

    if (isSValue(keySet)) {
      return new SValue(
          extract(valueMap, MOSAIK_ACTIVE_POWER, StandardUnits.ACTIVE_POWER_IN),
          extract(valueMap, MOSAIK_REACTIVE_POWER, StandardUnits.REACTIVE_POWER_IN));
    } else if (isPValue(keySet)) {
      return new PValue(extract(valueMap, MOSAIK_ACTIVE_POWER, StandardUnits.ACTIVE_POWER_IN));
    } else {
      throw new ConversionException("This method can only convert PValue or SValue.");
    }
  }

  private static boolean isSValue(Set<String> keySet) {
    return keySet.contains(MOSAIK_ACTIVE_POWER) && keySet.contains(MOSAIK_REACTIVE_POWER);
  }

  private static boolean isPValue(Set<String> keySet) {
    return keySet.contains(MOSAIK_ACTIVE_POWER) && !keySet.contains(MOSAIK_REACTIVE_POWER);
  }

  private static <Q extends Quantity<Q>> ComparableQuantity<Q> extract(
      Map<String, Double> valueMap, String field, Unit<Q> unit) {
    return Quantities.getQuantity(valueMap.get(field) * 1000, unit);
  }
}
