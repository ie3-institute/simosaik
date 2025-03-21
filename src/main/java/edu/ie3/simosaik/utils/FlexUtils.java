/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.simona.api.data.datacontainer.ExtInputDataContainer;
import edu.ie3.simosaik.exceptions.ConversionException;
import edu.ie3.simosaik.utils.MosaikMessageParser.MosaikMessage;
import edu.ie3.simosaik.utils.SimosaikUtils.Tuple3;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.quantity.Power;
import java.util.*;
import java.util.stream.Collectors;

import static edu.ie3.simosaik.utils.MosaikMessageParser.filterForUnit;
import static edu.ie3.simosaik.utils.SimosaikTranslation.*;
import static edu.ie3.simosaik.utils.SimosaikUtils.combineQuantities;
import static edu.ie3.simosaik.utils.SimosaikUtils.extract;
import static edu.ie3.util.quantities.PowerSystemUnits.KILOWATT;

public class FlexUtils {

  public static ExtInputDataContainer build(
          long tick,
          Long maybeNextTick,
          List<MosaikMessage> mosaikMessages
  ) {
    ExtInputDataContainer container = new ExtInputDataContainer(tick, maybeNextTick);

    getFlexRequests(mosaikMessages).forEach(container::addRequest);
    getSetPoint(mosaikMessages).forEach(container::addSetPoint);

    return container;
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
