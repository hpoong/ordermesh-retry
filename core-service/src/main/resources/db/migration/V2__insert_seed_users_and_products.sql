-- users seed data
INSERT INTO users (
    id,
    login_id,
    name,
    email,
    phone,
    status,
    point_balance,
    last_login_at,
    deleted_at,
    created_at,
    updated_at
) VALUES
    (1, 'user01', '김민수', 'user01@example.com', '010-1234-0001', 'ACTIVE', 0, '2026-04-01 09:10:00', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2, 'user02', '이서연', 'user02@example.com', '010-1234-0002', 'ACTIVE', 0, '2026-04-02 10:20:00', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3, 'user03', '박지훈', 'user03@example.com', '010-9999-9999', 'ACTIVE', 0,    '2026-03-28 08:00:00', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- products seed data
INSERT INTO products (
    id,
    product_name,
    product_code,
    price,
    stock_quantity,
    status,
    created_at,
    updated_at
) VALUES
    (1,  '노트북 거치대',        'PRD-001',  35000.00, 120, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (2,  '무선 키보드',          'PRD-002',  49000.00,  80, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (3,  '무선 마우스',          'PRD-003',  29000.00, 150, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (4,  'USB-C 허브',           'PRD-004',  39000.00,  60, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (5,  '27인치 모니터',        'PRD-005', 219000.00,  25, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (6,  '모니터 받침대',        'PRD-006',  27000.00,  70, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (7,  '게이밍 헤드셋',        'PRD-007',  89000.00,  40, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (8,  '웹캠 FHD',             'PRD-008',  65000.00,  55, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (9,  '블루투스 스피커',      'PRD-009',  72000.00,  45, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (10, '기계식 키보드',        'PRD-010', 109000.00,  35, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (11, '장패드',               'PRD-011',  12000.00, 200, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (12, '데스크 조명',          'PRD-012',  31000.00,  90, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (13, '노이즈 캔슬링 이어폰', 'PRD-013', 159000.00,  30, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (14, '휴대용 SSD 1TB',       'PRD-014', 145000.00,  20, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (15, '멀티탭 6구',           'PRD-015',  18000.00, 110, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (16, '스마트폰 거치대',      'PRD-016',  15000.00, 130, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (17, '태블릿 파우치',        'PRD-017',  22000.00,  75, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (18, '고속 충전기',          'PRD-018',  28000.00,   0, 'SOLD_OUT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (19, 'HDMI 케이블',          'PRD-019',   9000.00, 160, 'SALE',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    (20, '유선 랜 어댑터',       'PRD-020',  26000.00,  15, 'STOP',     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- identity sequence correction for PostgreSQL
SELECT setval('users_id_seq', (SELECT MAX(id) FROM users));
SELECT setval('products_id_seq', (SELECT MAX(id) FROM products));