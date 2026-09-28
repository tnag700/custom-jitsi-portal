CREATE TABLE config_set_mutation_lock (
    lock_id INTEGER NOT NULL PRIMARY KEY CHECK (lock_id = 1)
);

INSERT INTO config_set_mutation_lock (lock_id) VALUES (1);
