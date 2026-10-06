package com.rotmeter.app.db

object Sql {
    const val ENABLE_FOREIGN_KEYS = "PRAGMA foreign_keys=ON"

    const val CREATE_CATEGORIES = """
        CREATE TABLE categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL UNIQUE
        )
    """

    const val CREATE_APPS = """
        CREATE TABLE apps (
            package_name TEXT PRIMARY KEY,
            label TEXT NOT NULL,
            category_id INTEGER NOT NULL REFERENCES categories(id),
            rot_weight REAL NOT NULL
        )
    """

    const val CREATE_USAGE_LOG = """
        CREATE TABLE usage_log (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            log_date TEXT NOT NULL,
            package_name TEXT NOT NULL REFERENCES apps(package_name),
            minutes INTEGER NOT NULL CHECK(minutes >= 0),
            UNIQUE(log_date, package_name)
        )
    """

    const val CREATE_LIMITS = """
        CREATE TABLE limits (
            package_name TEXT PRIMARY KEY REFERENCES apps(package_name),
            daily_limit_min INTEGER NOT NULL CHECK(daily_limit_min > 0)
        )
    """

    const val CREATE_ALERTS = """
        CREATE TABLE alerts (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            alert_date TEXT NOT NULL,
            package_name TEXT NOT NULL REFERENCES apps(package_name),
            message TEXT NOT NULL,
            created_at TEXT NOT NULL DEFAULT (datetime('now', 'localtime')),
            notified INTEGER NOT NULL DEFAULT 0
        )
    """

    const val CREATE_CREDITS = """
        CREATE TABLE credits (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            credit_date TEXT NOT NULL,
            package_name TEXT NOT NULL REFERENCES apps(package_name),
            credit_minutes INTEGER NOT NULL
        )
    """

    const val CREATE_DAILY_ROT_VIEW = """
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
        GROUP BY u.log_date
    """

    const val CREATE_APP_TODAY_RANK_VIEW = """
        CREATE VIEW v_app_today_rank AS
        SELECT u.log_date, u.package_name, a.label, u.minutes, a.rot_weight
        FROM usage_log AS u
        JOIN apps AS a ON a.package_name = u.package_name
        ORDER BY u.minutes * a.rot_weight DESC
    """

    const val CREATE_LIMIT_INSERT_TRIGGER = """
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
        END
    """

    const val CREATE_LIMIT_UPDATE_TRIGGER = """
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
        END
    """

    const val CREATE_CREDIT_INSERT_TRIGGER = """
        CREATE TRIGGER trg_credit_insert AFTER INSERT ON usage_log
        WHEN (SELECT rot_weight FROM apps WHERE package_name = NEW.package_name) < 0
        BEGIN
            DELETE FROM credits
            WHERE credit_date = NEW.log_date AND package_name = NEW.package_name;
            INSERT INTO credits (credit_date, package_name, credit_minutes)
            VALUES (NEW.log_date, NEW.package_name, NEW.minutes);
        END
    """

    const val CREATE_CREDIT_UPDATE_TRIGGER = """
        CREATE TRIGGER trg_credit_update AFTER UPDATE OF minutes ON usage_log
        WHEN (SELECT rot_weight FROM apps WHERE package_name = NEW.package_name) < 0
        BEGIN
            DELETE FROM credits
            WHERE credit_date = NEW.log_date AND package_name = NEW.package_name;
            INSERT INTO credits (credit_date, package_name, credit_minutes)
            VALUES (NEW.log_date, NEW.package_name, NEW.minutes);
        END
    """

    const val SEED_CATEGORIES = """
        INSERT INTO categories (name) VALUES ('Social'), ('Video'), ('Productive'), ('Games')
    """

    const val SEED_APPS = """
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
        ('com.duolingo', 'Duolingo', (SELECT id FROM categories WHERE name = 'Productive'), -0.6)
    """

    const val DROP_LIMIT_INSERT_TRIGGER = "DROP TRIGGER IF EXISTS trg_limit_insert"
    const val DROP_LIMIT_UPDATE_TRIGGER = "DROP TRIGGER IF EXISTS trg_limit_update"
    const val DROP_CREDIT_INSERT_TRIGGER = "DROP TRIGGER IF EXISTS trg_credit_insert"
    const val DROP_CREDIT_UPDATE_TRIGGER = "DROP TRIGGER IF EXISTS trg_credit_update"
    const val DROP_DAILY_ROT_VIEW = "DROP VIEW IF EXISTS v_daily_rot"
    const val DROP_APP_TODAY_RANK_VIEW = "DROP VIEW IF EXISTS v_app_today_rank"
    const val DROP_CREDITS = "DROP TABLE IF EXISTS credits"
    const val DROP_ALERTS = "DROP TABLE IF EXISTS alerts"
    const val DROP_LIMITS = "DROP TABLE IF EXISTS limits"
    const val DROP_USAGE_LOG = "DROP TABLE IF EXISTS usage_log"
    const val DROP_APPS = "DROP TABLE IF EXISTS apps"
    const val DROP_CATEGORIES = "DROP TABLE IF EXISTS categories"

    const val DELETE_USAGE_FOR_DATE_APP = "DELETE FROM usage_log WHERE log_date = ? AND package_name = ?"
    const val DELETE_ALERTS_FOR_DATE_APP = "DELETE FROM alerts WHERE alert_date = ? AND package_name = ?"
    const val DELETE_LIMIT_FOR_APP = "DELETE FROM limits WHERE package_name = ?"
    const val INSERT_LIMIT = "INSERT INTO limits (package_name, daily_limit_min) VALUES (?, ?)"
    const val INSERT_USAGE = "INSERT INTO usage_log (log_date, package_name, minutes) VALUES (?, ?, ?)"
    const val COUNT_ALERTS_FOR_DATE_APP = """
        SELECT COUNT(*) FROM alerts WHERE alert_date = ? AND package_name = ?
    """

    const val SELECT_DAILY_ROT = """
        SELECT total_minutes, rot_score, productive_minutes
        FROM v_daily_rot WHERE log_date = ?
    """

    const val SELECT_TOP_ROT_APPS = """
        SELECT package_name, label, minutes, rot_weight
        FROM v_app_today_rank
        WHERE log_date = ? AND rot_weight > 0
        ORDER BY minutes * rot_weight DESC, package_name ASC
        LIMIT ?
    """

    const val SELECT_WEEK_SCORES = """
        SELECT log_date, rot_score FROM v_daily_rot
        WHERE log_date BETWEEN ? AND ?
        ORDER BY log_date ASC
    """

    const val SELECT_WEEK_OVER_WEEK = """
        SELECT COALESCE(SUM(CASE WHEN log_date BETWEEN ? AND ?
                   THEN rot_score ELSE 0 END), 0) AS this_week_score,
               COALESCE(SUM(CASE WHEN log_date BETWEEN ? AND ?
                   THEN rot_score ELSE 0 END), 0) AS last_week_score
        FROM v_daily_rot
        WHERE log_date BETWEEN ? AND ?
    """

    const val SELECT_WORST_APP = """
        SELECT u.package_name, a.label, SUM(u.minutes) AS minutes, a.rot_weight
        FROM usage_log AS u
        JOIN apps AS a ON a.package_name = u.package_name
        WHERE u.log_date BETWEEN ? AND ?
        GROUP BY u.package_name, a.label, a.rot_weight
        ORDER BY SUM(u.minutes * a.rot_weight) DESC, u.package_name ASC
        LIMIT 1
    """

    const val SELECT_WEEK_TOTALS_PER_APP = """
        SELECT u.package_name, a.label, SUM(u.minutes) AS minutes, a.rot_weight
        FROM usage_log AS u
        JOIN apps AS a ON a.package_name = u.package_name
        WHERE u.log_date BETWEEN ? AND ?
        GROUP BY u.package_name, a.label, a.rot_weight
        ORDER BY SUM(u.minutes * a.rot_weight) DESC, u.package_name ASC
    """

    const val SELECT_BEAT_AVERAGE_DAYS = """
        SELECT COUNT(*) FROM v_daily_rot
        WHERE rot_score < (SELECT AVG(rot_score) FROM v_daily_rot)
    """

    const val UPDATE_USAGE = """
        UPDATE usage_log SET minutes = ? WHERE log_date = ? AND package_name = ?
    """

    const val SELECT_ALL_APPS = """
        SELECT a.package_name, a.label, a.category_id, c.name AS category_name,
               a.rot_weight, l.daily_limit_min
        FROM apps AS a
        JOIN categories AS c ON c.id = a.category_id
        LEFT JOIN limits AS l ON l.package_name = a.package_name
        ORDER BY a.label ASC, a.package_name ASC
    """

    const val SELECT_ALL_APPS_TODAY = """
        SELECT a.package_name, a.label, a.category_id, c.name AS category_name,
               a.rot_weight, l.daily_limit_min, COALESCE(u.minutes, 0) AS today_minutes
        FROM apps AS a
        JOIN categories AS c ON c.id = a.category_id
        LEFT JOIN limits AS l ON l.package_name = a.package_name
        LEFT JOIN usage_log AS u ON u.package_name = a.package_name AND u.log_date = ?
        ORDER BY a.label ASC, a.package_name ASC
    """

    const val SELECT_BLOCKING_LIMIT = """
        SELECT a.label, l.daily_limit_min
        FROM limits AS l
        JOIN apps AS a ON a.package_name = l.package_name
        WHERE l.package_name = ?
    """

    const val UPDATE_LIMIT = "UPDATE limits SET daily_limit_min = ? WHERE package_name = ?"

    const val SELECT_ALERTS = """
        SELECT al.id, al.alert_date, al.package_name, a.label,
               al.message, al.created_at, al.notified
        FROM alerts AS al
        JOIN apps AS a ON a.package_name = al.package_name
        ORDER BY al.created_at DESC, al.id DESC
    """

    const val SELECT_UNNOTIFIED_ALERTS = """
        SELECT al.id, al.alert_date, al.package_name, a.label,
               al.message, al.created_at, al.notified
        FROM alerts AS al
        JOIN apps AS a ON a.package_name = al.package_name
        WHERE al.notified = 0
        ORDER BY al.created_at DESC, al.id DESC
    """

    const val MARK_ALERT_NOTIFIED = "UPDATE alerts SET notified = 1 WHERE id = ?"
    const val MARK_OLD_ALERTS_NOTIFIED = """
        UPDATE alerts SET notified = 1 WHERE alert_date < ? AND notified = 0
    """
    const val SELECT_TOTAL_CREDITS = """
        SELECT COALESCE(SUM(credit_minutes), 0) FROM credits WHERE credit_date = ?
    """

    // Group before Kotlin sums XP: several alerts must not duplicate a day's productive minutes.
    const val SELECT_GAMIFICATION_DAYS = """
        SELECT d.log_date, d.total_minutes, d.rot_score, d.productive_minutes,
               COUNT(al.id) AS alert_count,
               (SELECT AVG(rot_score) FROM v_daily_rot) AS average_rot_score
        FROM v_daily_rot AS d
        LEFT JOIN alerts AS al ON al.alert_date = d.log_date
        GROUP BY d.log_date, d.total_minutes, d.rot_score, d.productive_minutes
        ORDER BY d.log_date ASC
    """

    const val SELECT_TODAY_TOP_APP_LIMIT = """
        SELECT r.package_name, r.label, r.minutes, r.rot_weight, l.daily_limit_min
        FROM v_app_today_rank AS r
        LEFT JOIN limits AS l ON l.package_name = r.package_name
        WHERE r.log_date = ? AND r.rot_weight > 0
        ORDER BY r.minutes * r.rot_weight DESC, r.package_name ASC
        LIMIT 1
    """

    const val DELETE_ALL_USAGE = "DELETE FROM usage_log"
    const val DELETE_ALL_ALERTS = "DELETE FROM alerts"
    const val DELETE_ALL_CREDITS = "DELETE FROM credits"
}
