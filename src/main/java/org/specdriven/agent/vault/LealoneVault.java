package org.specdriven.agent.vault;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * {@link SecretVault} implementation backed by Lealone DB with AES-256-GCM encryption.
 * Secrets are encrypted using a master key obtained from {@link VaultMasterKey}.
 */
public class LealoneVault implements SecretVault {

    private static final Logger LOG = Logger.getLogger(LealoneVault.class.getName());

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;
    private static final int IV_LENGTH = 12;

    private final EventBus eventBus;
    private final String jdbcUrl;
    private final SecretKeySpec aesKey;
    private final SecureRandom secureRandom;

    public LealoneVault(EventBus eventBus, String jdbcUrl) {
        this.eventBus = eventBus;
        this.jdbcUrl = jdbcUrl;
        this.aesKey = deriveAesKey(VaultMasterKey.get());
        this.secureRandom = new SecureRandom();
        initTables();
    }

    @Override
    public String get(String key) {
        String sql = "SELECT encrypted_value, iv FROM vault_secrets WHERE key = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new VaultException("Secret not found: " + key);
                }
                String encryptedHex = rs.getString("encrypted_value");
                String ivHex = rs.getString("iv");
                return decrypt(encryptedHex, ivHex);
            }
        } catch (VaultException e) {
            throw e;
        } catch (SQLException e) {
            throw new VaultException("Failed to retrieve secret: " + key, e);
        }
    }

    @Override
    public void set(String key, String plaintext, String description) {
        long now = System.currentTimeMillis();
        String[] encrypted = encrypt(plaintext.getBytes(StandardCharsets.UTF_8));
        String encryptedHex = encrypted[0];
        String ivHex = encrypted[1];

        String mergeSql = "MERGE INTO vault_secrets (key, encrypted_value, iv, description, created_at, updated_at) "
                + "KEY(key) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(mergeSql)) {
            ps.setString(1, key);
            ps.setString(2, encryptedHex);
            ps.setString(3, ivHex);
            ps.setString(4, description);
            ps.setLong(5, now);
            ps.setLong(6, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new VaultException("Failed to store secret: " + key, e);
        }

        insertAudit("SET", key);
        publishEvent(EventType.VAULT_SECRET_CREATED, key);
    }

    @Override
    public void delete(String key) {
        String sql = "DELETE FROM vault_secrets WHERE key = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new VaultException("Failed to delete secret: " + key, e);
        }

        insertAudit("DELETE", key);
        publishEvent(EventType.VAULT_SECRET_DELETED, key);
    }

    @Override
    public List<VaultEntry> list() {
        String sql = "SELECT key, description, created_at FROM vault_secrets ORDER BY created_at";
        List<VaultEntry> entries = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                entries.add(new VaultEntry(
                        rs.getString("key"),
                        Instant.ofEpochMilli(rs.getLong("created_at")),
                        rs.getString("description")
                ));
            }
        } catch (SQLException e) {
            throw new VaultException("Failed to list vault entries", e);
        }
        return Collections.unmodifiableList(entries);
    }

    @Override
    public boolean exists(String key) {
        String sql = "SELECT COUNT(*) FROM vault_secrets WHERE key = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new VaultException("Failed to check existence: " + key, e);
        }
    }

    // --- Encryption helpers ---

    private String[] encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);
            HexFormat hex = HexFormat.of();
            return new String[]{hex.formatHex(ciphertext), hex.formatHex(iv)};
        } catch (Exception e) {
            throw new VaultException("Encryption failed", e);
        }
    }

    private String decrypt(String encryptedHex, String ivHex) {
        try {
            HexFormat hex = HexFormat.of();
            byte[] ciphertext = hex.parseHex(encryptedHex);
            byte[] iv = hex.parseHex(ivHex);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (VaultException e) {
            throw e;
        } catch (Exception e) {
            throw new VaultException("Decryption failed", e);
        }
    }

    private static SecretKeySpec deriveAesKey(String masterKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = sha256.digest(masterKey.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new VaultException("Failed to derive AES key", e);
        }
    }

    // --- DB helpers ---

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    private void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vault_secrets (
                        key             VARCHAR(255)  PRIMARY KEY,
                        encrypted_value CLOB          NOT NULL,
                        iv              VARCHAR(32)   NOT NULL,
                        description     CLOB,
                        created_at      BIGINT        NOT NULL,
                        updated_at      BIGINT        NOT NULL
                    )
                    """);
            stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS vault_audit_log (
                        id        BIGINT       PRIMARY KEY AUTO_INCREMENT,
                        operation VARCHAR(10)  NOT NULL,
                        vault_key VARCHAR(255) NOT NULL,
                        event_ts  BIGINT       NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            throw new VaultException("Failed to initialize vault tables", e);
        }
    }

    private void insertAudit(String operation, String vaultKey) {
        String sql = "INSERT INTO vault_audit_log (operation, vault_key, event_ts) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, operation);
            ps.setString(2, vaultKey);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.warning("Failed to insert vault audit log: " + e.getMessage());
        }
    }

    private void publishEvent(EventType type, String vaultKey) {
        if (eventBus == null) return;
        try {
            eventBus.publish(new Event(type, System.currentTimeMillis(), "LealoneVault",
                    Map.of("vaultKey", vaultKey)));
        } catch (Exception e) {
            LOG.warning("Failed to publish vault event " + type + ": " + e.getMessage());
        }
    }
}
