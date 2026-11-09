# Commands consumed by the online judge (and by scripts/test.py).
# See README-EN.md / README-ZH.md for the command contract.
#
# The compiler is written in Kotlin and built with Maven so that the judge's
# Java 21 image can build it offline after `mvn dependency:go-offline`:
#   - pom.xml downloads Kotlin + ANTLR from Maven Central during preparation;
#   - BUILD produces target/rxc.jar plus its runtime jars in target/lib/.
#
# Local development can keep using `make compiler` (compiler.mk + kotlinc),
# which does not need Maven.

# Optional build step. The judge runs it once, then runs every testcase against
# the built compiler. {runtime} must be a regular file (an empty one is fine:
# this compiler embeds everything in each generated program).
BUILD = mvn -B -ntp -DskipTests package && : > {runtime}

# Required for semantic tests: exit 0 to accept {source}, 1 to reject it.
SEMANTIC = java -cp 'target/rxc.jar:target/lib/*' rx.MainKt --stage semantic {source}

# Not implemented yet. The Semantic suite only uses SEMANTIC; suites that run
# IR/codegen/optimization will keep failing until these are filled in.
IR =
CODEGEN =
RUN =

