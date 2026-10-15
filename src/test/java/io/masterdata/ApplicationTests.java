package io.masterdata;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Arranca el contexto completo sobre H2 en memoria, como el resto de tests: no necesita PostgreSQL. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:application-tests;DB_CLOSE_DELAY=-1",
        "spring.datasource.driverClassName=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform="
})
class ApplicationTests {

    @Test
    void contextLoads() {
    }
}
