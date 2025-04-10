/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.simona.api.data.container.ExtResultContainer;
import edu.ie3.simona.api.data.em.model.EmSetPointResult;
import edu.ie3.simona.api.data.em.model.ExtendedFlexOptionsResult;
import edu.ie3.simona.api.data.em.model.FlexRequestResult;
import edu.ie3.simona.api.data.mapping.DataType;
import edu.ie3.simona.api.data.mapping.ExtEntityMapping;
import edu.ie3.util.quantities.PowerSystemUnits;
import java.util.*;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;

public class ResultUtils {
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

    } else if (result instanceof ExtendedFlexOptionsResult options
        && attrs.contains(FLEX_OPTIONS)) {
      Map<String, Object> data = new HashMap<>();
      String receiver = uuidToId.get(options.getReceiver());
      String sender = uuidToId.get(options.getSender());

      data.put("receiver", receiver);
      data.put("sender", sender);
      data.put(FLEX_OPTION_P_MIN, toDouble(options.getpMin()));
      data.put(FLEX_OPTION_P_REF, toDouble(options.getpRef()));
      data.put(FLEX_OPTION_P_MAX, toDouble(options.getpMax()));

      log.warn("Options: {}", data);

      return Map.of(FLEX_OPTIONS, data);

    } else if (result instanceof FlexOptionsResult options) {
      Map<String, Object> data = new HashMap<>();

      if (attrs.contains(FLEX_OPTION_P_MIN)) {
        data.put(FLEX_OPTION_P_MIN, toDouble(options.getpMin()));
      }

      if (attrs.contains(FLEX_OPTION_P_REF)) {
        data.put(FLEX_OPTION_P_REF, toDouble(options.getpRef()));
      }

      if (attrs.contains(FLEX_OPTION_P_MAX)) {
        data.put(FLEX_OPTION_P_MAX, toDouble(options.getpMax()));
      }

      return data;

    } else if (result instanceof EmSetPointResult setPoint && attrs.contains(MOSAIK_ACTIVE_POWER)) {
      Optional<Double> optional =
          setPoint.getSetPoint().flatMap(PValue::getP).map(ResultUtils::toDouble);
      if (optional.isPresent()) {
        return Map.of(MOSAIK_ACTIVE_POWER, optional.get());
      }

    } else if (result instanceof SystemParticipantResult) {
      log.warn("Participant result handling currently not implemented.");

    } else if (result instanceof NodeResult n && attrs.contains(MOSAIK_VOLTAGE_PU)) {
      return Map.of(
          MOSAIK_VOLTAGE_PU, n.getvMag().to(PowerSystemUnits.PU).getValue().doubleValue());
    }

    return Collections.emptyMap();
  }

  private static Map<String, Object> flexResults(
      ExtResultContainer container,
      Map<String, List<String>> requestedAttributes,
      ExtEntityMapping mapping) {
    Map<String, UUID> idToUuid = mapping.getExtId2UuidMapping(DataType.EXT_EM_INPUT);

    Map<String, Object> output = new HashMap<>();

    Map<UUID, FlexRequestResult> flexRequest = container.getResults(FlexRequestResult.class);

    Map<UUID, FlexOptionsResult> flexResults = container.getResults(FlexOptionsResult.class);

    Map<UUID, EmSetPointResult> setPointResults = container.getResults(EmSetPointResult.class);

    for (Map.Entry<String, List<String>> requested : requestedAttributes.entrySet()) {
      String entity = requested.getKey();
      List<String> attrs = requested.getValue();

      if (idToUuid.containsKey(entity)) {
        UUID asset = idToUuid.get(entity);

        Optional<Map<String, Double>> flexOptionsResult =
            Optional.ofNullable(flexResults.get(asset)).map(FlexUtils::getFlexMap);

        Optional<Map<String, Double>> setPointResult =
            Optional.ofNullable(setPointResults.get(asset)).flatMap(FlexUtils::getSetPoint);

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
                log.info("No data found for attribute '{}' for entity '{}'.", attr, entity);
              }
            }
            case FLEX_OPTION_P_MIN, FLEX_OPTION_P_REF, FLEX_OPTION_P_MAX -> {
              Optional<Double> val = flexOptionsResult.map(m -> m.get(attr));

              if (val.isPresent()) {
                data.put(attr, val.get());
                log.info(
                    "Data found for attribute '{}' for entity '{}': {}.", attr, entity, val.get());

              } else {
                log.info("No data found for attribute '{}' for entity '{}'.", attr, entity);
              }
            }
            case FLEX_REQUEST -> {
              if (flexRequest.containsKey(asset)) {
                data.put(attr, FLEX_REQUEST);
                log.info("Data found for attribute '{}' for entity '{}'.", attr, entity);

              } else {
                log.info("No data found for attribute '{}' for entity '{}'.", attr, entity);
              }
            }
          }
        }

        output.put(entity, data);
      } else {
        log.warn("Entity with id '{}' not found.", entity);
      }
    }

    return output;
  }

  public static double toDouble(ComparableQuantity<Power> power) {
    return power.to(PowerSystemUnits.MEGAWATT).getValue().doubleValue();
  }
}
