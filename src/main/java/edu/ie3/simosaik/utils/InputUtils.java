/*
 * © 2025. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import edu.ie3.datamodel.models.value.PValue;
import edu.ie3.datamodel.models.value.Value;
import edu.ie3.simona.api.data.container.ExtInputContainer;
import edu.ie3.simona.api.data.model.em.*;
import edu.ie3.simosaik.utils.MosaikMessageParser.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;

import javax.measure.quantity.Power;
import java.util.*;
import java.util.stream.Collectors;

import static edu.ie3.simosaik.SimosaikUnits.ACTIVE_POWER;
import static edu.ie3.simosaik.SimosaikUnits.REACTIVE_POWER;
import static edu.ie3.simosaik.utils.SimosaikUtils.*;

public final class InputUtils {
  private static final Logger log = LoggerFactory.getLogger(InputUtils.class);

  public static ExtInputContainer createInputDataContainer(
      long tick,
      long nextTick,
      List<ParsedMessage> mosaikMessages,
      List<MessageProcessor> messageProcessors) {
    log.debug("Parsed messages: {}", mosaikMessages);

    Map<String, List<Content>> receiverToMessages = new HashMap<>();

    mosaikMessages.stream()
        .collect(Collectors.groupingBy(ParsedMessage::receiver))
        .forEach(
            (receiver, messages) ->
                receiverToMessages.put(
                    receiver, messages.stream().map(ParsedMessage::content).toList()));

    log.debug("Receivers to messages: {}.", receiverToMessages);

    // building input container
    ExtInputContainer container = new ExtInputContainer(tick, nextTick);

    // process all input data
    messageProcessors.forEach(processor -> processor.process(container, receiverToMessages));

    return container;
  }

  // message processors

  public sealed interface MessageProcessor permits PrimaryMessageProcessor, EmMessageProcessor {
    void process(ExtInputContainer container, Map<String, List<Content>> receiverToMessages);
  }

  public record PrimaryMessageProcessor(Map<String, UUID> idToUuid) implements MessageProcessor {
    public void process(
        ExtInputContainer container, Map<String, List<Content>> receiverToMessages) {
      parsePrimary(receiverToMessages, idToUuid).forEach(container::addPrimaryValue);
    }
  }

  public record EmMessageProcessor(Map<String, UUID> idToUuid) implements MessageProcessor {
    public void process(
        ExtInputContainer container, Map<String, List<Content>> receiverToMessages) {
        parseFlexComMessages(receiverToMessages,  idToUuid).forEach(container::addFlexComMessage);
      parseFlexRequests(receiverToMessages, idToUuid).forEach(container::addRequest);
      parseFlexOptions(receiverToMessages, idToUuid).forEach(container::addFlexOptions);
      parseSetPoints(receiverToMessages, idToUuid).forEach(container::addSetPoint);
    }
  }

  // private method for message parsing

  private static Map<UUID, Value> parsePrimary(
      Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      log.warn("No primary external entity mapping found!");
      return Collections.emptyMap();
    }

    Map<UUID, Value> result = new HashMap<>();

    receiverToMessages.forEach(
        (receiver, messages) -> {
          UUID receiverUuid = idToUuid.get(receiver);

          List<DoubleValue> dvs =
              messages.stream()
                  .filter(msg -> msg.getClass() == DoubleValue.class)
                  .map(DoubleValue.class::cast)
                  .toList();

          Map<String, Double> attrToDouble = new HashMap<>();

          for (DoubleValue dv : dvs) {
            String attr = dv.attr();
            double d = dv.value();

            if (attrToDouble.containsKey(attr)) {
              double sum = attrToDouble.get(attr) + d;
              attrToDouble.put(attr, sum);
            } else {
              attrToDouble.put(attr, d);
            }
          }

          List<Value> values = convert(attrToDouble);

          if (values.isEmpty()) {
            log.debug("No primary value found for asset {}.", receiver);

          } else {
            if (values.size() > 1) {
              log.warn(
                  "Unexpected number of primary values for asset '{}'. Only the first one is considered",
                  receiver);
            }

            result.put(receiverUuid, values.getFirst());
          }
        });

    return result;
  }


  private static List<EmCommunicationMessage<?>> parseFlexComMessages(
          Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid
  ) {
      if (idToUuid.isEmpty()) {
          log.warn("No em external entity mapping found!");
          return Collections.emptyList();
      }



      List<EmCommunicationMessage<?>> result = new ArrayList<>();

      receiverToMessages.forEach((receiver, messages) -> {
          log.info("Com messages: {}", messages);

          if (idToUuid.containsKey(receiver)) {
              UUID receiverUuid = idToUuid.get(receiver);

              List<FlexComMessage> messageList = messages.stream().filter(m -> m.getClass() == FlexComMessage.class).map(FlexComMessage.class::cast).toList();

              for (FlexComMessage msg : messageList) {
                  UUID senderUuid = idToUuid.get(msg.sender());

                  List<? extends EmData> data = switch (msg.content()) {
                      case FlexRequestMessage r -> List.of(new FlexOptionRequest(receiverUuid, r.disaggregated()));
                      case FlexOptionsMessage(List<FlexOptionInformation> information) -> information.stream().map(optionMessage -> new PowerLimitFlexOptions(receiverUuid, idToUuid.get(optionMessage.sender()), optionMessage.pRef(), optionMessage.pMin(), optionMessage.pMax())).toList();
                      case FlexSetPointMessage(String r, String s, ComparableQuantity<Power> p, ComparableQuantity<Power> q) -> List.of(new EmSetPoint(senderUuid, p));
                      default -> List.of();
                  };

                  data.forEach(d -> result.add(new EmCommunicationMessage<>(receiverUuid, senderUuid, d)));
              }
          }
      });

      return result;
  }

  private static Map<UUID, FlexOptionRequest> parseFlexRequests(
      Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      log.warn("No em external entity mapping found!");
      return Collections.emptyMap();
    }

    Map<UUID, FlexOptionRequest> flexRequests = new HashMap<>();

    receiverToMessages.forEach(
        (receiver, messages) -> {
          if (idToUuid.containsKey(receiver)) {
            UUID receiverUuid = idToUuid.get(receiver);

            List<FlexRequestMessage> requests =
                messages.stream()
                    .filter(m -> m.getClass() == FlexRequestMessage.class)
                    .map(FlexRequestMessage.class::cast)
                    .toList();

            if (requests.size() > 1) {
              log.warn(
                  "Receiver '{}' received flex requests from multiple senders! This should not be possible!",
                  receiverUuid);
            }

            if (!requests.isEmpty()) {
              FlexRequestMessage requestMessage = requests.getFirst();
              FlexOptionRequest request =
                  new FlexOptionRequest(
                      receiverUuid,
                      requestMessage.disaggregated());

              flexRequests.put(receiverUuid, request);
            }
          }
        });

    return flexRequests;
  }

  private static Map<UUID, List<FlexOptions>> parseFlexOptions(
      Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      log.warn("No em external entity mapping found!");
      return Collections.emptyMap();
    }

    Map<UUID, List<FlexOptions>> flexOptions = new HashMap<>();

    receiverToMessages.forEach(
        (receiver, messages) -> {
          if (idToUuid.containsKey(receiver)) {

            UUID receiverUuid = idToUuid.get(receiver);

            List<FlexOptions> options =
                messages.stream()
                    .filter(m -> m.getClass() == FlexOptionsMessage.class)
                    .map(FlexOptionsMessage.class::cast)
                    .flatMap(m -> m.information().stream())
                    .map(
                        optionMessage ->
                                (FlexOptions) new PowerLimitFlexOptions(
                                receiverUuid,
                                idToUuid.get(optionMessage.sender()),
                                optionMessage.pRef(),
                                optionMessage.pMin(),
                                optionMessage.pMax()))
                    .toList();

            if (!options.isEmpty()) {
              flexOptions.put(receiverUuid, options);
            }
          }
        });

    return flexOptions;
  }

  private static List<EmSetPoint> parseSetPoints(
      Map<String, List<Content>> receiverToMessages, Map<String, UUID> idToUuid) {
    if (idToUuid.isEmpty()) {
      log.warn("No em external entity mapping found!");
      return Collections.emptyList();
    }

    List<EmSetPoint> setPoints = new ArrayList<>();

    receiverToMessages.forEach(
        (receiver, messages) -> {
          if (idToUuid.containsKey(receiver)) {

            UUID receiverUuid = idToUuid.get(receiver);

            List<FlexSetPointMessage> setPointValues =
                messages.stream()
                    .filter(m -> m.getClass() == FlexSetPointMessage.class)
                    .map(FlexSetPointMessage.class::cast)
                    .toList();

            if (!setPointValues.isEmpty()) {

              if (setPointValues.size() > 1) {
                log.debug("Received multiple set points for asset '{}'!", receiver);
              }

              FlexSetPointMessage setPointMessage = setPointValues.getFirst();

              Optional<PValue> powerValue = toPValue(setPointMessage.p(), null);

              if (powerValue.isEmpty()) {
                log.debug("No set point value found for asset {}.", receiver);
              } else {
                setPoints.add(new EmSetPoint(receiverUuid, powerValue, Collections.emptyMap()));
              }

            } else {
              // if set point is given as double values

              Map<String, Double> attrToValue =
                  messages.stream()
                      .filter(m -> m.getClass() == DoubleValue.class)
                      .map(DoubleValue.class::cast)
                      .collect(Collectors.toMap(DoubleValue::attr, DoubleValue::value));

              if (attrToValue.size() > 2) {
                log.debug("Received multiple set point values for asset '{}'!", receiver);
              } else {

                Optional<PValue> setPoint =
                    toPValue(
                        extract(attrToValue, ACTIVE_POWER), extract(attrToValue, REACTIVE_POWER));

                if (setPoint.isEmpty()) {
                  log.debug("No set point value found for asset {}.", receiver);
                } else {
                  setPoints.add(new EmSetPoint(receiverUuid, setPoint.get()));
                }
              }
            }
          }
        });

    log.info("Set points: {}", setPoints);
    return setPoints;
  }
}
