package az.tarlan.utility.proxy.adapter.persistence;

import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.IDEMPOTENCY_KEYS;
import static az.tarlan.utility.proxy.adapter.persistence.jooq.Tables.PAYMENTS;

// Real PostgreSQL of the same major version as compose and jOOQ codegen (ADR-0001); Flyway applies the migrations on startup.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
abstract class AbstractPersistenceIT {

    // One container for the whole test run: Spring caches the application context between test classes,
    // so a container stopped after the first class would leave the cached context pointing at a dead port.
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }

    @Autowired
    protected DSLContext dsl;

    @BeforeEach
    void cleanDatabase() {
        dsl.deleteFrom(IDEMPOTENCY_KEYS).execute();
        dsl.deleteFrom(PAYMENTS).execute();
    }
}
