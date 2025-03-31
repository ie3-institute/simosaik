package edu.ie3.simosaik.utils;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.SValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simosaik.utils.MosaikMessageParser.MosaikMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static edu.ie3.simosaik.utils.SimosaikTranslation.*;

class PrimaryUtils {
    private static final Logger log = LoggerFactory.getLogger(PrimaryUtils.class);

    static Map<UUID, Value> getPrimary(
            Collection<MosaikMessage> mosaikMessages,
            Map<String, UUID> idToUuid
    ) {
        Map<UUID, Value> primary = new HashMap<>();

        Map<String, List<MosaikMessage>> receiverToMessages = mosaikMessages.stream().collect(Collectors.groupingBy(MosaikMessage::receiver));

        for (Map.Entry<String, List<MosaikMessage>> rtm : receiverToMessages.entrySet()) {
            String receiver = rtm.getKey();

            List<Value> values = handleMessages(rtm.getValue());

            if (values.size() != 1) {
                log.warn("Unexpected number of values for asset '{}'. Currently, only one value is receivable", receiver);
            } else {
                primary.put(idToUuid.get(receiver), values.get(0));
            }
        }

        return primary;
    }

    static List<Value> handleMessages(List<MosaikMessage> messages) {
        Map<String, Double> unitToValues = new HashMap<>();

        messages.forEach(msg -> {
            String unit = msg.unit();
            Object value = msg.messageValue();

            if (value instanceof Double d) {
                if (unitToValues.containsKey(unit)) {
                    double sum = unitToValues.get(unit) + d;
                    unitToValues.put(unit, sum);
                } else {
                    unitToValues.put(unit, d);
                }
            } else {
                log.warn("Received value '{}' for unit '{}'.", value, unit);
            }
        });

        return toValues(unitToValues);
    }

    static List<Value> toValues(Map<String, Double> values) {
        List<Value> valueList = new ArrayList<>();

        // convert power
        Optional<ComparableQuantity<Power>> active =
                extractAny(values, MOSAIK_ACTIVE_POWER, MOSAIK_ACTIVE_POWER_IN);
        Optional<ComparableQuantity<Power>> reactive =
                extractAny(values, MOSAIK_REACTIVE_POWER, MOSAIK_REACTIVE_POWER_IN);


        if (reactive.isPresent()) {
            if (active.isPresent()) {
                valueList.add(new SValue(active.get(), reactive.get()));
            } else {
                valueList.add(new SValue(null, reactive.get()));
            }
        } else {
            active.ifPresent(quantity -> valueList.add(new PValue(quantity)));
        }

        return valueList;
    }


    @SuppressWarnings("unchecked")
    static <Q extends Quantity<Q>> Optional<ComparableQuantity<Q>> extractAny(
            Map<String, Double> valueMap, String... fields) {
        return Stream.of(fields)
                .map(field -> (ComparableQuantity<Q>) extract(valueMap, field))
                .filter(Objects::nonNull)
                .findFirst();
    }

    @SuppressWarnings("unchecked")
    static <Q extends Quantity<Q>> ComparableQuantity<Q> extract(
            Map<String, Double> valueMap, String field) {
        return Optional.ofNullable(valueMap.get(field))
                .map(value -> Quantities.getQuantity(value, (Unit<Q>) getPSDMUnit(field)))
                .orElse(null);
    }
}
