package com.synclite.qreader;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for QReader: validates that messages published to an MQTT broker
 * are read by QReader and written to a SyncLite SQLITE_APPENDER device.
 *
 * Prerequisites:
 *   - An MQTT broker must be running at tcp://localhost:1883 (or set system property mqtt.broker.url).
 *
 * TODO: Refactor to use Testcontainers or mocking to avoid external MQTT broker dependency.
 */
@Disabled("Requires external MQTT broker at tcp://localhost:1883; refactor with Testcontainers or mocks")
public class QReaderTest {

    private static final String MQTT_BROKER_URL = System.getProperty("mqtt.broker.url", "tcp://localhost:1883");
    private static final Path TEST_HOME = Path.of(System.getProperty("user.home"), "synclite", "tests");
    private static final Path DB_DIR = TEST_HOME.resolve("db").resolve("qreader").resolve("testqreader");
    private static final Path STAGE_DIR = TEST_HOME.resolve("stageDir");
    private static final String DEVICE_NAME = "testdevice";

    private Path qreaderConfigFile;
    private Path loggerConfigFile;
    private Thread qreaderThread;

    @BeforeEach
    void setup() throws Exception {
        // Clean DB directory
        deleteDirectory(DB_DIR.toFile());
        Files.createDirectories(DB_DIR);
        Files.createDirectories(STAGE_DIR);

        // Clean only the synclite_testdevice* directories inside STAGE_DIR
        File[] stageSubs = STAGE_DIR.toFile().listFiles();
        if (stageSubs != null) {
            for (File f : stageSubs) {
                if (f.isDirectory() && f.getName().startsWith("synclite-" + DEVICE_NAME)) {
                    deleteDirectory(f);
                }
            }
        }

        // Write synclite.conf (used by SQLiteAppender.initialize)
        loggerConfigFile = DB_DIR.resolve("synclite.conf");
        Files.writeString(loggerConfigFile,
                "local-data-stage-directory = " + STAGE_DIR + "\n" +
                "device-stage-type = FS\n");

        // Write synclite-qreader.conf
        qreaderConfigFile = DB_DIR.resolve("synclite-qreader.conf");
        Files.writeString(qreaderConfigFile,
                "synclite-device-dir=" + DB_DIR + "\n" +
                "synclite-logger-configuration-file=" + loggerConfigFile + "\n" +
                "mqtt-broker-url=" + MQTT_BROKER_URL + "\n" +
                "mqtt-qos-level=1\n" +
                "mqtt-clean-session=true\n" +
                "mqtt-broker-connection-timeout-s=10\n" +
                "mqtt-broker-connection-retry-interval-s=2\n" +
                "qreader-synclite-device-type=SQLITE_APPENDER\n" +
                "qreader-map-devices-to-single-synclite-device=true\n" +
                "qreader-default-synclite-device-name=" + DEVICE_NAME + "\n" +
                "qreader-ignore-messages-for-undefined-topics=false\n" +
                "qreader-default-synclite-table-name=default_table\n" +
                "qreader-ignore-corrupt_messages=false\n" +
                "qreader-corrupt-messages-synclite-table-name=corrupt_messages\n" +
                "qreader-message-batch-processing=false\n" +
                "qreader-message-batch-flush-interval-ms=1000\n" +
                "qreader-trace-level=DEBUG\n" +
                "mqtt-message-header-delimiter=/\n" +
                "src-message-field-delimiter=,\n" +
                "src-message-format=CSV\n");

            // Create QReader metadata database expected by QReaderDriver.loadTopics().
            Path metadataDb = DB_DIR.resolve("synclite_qreader_metadata.db");
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + metadataDb);
                 Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE IF NOT EXISTS topic_info ("
                    + "topic_name TEXT PRIMARY KEY,"
                    + "topic_table_name TEXT,"
                    + "topic_field_count INTEGER,"
                    + "topic_create_table_sql TEXT,"
                    + "topic_table_column_list TEXT,"
                    + "enable INTEGER DEFAULT 1)");
                stmt.execute("DELETE FROM topic_info WHERE topic_name='testtopic'");
                stmt.execute("INSERT INTO topic_info(topic_name, topic_table_name, topic_field_count, "
                    + "topic_create_table_sql, topic_table_column_list, enable) VALUES ("
                    + "'testtopic','default_table',3,"
                    + "'CREATE TABLE IF NOT EXISTS default_table(c1 TEXT,c2 TEXT,c3 TEXT)',"
                    + "'device_name,c1,c2,c3',1)");
            }
    }

    @AfterEach
    void cleanup() throws Exception {
        if (qreaderThread != null && qreaderThread.isAlive()) {
            qreaderThread.interrupt();
            qreaderThread.join(5000);
        }
    }

    @Test
    void testMqttToSQLiteAppenderDevice() throws Exception {
        // Start QReader in background thread.
        qreaderThread = new Thread(() -> {
            try {
                ConfLoader.getInstance().loadQReaderConfigProperties(qreaderConfigFile);
                QReaderDriver.getInstance().run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        qreaderThread.setDaemon(true);
        qreaderThread.start();

        // Wait for QReader to initialize.
        Thread.sleep(5000);
        assertTrue(qreaderThread.isAlive(), "QReader thread should still be running");

        // Publish test messages
        String topic = DEVICE_NAME + "/testtopic";
        String payload = "field1,field2,field3";
        try (MqttClient client = new MqttClient(MQTT_BROKER_URL,
                "qreader-test-" + UUID.randomUUID(), new MemoryPersistence())) {
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setCleanSession(true);
            client.connect(opts);
            client.publish(topic, new MqttMessage(payload.getBytes()));
            client.disconnect();
        }

        // Wait for QReader to process the message
        Thread.sleep(5000);

        // --- Validation 1: Data in device DB ---
        Path deviceDb = DB_DIR.resolve(DEVICE_NAME + ".db");
        assertTrue(Files.exists(deviceDb), "Device DB should exist at: " + deviceDb);

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + deviceDb)) {
            // Validate inserted data
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT device_name, c1, c2, c3 FROM default_table")) {
                assertTrue(rs.next(), "Should have at least one row in default_table");
                assertTrue(DEVICE_NAME.equals(rs.getString("device_name")), "device_name mismatch");
                assertTrue("field1".equals(rs.getString("c1")), "c1 mismatch");
                assertTrue("field2".equals(rs.getString("c2")), "c2 mismatch");
                assertTrue("field3".equals(rs.getString("c3")), "c3 mismatch");
            }
        }

        // --- Validation 2: CREATE TABLE and INSERT commands present in .sqllog log files ---
        Pattern sqllogPattern = Pattern.compile("^(\\d+)\\.sqllog$");

        // Prefer device-local SyncLite directory when available.
        Path activeLogDir = null;
        Path deviceLocalStageDir = DB_DIR.resolve(DEVICE_NAME + ".db.synclite");
        if (Files.exists(deviceLocalStageDir) && Files.isDirectory(deviceLocalStageDir)) {
            activeLogDir = deviceLocalStageDir;
        } else {
            // Fallback: pick latest stage directory for this device under configured STAGE_DIR.
            long latestDirMtime = Long.MIN_VALUE;
            try (Stream<Path> dirs = Files.list(STAGE_DIR)) {
                for (Path p : (Iterable<Path>) dirs::iterator) {
                    if (!Files.isDirectory(p)) continue;
                    if (!p.getFileName().toString().startsWith("synclite-" + DEVICE_NAME)) continue;
                    long mtime = Files.getLastModifiedTime(p).toMillis();
                    if (mtime > latestDirMtime) {
                        latestDirMtime = mtime;
                        activeLogDir = p;
                    }
                }
            }
        }

        assertNotNull(activeLogDir, "Device log directory should exist for " + DEVICE_NAME);

        java.util.List<Path> sqllogFiles = new java.util.ArrayList<>();
        try (Stream<Path> stageFiles = Files.list(activeLogDir)) {
            for (Path path : (Iterable<Path>) stageFiles::iterator) {
                if (!Files.isRegularFile(path)) continue;
                Matcher m = sqllogPattern.matcher(path.getFileName().toString());
                if (m.matches()) sqllogFiles.add(path);
            }
        }

        assertTrue(!sqllogFiles.isEmpty(),
                "At least one .sqllog file should exist in log directory: " + activeLogDir);

        // Scan all log segments; accumulate whether CREATE TABLE and INSERT were seen.
        boolean foundCreateTable = false;
        boolean foundInsert = false;
        for (Path logFile : sqllogFiles) {
            try (Connection logConn = DriverManager.getConnection("jdbc:sqlite:" + logFile);
                 Statement logStmt = logConn.createStatement();
                 ResultSet logRs = logStmt.executeQuery("SELECT sql FROM commandlog")) {
                while (logRs.next()) {
                    String sql = logRs.getString("sql");
                    if (sql == null) continue;
                    String upper = sql.trim().toUpperCase();
                    if (upper.startsWith("CREATE TABLE")) foundCreateTable = true;
                    if (upper.startsWith("INSERT")) foundInsert = true;
                    if (foundCreateTable && foundInsert) break;
                }
            } catch (Exception ignored) {
                // skip segments that don't have a commandlog table yet
            }
            if (foundCreateTable && foundInsert) break;
        }

        assertTrue(foundCreateTable,
                "commandlog in .sqllog files should contain a CREATE TABLE command");
        assertTrue(foundInsert,
                "commandlog in .sqllog files should contain an INSERT command");

        System.out.println("[QReaderTest PASSED] Found CREATE TABLE and INSERT commands in .sqllog log files under " + activeLogDir);
    }

    private static void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) deleteDirectory(f);
                else f.delete();
            }
        }
        dir.delete();
    }
}
