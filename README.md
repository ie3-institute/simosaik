# simosaik




## About

Simosaik is an external simulation for the agent-based discrete-event power system simulation model [SIMONA](https://github.com/ie3-institute/simona).
It is used to connect SIMONA with the co-simulation framework [MOSAIK](https://mosaik.offis.de/) is a co-simulation framework from [Offis e.V.](https://www.offis.de/).
Simosaik takes care of the communication and value conversion between SIMONA and MOSAIK.


## How to use

Currently, to use simosaik you need the development repository ([here](https://github.com/ie3-institute/simosaik_dev)).


**Step 1:** <br>
You have to use the following branches:

- simosaik: `main`
- simonaAPI: `ms/ReCoDe`
- simona: `ms/simosaik-dev`


**Step 2:** <br>
Add the mosaik API to the folder `./libs`.


**Step 3:** <br>
After selecting these branches, you have to run the following `shadowJar` on SIMONA and simosaik.


**Step 4:** <br>
Add an execution of the jar containing SIMONA to your MOSAIK scenario.


**Step 5:** <br>
Add the following to your SIMONA configuration file: 

- ``simona.input.extSimDir = "path/to/simosaik.jar"``

- ``simosaik.mappingPath = "path/to/ext_entity_mapping.csv"``

- ``simosaik.simulation = "name_of_the_simosaik_simulation"``


## Simosaik simulations

The following simulations can be selected:

- `PrimaryResult`: Provides SIMONA with primary data and MOSAIK with SIMONA results
- `MosaikOptimizer`: Optimizer for em agents in SIMONA

If no simulation is selected, simosaik will use the `PrimaryResult` simulation.
