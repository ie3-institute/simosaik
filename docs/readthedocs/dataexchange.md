# Data Exchange

Simosaik support both simple and tiered operation mode.

## Simple mode

When using the simple mode, input data from mosaik is sent to SIMONA. SIMONA answers with result data after the power
flow calculaton.

![Simple operation mode](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/ie3-institute/simosaik/blob/main/docs/protocol/SIMONA-mosaik-protocol-simple.puml)


## Tiered mode

When using specific models, like the em communication model, simosaik will use the tiered operation mode. During this mode,
during each tier input data can be sent to SIMONA and results to mosaik.

![Tiered operation mode](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/ie3-institute/simosaik/blob/main/docs/protocol/SIMONA-mosaik-protocol-tiered.puml)
