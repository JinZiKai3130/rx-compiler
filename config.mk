# Commands consumed by the online judge (and by scripts/test.py).
# See README-EN.md / README-ZH.md for the command contract.
#
# The judge prepares dependencies for at most 180 s, which is not enough to
# download the Kotlin compiler from Maven Central. The compiler is therefore
# submitted prebuilt under dist/ (built locally with `make dist`):
#   - .rx-oj.toml disables dependency preparation;
#   - BUILD only validates the runtime and creates {runtime};
#   - SEMANTIC runs dist/rxc.jar with its jars from dist/lib/.
#
# Local development can keep using `make compiler` (compiler.mk + kotlinc)
# or `make dist` (pom.xml + Maven). Rerun `make dist` and commit dist/ after
# changing compiler sources.

# Optional build step. {runtime} must be a regular file (an empty one is fine:
# this compiler embeds everything in each generated program).
BUILD = test -f dist/rxc.jar || { echo "dist/rxc.jar missing: run 'make dist' and commit dist/" >&2; exit 1; }; command -v java >/dev/null 2>&1 || { echo "java not found: set Language = Java on rxoj.site" >&2; exit 1; }; : > {runtime}

# Required for semantic tests: exit 0 to accept {source}, 1 to reject it.
SEMANTIC = java -cp 'dist/rxc.jar:dist/lib/*' rx.MainKt --stage semantic {source}

# Not implemented yet. The Semantic suite only uses SEMANTIC; suites that run
# IR/codegen/optimization will keep failing until these are filled in.
IR =
CODEGEN =
RUN =

