# pylint: disable=missing-module-docstring,import-error,missing-function-docstring,unused-argument,invalid-name
import os
import sys
from argparse import ArgumentParser

import mx

suite = mx.suite("somns")


bn_parser = ArgumentParser(
    prog="mx build-native", description="Build SOMns native image"
)


bn_parser.add_argument(
    "-bt",
    "--build-somns",
    action="store_true",
    dest="build_somns",
    help="build SOMns first.",
    default=False,
)
bn_parser.add_argument(
    "-bn",
    "--build-native-image-tool",
    action="store_true",
    dest="build_native_image_tool",
    help="build the native-image tool first.",
    default=False,
)
bn_parser.add_argument(
    "-g",
    "--graalvm",
    action="store",
    dest="graalvm",
    default=None,
    help="path to GraalVM distribution",
    metavar="<path>",
)

bn_parser.add_argument(
    "-d",
    "--with-debugger",
    action="store_true",
    dest="with_debugger",
    help="wait for Java debugger to attach.",
    default=False,
)
bn_parser.add_argument(
    "-J",
    "--no-jit",
    action="store_true",
    dest="without_jit",
    default=False,
    help="disables the JIT compiler, creating an interpreter-only binary.",
)
bn_parser.add_argument(
    "-g1",
    "--use-g1",
    action="store_true",
    dest="use_g1",
    default=False,
    help="Use the G1 garbage collector. Only supported on Linux and with Oracle GraalVM.",
)

bn_parser.add_argument(
    "-m",
    "--dump-method",
    action="store",
    dest="method_filter",
    default=None,
    metavar="<method-filter>",
    help="dump compiler graphs for the selected methods. For the syntax "
    + "see https://github.com/oracle/graal/blob/master/compiler/"
    + "src/org.graalvm.compiler.debug/src/org/graalvm/compiler/"
    + "debug/doc-files/MethodFilterHelp.txt",
)


def get_svm_path():
    truffle_suite = mx.suite("truffle", fatalIfMissing=False)
    return truffle_suite.dir.replace("/truffle", "/substratevm")


def get_output_name(opt):
    output_name = "/som-native"

    if opt.without_jit:
        output_name += "-interp"

    return output_name


@mx.command(suite.name, "build-native-image-tool")
def build_native_image_tool(args, **kwargs):
    """build the native-image tool"""
    svm_path = get_svm_path()
    mx.run_mx(["build"], svm_path)


BASE_DIR = suite.dir
TRUFFLE_DIR = BASE_DIR + "/../graal"

MODULE_PATH_ENTRIES = [
    BASE_DIR + "/mxbuild/dists/somns.jar",
    TRUFFLE_DIR + "/sdk/mxbuild/dists/graal-sdk.jar",
    TRUFFLE_DIR + "/sdk/mxbuild/dists/collections.jar",
    TRUFFLE_DIR + "/sdk/mxbuild/dists/polyglot.jar",
    TRUFFLE_DIR + "/sdk/mxbuild/dists/word.jar",
    TRUFFLE_DIR + "/sdk/mxbuild/dists/jniutils.jar",
    TRUFFLE_DIR + "/truffle/mxbuild/dists/truffle-runtime.jar",
    TRUFFLE_DIR + "/substratevm/mxbuild/dists/truffle-runtime-svm.jar",
    TRUFFLE_DIR + "/truffle/mxbuild/dists/truffle-api.jar",
]


@mx.command(
    suite.name,
    "build-native",
    usage_msg="[options]",
    doc_function=bn_parser.print_help,
)
def build_native(args, **kwargs):
    """build SOMns native images"""
    opt = bn_parser.parse_args(args)
    output_name = get_output_name(opt)

    svm_path = get_svm_path()

    if opt.build_somns:
        mx.run_mx(["build"], suite)
    if opt.build_native_image_tool:
        mx.run_mx(["build"], svm_path)

    if opt.graalvm:
        cmd = [opt.graalvm + "/bin/native-image"]
        output_name += "-ee"
    else:
        cmd = ["native-image"]

    if opt.with_debugger:
        cmd += [
            "-J-Xdebug",
            "-J-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000",
        ]

    cmd += [
        "--no-fallback",
        "--link-at-build-time",
        "-H:+ReportExceptionStackTraces",
        "-H:+UnlockExperimentalVMOptions",
        "-H:-DeleteLocalSymbols",
        "-Dsom.interp=" + opt.type,
    ]

    if opt.method_filter:
        cmd += [
            "--initialize-at-build-time=trufflesom,org.graalvm.graphio",
            "-H:Dump=:3",
            "-H:PrintGraph=File",
            "-H:MethodFilter=" + opt.method_filter,
        ]
    else:
        cmd += ["--initialize-at-build-time=bd,tools,trufflesom"]

    if opt.without_jit:
        cmd += ["-Dsom.jitCompiler=false"]

    # -H:+PrintAnalysisCallTree
    # -H:+PrintRuntimeCompileMethods
    # -H:+PrintMethodHistogram
    # -H:+RuntimeAssertions
    # -H:+EnforceMaxRuntimeCompileMethods

    cmd += [
        "--module-path",
        ":".join(MODULE_PATH_ENTRIES),
        "-o",
        suite.dir + output_name,
    ]

    if opt.use_g1 and opt.graalvm and os.uname().sysname != "Darwin":
        cmd += ["--gc=G1"]

    cmd += ["trufflesom.Launcher"]

    if opt.graalvm:
        mx.run(cmd, svm_path)
    else:
        mx.run_mx(cmd, svm_path)


@mx.command(suite.name, "tests-junit")
def tests_junit(args, **kwargs):
    """run Java unit tests"""
    mx.run_mx(["unittest", "--suite", "somns"])


@mx.command(suite.name, "tests-som")
def tests_som(args, **kwargs):
    """run SOM unit tests"""
    mx.run(
        [
            suite.dir + "/som",
            "-G",
            "--no-embedded-graal",
            suite.dir + "/core-lib/TestSuite/TestRunner.ns",
        ]
    )


@mx.command(suite.name, "tests-native")
def tests_native(args, **kwargs):
    """run SOMns unit tests on native"""
    possible_native_binaries = [
        "som-native",
        "som-native-interp",
    ]

    did_run = False
    for b in possible_native_binaries:
        binary = suite.dir + "/" + b
        if os.path.exists(binary):
            print("Run Unit Tests on " + binary + ":")
            mx.run(
                [
                    binary,
                    suite.dir + "/core-lib/TestSuite/TestRunner.ns",
                ]
            )
            did_run = True

    if not did_run:
        print("No binary found to run tests on")
        sys.exit(1)


@mx.command(suite.name, "tests-nodestats")
def tests_nodestats(args, **kwargs):
    """run nodestats tests"""
    mx.run(["tests/tools/nodestats/test.sh"])


@mx.command(suite.name, "tests-coverage")
def tests_coverage(args, **kwargs):
    """run coverage tests"""
    mx.run(["tests/tools/coverage/test.sh"])


@mx.command(suite.name, "tests-update-data")
def tests_update_data(args, **kwargs):
    """update expected data for tests"""
    mx.run(["tests/tools/nodestats/test.sh", "update"])
    mx.run(["tests/tools/coverage/test.sh", "update"])
