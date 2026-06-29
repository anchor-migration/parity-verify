package com.anchor.migration.parityverify.core;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

final class AstSsotReader {

    record ExportRunRef(int id, String sourceRoot, String toolVersion) {}

    ExportRunRef latestExportRun(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id, source_root, tool_version FROM export_run ORDER BY id DESC LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No export_run row found");
                }
                return new ExportRunRef(rs.getInt(1), rs.getString(2), rs.getString(3));
            }
        }
    }

    Map<String, Map<String, String>> loadTypes(Connection conn, int exportRunId) throws SQLException {
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT stable_id, package_name, simple_name, kind, extends_type, implements_list
                FROM java_type
                WHERE export_run_id = ?
                ORDER BY stable_id
                """)) {
            ps.setInt(1, exportRunId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String stableId = rs.getString(1);
                    Map<String, String> attrs = new LinkedHashMap<>();
                    attrs.put("package_name", rs.getString(2));
                    attrs.put("simple_name", rs.getString(3));
                    attrs.put("kind", rs.getString(4));
                    attrs.put("extends_type", rs.getString(5));
                    attrs.put("implements_list", rs.getString(6));
                    rows.put(stableId, attrs);
                }
            }
        }
        return rows;
    }

    Map<String, Map<String, String>> loadMethods(Connection conn, int exportRunId) throws SQLException {
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT stable_id, name, return_type, modifiers
                FROM java_method
                WHERE export_run_id = ?
                ORDER BY stable_id
                """)) {
            ps.setInt(1, exportRunId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String stableId = rs.getString(1);
                    Map<String, String> attrs = new LinkedHashMap<>();
                    attrs.put("name", rs.getString(2));
                    attrs.put("return_type", rs.getString(3));
                    attrs.put("modifiers", rs.getString(4));
                    rows.put(stableId, attrs);
                }
            }
        }
        return rows;
    }

    Map<String, Map<String, String>> loadFields(Connection conn, int exportRunId) throws SQLException {
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT stable_id, name, field_type, modifiers
                FROM java_field
                WHERE export_run_id = ?
                ORDER BY stable_id
                """)) {
            ps.setInt(1, exportRunId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String stableId = rs.getString(1);
                    Map<String, String> attrs = new LinkedHashMap<>();
                    attrs.put("name", rs.getString(2));
                    attrs.put("field_type", rs.getString(3));
                    attrs.put("modifiers", rs.getString(4));
                    rows.put(stableId, attrs);
                }
            }
        }
        return rows;
    }

    Map<String, Map<String, String>> loadCrosswalkLinks(Connection conn) throws SQLException {
        Map<String, Map<String, String>> rows = new LinkedHashMap<>();
        if (!tableExists(conn, "code_schema_link")) {
            return rows;
        }
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT edge_kind, source_stable_id, target_stable_id, mapping_role, profile_id
                FROM code_schema_link
                ORDER BY edge_kind, source_stable_id, target_stable_id
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String stableId =
                            rs.getString(1) + "|" + rs.getString(2) + "|" + rs.getString(3);
                    Map<String, String> attrs = new LinkedHashMap<>();
                    attrs.put("edge_kind", rs.getString(1));
                    attrs.put("source_stable_id", rs.getString(2));
                    attrs.put("target_stable_id", rs.getString(3));
                    attrs.put("mapping_role", rs.getString(4));
                    attrs.put("profile_id", rs.getString(5));
                    rows.put(stableId, attrs);
                }
            }
        }
        return rows;
    }

    Connection open(Path dbPath) throws SQLException {
        return DriverManager.getConnection("jdbc:sqlite:" + dbPath.toAbsolutePath());
    }

    private static boolean tableExists(Connection conn, String table) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
