/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.utils.MosaikMessageParser.filterForUnit;
import static edu.ie3.simosaik.utils.SimosaikTranslation.*;
import static edu.ie3.simosaik.utils.SimosaikUtils.combineQuantities;
import static edu.ie3.simosaik.utils.SimosaikUtils.extract;
import static edu.ie3.util.quantities.PowerSystemUnits.KILOWATT;

import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simona.api.data.em.model.EmSetPointResult;
import edu.ie3.simosaik.exceptions.ConversionException;
import edu.ie3.simosaik.utils.MosaikMessageParser.MosaikMessage;
import edu.ie3.simosaik.utils.SimosaikUtils.Tuple3;
import java.util.*;
import java.util.stream.Collectors;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

public class FlexUtils {

  private static final Logger log = LoggerFactory.getLogger(FlexUtils.class);

  public static ExtInputDataContainer build(
      long tick, Long maybeNextTick, List<MosaikMessage> mosaikMessages) {
    ExtInputDataContainer container = new ExtInputDataContainer(tick, maybeNextTick);

    getFlexRequests(mosaikMessages).forEach(container::addRequest);
    getSetPoint(mosaikMessages).forEach(container::addSetPoint);

    return container;
  }

  public static Map<String, Object> createOutput(
      ExtResultContainer resultContainer, Map<String, List<String>> requestedAttributes) {
    Map<String, Object> output = new HashMap<>();

    Map<String, FlexOptionsResult> flexResults =
        resultContainer.getResults(FlexOptionsResult.class);

    Map<String, EmSetPointResult> setPointResults =
        resultContainer.getResults(EmSetPointResult.class);

    for (Map.Entry<String, List<String>> requested : requestedAttributes.entrySet()) {
      String entity = requested.getKey();
      List<String> attrs = requested.getValue();

      Optional<Map<String, Double>> flexOptionsResult =
          Optional.ofNullable(flexResults.get(entity)).map(FlexUtils::getFlexMap);

      Optional<Map<String, Double>> setPointResult =
          Optional.ofNullable(setPointResults.get(entity)).flatMap(FlexUtils::getSetPoint);

      Map<String, Object> data = new HashMap<>();

      for (String attr : attrs) {
        switch (attr) {
          case MOSAIK_ACTIVE_POWER, MOSAIK_REACTIVE_POWER -> {
            Optional<Double> val = setPointResult.map(m -> m.get(attr));

            if (val.isPresent()) {
              data.put(attr, val.get());
            } else {
              log(entity, attr);
            }
          }
          case FLEX_OPTION_P_MIN,
              FLEX_OPTION_MAP_P_MIN,
              FLEX_OPTION_P_REF,
              FLEX_OPTION_MAP_P_REF,
              FLEX_OPTION_P_MAX,
              FLEX_OPTION_MAP_P_MAX -> {
            Optional<Double> val = flexOptionsResult.map(m -> m.get(attr));

            if (val.isPresent()) {
              data.put(attr, val.get());
            } else {
              log(entity, attr);
            }
          }
        }
      }

      output.put(entity, data);
    }

    return output;
  }

  public static Map<String, List<String>> getFlexRequests(
      Collection<MosaikMessage> mosaikMessages) {
    Map<String, List<String>> flexRequests = new HashMap<>();

    List<MosaikMessage> filtered = filterForUnit(mosaikMessages, FLEX_REQUEST);

    for (MosaikMessage mosaikMessage : filtered) {
      String sender = mosaikMessage.sender();
      String receiver = mosaikMessage.receiver();

      if (flexRequests.containsKey(sender)) {
        flexRequests.get(sender).add(receiver);
      } else {
        List<String> receivers = new ArrayList<>();
        receivers.add(receiver);
        flexRequests.put(sender, receivers);
      }
    }

    return flexRequests;
  }

  public static Map<String, PValue> getSetPoint(Collection<MosaikMessage> mosaikMessages) {
    Map<String, PValue> setPoints = new HashMap<>();

    List<Tuple3<ComparableQuantity<Power>>> activePowerMap =
        extract(mosaikMessages, MOSAIK_ACTIVE_POWER);
    List<Tuple3<ComparableQuantity<Power>>> reactivePowerMap =
        extract(mosaikMessages, MOSAIK_REACTIVE_POWER);

    Map<String, ComparableQuantity<Power>> activePowerToReceiver = toMap(activePowerMap);
    Map<String, ComparableQuantity<Power>> reactivePowerToReceiver = toMap(reactivePowerMap);

    Set<String> receivers = new HashSet<>(activePowerToReceiver.keySet());
    receivers.removeAll(reactivePowerToReceiver.keySet());

    for (String receiver : receivers) {
      Optional<ComparableQuantity<Power>> active =
          Optional.ofNullable(activePowerToReceiver.get(receiver));

      Optional<ComparableQuantity<Power>> reactive =
          Optional.ofNullable(reactivePowerToReceiver.get(receiver));

      if (active.isPresent() && reactive.isPresent()) {
        setPoints.put(receiver, new SValue(active.get(), reactive.get()));
      } else if (active.isPresent()) {
        setPoints.put(receiver, new PValue(active.get()));
      } else if (reactive.isPresent()) {
        setPoints.put(receiver, new SValue(Quantities.getQuantity(0d, KILOWATT), reactive.get()));
      } else {
        throw new ConversionException("No active or reactive power found for " + receiver);
      }
    }

    return setPoints;
  }

  private static Map<String, Double> getFlexMap(FlexOptionsResult flexOptionsResult) {
    double pMin = flexOptionsResult.getpMin().getValue().doubleValue();
    double pRef = flexOptionsResult.getpRef().getValue().doubleValue();
    double pMax = flexOptionsResult.getpMax().getValue().doubleValue();

    return Map.of(
        FLEX_OPTION_P_MIN, pMin,
        FLEX_OPTION_P_REF, pRef,
        FLEX_OPTION_P_MAX, pMax,
        FLEX_OPTION_MAP_P_MIN, pMin,
        FLEX_OPTION_MAP_P_REF, pRef,
        FLEX_OPTION_MAP_P_MAX, pMax);
  }

  private static Optional<Map<String, Double>> getSetPoint(EmSetPointResult setPoint) {
    return setPoint
        .getSetPoint()
        .map(
            value -> {
              double p = value.getP().map(quantity -> quantity.getValue().doubleValue()).orElse(0d);
              double q = 0d;

              if (value instanceof SValue sValue) {
                q = sValue.getQ().map(quantity -> quantity.getValue().doubleValue()).orElse(0d);
              }

              return Map.of(
                  MOSAIK_ACTIVE_POWER, p,
                  MOSAIK_REACTIVE_POWER, q);
            });
  }

  private static void log(String entity, String attr) {
    log.info("No data found for attribute '{}' for entity '{}'.", attr, entity);
  }

  private static Map<String, ComparableQuantity<Power>> toMap(
      List<Tuple3<ComparableQuantity<Power>>> list) {
    return list.stream().collect(Collectors.groupingBy(Tuple3::receiver)).entrySet().stream()
        .map(
            e -> {
              String receiver = e.getKey();

              Optional<ComparableQuantity<Power>> option = combineQuantities(e.getValue());

              return option.map(o -> Map.entry(receiver, o));
            })
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
