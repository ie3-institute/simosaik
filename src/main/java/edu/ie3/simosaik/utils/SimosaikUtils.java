/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik.utils;

import static edu.ie3.simosaik.SimosaikUnits.*;

import edu.ie3.datamodel.io.naming.timeseries.ColumnScheme;
import edu.ie3.datamodel.models.value.*;
import edu.ie3.simona.api.data.connection.ExtEmDataConnection.EmMode;
import edu.ie3.simona.api.mapping.DataType;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simona.api.mapping.ExtEntityEntry;
import edu.ie3.simosaik.MosaikSimulator;
import edu.ie3.simosaik.RunSimosaik;
import java.util.*;
import java.util.function.Consumer;
import javax.measure.Quantity;
import javax.measure.Unit;
import javax.measure.quantity.Power;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tech.units.indriya.ComparableQuantity;
import tech.units.indriya.quantity.Quantities;

/** Class with helpful methods to couple SIMONA and MOSAIK */
public final class SimosaikUtils {

  private static final Logger log = LoggerFactory.getLogger(SimosaikUtils.class);

  private SimosaikUtils() {}

  /**
   * Starts MOSAIK connection
   *
   * @param mosaikSimulator Simulator that extends the MOSAIK API
   * @param mosaikIP IP address for the connection with MOSAIK
   */
  public static void startMosaikSimulation(MosaikSimulator mosaikSimulator, String mosaikIP) {
    try {
      RunSimosaik simosaikRunner = new RunSimosaik(mosaikIP, mosaikSimulator);
      new Thread(simosaikRunner, "Simosaik").start();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static Map<UUID, Class<? extends Value>> buildAssetsToValueClasses(
      ExtEntityMapping entityMapping) {
    Map<UUID, Class<? extends Value>> assetsToValueClasses = new HashMap<>();

    for (ExtEntityEntry extEntityEntry : entityMapping.getEntries(DataType.EXT_PRIMARY_INPUT)) {
      Optional<ColumnScheme> scheme = extEntityEntry.columnScheme();

      scheme.ifPresent(
          columnScheme ->
              assetsToValueClasses.put(extEntityEntry.uuid(), columnScheme.getValueClass()));
    }

    return assetsToValueClasses;
  }

  public static Optional<EmMode> findEmMode(Set<DataType> dataTypes) {
    boolean base = dataTypes.contains(DataType.EXT_EM_INPUT);
    boolean communication = dataTypes.contains(DataType.EXT_EM_COMMUNICATION);

    if (base && communication) {
      log.warn("Multiple em modes present! This is not supported!");
      return Optional.empty();
    } else if (base) {
      return Optional.of(EmMode.BASE);
    } else if (communication) {
      return Optional.of(EmMode.EM_COMMUNICATION);
    } else {
      log.debug("No em mode present.");
      return Optional.empty();
    }
  }

  public static List<UUID> buildEmData(ExtEntityMapping entityMapping) {
    Set<DataType> dataTypes = entityMapping.getDataTypes();

    if (dataTypes.contains(DataType.EXT_EM_INPUT)) {
      return entityMapping.getEntries(DataType.EXT_EM_INPUT).stream()
          .map(ExtEntityEntry::uuid)
          .toList();

    } else if (dataTypes.contains(DataType.EXT_EM_COMMUNICATION)) {
      return entityMapping.getEntries(DataType.EXT_EM_COMMUNICATION).stream()
          .map(ExtEntityEntry::uuid)
          .toList();

    } else {
      log.warn("No em data found!");
      return Collections.emptyList();
    }
  }

  public static Map<DataType, List<UUID>> buildResultMapping(ExtEntityMapping entityMapping) {
    Map<DataType, List<UUID>> resultMapping = new HashMap<>();

    Consumer<DataType> consumer =
        type -> {
          List<UUID> assets =
              entityMapping.getEntries(type).stream().map(ExtEntityEntry::uuid).toList();

          if (!assets.isEmpty()) {
            resultMapping.put(type, assets);
          }
        };

    consumer.accept(DataType.EXT_GRID_RESULT);
    consumer.accept(DataType.EXT_PARTICIPANT_RESULT);
    consumer.accept(DataType.EXT_FLEX_OPTIONS_RESULT);

    return resultMapping;
  }

  // converting inputs

  /**
   * Method to get all values from a given map.
   *
   * @param attrToValue map: unit to value
   * @return a list of {@link Value}s
   */
  public static List<Value> convert(Map<String, Double> attrToValue) {
    List<Value> valueList = new ArrayList<>();

    // convert power
    ComparableQuantity<Power> active = extract(attrToValue, ACTIVE_POWER);
    ComparableQuantity<Power> reactive = extract(attrToValue, REACTIVE_POWER);
    ComparableQuantity<Power> heat = extract(attrToValue, THERMAL_POWER);

    toPValue(active, reactive, heat).ifPresent(valueList::add);

    return valueList;
  }

  /**
   * Creates an option for a {@link PValue} from the given inputs.
   *
   * @param active power
   * @param reactive power
   * @return option for a power value
   */
  public static Optional<PValue> toPValue(
      ComparableQuantity<Power> active, ComparableQuantity<Power> reactive) {
    return toPValue(active, reactive, null);
  }

  /**
   * Creates an option for a {@link PValue} from the given inputs.
   *
   * @param active power
   * @param reactive power
   * @param heat demand
   * @return option for a power value
   */
  public static Optional<PValue> toPValue(
      ComparableQuantity<Power> active,
      ComparableQuantity<Power> reactive,
      ComparableQuantity<Power> heat) {
    if (reactive != null && heat != null) {
      // we have at least reactive power and heat
      return Optional.of(new HeatAndSValue(active, reactive, heat));

    } else if (reactive != null) {
      // we have at least reactive power
      return Optional.of(new SValue(active, reactive));

    } else if (heat != null) {
      // we have at least heat
      return Optional.of(new HeatAndPValue(active, heat));

    } else if (active != null) {
      // we have active power
      return Optional.of(new PValue(active));
    }

    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  public static <Q extends Quantity<Q>> ComparableQuantity<Q> extract(
      Map<String, Double> valueMap, String field) {
    return Optional.ofNullable(valueMap.get(field))
        .map(value -> Quantities.getQuantity(value, (Unit<Q>) getPSDMUnit(field)))
        .orElse(null);
  }
}
