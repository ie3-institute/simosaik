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

import edu.ie3.datamodel.models.result.CongestionResult;
import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.connector.ConnectorResult;
import edu.ie3.datamodel.models.result.connector.LineResult;
import edu.ie3.datamodel.models.result.system.ElectricalEnergyStorageResult;
import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import edu.ie3.datamodel.models.result.system.SystemParticipantWithHeatResult;
import edu.ie3.datamodel.models.value.HeatAndPValue;
import edu.ie3.datamodel.models.value.HeatAndSValue;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.simona.api.data.container.ExtOutputContainer;
import edu.ie3.simona.api.data.model.em.*;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.util.interval.ClosedInterval;
import java.util.*;
import java.util.stream.Collectors;
import javax.measure.quantity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;

public final class OutputUtils {
  private static final Logger log = LoggerFactory.getLogger(OutputUtils.class);

  public static Map<String, Object> onlyTickInformation(
      Map<String, List<String>> requestedAttributes, long nextTick) {
    Map<String, Object> output = new HashMap<>();

    for (Map.Entry<String, List<String>> entry : requestedAttributes.entrySet()) {
      String externalEntity = entry.getKey();
      List<String> attrs = entry.getValue();

      if (attrs.contains(SIMONA_NEXT_TICK)) {
        output.put(externalEntity, Map.of(SIMONA_NEXT_TICK, nextTick));
      }
    }

    return output;
  }

  public static Map<String, Object> createOutput(
      ExtOutputContainer container,
      Map<String, List<String>> requestedAttributes,
      ExtEntityMapping mapping) {
    log.debug("Requested attributes: {}", requestedAttributes);
    log.debug("Result container: {}", container.getResults());

    Map<String, Object> output = new HashMap<>();

    for (Map.Entry<String, List<String>> entry : requestedAttributes.entrySet()) {
      String externalEntity = entry.getKey();
      List<String> attrs = entry.getValue();

      UUID asset = mapping.from(externalEntity);

      List<ResultEntity> results = container.getResult(asset);
      List<EmData> emData = container.getEmData(asset);

      // log.info("{} ({}): {}, {}", externalEntity, asset, results, emData);

      if (!results.isEmpty() || !emData.isEmpty()) {
        Map<String, Object> data = new HashMap<>();

        // handle results
        for (ResultEntity result : results) {
          data.putAll(handleResult(result, attrs, mapping));
        }

        // handle em data
        emData.stream()
            .map(d -> handleEmData(d, mapping))
            .collect(Collectors.groupingBy(ProcessedEmData::attr))
            .forEach(
                (attr, processedEmDataList) -> {
                  if (attr.equals(FLEX_COM)) {
                    data.put(
                        attr, processedEmDataList.stream().map(ProcessedEmData::data).toList());
                  } else {
                    data.put(attr, processedEmDataList.getFirst().data);
                  }
                });

        if (!data.isEmpty()) {
          output.put(externalEntity, data);
        }

      } else {
        log.debug("No results or em data found for asset {}.", externalEntity);
      }
    }

    return output;
  }

  private static Map<String, Object> handleResult(
      ResultEntity result, List<String> attrs, ExtEntityMapping mapping) {
    return switch (result) {
      case FlexOptionsResult options ->
          handleFlexOptionResults(options, attrs, mapping.getExtUuid2IdMapping());
      case SystemParticipantResult participant -> handleParticipantResult(participant, attrs);
      case NodeResult n -> {
        Map<String, Object> data = new HashMap<>();

        if (attrs.contains(VOLTAGE_MAG)) {
          data.put(VOLTAGE_MAG, toPu(n.getvMag()));
        }

        if (attrs.contains(VOLTAGE_ANG)) {
          data.put(VOLTAGE_ANG, toRadians(n.getvAng()));
        }

        yield data;
      }
      case ConnectorResult connector -> handleConnectorResult(connector, attrs);

      case CongestionResult congestion -> {
        Map<String, Object> data = new HashMap<>();

        if (attrs.contains(CONGESTION)) {
          data.put("model", mapping.from(congestion.getInputModel()));
          data.put("subgrid", congestion.getSubgrid());
          data.put("type", congestion.getType().type);
          data.put("value", toPercent(congestion.getValue()));
          data.put("min", toPercent(congestion.getMin()));
          data.put("max", toPercent(congestion.getMax()));
        }

        yield Map.of(CONGESTION, data);
      }

      case null, default -> {
        if (result != null) {
          log.warn("Result of type '{}' is currently not supported.", result.getClass());
        }

        yield Collections.emptyMap();
      }
    };
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

  private static ProcessedEmData handleEmData(EmData emData, ExtEntityMapping mapping) {
    return switch (emData) {
      case FlexOptionRequest(UUID receiver, boolean disaggregated) -> {
        Map<String, Object> data = new HashMap<>();
        data.put("receiver", mapping.from(receiver));
        data.put("disaggregated", disaggregated);
        yield new ProcessedEmData(FLEX_REQUEST, data);
      }
      case FlexOptions o -> new ProcessedEmData(FLEX_OPTIONS, handleFlexOptions(o, mapping, true));

      case EmSetPoint(UUID receiver, Optional<PValue> power, Map<UUID, PValue> disaggregated) -> {
        Map<String, Object> data = new HashMap<>();
        data.put("receiver", mapping.from(receiver));

        // only add power if it is not empty
        power.ifPresent(powerValue -> data.putAll(fromPValue(powerValue)));

        // handle disaggregated set points
        Map<String, Map<String, Double>> disaggregatedSetPoints = new HashMap<>();
        disaggregated.forEach(
            (uuid, setPoint) ->
                disaggregatedSetPoints.put(mapping.from(uuid), fromPValue(setPoint)));

        data.put("disaggregated", disaggregatedSetPoints);

        yield new ProcessedEmData(FLEX_SET_POINT, data);
      }

      case EmCommunicationMessage(UUID receiver, UUID sender, UUID msgId, EmData content) -> {
        Map<String, Object> data = new HashMap<>();
        data.put("receiver", mapping.from(receiver));
        data.put("sender", mapping.from(sender));
        data.put("msg_id", msgId.toString());

        // handle content
        ProcessedEmData processedContent = handleEmData(content, mapping);

        data.put("type", processedContent.attr);
        data.put("content", processedContent.data);

        yield new ProcessedEmData(FLEX_COM, data);
      }

      case null, default -> {
        log.warn("Result of type '{}' is currently not supported.", emData);
        yield new ProcessedEmData("", Collections.emptyMap());
      }
    };
  }

  private static Map<String, Object> handleFlexOptions(
      FlexOptions options, ExtEntityMapping mapping, boolean considerDisaggregated) {
    Map<UUID, FlexOptions> disaggregated = options.disaggregated();
    Map<String, Object> res = new HashMap<>();

    // handling of receiver
    res.put("receiver", mapping.from(options.receiver()));

    switch (options) {
      case PowerLimitFlexOptions(
          UUID receiver,
          UUID model,
          ComparableQuantity<Power> pRef,
          ComparableQuantity<Power> pMin,
          ComparableQuantity<Power> pMax,
          Map<UUID, FlexOptions> d) -> {
        res.put("model", mapping.from(model));
        res.put(FLEX_OPTION_P_REF, toActive(pRef));
        res.put(FLEX_OPTION_P_MIN, toActive(pMin));
        res.put(FLEX_OPTION_P_MAX, toActive(pMax));
      }

      case EnergyBoundariesFlexOptions(
          UUID receiver,
          UUID model,
          String flexType,
          ComparableQuantity<Power> pMin,
          ComparableQuantity<Power> pMax,
          ComparableQuantity<Dimensionless> etaCharge,
          ComparableQuantity<Dimensionless> etaDischarge,
          Map<Long, ClosedInterval<ComparableQuantity<Energy>>> tickToEnergyLimits,
          Map<UUID, FlexOptions> d) -> {
        res.put("model", mapping.from(model));
        res.put("flexType", flexType);

        res.put(FLEX_OPTION_P_MIN, toActive(pMin));
        res.put(FLEX_OPTION_P_MAX, toActive(pMax));

        res.put("eta_charge", toPercent(etaCharge));
        res.put("eta_discharge", toPercent(etaDischarge));

        log.warn("Tick to energy limits is currently not supported.");
      }

      default -> log.warn("Result of type '{}' is currently not supported.", options);
    }

    // handling of disaggregated flex options
    if (considerDisaggregated) {
      res.put(
          "disaggregated",
          disaggregated.entrySet().stream()
              .collect(
                  Collectors.toMap(
                      Map.Entry::getKey, e -> handleFlexOptions(e.getValue(), mapping, false))));
    }

    return res;
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
      String model = uuidToId.get(extended.getInputModel());
      data.put("model", model);

      data.put(FLEX_OPTION_P_MIN, pMin);
      data.put(FLEX_OPTION_P_REF, pRef);
      data.put(FLEX_OPTION_P_MAX, pMax);

      return Map.of(FLEX_OPTIONS, data);

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
  public static Map<String, Double> fromPValue(PValue pValue) {
    Map<String, Double> data = new HashMap<>();

    pValue.getP().ifPresent(p -> data.put(ACTIVE_POWER, toActive(p)));

    if (pValue instanceof SValue sValue) {
      sValue.getQ().ifPresent(q -> data.put(REACTIVE_POWER, toReactive(q)));
    }

    if (pValue instanceof HeatAndPValue hValue) {
      hValue.getHeatDemand().ifPresent(h -> data.put(THERMAL_POWER, toActive(h)));
    } else if (pValue instanceof HeatAndSValue hValue) {
      hValue.getHeatDemand().ifPresent(h -> data.put(THERMAL_POWER, toActive(h)));
    }

    return data;
  }

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

  // helper class
  private record ProcessedEmData(String attr, Map<String, Object> data) {}
}
