/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimosaikUnits.*;
import static edu.ie3.util.quantities.PowerSystemUnits.*;
import static tech.units.indriya.unit.Units.AMPERE;
import static tech.units.indriya.unit.Units.RADIAN;

import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.connector.ConnectorResult;
import edu.ie3.datamodel.models.result.connector.LineResult;
import edu.ie3.datamodel.models.result.system.ElectricalEnergyStorageResult;
import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import edu.ie3.datamodel.models.result.system.SystemParticipantWithHeatResult;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simona.api.data.em.model.EmSetPointResult;
import edu.ie3.simona.api.data.em.model.ExtendedFlexOptionsResult;
import edu.ie3.simona.api.data.em.model.FlexRequestResult;
import edu.ie3.simona.api.data.mapping.ExtEntityMapping;
import java.util.*;
import javax.measure.quantity.Angle;
import javax.measure.quantity.Dimensionless;
import javax.measure.quantity.ElectricCurrent;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;

public final class ResultUtils {
  private static final Logger log = LoggerFactory.getLogger(ResultUtils.class);

  public static Map<String, Object> createOutput(
      ExtResultContainer container,
      Map<String, List<String>> requestedAttributes,
      ExtEntityMapping mapping) {
    log.info("Requested attributes: {}", requestedAttributes);

    Map<String, UUID> idToUuid = mapping.getFullMapping();
    Map<UUID, String> uuidToId = mapping.getFullMappingReverse();

    Map<String, Object> output = new HashMap<>();

    for (Map.Entry<String, List<String>> entry : requestedAttributes.entrySet()) {
      String externalEntity = entry.getKey();
      List<String> attrs = entry.getValue();

      if (idToUuid.containsKey(externalEntity)) {
        UUID asset = idToUuid.get(externalEntity);

        ResultEntity result = container.getResult(asset);

        Map<String, Object> data = handleResult(result, attrs, uuidToId);

        if (!data.isEmpty()) {
          output.put(externalEntity, data);
        }

      } else {
        log.info("No results found for asset {}.", externalEntity);
      }
    }

    return output;
  }

  private static Map<String, Object> handleResult(
      ResultEntity result, List<String> attrs, Map<UUID, String> uuidToId) {

    if (result instanceof FlexRequestResult r && attrs.contains(FLEX_REQUEST)) {
      Map<String, Object> data = new HashMap<>();
      data.put(FLEX_REQUEST, r.getReceivers().stream().map(uuidToId::get).toList());
      return data;

    } else if (result instanceof EmSetPointResult setPointResult
        && attrs.contains(FLEX_SET_POINT)) {
      return handleEmSetPointResult(setPointResult, uuidToId);

    } else if (result instanceof FlexOptionsResult options) {
      return handleFlexOptionResults(options, attrs, uuidToId);

    } else if (result instanceof SystemParticipantResult participant) {
      return handleParticipantResult(participant, attrs);

    } else if (result instanceof NodeResult n) {
      Map<String, Object> data = new HashMap<>();

      if (attrs.contains(VOLTAGE_MAG)) {
        data.put(VOLTAGE_MAG, toPu(n.getvMag()));
      }

      if (attrs.contains(VOLTAGE_ANG)) {
        data.put(VOLTAGE_ANG, toRadians(n.getvAng()));
      }

      return data;

    } else if (result instanceof ConnectorResult connector) {
      return handleConnectorResult(connector, attrs);

    } else {
      if (result != null) {
        log.info("Result of type '{}' is currently not supported.", result.getClass());
      }

      return Collections.emptyMap();
    }
  }

  private static Map<String, Object> handleConnectorResult(
      ConnectorResult result, List<String> attrs) {
    if (result instanceof LineResult line) {
      Map<String, Object> data = new HashMap<>();

      boolean portA = line.getiAMag().isGreaterThanOrEqualTo(line.getiBMag());

      if (attrs.contains(CURRENT_MAG)) {
        ComparableQuantity<ElectricCurrent> current = portA ? line.getiAMag() : line.getiBMag();
        data.put(CURRENT_MAG, toAmpere(current));
      }

      if (attrs.contains(CURRENT_ANG)) {
        ComparableQuantity<Angle> angle = portA ? line.getiAAng() : line.getiBAng();
        data.put(CURRENT_ANG, toRadians(angle));
      }

      return data;
    } else {
      if (result != null) {
        log.warn("Connector result of type '{}' are not supported.", result.getClass());
      }
    }

    return Collections.emptyMap();
  }

  private static Map<String, Object> handleParticipantResult(
      SystemParticipantResult result, List<String> attrs) {
    Map<String, Object> data = new HashMap<>();

    if (attrs.contains(ACTIVE_POWER)) {
      data.put(ACTIVE_POWER, toActive(result.getP()));
    }

    if (attrs.contains(REACTIVE_POWER)) {
      data.put(REACTIVE_POWER, toActive(result.getQ()));
    }

    if (result instanceof SystemParticipantWithHeatResult withHeat
        && attrs.contains(THERMAL_POWER)) {
      data.put(THERMAL_POWER, toActive(withHeat.getqDot()));
    }

    if (result instanceof ElectricalEnergyStorageResult storage && attrs.contains(SOC)) {
      data.put(SOC, toPercent(storage.getSoc()));
    }

    return data;
  }

  private static Map<String, Object> handleEmSetPointResult(
      EmSetPointResult setPointResult, Map<UUID, String> uuidToId) {
    String sender = uuidToId.get(setPointResult.getSender());
    Map<String, Object> dataMap = new HashMap<>();

    setPointResult
        .getReceiverToSetPoint()
        .forEach(
            (receiverUuid, setPoint) -> {
              String receiver = uuidToId.get(receiverUuid);

              Double active = setPoint.getP().map(ResultUtils::toActive).orElse(null);
              Double reactive = null;

              if (setPoint instanceof SValue sValue) {
                reactive = sValue.getQ().map(ResultUtils::toReactive).orElse(null);
              }

              Map<String, Object> data = new HashMap<>();
              data.put("receiver", receiver);
              data.put("sender", sender);
              data.put(ACTIVE_POWER, active);
              data.put(REACTIVE_POWER, reactive);

              dataMap.put(receiver, data);
            });

    return Map.of(FLEX_SET_POINT, dataMap);
  }

  private static Map<String, Object> handleFlexOptionResults(
      FlexOptionsResult result, List<String> attrs, Map<UUID, String> uuidToId) {
    // get all information
    double pMin = toActive(result.getpMin());
    double pRef = toActive(result.getpRef());
    double pMax = toActive(result.getpMax());

    Map<String, Double> connectedPmin = new HashMap<>();
    Map<String, Double> connectedPref = new HashMap<>();
    Map<String, Double> connectedPmax = new HashMap<>();

    // add aggregated flex options
    connectedPmin.put("EM", pMin);
    connectedPref.put("EM", pRef);
    connectedPmax.put("EM", pMax);

    if (result instanceof ExtendedFlexOptionsResult extended) {
      // add disaggregated flex options
      Map<UUID, FlexOptionsResult> disaggregatedOptions = extended.getDisaggregated();

      for (UUID uuid : disaggregatedOptions.keySet()) {
        String id = uuidToId.get(uuid);
        FlexOptionsResult partialOption = disaggregatedOptions.get(uuid);

        connectedPmin.put(id, toActive(partialOption.getpMin()));
        connectedPref.put(id, toActive(partialOption.getpRef()));
        connectedPmax.put(id, toActive(partialOption.getpMax()));
      }
    }

    Map<String, Object> data = new HashMap<>();

    if (result instanceof ExtendedFlexOptionsResult extended && attrs.contains(FLEX_OPTIONS)) {
      String receiver = uuidToId.get(extended.getReceiver());
      String sender = uuidToId.get(extended.getSender());

      data.put("receiver", receiver);
      data.put("sender", sender);

      data.put(FLEX_OPTION_P_MIN, pMin);
      data.put(FLEX_OPTION_P_REF, pRef);
      data.put(FLEX_OPTION_P_MAX, pMax);

      log.warn("Options: {}", data);
      return Map.of(FLEX_OPTIONS, data);

    } else if (result instanceof ExtendedFlexOptionsResult extended
        && attrs.contains(FLEX_OPTIONS_DISAGGREGATED)) {
      String receiver = uuidToId.get(extended.getReceiver());
      String sender = uuidToId.get(extended.getSender());

      data.put("receiver", receiver);
      data.put("sender", sender);

      data.put(FLEX_OPTION_MAP_P_MIN, connectedPmin);
      data.put(FLEX_OPTION_MAP_P_REF, connectedPref);
      data.put(FLEX_OPTION_MAP_P_MAX, connectedPmax);

      return Map.of(FLEX_OPTIONS_DISAGGREGATED, data);

    } else {
      if (attrs.contains(FLEX_OPTION_P_MIN)) data.put(FLEX_OPTION_P_MIN, pMin);

      if (attrs.contains(FLEX_OPTION_P_REF)) data.put(FLEX_OPTION_P_REF, pRef);

      if (attrs.contains(FLEX_OPTION_P_MAX)) data.put(FLEX_OPTION_P_MAX, pMax);

      if (attrs.contains(FLEX_OPTION_MAP_P_MIN)) data.put(FLEX_OPTION_MAP_P_MIN, connectedPmin);

      if (attrs.contains(FLEX_OPTION_MAP_P_REF)) data.put(FLEX_OPTION_MAP_P_REF, connectedPref);

      if (attrs.contains(FLEX_OPTION_MAP_P_MAX)) data.put(FLEX_OPTION_MAP_P_MAX, connectedPmax);

      return data;
    }
  }

  // converting results
  public static double toActive(ComparableQuantity<Power> c) {
    return c.to(MEGAWATT).getValue().doubleValue();
  }

  public static double toReactive(ComparableQuantity<Power> c) {
    return c.to(MEGAVAR).getValue().doubleValue();
  }

  public static double toPu(ComparableQuantity<Dimensionless> c) {
    return c.to(PU).getValue().doubleValue();
  }

  public static double toAmpere(ComparableQuantity<ElectricCurrent> c) {
    return c.to(AMPERE).getValue().doubleValue();
  }

  public static double toRadians(ComparableQuantity<Angle> c) {
    return c.to(RADIAN).getValue().doubleValue();
  }

  public static double toPercent(ComparableQuantity<Dimensionless> c) {
    return c.to(PERCENT).getValue().doubleValue();
  }
}
