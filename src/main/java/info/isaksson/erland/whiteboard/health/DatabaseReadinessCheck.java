package info.isaksson.erland.whiteboard.health;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.agroal.api.AgroalDataSource;

/**
 * Readiness check that verifies database connectivity.
 *
 * In test profile (or if no datasource is configured), this check reports UP with a note.
 */
@Readiness
@ApplicationScoped
public class DatabaseReadinessCheck implements HealthCheck {

    @Inject
    Instance<AgroalDataSource> dataSource;

    @Override
    public HealthCheckResponse call() {
        if (dataSource == null || dataSource.isUnsatisfied()) {
            return HealthCheckResponse.named("database").up().withData("note", "no datasource configured").build();
        }

        try (Connection c = dataSource.get().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT 1");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return HealthCheckResponse.named("database").up().build();
        } catch (Exception e) {
            return HealthCheckResponse.named("database").down().withData("error", e.getClass().getSimpleName()).build();
        }
    }
}
