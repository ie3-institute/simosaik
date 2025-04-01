/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.utils.MosaikMessageParser.filterForUnit;
import static edu.ie3.simosaik.utils.SimosaikTranslation.*;
import static edu.ie3.util.quantities.PowerSystemUnits.KILOWATT;

import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.simona.api.data.em.model.EmSetPointResult;
import edu.ie3.simona.api.data.em.model.FlexOptions;
import edu.ie3.simosaik.exceptions.ConversionException;
import edu.ie3.simosaik.utils.MosaikMessageParser.MosaikMessage;
import edu.ie3.util.quantities.PowerSystemUnits;
import java.util.*;
import java.util.stream.Collectors;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

class FlexUtils {
  private static final Logger log = LoggerFactory.getLogger(FlexUtils.class);

  static Map<UUID, List<UUID>> getFlexRequests(
      Collection<MosaikMessage> mosaikMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<UUID, List<UUID>> flexRequests = new HashMap<>();

    List<MosaikMessage> filtered = filterForUnit(mosaikMessages, FLEX_REQUEST);

    for (MosaikMessage mosaikMessage : filtered) {
      UUID sender = idToUuid.get(mosaikMessage.sender());
      UUID receiver = idToUuid.get(mosaikMessage.receiver());

      if (flexRequests.containsKey(sender)) {
        flexRequests.get(sender).add(receiver);
      } else {
        List<UUID> receivers = new ArrayList<>();
        receivers.add(receiver);
        flexRequests.put(sender, receivers);
      }
    }

    return flexRequests;
  }

  static Map<UUID, List<FlexOptions>> getFlexOptions(
      Collection<MosaikMessage> mosaikMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<UUID, List<FlexOptions>> flexOptions = new HashMap<>();

    mosaikMessages.stream()
        .collect(Collectors.groupingBy(MosaikMessage::receiver))
        .forEach(
            (receiver, msgs) -> {
              Map<String, List<MosaikMessage>> senderToMessages =
                  msgs.stream().collect(Collectors.groupingBy(MosaikMessage::sender));

              List<FlexOptions> flexOptionsList = convert(senderToMessages, idToUuid);

              if (!flexOptionsList.isEmpty()) {
                flexOptions.put(idToUuid.get(receiver), flexOptionsList);
              }
            });

    return flexOptions;
  }

  @SuppressWarnings("unchecked")
  private static List<FlexOptions> convert(
      Map<String, List<MosaikMessage>> senderToMessage, Map<String, UUID> idToUuid) {
    List<FlexOptions> flexOptions = new ArrayList<>();

    for (Map.Entry<String, List<MosaikMessage>> entry : senderToMessage.entrySet()) {
      UUID sender = idToUuid.get(entry.getKey());
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

  static Map<UUID, PValue> getSetPoint(
      Collection<MosaikMessage> mosaikMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<UUID, PValue> setPoints = new HashMap<>();

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

      UUID receiverId = idToUuid.get(receiver);

      if (active.isPresent() && reactive.isPresent()) {
        setPoints.put(receiverId, new SValue(active.get(), reactive.get()));
      } else if (active.isPresent()) {
        setPoints.put(receiverId, new PValue(active.get()));
      } else if (reactive.isPresent()) {
        setPoints.put(receiverId, new SValue(Quantities.getQuantity(0d, KILOWATT), reactive.get()));
      } else {
        throw new ConversionException("No active or reactive power found for " + receiver);
      }
    }

    return setPoints;
  }

  static Map<String, Double> getFlexMap(FlexOptionsResult flexOptionsResult) {
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

  static Optional<Map<String, Double>> getSetPoint(EmSetPointResult setPoint) {
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

  static Map<String, ComparableQuantity<Power>> toMap(
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

  record Tuple3<T>(String sender, String receiver, T value) {}

  static <Q extends Quantity<Q>> List<Tuple3<ComparableQuantity<Q>>> extract(
      Collection<MosaikMessageParser.MosaikMessage> messages, String unit) {
    List<Tuple3<ComparableQuantity<Q>>> tuples = new ArrayList<>();

    for (MosaikMessageParser.MosaikMessage message : messages) {
      if (message.unit().equals(unit) && message.messageValue() != null) {

        Unit<Q> pdsmUnit = getPSDMUnit(message.unit());
        double value = (double) message.messageValue();

        tuples.add(
            new Tuple3<>(
                message.sender(), message.receiver(), Quantities.getQuantity(value, pdsmUnit)));
      }
    }

    return tuples;
  }

  static <Q extends Quantity<Q>> Optional<ComparableQuantity<Q>> combineQuantities(
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
}
