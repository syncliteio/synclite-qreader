# SyncLite QReader: OSS runtime, UI hardening, CSRF coverage, and integration test updates

## Summary

This change set hardens the SyncLite QReader OSS flow end to end:

- aligns the default device/runtime path with STREAMING and appender-based OSS usage
- fixes logger class loading so QReader only loads the SyncLite backend required by the configured device type
- adds an integration test for MQTT-to-SQLite appender ingestion and `.sqllog` validation
- introduces CSRF protection across the web app and converts start/stop actions to POST-only flows
- improves JSP output safety with HTML escaping and validation of JVM arguments
- updates dashboard and topic configuration UX to better match the consolidator behavior
- switches logging dependencies from legacy Log4j/Log4j2 compatibility artifacts to reload4j

## What Changed

### Core runtime and configuration

- Updated the core module to build on Java 11 and added JUnit 5/Surefire support in [root/core/pom.xml](root/core/pom.xml).
- Changed the default SyncLite device type from `TELEMETRY` to `STREAMING` in [root/core/src/main/java/com/synclite/qreader/ConfLoader.java](root/core/src/main/java/com/synclite/qreader/ConfLoader.java).
- Improved configuration error messages for invalid/missing paths and boolean properties in [root/core/src/main/java/com/synclite/qreader/ConfLoader.java](root/core/src/main/java/com/synclite/qreader/ConfLoader.java).
- Updated [root/core/src/main/java/com/synclite/qreader/DeviceWriter.java](root/core/src/main/java/com/synclite/qreader/DeviceWriter.java) so `STREAMING` uses `io.synclite.logger.Streaming` instead of the telemetry driver path.
- Updated [root/core/src/main/java/com/synclite/qreader/QReaderDriver.java](root/core/src/main/java/com/synclite/qreader/QReaderDriver.java) and [root/core/src/main/java/com/synclite/qreader/SchemaChangeDriver.java](root/core/src/main/java/com/synclite/qreader/SchemaChangeDriver.java) to load only the SyncLite logger class required for the configured device type.
- Fixed topic column list initialization in [root/core/src/main/java/com/synclite/qreader/Topic.java](root/core/src/main/java/com/synclite/qreader/Topic.java) by parsing the stored comma-separated column list instead of self-assigning a null field.
- Added clarifying threading comments and minor collection cleanup in [root/core/src/main/java/com/synclite/qreader/QReaderDriver.java](root/core/src/main/java/com/synclite/qreader/QReaderDriver.java).

### Integration testing

- Added [root/core/src/test/java/com/synclite/qreader/QReaderTest.java](root/core/src/test/java/com/synclite/qreader/QReaderTest.java) to validate:
  - QReader reads from an MQTT broker at `tcp://localhost:1883`
  - data is written into a `SQLITE_APPENDER` device database
  - generated `.sqllog` files contain both `CREATE TABLE` and `INSERT` commands
- Added [root/core/src/test/java/io/synclite/logger/Telemetry.java](root/core/src/test/java/io/synclite/logger/Telemetry.java) as a test-scope compatibility stub for OSS logger builds.

### Web security and servlet behavior

- Added [root/web/src/main/java/com/synclite/qreader/web/CSRFTokenFilter.java](root/web/src/main/java/com/synclite/qreader/web/CSRFTokenFilter.java) to:
  - generate a per-session CSRF token
  - validate CSRF tokens on all POST requests
  - set baseline response security headers
- Added missing CSRF hidden inputs across POST-backed JSP forms, including:
  - [root/web/src/main/webapp/configureMQTTReader.jsp](root/web/src/main/webapp/configureMQTTReader.jsp)
  - [root/web/src/main/webapp/configureMQTTTables.jsp](root/web/src/main/webapp/configureMQTTTables.jsp)
  - [root/web/src/main/webapp/selectDeviceDirectory.jsp](root/web/src/main/webapp/selectDeviceDirectory.jsp)
  - [root/web/src/main/webapp/confirmReadJob.jsp](root/web/src/main/webapp/confirmReadJob.jsp)
  - [root/web/src/main/webapp/dashboard.jsp](root/web/src/main/webapp/dashboard.jsp)
  - [root/web/src/main/webapp/jobTrace.jsp](root/web/src/main/webapp/jobTrace.jsp)
  - [root/web/src/main/webapp/loadJob.jsp](root/web/src/main/webapp/loadJob.jsp)
  - [root/web/src/main/webapp/removeTopicEntries.jsp](root/web/src/main/webapp/removeTopicEntries.jsp)
  - [root/web/src/main/webapp/resetJob.jsp](root/web/src/main/webapp/resetJob.jsp)
  - [root/web/src/main/webapp/alterTables.jsp](root/web/src/main/webapp/alterTables.jsp)
  - [root/web/src/main/webapp/configureNumSchedules.jsp](root/web/src/main/webapp/configureNumSchedules.jsp)
  - [root/web/src/main/webapp/configureScheduler.jsp](root/web/src/main/webapp/configureScheduler.jsp)
- Changed [root/web/src/main/java/com/synclite/qreader/web/StartJob.java](root/web/src/main/java/com/synclite/qreader/web/StartJob.java) so GET redirects to confirmation and only POST starts the job.
- Changed [root/web/src/main/java/com/synclite/qreader/web/StopJob.java](root/web/src/main/java/com/synclite/qreader/web/StopJob.java) so only POST stops the job.
- Updated [root/web/src/main/webapp/html/menu.html](root/web/src/main/webapp/html/menu.html) and [root/web/src/main/webapp/css/SyncLiteStyle.css](root/web/src/main/webapp/css/SyncLiteStyle.css) to use CSRF-protected POST forms for Start/Stop while keeping sidebar styling consistent.
- Updated [root/web/src/main/java/com/synclite/qreader/web/JobStopper.java](root/web/src/main/java/com/synclite/qreader/web/JobStopper.java) to use `Runtime.exec(String[])` for safer process invocation.
- Added JVM argument validation in [root/web/src/main/java/com/synclite/qreader/web/ValidateMQTTReader.java](root/web/src/main/java/com/synclite/qreader/web/ValidateMQTTReader.java) to reject shell metacharacters and allow only safe JVM flags.

### Web UX and output encoding

- Added OWASP encoder-based escaping for user-facing error messages across multiple JSPs, including:
  - [root/web/src/main/webapp/configureMQTTReader.jsp](root/web/src/main/webapp/configureMQTTReader.jsp)
  - [root/web/src/main/webapp/configureMQTTTables.jsp](root/web/src/main/webapp/configureMQTTTables.jsp)
  - [root/web/src/main/webapp/configureNumSchedules.jsp](root/web/src/main/webapp/configureNumSchedules.jsp)
  - [root/web/src/main/webapp/configureScheduler.jsp](root/web/src/main/webapp/configureScheduler.jsp)
  - [root/web/src/main/webapp/alterTables.jsp](root/web/src/main/webapp/alterTables.jsp)
  - [root/web/src/main/webapp/jobError.jsp](root/web/src/main/webapp/jobError.jsp)
- Improved form titles/help text and fixed user-visible typo text in:
  - [root/web/src/main/webapp/configureMQTTReader.jsp](root/web/src/main/webapp/configureMQTTReader.jsp)
  - [root/web/src/main/webapp/loadJob.jsp](root/web/src/main/webapp/loadJob.jsp)
  - [root/web/src/main/webapp/selectDeviceDirectory.jsp](root/web/src/main/webapp/selectDeviceDirectory.jsp)
- Updated [root/web/src/main/webapp/configureMQTTReader.jsp](root/web/src/main/webapp/configureMQTTReader.jsp) defaults to use `STREAMING` instead of `TELEMETRY`.
- Updated [root/web/src/main/webapp/configureMQTTTables.jsp](root/web/src/main/webapp/configureMQTTTables.jsp) so the Create Table SQL field is editable and auto-generated from table name + field count using `TEXT` columns.
- Updated [root/web/src/main/webapp/dashboard.jsp](root/web/src/main/webapp/dashboard.jsp) to:
  - include CSRF on dashboard auto-refresh POSTs
  - show the top-line status message `Receiving events into SyncLite devices at device directory : ...`
  - align the refresh control like the consolidator dashboard

### Logging dependency migration

- Replaced the logging dependency with `reload4j` in:
  - [root/core/pom.xml](root/core/pom.xml)
  - [root/web/pom.xml](root/web/pom.xml)
- Removed obsolete `log4j2.xml` configuration files from the source tree.

## Companion Change Outside This Repo

This work depends on a companion logger fix in `synclite-logger-java`:

- [logger/src/main/java/io/synclite/logger/SQLLogger.java](../synclite-logger-java/logger/src/main/java/io/synclite/logger/SQLLogger.java)
  - made `tracer.getAppender("SyncLiteLogger")` null-safe during shutdown to avoid teardown NPEs

## Validation

Executed during development:

- `mvn -f root/core/pom.xml -Drevision=oss -DskipTests package`
- `mvn -f root/web/pom.xml -Drevision=oss -DskipTests package`
- `mvn -f root/core/pom.xml -Drevision=oss test -Dtest=QReaderTest`

Observed outcomes:

- core build succeeded after the OSS/logger initialization fixes
- web build succeeded when `WEB-INF/lib` files were not locked by Tomcat
- `QReaderTest` passed and verified data ingestion plus `.sqllog` `CREATE TABLE`/`INSERT` presence

## Notes For Reviewers

- The working tree currently contains generated artifacts and IDE metadata in addition to source changes.
- Notable generated/untracked content includes:
  - Eclipse metadata files under `root/.project`, `root/*/.classpath`, and `root/*/.settings`
  - generated test/runtime logs such as `root/core/derby.log`, `root/core/synclite_consolidator.trace`, and `synclite-logger-java/logger/derby.log`
  - packaged binaries copied into [root/web/src/main/webapp/WEB-INF/lib](root/web/src/main/webapp/WEB-INF/lib)
- If the PR should stay source-only, these generated artifacts should be excluded before merge.