#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out/tests
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/RelationLogic.java tests/RelationLogicTest.java
java -cp out/tests RelationLogicTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/PrefixSearchLogic.java tests/PrefixSearchLogicTest.java
java -cp out/tests PrefixSearchLogicTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/RelationshipRequest.java tests/RelationshipRequestTest.java
java -cp out/tests RelationshipRequestTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/PreviewLogic.java tests/PreviewLogicTest.java
java -cp out/tests PreviewLogicTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/RetryPolicy.java tests/RetryPolicyTest.java
java -cp out/tests RetryPolicyTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/HistoryText.java app/src/main/java/com/blackapps/follow/ProfileLinks.java tests/HistoryTextTest.java
java -cp out/tests HistoryTextTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/NewPeople.java app/src/main/java/com/blackapps/follow/RequestTrace.java tests/NewPeopleTest.java
java -cp out/tests NewPeopleTest
python3 tests/test_migration.py
mkdir -p out/session-tests
java com.sun.tools.javac.Main -encoding UTF-8 -d out/session-tests tests/session/android/content/*.java tests/session/android/webkit/*.java tests/session/com/blackapps/follow/*.java app/src/main/java/com/blackapps/follow/Session.java app/src/main/java/com/blackapps/follow/RetryPolicy.java tests/SessionPolicyTest.java
java -cp out/session-tests SessionPolicyTest
if [[ -n "${BF_JSON_JAR:-}" ]]; then
  java com.sun.tools.javac.Main -encoding UTF-8 -cp "$BF_JSON_JAR" -d out/tests app/src/main/java/com/blackapps/follow/ProfileLookup.java app/src/main/java/com/blackapps/follow/ViewerVerifier.java app/src/main/java/com/blackapps/follow/ResponsePolicy.java tests/ViewerVerifierTest.java tests/ProfileLookupTest.java
  java -cp "$BF_JSON_JAR:out/tests" ViewerVerifierTest
  java -cp "$BF_JSON_JAR:out/tests" ProfileLookupTest
  java com.sun.tools.javac.Main -encoding UTF-8 -cp "$BF_JSON_JAR" -d out/tests app/src/main/java/com/blackapps/follow/RelationshipRequest.java app/src/main/java/com/blackapps/follow/MobileRequestLogic.java tests/MobileRequestLogicTest.java
  java -cp "$BF_JSON_JAR:out/tests" MobileRequestLogicTest
  java com.sun.tools.javac.Main -encoding UTF-8 -cp "$BF_JSON_JAR" -d out/tests app/src/main/java/com/blackapps/follow/WebSession.java tests/WebSessionTest.java
  java -cp "$BF_JSON_JAR:out/tests" WebSessionTest
  mkdir -p out/monitor-tests
  java com.sun.tools.javac.Main -encoding UTF-8 -cp "$BF_JSON_JAR" -d out/monitor-tests tests/session/android/content/*.java tests/session/android/webkit/*.java tests/monitor/android/database/sqlite/*.java tests/monitor/com/blackapps/follow/*.java app/src/main/java/com/blackapps/follow/Session.java app/src/main/java/com/blackapps/follow/RetryPolicy.java app/src/main/java/com/blackapps/follow/Monitor.java tests/MonitorPolicyTest.java
  java -cp "$BF_JSON_JAR:out/monitor-tests" MonitorPolicyTest
else
  echo 'Session regression tests: set BF_JSON_JAR to org.json:json:20240303 (test dependency only).'
fi
