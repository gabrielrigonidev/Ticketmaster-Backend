INSERT INTO tb_scopes (scope_id, name) VALUES
    (1, 'events:list'), (2, 'events:read'), (3, 'seats:list'),
    (4, 'bookings:reserve'), (5, 'bookings:confirm'), (6, 'bookings:reject')
ON CONFLICT (scope_id) DO NOTHING;

INSERT INTO tb_roles (role_id, name) VALUES
    (1, 'user'), (2, 'admin'), (3, 'payment_gtw')
ON CONFLICT (role_id) DO NOTHING;

INSERT INTO tb_role_scopes (role_id, scope_id) VALUES
    (1,1),(1,2),(1,3),(1,4)
ON CONFLICT (role_id, scope_id) DO NOTHING;

INSERT INTO tb_role_scopes (role_id, scope_id) VALUES
    (3,5),(3,6)
ON CONFLICT (role_id, scope_id) DO NOTHING;
