package edu.ie3.simosaik;

import de.offis.mosaik.api.SimProcess;

public class RunSimosaik implements Runnable {

    private final SimonaSimulator simonaSimulator;

    private final String mosaikIP;

    public RunSimosaik(
            String mosaikIP,
            SimonaSimulator simonaSimulator
    ) {
        this.simonaSimulator = simonaSimulator;
        this.mosaikIP = mosaikIP;
    }

    @Override
    public void run() {
        try {
            SimProcess.startSimulation(new String[]{mosaikIP}, simonaSimulator);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
