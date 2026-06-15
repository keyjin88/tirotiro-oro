package oro.tirotiro.equipmentwarehouse.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.core.yaml.YamlChangeLogParser;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.junit.jupiter.api.Test;

class LiquibaseChangelogTests {

    @Test
    void masterChangelogIncludesPersistenceSchemaChangesets() throws Exception {
        DatabaseChangeLog changeLog = new YamlChangeLogParser().parse(
                "db/changelog/db.changelog-master.yaml",
                new ChangeLogParameters(),
                new ClassLoaderResourceAccessor());

        assertThat(changeLog.getChangeSets())
                .extracting("id")
                .containsExactly(
                        "001-create-users",
                        "002-create-permissions",
                        "003-create-equipment-catalog",
                        "004-create-bookings",
                        "005-create-audit-log");
    }
}
