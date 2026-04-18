package org.specdriven.agent.testsupport;

import java.util.UUID;

public final class LealoneTestDb {

    private LealoneTestDb() {
    }

    public static String freshJdbcUrl() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "jdbc:lealone:embed:test_db_" + suffix + "?PERSISTENT=false";
    }
}
