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
import edu.ie3.simona.api.data.em.model.FlexOptions;
import edu.ie3.simona.api.data.em.model.FlexRequestResult;
import edu.ie3.simosaik.exceptions.ConversionException;
import edu.ie3.simosaik.utils.MosaikMessageParser.MosaikMessage;
import edu.ie3.simosaik.utils.SimosaikUtils.Tuple3;
import edu.ie3.util.quantities.PowerSystemUnits;
import java.util.*;
import java.util.stream.Collectors;
import javax.measure.Unit;
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
    getFlexOptions(mosaikMessages).forEach(container::addFlexOptions);
    getSetPoint(mosaikMessages).forEach(container::addSetPoint);

    return container;
  }

  public static Map<String, Object> createOutput(
      ExtResultContainer resultContainer, Map<String, List<String>> requestedAttributes) {
    Map<String, Object> output = new HashMap<>();

    Map<String, FlexRequestResult> flexRequest =
        resultContainer.getResults(FlexRequestResult.class);

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
              log.info(
                  "Data found for attribute '{}' for entity '{}': {}.", attr, entity, val.get());

            } else {
              log(entity, attr);
            }
          }
          case FLEX_OPTION_P_MIN, FLEX_OPTION_P_REF, FLEX_OPTION_P_MAX -> {
            Optional<Double> val = flexOptionsResult.map(m -> m.get(attr));

            if (val.isPresent()) {
              data.put(attr, val.get());
              log.info(
                  "Data found for attribute '{}' for entity '{}': {}.", attr, entity, val.get());

            } else {
              log(entity, attr);
            }
          }
          case FLEX_REQUEST -> {
            if (flexRequest.containsKey(entity)) {
              data.put(attr, FLEX_REQUEST);
              log.info("Data found for attribute '{}' for entity '{}'.", attr, entity);

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

  public static Map<String, List<FlexOptions>> getFlexOptions(
      Collection<MosaikMessage> mosaikMessages) {
    Map<String, List<FlexOptions>> flexOptions = new HashMap<>();

    mosaikMessages.stream()
        .collect(Collectors.groupingBy(MosaikMessage::receiver))
        .forEach(
            (receiver, msgs) -> {
              Map<String, List<MosaikMessage>> senderToMessages =
                  msgs.stream().collect(Collectors.groupingBy(MosaikMessage::sender));

              List<FlexOptions> flexOptionsList = convert(senderToMessages);

              if (!flexOptionsList.isEmpty()) {
                flexOptions.put(receiver, flexOptionsList);
              }
            });

    return flexOptions;
  }

  @SuppressWarnings("unchecked")
  private static List<FlexOptions> convert(Map<String, List<MosaikMessage>> senderToMessage) {
    List<FlexOptions> flexOptions = new ArrayList<>();

    for (Map.Entry<String, List<MosaikMessage>> entry : senderToMessage.entrySet()) {
      String sender = entry.getKey();
      List<MosaikMessage> mosaikMessages = entry.getValue();

      Map<String, ComparableQuantity<Power>> unitToPower = new HashMap<>();

      for (MosaikMessage mosaikMessage : mosaikMessages) {
        String unit = mosaikMessage.unit();
        Object value = mosaikMessage.messageValue();

        if (value instanceof Double d) {
          unitToPower.put(unit, Quantities.getQuantity(d, (Unit<Power>) getPSDMUnit(unit)));
        } else {
          log.warn("Received value '{}' for unit '{}'.", value, unit);
        }
      }

      ComparableQuantity<Power> pMin = unitToPower.get(FLEX_OPTION_P_MIN);
      ComparableQuantity<Power> pRef = unitToPower.get(FLEX_OPTION_P_REF);
      ComparableQuantity<Power> pMax = unitToPower.get(FLEX_OPTION_P_MAX);


      if (pMin != null && pRef != null && pMax != null) {
        flexOptions.add(new FlexOptions(sender, pMin, pRef, pMax));
      } else {
        log.warn("Received values: pMin={}, pRef={}, pMax={}", pMin, pRef, pMax);
      }
    }

    return flexOptions;
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
    receivers.addAll(reactivePowerToReceiver.keySet());

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
    double pMin =
        flexOptionsResult.getpMin().to(PowerSystemUnits.MEGAWATT).getValue().doubleValue();
    double pRef =
        flexOptionsResult.getpRef().to(PowerSystemUnits.MEGAWATT).getValue().doubleValue();
    double pMax =
        flexOptionsResult.getpMax().to(PowerSystemUnits.MEGAWATT).getValue().doubleValue();

    return Map.of(
        FLEX_OPTION_P_MIN, pMin,
        FLEX_OPTION_P_REF, pRef,
        FLEX_OPTION_P_MAX, pMax);
  }

  private static Optional<Map<String, Double>> getSetPoint(EmSetPointResult setPoint) {
    return setPoint
        .getSetPoint()
        .map(
            value -> {
              double p =
                  value
                      .getP()
                      .map(
                          quantity ->
                              quantity.to(PowerSystemUnits.MEGAWATT).getValue().doubleValue())
                      .orElse(0d);
              double q = 0d;

              if (value instanceof SValue sValue) {
                q =
                    sValue
                        .getQ()
                        .map(
                            quantity ->
                                quantity.to(PowerSystemUnits.MEGAVAR).getValue().doubleValue())
                        .orElse(0d);
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
