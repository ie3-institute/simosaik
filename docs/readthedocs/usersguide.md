# User's guide

The following content will give you all information you need to couple SIMONA with mosaik.

## What is the purpose of simosaik?

Mosaik and SIMONA are written in different programming languages, that cannot be connected directly. Therefore, in order
to couple SIMONA with mosaik, the following APIs are needed:

- [mosaik-java-api](https://gitlab.com/mosaik/api/mosaik-api-java): The API used by mosaik, to add simulators written in java.
- [simonaAPI](https://github.com/ie3-institute/simonaAPI): The API used by SIMONA, to add external simulations.

To connect these two APIs, we created simoasik. During the co-simulation simosaik is treated like every other simulator
by mosaik, while SIMONA on the other hands, sees simosaik as an external simulation.

To exchange data between mosaik and SIMONA, simosaik translates all supported information into the corresponding data format
used by either mosaik or SIMONA. Simosaik also handels the synchronisation of both mosaik and SIMONA during the co-simulation.


## Selecting the versions of the APIs

Simosaik comes with a version of the simonaAPI and a version of the mosaik-api-java included. To update these to a newer
version go to the `build.gradle` file.


## Steps to set up the connection

This section will give you a step-by-step instruction on how to set up the coupling.

**Step 1:** <br>
The first step is to create a fat JAR (java archive) file. One simple way to do this, is to run the `shadowJar` task of
this gradle project.

**Step 2:** <br>
Since mosaik is responsible for starting the co-simulation, we also need an executable fat JAR file for SIMONA. This can
be done by running the `shadowJar` task in the SIMONA project.

**Step 3:** <br>
Add the following statement to your mosaik simulation configuration:

```
'SimonaPowerGrid': {
   'cmd': 'java -cp path/to/simonaJar edu.ie3.simona.main.RunSimonaStandalone --config=path/to/simona/configuration/file --ext-address=%(addr)s',
   "auto_terminate": False,
}
```

**Step 4:** <br>
Add the following configuration to the SIMONA config file:

- ``simona.input.extSimDir = "path/to/simosaik.jar"``


## Configure SIMONA models

The models, that are available in mosaik to create entities, need to be specified when starting the simulation in mosaik.
To specify these models, you need to add the `models` argument with a list of selected models to the `world.start` method:

```
world.start('SimonaPowerGrid', models=["p"])
```

An overview of the available models can be found [here](/models). Each model has a list of attributes, that can use when
connecting to other mosaik models. An overview of all available attributes with their units and values can be found [here](/attributes).

## Creating mosaik entities

To create mosaik entities from the SIMONA model, you need to pass two arguments:
```
simonaActivePowerEntities = simonaSimulation.ActivePower.create(2, mapping=active_power_mapping)
```

The first is the number of entities, you want to create. The second argument is the mapping, that will be used by simosaik.
The mapping contains a SIMONA `Universally Unique Identifier` (`UUID`), that is mapped to a mosaik `eid`. The given `eids`
are used to identify the entities in mosaik, while the `UUIDs` are used by mosaik to send route the data to the correct model
in SIMONA.

```
active_power_mapping = {
    "4dca3b1d-5d24-444a-b4df-f4fa23b9ef1b": "Load_Node_1",
    "9c5991bc-24df-496b-b4ce-5ec27657454c": "Load_Node_2"
}
```

The number of entries in the mapping must match the number of entities that is parsed as an argument.
