# The `som` Launcher Script

To document, simplify, and standardize how SOMns is started or debugged, we
use a Python script to execute the actual Java program.

The Python script manages various command-line parameters and selects the JVM
to be used for execution.

Below, we see that `./som --help` supports a large set of options, of which we
detail only a few.

The basic options include `-d` to allow us to attach a Java debugger, for instance
from Eclipse, `-dnu` to print stack traces on a `#doesNotUnderstand` message,
or `-G` to run without Graal-based JIT compilation.
Generally, the options are designed to use upper-case shorthands when they
disable a feature.

Further below, we see different categories of other options. This includes
flags to investigate and understand how Graal executes the interpreter,
options for various profiling tools, as well as tools for SOMns code coverage,
dynamic execution metrics, or interactive debugging of SOMns code
(currently called 'web debugger' `-wd`).

```
$ ./som --help
usage: som [-h] [-d] [-t THREADS] [-p SOM_PLATFORM] [-k SOM_KERNEL] [-dnu]
           [-i] [-if] [-io ONLY_IGV] [-l] [-ti] [-w] [-f] [-v] [-gp] [-ga]
           [-gi] [-gb] [-tp] [-td] [-wd] [-dm] [-at]
           [-atcfg ACTOR_TRACING_CFG] [-mt] [-tf TRACE_FILE] [-TF]
           [--coverage COVERAGE] [-o ONLY_COMPILE] [-A] [-B] [-C] [-G] [-X]
           [-T] [--no-graph-pe] [-vv] [--print-graal-options]
           [-D JAVA_PROPERTIES]
           ...

optional arguments:
  -h, --help                   show this help message and exit
  -d, --debug                  wait for debugger to attach
  -dnu, --stack-trace-on-dnu   print a stack trace on #doesNotUnderstand:
  -G, --interpreter            run without Graal

Investigate Execution
  -i, --igv                    dump compilation details to IGV
  -l, --low-level              enable low-level optimization output
  -v, --visual-vm              connect to VisualVM for profiling

Profile Execution
  -gp, --graal-profile         enable Graal-level profiling after warmup
  -tp, --truffle-profile       enable Graal-level profiling after warmup

Tools
  -td, --truffle-debugger      start Truffle debugger
  -wd, --web-debugger          start Web debugger
  -dm, --dynamic-metrics       capture Dynamic Metrics
  --coverage COVERAGE          determine code coverage and store in given file
```