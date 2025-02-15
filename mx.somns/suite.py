suite = {
    "name": "somns",
    "mxversion": "7.40.1",
    "versionConflictResolution": "latest",
    "version": "0.0.1",
    "release": False,
    "groupId": "trufflesom",
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
        "CHECKSTYLE_10.21.0": {
            "urls": [
                "https://github.com/checkstyle/checkstyle/releases/download/checkstyle-10.21.0/checkstyle-10.21.0-all.jar"
            ],
            "digest": "sha512:401940e1475a333afee636535708fa842b1a11b30f9fd43518589aaf94c2cf601b24f83176e95ffc171e2befe968267262b24a0a3931a009b39531a6fe570e60",
            "licence": "LGPLv21",
        },
        "SOMNS_DEPS_0.3.7": {
            "urls": ["https://stefan-marr.de/dev/somns-deps-0.3.7.jar"],
            "digest": "sha512:a2faf1811856aebfd10e75f4dfedb9f0673e24369989b7ecc7f4b0dc7b9095dd90e7e103011f153edf425c761b1551845102751b749ab92abdc2c7da3d9dbbfa",
        },
        "SOMNS_DEPS_0.3.7_DEV": {
            "urls": ["https://stefan-marr.de/dev/somns-deps-dev-0.3.7.jar"],
            "digest": "sha512:b82d82e3ca99668b2e87438d7ae1e652b51b18bef81dfeefae9c02f684b6ef2024fdb7fd97f8f6357da445da832b5108b017636bb86d4470e104d5f8296a452e",
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
                "SOMNS_DEPS_0.3.7",
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
            "dependencies": ["truffle:TRUFFLE_API", "SOMNS", "mx:JUNIT"],
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
                    "com.oracle.truffle.tools.profiler",
                ],
            },
            "dependencies": ["somns"],
            "distDependencies": [
                "truffle:TRUFFLE_API",
                "truffle:TRUFFLE_JSON",
                "tools:TRUFFLE_PROFILER",
            ],  # , "tools:TRUFFLE_COVERAGE",
        },
        "TRUFFLESOM_TEST": {
            "description": "TruffleSOM JUnit Tests",
            "javaCompliance": "17+",
            "dependencies": ["tests"],
            "exclude": ["mx:JUNIT", "mx:HAMCREST"],
            "distDependencies": ["SOMNS", "truffle:TRUFFLE_TEST"],
            "testDistribution": True,
        },
    },
}
