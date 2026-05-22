package io.mindspice.magenta2.avatar;

import javax.sql.DataSource;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

@Component
public class AvatarSchemaInitializer implements InitializingBean {
    private final DataSource dataSource;

    public AvatarSchemaInitializer(@Qualifier("avatarDataSource") DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() {
        initialize();
    }

    public void initialize() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(new ClassPathResource("avatar-schema.sql"));
        populator.execute(dataSource);
    }
}
