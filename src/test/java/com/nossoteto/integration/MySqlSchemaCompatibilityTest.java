package com.nossoteto.integration;

import com.nossoteto.model.PointLedgerEntry;
import com.nossoteto.model.User;
import jakarta.persistence.Entity;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlSchemaCompatibilityTest {

    @Test
    void allEntitiesShouldMapEnumColumnsAsVarcharOnMySqlDialect() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));

        List<Class<?>> entityClasses = new ArrayList<>();
        for (BeanDefinition bd : scanner.findCandidateComponents("com.nossoteto.model")) {
            entityClasses.add(Class.forName(bd.getBeanClassName()));
        }

        assertTrue(entityClasses.size() >= 13,
                () -> "Expected at least 13 @Entity classes in com.nossoteto.model, found " + entityClasses.size());
        assertTrue(entityClasses.contains(User.class), "Expected User.class in scanned entities");
        assertTrue(entityClasses.contains(PointLedgerEntry.class),
                "Expected PointLedgerEntry.class in scanned entities");

        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect")
                .build();
        try {
            MetadataSources sources = new MetadataSources(registry);
            entityClasses.forEach(sources::addAnnotatedClass);
            Metadata metadata = sources.buildMetadata();

            for (PersistentClass entityBinding : metadata.getEntityBindings()) {
                for (Column column : entityBinding.getTable().getColumns()) {
                    Integer sqlTypeCode = column.getSqlTypeCode(metadata);
                    assertNotEquals(
                            SqlTypes.ENUM,
                            sqlTypeCode,
                            () -> "Table '" + entityBinding.getTable().getName()
                                    + "' column '" + column.getName()
                                    + "' mapped as MySQL ENUM instead of VARCHAR (breaks Flyway schema validation)"
                    );
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
