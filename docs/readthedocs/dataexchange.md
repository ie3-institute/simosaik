# Data Exchange

Simosaik support both simple and tiered operation mode.

## Simple mode

When using the simple mode, input data from mosaik is sent to SIMONA. SIMONA answers with result data after the power
flow calculaton.

![Simple operation mode](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/ie3-institute/simosaik/refs/heads/main/docs/protocol/SIMONA-mosaik-protocol-simple.puml)


## Tiered mode

When using specific models, like the em communication model, simosaik will use the tiered operation mode. This mode enables
multiple sub-ticks per normal tick. During each sub-tick input data can be sent to SIMONA and result data to mosaik.

![Tiered operation mode](http://www.plantuml.com/plantuml/proxy?cache=no&src=https://raw.githubusercontent.com/ie3-institute/simosaik/refs/heads/main/docs/protocol/SIMONA-mosaik-protocol-tiered.puml)
