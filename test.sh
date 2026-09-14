#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p out/tests
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/RelationLogic.java tests/RelationLogicTest.java
java -cp out/tests RelationLogicTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/RetryPolicy.java tests/RetryPolicyTest.java
java -cp out/tests RetryPolicyTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/HistoryText.java app/src/main/java/com/blackapps/follow/ProfileLinks.java tests/HistoryTextTest.java
java -cp out/tests HistoryTextTest
java com.sun.tools.javac.Main -encoding UTF-8 -d out/tests app/src/main/java/com/blackapps/follow/NewPeople.java app/src/main/java/com/blackapps/follow/RequestTrace.java tests/NewPeopleTest.java
java -cp out/tests NewPeopleTest
python3 tests/test_migration.py
if [[ -n "${BF_JSON_JAR:-}" ]]; then
  java com.sun.tools.javac.Main -encoding UTF-8 -cp "$BF_JSON_JAR" -d out/tests app/src/main/java/com/blackapps/follow/ProfileLookup.java app/src/main/java/com/blackapps/follow/ViewerVerifier.java app/src/main/java/com/blackapps/follow/ResponsePolicy.java tests/ViewerVerifierTest.java tests/ProfileLookupTest.java
  java -cp "$BF_JSON_JAR:out/tests" ViewerVerifierTest
  java -cp "$BF_JSON_JAR:out/tests" ProfileLookupTest
  mkdir -p out/transport-tests
  java com.sun.tools.javac.Main -encoding UTF-8 -cp "$BF_JSON_JAR" -d out/transport-tests tests/transport/android/content/Context.java tests/transport/android/webkit/CookieManager.java tests/transport/com/blackapps/follow/Session.java tests/transport/com/blackapps/follow/Store.java app/src/main/java/com/blackapps/follow/InstagramClient.java app/src/main/java/com/blackapps/follow/ProfileLookup.java app/src/main/java/com/blackapps/follow/ViewerVerifier.java app/src/main/java/com/blackapps/follow/ResponsePolicy.java app/src/main/java/com/blackapps/follow/RetryPolicy.java app/src/main/java/com/blackapps/follow/RelationLogic.java app/src/main/java/com/blackapps/follow/ProfileLinks.java app/src/main/java/com/blackapps/follow/RequestTrace.java tests/TransportTest.java
  java -cp "$BF_JSON_JAR:out/transport-tests" TransportTest
else
  echo 'Session regression tests: set BF_JSON_JAR to org.json:json:20240303 (test dependency only).'
fi
