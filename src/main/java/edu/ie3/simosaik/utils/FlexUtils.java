/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.utils.MosaikMessageParser.filterForUnit;
import static edu.ie3.simosaik.utils.MosaikMessageParser.trim;
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
import java.util.function.Function;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

class FlexUtils {
  private static final Logger log = LoggerFactory.getLogger(FlexUtils.class);

  /**
   * @param mosaikMessages to extract flex requests from
   * @param idToUuid mapping to convert mosaik eid to SIMONA uuid
   * @return map: receiver to option of sender
   */
  static Map<UUID, Optional<UUID>> getFlexRequests(
      Collection<MosaikMessage> mosaikMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<UUID, Optional<UUID>> flexRequests = new HashMap<>();

    List<MosaikMessage> filtered = filterForUnit(mosaikMessages, FLEX_REQUEST);

    for (MosaikMessage mosaikMessage : filtered) {
      UUID receiver = idToUuid.get(mosaikMessage.receiver());

      MultiValueMap<String, Object> unitToValues = mosaikMessage.unitToValues();

      log.warn("Received values: {}", unitToValues);

      if (unitToValues.containsKey(FLEX_REQUEST)) {

        Function<Object, Optional<UUID>> convert =
            obj -> {
              if (obj instanceof String str) {
                String sender = trim(str);
                // log.info("Converting sender '{}'.", sender);
                return Optional.ofNullable(idToUuid.get(sender));
              }
              return Optional.empty();
            };

        Optional<UUID> sender = unitToValues.getFirst(FLEX_REQUEST).flatMap(convert);

        if (flexRequests.containsKey(receiver)) {
          log.warn("Receiver {} has received flex request from multiple em agents!", receiver);
        } else {
          flexRequests.put(receiver, sender);
        }

      } else {
        log.warn("No sender found for mosaik message {}", mosaikMessage);
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

    mosaikMessages.forEach(
        mosaikMessage -> {
          UUID receiver = idToUuid.get(mosaikMessage.receiver());
          MultiValueMap<String, Object> unitToValues = mosaikMessage.unitToValues();

          Optional<Map<String, Map<String, Object>>> mapOption =
              unitToValues.getFirst(FLEX_OPTIONS);

          List<FlexOptions> flexOptionsList = new ArrayList<>();

          mapOption.ifPresent(
              map ->
                  map.values()
                      .forEach(
                          options ->
                              flexOptionsList.add(
                                  new FlexOptions(
                                      receiver,
                                      idToUuid.get((String) options.get("sender")),
                                      extract(options, FLEX_OPTION_P_MIN),
                                      extract(options, FLEX_OPTION_P_REF),
                                      extract(options, FLEX_OPTION_P_MAX)))));

          if (!flexOptionsList.isEmpty()) {
            flexOptions.put(receiver, flexOptionsList);
          }
        });

    return flexOptions;
  }

  static Map<UUID, PValue> getSetPoint(
      Collection<MosaikMessage> mosaikMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<UUID, PValue> setPoints = new HashMap<>();

    Map<String, ComparableQuantity<Power>> activePowerMap =
        extract(mosaikMessages, MOSAIK_ACTIVE_POWER);
    Map<String, ComparableQuantity<Power>> reactivePowerMap =
        extract(mosaikMessages, MOSAIK_REACTIVE_POWER);

    Set<String> receivers = new HashSet<>(activePowerMap.keySet());
    receivers.addAll(reactivePowerMap.keySet());

    for (String receiver : receivers) {
      Optional<ComparableQuantity<Power>> active =
          Optional.ofNullable(activePowerMap.get(receiver));

      Optional<ComparableQuantity<Power>> reactive =
          Optional.ofNullable(reactivePowerMap.get(receiver));

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

  static <Q extends Quantity<Q>> ComparableQuantity<Q> extract(
      Map<String, Object> map, String field) {
    double d = (double) map.get(field);
    Unit<Q> unit = getPSDMUnit(field);
    return Quantities.getQuantity(d, unit);
  }

  static <Q extends Quantity<Q>> Map<String, ComparableQuantity<Q>> extract(
      Collection<MosaikMessage> messages, String unit) {
    Map<String, ComparableQuantity<Q>> result = new HashMap<>();

    for (MosaikMessage message : messages) {
      MultiValueMap<String, Object> map = message.unitToValues();

      // all values that are not null and of type double
      List<Double> values =
          map.get(unit).stream()
              .filter(Objects::nonNull)
              .map(
                  o -> {
                    if (o instanceof Double d) {
                      return d;
                    } else {
                      return null;
                    }
                  })
              .filter(Objects::nonNull)
              .toList();

      if (!values.isEmpty()) {
        String receiver = message.receiver();
        Unit<Q> pdsmUnit = getPSDMUnit(unit);

        double sum = 0d;

        for (double d : values) {
          sum += d;
        }

        result.put(receiver, Quantities.getQuantity(sum, pdsmUnit));
      }
    }

    return result;
  }
}
