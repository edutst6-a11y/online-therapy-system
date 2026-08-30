-- Insert Sample Users
INSERT INTO users (id, name, email, role, password) VALUES 
(1, 'Sarah Jenkins', 'sarah.j@example.com', 'CLIENT', 'password123'),
(2, 'Michael Ndlovu', 'michael.n@example.com', 'CLIENT', 'password123'),
(3, 'Dr. Emily Vance', 'dr.vance@therapy.com', 'THERAPIST', 'securepass123'),
(4, 'Dr. James Smith', 'dr.smith@therapy.com', 'THERAPIST', 'securepass123');

-- Insert Sample Appointments
INSERT INTO appointments (id, client_id, therapist_id, appointment_date, status) VALUES 
(1, 1, 3, '2026-09-01 10:00:00', 'SCHEDULED'),
(2, 2, 4, '2026-09-02 14:00:00', 'COMPLETED');
ALTER TABLE users ALTER COLUMN id RESTART WITH 100;

ALTER TABLE appointments ALTER COLUMN id RESTART WITH 100;
