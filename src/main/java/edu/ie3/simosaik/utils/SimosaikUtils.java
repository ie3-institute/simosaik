/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimosaikUnits.*;

import de.offis.mosaik.api.SimProcess;
import edu.ie3.datamodel.models.value.*;
import edu.ie3.simosaik.MosaikSimulator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

/** Class with helpful methods to couple SIMONA and MOSAIK */
public final class SimosaikUtils {

  private SimosaikUtils() {}

  /**
   * Starts MOSAIK connection
   *
   * @param mosaikSimulator Simulator that extends the MOSAIK API
   * @param mosaikIP IP address for the connection with MOSAIK
   */
  public static Supplier<Boolean> startMosaikSimulator(
      MosaikSimulator mosaikSimulator, String mosaikIP, Thread.UncaughtExceptionHandler handler) {

    // mosaik simulator thread
    Thread thread =
        new Thread("Simosaik") {
          @Override
          public void run() {
            try {
              SimProcess.startSimulation(new String[] {mosaikIP}, mosaikSimulator);
            } catch (Exception e) {
              handler.uncaughtException(this, e);
            }
          }
        };

    thread.start();
    return thread::isAlive;
  }

  // converting inputs

  /**
   * Method to get all values from a given map.
   *
   * @param attrToValue map: unit to value
   * @return a list of {@link Value}s
   */
  public static List<Value> convert(Map<String, Double> attrToValue) {
    List<Value> valueList = new ArrayList<>();

    // convert power
    ComparableQuantity<Power> active = extract(attrToValue, ACTIVE_POWER);
    ComparableQuantity<Power> reactive = extract(attrToValue, REACTIVE_POWER);
    ComparableQuantity<Power> heat = extract(attrToValue, THERMAL_POWER);

    toPValue(active, reactive, heat).ifPresent(valueList::add);

    return valueList;
  }

  /**
   * Creates an option for a {@link PValue} from the given inputs.
   *
   * @param active power
   * @param reactive power
   * @return option for a power value
   */
  public static Optional<PValue> toPValue(
      ComparableQuantity<Power> active, ComparableQuantity<Power> reactive) {
    return toPValue(active, reactive, null);
  }

  /**
   * Creates an option for a {@link PValue} from the given inputs.
   *
   * @param active power
   * @param reactive power
   * @param heat demand
   * @return option for a power value
   */
  public static Optional<PValue> toPValue(
      ComparableQuantity<Power> active,
      ComparableQuantity<Power> reactive,
      ComparableQuantity<Power> heat) {
    if (reactive != null && heat != null) {
      // we have at least reactive power and heat
      return Optional.of(new HeatAndSValue(active, reactive, heat));

    } else if (reactive != null) {
      // we have at least reactive power
      return Optional.of(new SValue(active, reactive));

    } else if (heat != null) {
      // we have at least heat
      return Optional.of(new HeatAndPValue(active, heat));

    } else if (active != null) {
      // we have active power
      return Optional.of(new PValue(active));
    }

    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  public static <Q extends Quantity<Q>> ComparableQuantity<Q> extract(
      Map<String, Double> valueMap, String field) {
    return Optional.ofNullable(valueMap.get(field))
        .map(value -> Quantities.getQuantity(value, (Unit<Q>) getPSDMUnit(field)))
        .orElse(null);
  }
}
