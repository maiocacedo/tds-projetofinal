package com.nossoteto.integration;

import com.nossoteto.model.Chore;
import com.nossoteto.model.Expense;
import com.nossoteto.model.ExpenseSplit;
import com.nossoteto.model.ExpenseSplitVote;
import com.nossoteto.model.Grocery;
import com.nossoteto.model.House;
import com.nossoteto.model.MatchRequest;
import com.nossoteto.model.MatchVote;
import com.nossoteto.model.User;
import com.nossoteto.model.UserProfile;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.type.SqlTypes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

class MySqlSchemaCompatibilityTest {

    @Test
    void allEntitiesShouldMapEnumColumnsAsVarcharOnMySqlDialect() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.MySQLDialect")
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClass(User.class)
                    .addAnnotatedClass(UserProfile.class)
                    .addAnnotatedClass(House.class)
                    .addAnnotatedClass(MatchRequest.class)
                    .addAnnotatedClass(MatchVote.class)
                    .addAnnotatedClass(Expense.class)
                    .addAnnotatedClass(ExpenseSplit.class)
                    .addAnnotatedClass(ExpenseSplitVote.class)
                    .addAnnotatedClass(Chore.class)
                    .addAnnotatedClass(Grocery.class)
                    .buildMetadata();

            for (PersistentClass entityBinding : metadata.getEntityBindings()) {
                for (Column column : entityBinding.getTable().getColumns()) {
                    Integer sqlTypeCode = column.getSqlTypeCode(metadata);
                    assertNotEquals(
                            SqlTypes.ENUM,
                            sqlTypeCode,
                            () -> "Table '" + entityBinding.getTable().getName()
                                    + "' column '" + column.getName()
                                    + "' mapped as MySQL ENUM instead of VARCHAR (breaks Flyway V2 schema validation)"
                    );
                }
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
