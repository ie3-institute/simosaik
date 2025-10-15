/*
 * © 2024. TU Dortmund University,
 * Institute of Energy Systems, Energy Efficiency and Energy Economics,
 * Research group Distribution grid planning and operation
 */

package edu.ie3.simosaik;

import edu.ie3.datamodel.models.input.AssetInput;
import edu.ie3.datamodel.models.input.container.JointGridContainer;
import edu.ie3.simona.api.ExtLinkInterface;
import edu.ie3.simona.api.data.ExtSimAdapterData;
import edu.ie3.simona.api.mapping.DataType;
import edu.ie3.simona.api.mapping.ExtEntityEntry;
import edu.ie3.simona.api.mapping.ExtEntityMapping;
import edu.ie3.simosaik.synchronization.Synchronizer;
import edu.ie3.simosaik.utils.SimosaikUtils;

import java.util.ArrayList;
import java.util.List;

public final class SimosaikExtLink implements ExtLinkInterface {
  private MosaikSimulation extSim;

  @Override
  public MosaikSimulation getExtSimulation() {
    return extSim;
  }

  @Override
  public void setup(ExtSimAdapterData data) {
    ArgsParser.Arguments arguments = ArgsParser.parse(data.getMainArgs());

    String mosaikIP = arguments.mosaikIP();

    JointGridContainer grid = data.getGrid();
    List<AssetInput> assets = new ArrayList<>();
    assets.addAll(grid.getRawGrid().allEntitiesAsList());
    assets.addAll(grid.getSystemParticipants().allEntitiesAsList());

    List<ExtEntityEntry> entries = new ArrayList<>();
    assets.forEach(asset -> entries.add(new ExtEntityEntry(asset.getUuid(), asset.getId(), DataType.GENERAL)));
    ExtEntityMapping mapping = new ExtEntityMapping(entries);

    // for synchronising both simulations
    Synchronizer synchronizer = new Synchronizer();

    // creating and starting the simulator
    MosaikSimulator simulator = new MosaikSimulator(synchronizer, mapping);
    SimosaikUtils.startMosaikSimulation(simulator, mosaikIP);

    // creating the external simulation
    extSim = new MosaikSimulation(synchronizer);
    extSim.setAdapterData(data);
  }
}
