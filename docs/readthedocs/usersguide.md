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

## Configure SIMONA models
