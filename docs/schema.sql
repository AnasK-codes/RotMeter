-- RotMeter schema and seeds, copied from app/src/main/java/com/rotmeter/app/db/Sql.kt.
-- Run once against an empty SQLite database. Dates are stored as yyyy-MM-dd text.

PRAGMA foreign_keys=ON;

-- CREATE_CATEGORIES
CREATE TABLE categories (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL UNIQUE
);

-- CREATE_APPS
CREATE TABLE apps (
    package_name TEXT PRIMARY KEY,
    label TEXT NOT NULL,
    category_id INTEGER NOT NULL REFERENCES categories(id),
    rot_weight REAL NOT NULL
);

-- CREATE_USAGE_LOG
CREATE TABLE usage_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    log_date TEXT NOT NULL,
    package_name TEXT NOT NULL REFERENCES apps(package_name),
    minutes INTEGER NOT NULL CHECK(minutes >= 0),
    UNIQUE(log_date, package_name)
);

-- CREATE_LIMITS
CREATE TABLE limits (
    package_name TEXT PRIMARY KEY REFERENCES apps(package_name),
    daily_limit_min INTEGER NOT NULL CHECK(daily_limit_min > 0)
);

-- CREATE_ALERTS
CREATE TABLE alerts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    alert_date TEXT NOT NULL,
    package_name TEXT NOT NULL REFERENCES apps(package_name),
    message TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
    notified INTEGER NOT NULL DEFAULT 0
);

-- CREATE_CREDITS
CREATE TABLE credits (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    credit_date TEXT NOT NULL,
    package_name TEXT NOT NULL REFERENCES apps(package_name),
    credit_minutes INTEGER NOT NULL
);

-- CREATE_DAILY_ROT_VIEW
CREATE VIEW v_daily_rot AS
SELECT u.log_date,
       SUM(u.minutes) AS total_minutes,
       SUM(CASE WHEN a.rot_weight > 0
           THEN u.minutes * a.rot_weight ELSE 0 END) AS rot_minutes,
       SUM(CASE WHEN a.rot_weight < 0
           THEN u.minutes * -a.rot_weight ELSE 0 END) AS productive_minutes,
       MAX(0, ROUND(SUM(u.minutes * a.rot_weight))) AS rot_score
FROM usage_log AS u
JOIN apps AS a ON a.package_name = u.package_name
GROUP BY u.log_date;

-- CREATE_APP_TODAY_RANK_VIEW
CREATE VIEW v_app_today_rank AS
SELECT u.log_date, u.package_name, a.label, u.minutes, a.rot_weight
FROM usage_log AS u
JOIN apps AS a ON a.package_name = u.package_name
ORDER BY u.minutes * a.rot_weight DESC;

-- CREATE_LIMIT_INSERT_TRIGGER
CREATE TRIGGER trg_limit_insert AFTER INSERT ON usage_log
WHEN NEW.minutes > (
    SELECT daily_limit_min FROM limits WHERE package_name = NEW.package_name
) AND NOT EXISTS (
    SELECT 1 FROM alerts
    WHERE alert_date = NEW.log_date AND package_name = NEW.package_name
)
BEGIN
    INSERT INTO alerts (alert_date, package_name, message)
    SELECT NEW.log_date, NEW.package_name,
           'You crossed your limit on ' || label || '. Touch grass.'
    FROM apps WHERE package_name = NEW.package_name;
END;

-- CREATE_LIMIT_UPDATE_TRIGGER
CREATE TRIGGER trg_limit_update AFTER UPDATE OF minutes ON usage_log
WHEN NEW.minutes > (
    SELECT daily_limit_min FROM limits WHERE package_name = NEW.package_name
) AND NOT EXISTS (
    SELECT 1 FROM alerts
    WHERE alert_date = NEW.log_date AND package_name = NEW.package_name
)
BEGIN
    INSERT INTO alerts (alert_date, package_name, message)
    SELECT NEW.log_date, NEW.package_name,
           'You crossed your limit on ' || label || '. Touch grass.'
    FROM apps WHERE package_name = NEW.package_name;
END;

-- CREATE_CREDIT_INSERT_TRIGGER
CREATE TRIGGER trg_credit_insert AFTER INSERT ON usage_log
WHEN (SELECT rot_weight FROM apps WHERE package_name = NEW.package_name) < 0
BEGIN
    DELETE FROM credits
    WHERE credit_date = NEW.log_date AND package_name = NEW.package_name;
    INSERT INTO credits (credit_date, package_name, credit_minutes)
    VALUES (NEW.log_date, NEW.package_name, NEW.minutes);
END;

-- CREATE_CREDIT_UPDATE_TRIGGER
CREATE TRIGGER trg_credit_update AFTER UPDATE OF minutes ON usage_log
WHEN (SELECT rot_weight FROM apps WHERE package_name = NEW.package_name) < 0
BEGIN
    DELETE FROM credits
    WHERE credit_date = NEW.log_date AND package_name = NEW.package_name;
    INSERT INTO credits (credit_date, package_name, credit_minutes)
    VALUES (NEW.log_date, NEW.package_name, NEW.minutes);
END;

-- SEED_CATEGORIES
INSERT INTO categories (name) VALUES ('Social'), ('Video'), ('Productive'), ('Games');

-- SEED_APPS
INSERT INTO apps (package_name, label, category_id, rot_weight) VALUES
('com.instagram.android', 'Instagram', (SELECT id FROM categories WHERE name = 'Social'), 1.0),
('com.zhiliaoapp.musically', 'TikTok', (SELECT id FROM categories WHERE name = 'Video'), 1.0),
('com.snapchat.android', 'Snapchat', (SELECT id FROM categories WHERE name = 'Social'), 0.8),
('com.twitter.android', 'X', (SELECT id FROM categories WHERE name = 'Social'), 0.7),
('com.reddit.frontpage', 'Reddit', (SELECT id FROM categories WHERE name = 'Social'), 0.7),
('com.facebook.katana', 'Facebook', (SELECT id FROM categories WHERE name = 'Social'), 0.6),
('com.google.android.youtube', 'YouTube', (SELECT id FROM categories WHERE name = 'Video'), 0.6),
('com.netflix.mediaclient', 'Netflix', (SELECT id FROM categories WHERE name = 'Video'), 0.4),
('com.supercell.clashofclans', 'Clash of Clans', (SELECT id FROM categories WHERE name = 'Games'), 0.5),
('com.leetcode.app', 'LeetCode', (SELECT id FROM categories WHERE name = 'Productive'), -1.0),
('com.amazon.kindle', 'Kindle', (SELECT id FROM categories WHERE name = 'Productive'), -0.8),
('com.duolingo', 'Duolingo', (SELECT id FROM categories WHERE name = 'Productive'), -0.6);
