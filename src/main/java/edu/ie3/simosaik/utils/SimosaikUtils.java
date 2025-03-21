/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simosaik.MosaikSimulator;
import edu.ie3.simosaik.RunSimosaik;
import edu.ie3.simosaik.exceptions.ConversionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import java.util.*;
import java.util.stream.Stream;

import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

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

  /** Converts input data from MOSAIK to a data format that can be read by SIMONA API */
  @SuppressWarnings("unchecked")
  public static ExtInputDataContainer createExtInputDataContainer(
      long currentTick, Map<String, Object> mosaikInput, long nextTick) {
    ExtInputDataContainer extInputDataContainer = new ExtInputDataContainer(currentTick, nextTick);
    mosaikInput.forEach(
        (assetId, inputValue) ->
            extInputDataContainer.addPrimaryValue(
                assetId,
                convertMosaikDataToValue(assetId, (Map<String, Map<String, Number>>) inputValue)));
    return extInputDataContainer;
  }

  /**
   * Method to translate a MOSAIK input value into a {@link Value}.
   *
   * @param inputValue map: mosaik field name to value map
   * @return a new value
   */
  public static Value convertMosaikDataToValue(
      String assetId, Map<String, Map<String, Number>> inputValue) throws ConversionException {
    Map<String, Double> valueMap = new HashMap<>();

    // check data
    for (Map.Entry<String, Map<String, Number>> attr : inputValue.entrySet()) {
      Collection<Number> values = attr.getValue().values();

      if (values.contains(null)) {
        log.warn("Received null value for attribute '{}' of asset '{}'.", attr.getKey(), assetId);
      } else {
        valueMap.put(
            attr.getKey(),
            values.stream()
                .filter(Objects::nonNull)
                .map(Number::doubleValue)
                .reduce(0d, Double::sum));
      }
    }

    return toValue(valueMap);
  }

  public static Value toValue(Map<String, Double> valueMap) {
    // convert power
    Optional<ComparableQuantity<Power>> active =
        extractAny(valueMap, MOSAIK_ACTIVE_POWER, MOSAIK_ACTIVE_POWER_IN);
    Optional<ComparableQuantity<Power>> reactive =
        extractAny(valueMap, MOSAIK_REACTIVE_POWER, MOSAIK_REACTIVE_POWER_IN);

    if (reactive.isPresent()) {
      ComparableQuantity<Power> activePower = active.orElse(null);
      return new SValue(activePower, reactive.get());
    }
    if (active.isPresent()) {
      return new PValue(extract(valueMap, MOSAIK_REACTIVE_POWER));
    }

    throw new ConversionException("This method only supports active and reactive power!");
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

  public record Tuple3<T>(String sender, String receiver, T value) {}

  public static <Q extends Quantity<Q>> List<Tuple3<ComparableQuantity<Q>>> extract(
      Collection<MosaikMessageParser.MosaikMessage> messages, String unit) {
    List<Tuple3<ComparableQuantity<Q>>> tuples = new ArrayList<>();

    for (MosaikMessageParser.MosaikMessage message : messages) {
      if (message.unit().equals(unit)) {

        Unit<Q> pdsmUnit = getPSDMUnit(message.unit());
        double value = (double) message.messageValue();

        tuples.add(
            new Tuple3<>(
                message.sender(), message.receiver(), Quantities.getQuantity(value, pdsmUnit)));
      }
    }

    return tuples;
  }

  public static <Q extends Quantity<Q>> Optional<ComparableQuantity<Q>> combineQuantities(
      List<Tuple3<ComparableQuantity<Q>>> list) {
    if (list.isEmpty()) {
      return Optional.empty();
    }

    ComparableQuantity<Q> combined = list.get(0).value();

    for (int i = 1; i < list.size(); i++) {
      combined = combined.add(list.get(i).value);
    }

    return Optional.of(combined);
  }

  public static void addResult(
      ExtResultContainer results, String id, String attr, Map<String, Object> outputMap) {
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

  @SuppressWarnings("unchecked")
  private static <Q extends Quantity<Q>> Optional<ComparableQuantity<Q>> extractAny(
      Map<String, Double> valueMap, String... fields) {
    return Stream.of(fields)
        .map(field -> (ComparableQuantity<Q>) extract(valueMap, field))
        .filter(Objects::nonNull)
        .findFirst();
  }

  @SuppressWarnings("unchecked")
  private static <Q extends Quantity<Q>> ComparableQuantity<Q> extract(
      Map<String, Double> valueMap, String field) {
    return Optional.ofNullable(valueMap.get(field))
        .map(value -> Quantities.getQuantity(value, (Unit<Q>) getPSDMUnit(field)))
        .orElse(null);
  }
}
