package io.masterdata;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Arranca el contexto completo sobre H2 en memoria, como el resto de tests: no necesita PostgreSQL. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform="
})
class ApplicationTests {

    @Autowired
    private DataSource dataSource;

    @Test
    void contextLoads() {
    }

    @Test
    void connectionPoolUsesConfiguredSettings() {
        HikariDataSource pool = (HikariDataSource) dataSource;

        assertThat(pool.getMaximumPoolSize()).isEqualTo(10);
        assertThat(pool.getMinimumIdle()).isEqualTo(10);
        assertThat(pool.getConnectionTimeout()).isEqualTo(3000);
        assertThat(pool.getLeakDetectionThreshold()).isEqualTo(2000);
    }
}
