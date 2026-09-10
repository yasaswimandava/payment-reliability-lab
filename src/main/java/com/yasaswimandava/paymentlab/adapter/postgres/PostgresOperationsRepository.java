package com.yasaswimandava.paymentlab.adapter.postgres;

import com.yasaswimandava.paymentlab.domain.OperationsSnapshot;
import com.yasaswimandava.paymentlab.port.OperationsRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;

public final class PostgresOperationsRepository implements OperationsRepository {

    private static final String SNAPSHOT = """
            select
                (select count(*) from payments) as total_payments,
                (select count(*) from payments where status = 'RECEIVED') as received_payments,
                (select count(*) from payments
                    where status = 'RECEIVED'
                    and updated_at < current_timestamp - interval '30 seconds')
                    as stale_received_payments,
                (select count(*) from payments where status = 'AUTHORIZED') as authorized_payments,
                (select count(*) from payments where status = 'DECLINED') as declined_payments,
                (select count(*) from outbox_events where status <> 'PUBLISHED')
                    as unpublished_outbox_events,
                (select count(*) from outbox_events
                    where status <> 'PUBLISHED'
                    and occurred_at < current_timestamp - interval '30 seconds')
                    as stale_unpublished_outbox_events,
                (select count(*) from outbox_events where status = 'PROCESSING')
                    as processing_outbox_events,
                (select count(*) from outbox_events where status = 'FAILED')
                    as failed_outbox_events,
                (select min(occurred_at) from outbox_events where status <> 'PUBLISHED')
                    as oldest_unpublished_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresOperationsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public OperationsSnapshot snapshot() {
        return jdbcTemplate.queryForObject(SNAPSHOT, this::mapSnapshot);
    }

    private OperationsSnapshot mapSnapshot(ResultSet resultSet, int rowNumber)
            throws SQLException {
        OffsetDateTime oldest = resultSet.getObject(
                "oldest_unpublished_at", OffsetDateTime.class);
        return new OperationsSnapshot(
                resultSet.getLong("total_payments"),
                resultSet.getLong("received_payments"),
                resultSet.getLong("stale_received_payments"),
                resultSet.getLong("authorized_payments"),
                resultSet.getLong("declined_payments"),
                resultSet.getLong("unpublished_outbox_events"),
                resultSet.getLong("stale_unpublished_outbox_events"),
                resultSet.getLong("processing_outbox_events"),
                resultSet.getLong("failed_outbox_events"),
                oldest == null ? null : oldest.toInstant());
    }
}
