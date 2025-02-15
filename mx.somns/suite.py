suite = {
    "name": "somns",
    "mxversion": "7.40.1",
    "versionConflictResolution": "latest",
    "version": "0.0.1",
    "release": False,
    "groupId": "somns",
    "url": "https://github.com/smarr/SOMns",
    "scm": {
        "url": "https://github.com/smarr/SOMns",
        "read": "https://github.com/smarr/SOMns.git",
        "write": "git@github.com:smarr/SOMns.git",
    },
    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
                "version": "217d8bc47ee1076cca072c8e8e9a760d24b12b5d",
                "urls": [{"url": "https://github.com/smarr/truffle", "kind": "git"}],
            },
            {
                "name": "tools",
                "subdir": True,
                "version": "217d8bc47ee1076cca072c8e8e9a760d24b12b5d",
                "urls": [{"url": "https://github.com/smarr/truffle", "kind": "git"}],
            },
        ]
    },
    "libraries": {
        "CHECKSTYLE_10.21.0" : {
          "urls" : [
            "https://github.com/checkstyle/checkstyle/releases/download/checkstyle-10.21.0/checkstyle-10.21.0-all.jar"
          ],
          "digest": "sha512:401940e1475a333afee636535708fa842b1a11b30f9fd43518589aaf94c2cf601b24f83176e95ffc171e2befe968267262b24a0a3931a009b39531a6fe570e60",
          "licence" : "LGPLv21",
          "maven" : {
            "groupId" : "com.puppycrawl.tools",
            "artifactId" : "checkstyle",
            "version" : "10.21.0",
          }
        },
        "SOMNS_DEPS_0.3.8": {
            "urls": ["https://stefan-marr.de/dev/somns-deps-0.3.8.jar"],
            "digest": "sha512:4fee76ab8797ca57b35f735489073d51b8f760a0d8f548830c09827898e666fb87f901c37789d32b54db6776784828d1ecfe1093b966f2200ec3f8b66c9dfed5",
        },
        "SOMNS_DEPS_0.3.8_DEV": {
            "urls": ["https://stefan-marr.de/dev/somns-deps-dev-0.3.8.jar"],
            "digest": "sha512:b506ef4feac9ffe90c2a87aa46fb0c48c2fcfa146998fd43e1d28cdfe397b1ea1244b96d9a57f819e5642aefdea3be562be00c0d7bd568b63f208ff285510bb4",
        },

        "AFFINITY": {
            "moduleName": "net.openhft.affinity",
            "digest" : "sha512:dc7684a3504280723813f7a6852e7a6178ac50410c40b8857c41a89d3779d2f7f3c8004f2ac231981162116aadd6d0a6f3135d84f1e255a50618ff5322ea1638",
            "sourceDigest" : "sha512:66ef85ff88e52079bdcbfed147dbeb6b75c5273457b47208172576541c3c549588d74c24aaf22c385fd28411ff5262ad3fd61dc8deef82c7ce0041291740efd7",
            "maven" : {
                "groupId" : "net.openhft",
                "artifactId" : "affinity",
                "version" : "3.23.2",
            }
        },

        "LABS_JDK": {
            "id": "labsjdk-ce-latest",
            # I am just using the suite.py to store the info
            # so but manage it in mx_somns.py
            "path": ".",
        },
    },
    "projects": {
        "somns": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "truffle:TRUFFLE_API",
                "tools:TRUFFLE_PROFILER",
                "SOMNS_DEPS_0.3.8",
                "AFFINITY",
            ],
            "requires": [
                "java.logging",
                "java.management",
                "jdk.management",
                "jdk.unsupported",  # sun.misc.Unsafe
                "jdk.httpserver"
            ],
            "requiresConcealed": {
                "java.base": ["jdk.internal.module"],
            },
            "checkstyleVersion": "10.21.0",
            "jacoco": "include",
            "javaCompliance": "17+",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "workingSets": "SOMns",
        },
        "tests": {
            "dir": ".",
            "sourceDirs": ["tests"],
            "requires": [
                "java.logging",
            ],
            "dependencies": ["truffle:TRUFFLE_API", "SOMNS", "mx:JUNIT", "sdk:POLYGLOT_TCK"],
            "checkstyle": "somns",
            "jacoco": "include",
            "javaCompliance": "17+",
            "workingSets": "SOMNS",
            "annotationProcessors": ["truffle:TRUFFLE_DSL_PROCESSOR"],
            "testProject": True,
        },
    },
    "distributions": {
        "SOMNS": {
            "description": "SOMns",
            "moduleInfo": {
                "name": "somns",
                "exports": [
                    "somns.* to org.graalvm.truffle",
                ],
                "requires": [
                    "jdk.unsupported",
                    "org.graalvm.collections",
                    "org.graalvm.polyglot",
                ],
            },
            "dependencies": ["somns"],
            "distDependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
                "tools:TRUFFLE_PROFILER",
                "sdk:NATIVEIMAGE",
            ],  # , "tools:TRUFFLE_COVERAGE",
        },
        "SOMNS_TEST": {
            "description": "SOMns JUnit Tests",
            "javaCompliance": "17+",
            "dependencies": ["tests"],
            "exclude": ["mx:JUNIT", "mx:HAMCREST"],
            "distDependencies": ["SOMNS", "truffle:TRUFFLE_TEST"],
            "testDistribution": True,
        },
    },
}
