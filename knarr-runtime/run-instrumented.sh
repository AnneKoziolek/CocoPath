#!/bin/bash
set -euo pipefail

# Resolve key paths
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Prefer Java 17; allow override via JAVA_HOME if already set
JAVA_HOME_DEFAULT="/usr/lib/jvm/java-17-openjdk-amd64"
if [[ -z "${JAVA_HOME:-}" && -x "${JAVA_HOME_DEFAULT}/bin/java" ]]; then
  export JAVA_HOME="$JAVA_HOME_DEFAULT"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="$JAVA_HOME/bin:$PATH"
fi

echo "Using java: $(java -version 2>&1 | head -1)"

# Resolve Galette agent location
GALETTE_AGENT=""
if [[ -f "${ROOT_DIR}/galette-agent/target/galette-agent-1.0.0-SNAPSHOT.jar" ]]; then
  GALETTE_AGENT="${ROOT_DIR}/galette-agent/target/galette-agent-1.0.0-SNAPSHOT.jar"
elif [[ -f "$HOME/.m2/repository/edu/neu/ccs/prl/galette/galette-agent/1.0.0-SNAPSHOT/galette-agent-1.0.0-SNAPSHOT.jar" ]]; then
  GALETTE_AGENT="$HOME/.m2/repository/edu/neu/ccs/prl/galette/galette-agent/1.0.0-SNAPSHOT/galette-agent-1.0.0-SNAPSHOT.jar"
else
  echo "Galette agent jar not found" >&2
  exit 1
fi

echo "Galette agent: $GALETTE_AGENT"

# Build external Amalthea-acset (required for Vitruvius classes)
EXTERNAL_PATH="/home/anne/CocoPath/Amalthea-acset"
if [[ -d "$EXTERNAL_PATH" ]]; then
  echo "Building external Amalthea-acset for Vitruvius dependencies..."
  (cd "$EXTERNAL_PATH" && mvn -q clean install -DskipTests -Dcheckstyle.skip=true)
  echo "Done."

  # Copy generated reaction files to internal project
  # This ensures the internal project uses the latest generated code with symbolic execution support
  echo "Copying generated reaction files to internal amalthea-acset-integration..."
  INTERNAL_PATH="${ROOT_DIR}/amalthea-acset-integration"
  if [[ -d "$INTERNAL_PATH/consistency/src/main/java/mir" ]]; then
    # Copy reactions and routines
    if [[ -d "$EXTERNAL_PATH/consistency/target/generated-sources/reactions/mir" ]]; then
      cp -r "$EXTERNAL_PATH/consistency/target/generated-sources/reactions/mir/reactions" \
            "$INTERNAL_PATH/consistency/src/main/java/mir/" 2>/dev/null || true
      cp -r "$EXTERNAL_PATH/consistency/target/generated-sources/reactions/mir/routines" \
            "$INTERNAL_PATH/consistency/src/main/java/mir/" 2>/dev/null || true
      echo "Reaction files copied successfully."
    else
      echo "WARNING: Generated reaction files not found in external Amalthea-acset"
    fi
  else
    echo "WARNING: Internal amalthea-acset-integration mir directory not found"
  fi
else
  echo "WARNING: External Amalthea-acset not found at $EXTERNAL_PATH"
  echo "Vitruvius transformations may fail without it."
fi

# Build knarr-runtime with instrumentation (run from root pom)
mvn -q -f "${ROOT_DIR}/pom.xml" clean install -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dskip=true -pl knarr-runtime -am
mvn -q -f "${ROOT_DIR}/pom.xml" process-test-resources -Dmaven.test.skip=true -Dcheckstyle.skip=true -Dskip=true -pl knarr-runtime

INSTRUMENTED_JAVA="${SCRIPT_DIR}/target/galette/java"
if [[ ! -x "$INSTRUMENTED_JAVA/bin/java" ]]; then
  echo "Instrumented java not found at $INSTRUMENTED_JAVA/bin/java" >&2
  exit 1
fi

# Build runtime classpath
mvn -q -f "${ROOT_DIR}/pom.xml" -DincludeScope=runtime -Dmdep.outputFile="${SCRIPT_DIR}/cp.txt" -pl knarr-runtime dependency:build-classpath
if [[ ! -f "${SCRIPT_DIR}/cp.txt" ]]; then
  echo "Failed to build classpath" >&2
  exit 1
fi
CP="${SCRIPT_DIR}/target/classes:${SCRIPT_DIR}/target/test-classes:$(cat "${SCRIPT_DIR}/cp.txt")"

echo "Classpath entries: $(echo "$CP" | tr ':' '\n' | wc -l)"

mkdir -p target/galette/cache

MAIN_CLASS="edu.neu.ccs.prl.galette.vitruvius.AutomaticVitruvMultiVarPathExploration"

set -x
"$INSTRUMENTED_JAVA/bin/java" \
  -cp "$CP" \
  -Xbootclasspath/a:"$GALETTE_AGENT" \
  -javaagent:"$GALETTE_AGENT" \
  -Dgalette.cache=target/galette/cache \
  -Dgalette.coverage=true \
  -Dsymbolic.execution.debug=true \
  -Dgalette.debug=true \
  -Dpath.explorer.max.iterations=30 \
  -DDEBUG=true \
  -Dpath.explorer.debug=true \
  -Dconstraint.solver.debug=true \
  "$MAIN_CLASS" "$@"
