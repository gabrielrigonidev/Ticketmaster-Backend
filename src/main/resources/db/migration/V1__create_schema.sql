-- F1: Eventos & Assentos
CREATE TABLE IF NOT EXISTS tb_events (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255),
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS tb_seats (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT,
    name VARCHAR(255),
    status VARCHAR(50),
    CONSTRAINT fk_seats_event FOREIGN KEY (event_id) REFERENCES tb_events(id)
);
