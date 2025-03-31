package edu.ie3.simosaik.utils;

import edu.ie3.datamodel.models.result.NodeResult;
import edu.ie3.datamodel.models.result.ResultEntity;
import edu.ie3.datamodel.models.result.system.FlexOptionsResult;
import edu.ie3.datamodel.models.result.system.SystemParticipantResult;
import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.simona.api.data.datacontainer.ExtResultContainer;
import edu.ie3.simona.api.data.em.model.EmSetPointResult;
import edu.ie3.simona.api.data.em.model.FlexRequestResult;
import edu.ie3.simona.api.simulation.mapping.DataType;
import edu.ie3.simona.api.simulation.mapping.ExtEntityMapping;
import edu.ie3.util.quantities.PowerSystemUnits;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

public class ResultUtils {
    private static final Logger log = LoggerFactory.getLogger(ResultUtils.class);


    public static Map<String, Object> createOutput(
            ExtResultContainer container,
            Map<String, List<String>> requestedAttributes,
            ExtEntityMapping mapping
    ) {
        Map<String, UUID> idToUuid = mapping.getFullMapping();
        Map<String, Object> output = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : requestedAttributes.entrySet()) {
            String receiver = entry.getKey();
            List<String> attrs = entry.getValue();

            if (idToUuid.containsKey(receiver)) {
                UUID asset = idToUuid.get(receiver);

                ResultEntity result = container.getResult(asset);

                Map<String, Object> data = handleResult(result, attrs);

                if (!data.isEmpty()) {
                    output.put(receiver, data);
                }

            } else {
                log.info("No results found for asset {}.", receiver);
            }
        }

        return output;
    }

    private static Map<String, Object> handleResult(
            ResultEntity result,
            List<String> attrs
    ) {

        if (result instanceof FlexRequestResult) {
            if (attrs.contains(FLEX_REQUEST)) {
                return Map.of(FLEX_REQUEST, FLEX_REQUEST);
            }
        } else if (result instanceof FlexOptionsResult options) {
            Map<String, Object> data = new HashMap<>();

            if (attrs.contains(FLEX_OPTION_P_MIN)) {
                data.put(FLEX_OPTION_P_MIN, options.getpMin().to(PowerSystemUnits.MEGAWATT).getValue().doubleValue());
            }

            if (attrs.contains(FLEX_OPTION_P_REF)) {
                data.put(FLEX_OPTION_P_REF, options.getpRef().to(PowerSystemUnits.MEGAWATT).getValue().doubleValue());
            }

            if (attrs.contains(FLEX_OPTION_P_MAX)) {
                data.put(FLEX_OPTION_P_MAX, options.getpMax().to(PowerSystemUnits.MEGAWATT).getValue().doubleValue());
            }

            return data;
        } else if (result instanceof EmSetPointResult setPoint) {
            if (attrs.contains(MOSAIK_ACTIVE_POWER)) {
                Optional<Double> optional = setPoint.getSetPoint().flatMap(PValue::getP).map(p -> p.to(PowerSystemUnits.MEGAWATT).getValue().doubleValue());

                if (optional.isPresent()) {
                    return Map.of(MOSAIK_ACTIVE_POWER, optional.get());
                }
            }
        } else if (result instanceof SystemParticipantResult) {
            log.warn("Participant result handling currently not implemented.");
        } else if (result instanceof NodeResult n) {
            if (attrs.contains(MOSAIK_VOLTAGE_PU)) {
                return Map.of(MOSAIK_VOLTAGE_PU, n.getvMag().to(PowerSystemUnits.PU).getValue().doubleValue());
            }
        }

       return Collections.emptyMap();
    }


    private static Map<String, Object> flexResults(
            ExtResultContainer container,
            Map<String, List<String>> requestedAttributes,
            ExtEntityMapping mapping
    ) {
        Map<String, UUID> idToUuid = mapping.getExtId2UuidMapping(DataType.EXT_EM_INPUT);

        Map<String, Object> output = new HashMap<>();

        Map<UUID, FlexRequestResult> flexRequest =
                container.getResults(FlexRequestResult.class);

        Map<UUID, FlexOptionsResult> flexResults =
                container.getResults(FlexOptionsResult.class);

        Map<UUID, EmSetPointResult> setPointResults =
                container.getResults(EmSetPointResult.class);

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
                                log.info("Data found for attribute '{}' for entity '{}': {}.", attr, entity, val.get());

                            } else {
                                log.info("No data found for attribute '{}' for entity '{}'.", attr, entity);
                            }
                        }
                        case FLEX_OPTION_P_MIN, FLEX_OPTION_P_REF, FLEX_OPTION_P_MAX -> {
                            Optional<Double> val = flexOptionsResult.map(m -> m.get(attr));

                            if (val.isPresent()) {
                                data.put(attr, val.get());
                                log.info("Data found for attribute '{}' for entity '{}': {}.", attr, entity, val.get());

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
}
