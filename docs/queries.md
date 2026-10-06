# RotMeter SQL queries

SQL below is copied from [`Sql.kt`](../app/src/main/java/com/rotmeter/app/db/Sql.kt).
Each `?` is a bound parameter, listed in positional order; dates use `yyyy-MM-dd` and ranges include both endpoints.
The two update-then-insert examples are conditional repository operations, not instructions to run both statements unconditionally.

## Daily weighted Rot Score

**Constants:** `CREATE_DAILY_ROT_VIEW`

```sql
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
```

Groups usage by date and adds raw minutes, positive weighted minutes, and negative-weight productive minutes.  
Rounds the net weighted minutes and clamps the score to zero; productive_minutes is weighted, unlike the raw credit counter.

**Screen:** Home: Rot Score and level; Stats: seven-day chart and days below the average.

## Today’s ranking view

**Constants:** `CREATE_APP_TODAY_RANK_VIEW`

```sql
CREATE VIEW v_app_today_rank AS
SELECT u.log_date, u.package_name, a.label, u.minutes, a.rot_weight
FROM usage_log AS u
JOIN apps AS a ON a.package_name = u.package_name
ORDER BY u.minutes * a.rot_weight DESC;
```

Joins each usage record to its app label and weight, keeping the date on every row.  
Orders by minutes multiplied by weight; the name does not itself filter to today, so the screen query supplies the date.

**Screen:** Home: top three rot apps.

## Today’s score lookup

**Constants:** `SELECT_DAILY_ROT`

```sql
SELECT total_minutes, rot_score, productive_minutes
FROM v_daily_rot WHERE log_date = ?;
```

Reads the daily totals and score for one date from the daily view.  
The repository returns zero values when that date has no row.

**Screen:** Home: score and level.

**Parameters:** date.

## Top rot apps

**Constants:** `SELECT_TOP_ROT_APPS`

```sql
SELECT package_name, label, minutes, rot_weight
FROM v_app_today_rank
WHERE log_date = ? AND rot_weight > 0
ORDER BY minutes * rot_weight DESC, package_name ASC
LIMIT ?;
```

Keeps only positive-weight apps on the requested date and ranks their weighted contribution.  
Returns raw minutes for display, with package name breaking ties and LIMIT controlling the list size.

**Screen:** Home: top three rot apps.

**Parameters:** date, limit (Home uses 3).

## Seven-day scores

**Constants:** `SELECT_WEEK_SCORES`

```sql
SELECT log_date, rot_score FROM v_daily_rot
WHERE log_date BETWEEN ? AND ?
ORDER BY log_date ASC;
```

Reads daily scores in the inclusive seven-day range and sorts them from oldest to newest.  
Kotlin fills missing dates with zero so the chart always receives seven days.

**Screen:** Stats: seven bars.

**Parameters:** endDate minus 6 days, endDate.

## Week-over-week conditional aggregation

**Constants:** `SELECT_WEEK_OVER_WEEK`

```sql
SELECT COALESCE(SUM(CASE WHEN log_date BETWEEN ? AND ?
           THEN rot_score ELSE 0 END), 0) AS this_week_score,
       COALESCE(SUM(CASE WHEN log_date BETWEEN ? AND ?
           THEN rot_score ELSE 0 END), 0) AS last_week_score
FROM v_daily_rot
WHERE log_date BETWEEN ? AND ?;
```

One query uses two CASE expressions to add the daily scores for this seven-day period and the preceding seven days.  
COALESCE returns zero for empty periods; Kotlin calculates the percentage and handles a zero previous-week total.

**Screen:** Home: this week versus last week.

**Parameters:** endDate minus 6 days, endDate, endDate minus 13 days, endDate minus 7 days, endDate minus 13 days, endDate.

## Worst app with GROUP BY

**Constants:** `SELECT_WORST_APP`

```sql
SELECT u.package_name, a.label, SUM(u.minutes) AS minutes, a.rot_weight
FROM usage_log AS u
JOIN apps AS a ON a.package_name = u.package_name
WHERE u.log_date BETWEEN ? AND ?
GROUP BY u.package_name, a.label, a.rot_weight
ORDER BY SUM(u.minutes * a.rot_weight) DESC, u.package_name ASC
LIMIT 1;
```

Groups the selected dates by app and sums each app’s raw minutes.  
Orders by summed weighted minutes and returns one winner, while the screen displays that winner’s raw minutes.

**Screen:** Stats: Worst app this week.

**Parameters:** startDate, endDate.

## Weekly totals per app

**Constants:** `SELECT_WEEK_TOTALS_PER_APP`

```sql
SELECT u.package_name, a.label, SUM(u.minutes) AS minutes, a.rot_weight
FROM usage_log AS u
JOIN apps AS a ON a.package_name = u.package_name
WHERE u.log_date BETWEEN ? AND ?
GROUP BY u.package_name, a.label, a.rot_weight
ORDER BY SUM(u.minutes * a.rot_weight) DESC, u.package_name ASC;
```

Groups usage across the selected dates into one total per app, including productive apps.  
Returns raw minute totals and orders rows by weighted contribution, with package name breaking ties.

**Screen:** Stats: App minutes this week.

**Parameters:** startDate, endDate.

## Beat-average days subquery

**Constants:** `SELECT_BEAT_AVERAGE_DAYS`

```sql
SELECT COUNT(*) FROM v_daily_rot
WHERE rot_score < (SELECT AVG(rot_score) FROM v_daily_rot);
```

The inner query finds the mean Rot Score across all recorded dates.  
The outer query counts recorded dates strictly below that mean; missing dates are excluded and an empty database returns zero.

**Screen:** Stats: Days you beat your average.

## All apps with optional limits

**Constants:** `SELECT_ALL_APPS`

```sql
SELECT a.package_name, a.label, a.category_id, c.name AS category_name,
       a.rot_weight, l.daily_limit_min
FROM apps AS a
JOIN categories AS c ON c.id = a.category_id
LEFT JOIN limits AS l ON l.package_name = a.package_name
ORDER BY a.label ASC, a.package_name ASC;
```

Joins apps to their category and uses LEFT JOIN to keep apps that have no limit.  
A missing limit is returned as null and displayed as No limit; labels determine the list order.

**Screen:** Limits: app list; also supplies the tracked packages for usage sync.

## Newest alerts

**Constants:** `SELECT_ALERTS`

```sql
SELECT al.id, al.alert_date, al.package_name, a.label,
       al.message, al.created_at, al.notified
FROM alerts AS al
JOIN apps AS a ON a.package_name = al.package_name
ORDER BY al.created_at DESC, al.id DESC;
```

Joins stored roasts to current app labels so each item has a message, label, and creation time.  
Sorts by creation time descending and then alert ID descending to make ties deterministic.

**Screen:** Alerts: roast list.

## Pending notifications

**Constants:** `SELECT_UNNOTIFIED_ALERTS`

```sql
SELECT al.id, al.alert_date, al.package_name, a.label,
       al.message, al.created_at, al.notified
FROM alerts AS al
JOIN apps AS a ON a.package_name = al.package_name
WHERE al.notified = 0
ORDER BY al.created_at DESC, al.id DESC;
```

Reads only alerts whose notified flag is zero and includes each app’s label.  
Manual sync, demo loading, and the worker attempt delivery; blocked notifications leave these rows pending.

**Screen:** Notification shade; the corresponding rows also appear on Alerts.

## Mark delivered notifications

**Constants:** `MARK_ALERT_NOTIFIED`

```sql
UPDATE alerts SET notified = 1 WHERE id = ?;
```

Changes one alert’s notified flag to one after NotificationHelper reports successful posting.  
It keeps the alert in the database, so notification delivery does not remove it from the Alerts screen.

**Screen:** Notification delivery after sync, demo load, or worker execution.

**Parameters:** alert ID.

## Mark older demo alerts

**Constants:** `MARK_OLD_ALERTS_NOTIFIED`

```sql
UPDATE alerts SET notified = 1 WHERE alert_date < ? AND notified = 0;
```

Marks unnotified alerts dated before the supplied date as already handled.  
Demo loading uses today as the cutoff so only today’s demo alerts are eligible for later notification delivery.

**Screen:** Load demo data; Alerts still shows historical roasts.

**Parameters:** today in yyyy-MM-dd format.

## Today’s productive credits

**Constants:** `SELECT_TOTAL_CREDITS`

```sql
SELECT COALESCE(SUM(credit_minutes), 0) FROM credits WHERE credit_date = ?;
```

Adds credit_minutes from productive apps for the requested date and returns zero when no rows match.  
These are raw productive minutes maintained by triggers, rather than the weighted productive_minutes in v_daily_rot.

**Screen:** Home: Productive credits today.

**Parameters:** date.

## Usage update-then-insert

**Constants:** `UPDATE_USAGE`, `INSERT_USAGE`

```sql
UPDATE usage_log SET minutes = ? WHERE log_date = ? AND package_name = ?;

INSERT INTO usage_log (log_date, package_name, minutes) VALUES (?, ?, ?);
```

Inside a transaction, the repository updates the existing date-and-package row first and inserts only if zero rows were updated.  
Both paths fire the matching SQL triggers, and this avoids SQLite UPSERT syntax on older Android versions.

**Screen:** Home: Sync now and Load demo data; worker updates feed all screens.

**Parameters:** UPDATE: minutes, date, packageName; INSERT: date, packageName, minutes.

## Limit update-then-insert

**Constants:** `UPDATE_LIMIT`, `INSERT_LIMIT`

```sql
UPDATE limits SET daily_limit_min = ? WHERE package_name = ?;

INSERT INTO limits (package_name, daily_limit_min) VALUES (?, ?);
```

Inside a transaction, the repository updates the app’s limit and inserts only if no existing row was updated.  
The dialog accepts 5–600 minutes; saving a limit alone does not fire usage_log triggers.

**Screen:** Limits: Set limit.

**Parameters:** UPDATE: minutes, packageName; INSERT: packageName, minutes.

## Remove a limit

**Constants:** `DELETE_LIMIT_FOR_APP`

```sql
DELETE FROM limits WHERE package_name = ?;
```

Deletes the limit row for the selected app while preserving the app and its usage history.  
Future usage writes no longer meet a limit-trigger condition for that app, but existing alerts remain.

**Screen:** Limits: Remove limit.

**Parameters:** packageName.

## Limit trigger after insert

**Constants:** `CREATE_LIMIT_INSERT_TRIGGER`

```sql
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
```

After inserting usage, checks whether minutes strictly exceed the app’s limit and no alert exists for that date and app.  
Builds a roast from the app label and inserts an alert; an absent limit produces no alert.

**Screen:** Alerts and roast notifications after a new usage row.

## Limit trigger after update

**Constants:** `CREATE_LIMIT_UPDATE_TRIGGER`

```sql
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
```

After updating usage minutes, checks the same limit and date-and-app alert-existence conditions.  
Creates a roast only if none already exists for that day and app, avoiding repeated alerts during later syncs.

**Screen:** Alerts and roast notifications after a usage-minute update.

## Credit trigger after insert

**Constants:** `CREATE_CREDIT_INSERT_TRIGGER`

```sql
CREATE TRIGGER trg_credit_insert AFTER INSERT ON usage_log
WHEN (SELECT rot_weight FROM apps WHERE package_name = NEW.package_name) < 0
BEGIN
    DELETE FROM credits
    WHERE credit_date = NEW.log_date AND package_name = NEW.package_name;
    INSERT INTO credits (credit_date, package_name, credit_minutes)
    VALUES (NEW.log_date, NEW.package_name, NEW.minutes);
END;
```

For a newly inserted usage row whose app has a negative weight, deletes any existing credit for the same date and package.  
Inserts the new raw minute value so repeated writes do not accumulate duplicate credit amounts.

**Screen:** Home: Productive credits today.

## Credit trigger after update

**Constants:** `CREATE_CREDIT_UPDATE_TRIGGER`

```sql
CREATE TRIGGER trg_credit_update AFTER UPDATE OF minutes ON usage_log
WHEN (SELECT rot_weight FROM apps WHERE package_name = NEW.package_name) < 0
BEGIN
    DELETE FROM credits
    WHERE credit_date = NEW.log_date AND package_name = NEW.package_name;
    INSERT INTO credits (credit_date, package_name, credit_minutes)
    VALUES (NEW.log_date, NEW.package_name, NEW.minutes);
END;
```

For a negative-weight app whose usage minutes change, removes the previous credit row for that date and package.  
Inserts the updated raw minute value so credits follow the current usage total rather than adding it again.

**Screen:** Home: Productive credits today.

## Daily XP, streak, and badge inputs

**Constants:** `SELECT_GAMIFICATION_DAYS`

```sql
SELECT d.log_date, d.total_minutes, d.rot_score, d.productive_minutes,
       COUNT(al.id) AS alert_count,
       (SELECT AVG(rot_score) FROM v_daily_rot) AS average_rot_score
FROM v_daily_rot AS d
LEFT JOIN alerts AS al ON al.alert_date = d.log_date
GROUP BY d.log_date, d.total_minutes, d.rot_score, d.productive_minutes
ORDER BY d.log_date ASC;
```

Returns one row per recorded usage day with its weighted productive minutes, score, alert count, and the average score across all recorded days.  
The LEFT JOIN retains days without alerts; grouping keeps multiple alerts from duplicating productive minutes when Kotlin sums XP and walks consecutive clean days.

**Screen:** Home: player XP, clean-day streaks, daily quests, and badges.

## Top rot app and its optional limit

**Constants:** `SELECT_TODAY_TOP_APP_LIMIT`

```sql
SELECT r.package_name, r.label, r.minutes, r.rot_weight, l.daily_limit_min
FROM v_app_today_rank AS r
LEFT JOIN limits AS l ON l.package_name = r.package_name
WHERE r.log_date = ? AND r.rot_weight > 0
ORDER BY r.minutes * r.rot_weight DESC, r.package_name ASC
LIMIT 1;
```

Selects today’s positive-weight app with the largest minutes times weight, along with its daily limit when one exists.  
Home completes the quest when minutes are at or below that limit, and shows “Set a limit” when the LEFT JOIN returns a NULL limit.

**Screen:** Home: daily top-app-limit quest.

**Parameters:** today’s date (`yyyy-MM-dd`).

## Limits with today’s usage in one query

**Constants:** `SELECT_ALL_APPS_TODAY`

```sql
SELECT a.package_name, a.label, a.category_id, c.name AS category_name,
       a.rot_weight, l.daily_limit_min, COALESCE(u.minutes, 0) AS today_minutes
FROM apps AS a
JOIN categories AS c ON c.id = a.category_id
LEFT JOIN limits AS l ON l.package_name = a.package_name
LEFT JOIN usage_log AS u ON u.package_name = a.package_name AND u.log_date = ?
ORDER BY a.label ASC, a.package_name ASC;
```

Returns every seeded app with its category, optional daily limit, and today’s minutes in one query.  
The date stays in the LEFT JOIN condition so missing usage becomes zero and apps without a limit still appear.

**Screen:** Limits: usage-versus-limit progress bars and limit pills.

**Parameters:** today’s date (`yyyy-MM-dd`).

## Selected-app blocking limit

**Constants:** `SELECT_BLOCKING_LIMIT`

```sql
SELECT a.label, l.daily_limit_min
FROM limits AS l
JOIN apps AS a ON a.package_name = l.package_name
WHERE l.package_name = ?;
```

Returns the app label and current daily limit for one package; a missing limit returns no row and allows access.  
The service checks user selection, the master switch, and real foreground minutes before returning to Home; selection is stored in SharedPreferences.

**Screen:** Limits configures selection and limits; the Accessibility Service enforces them when a selected app opens.

**Parameters:** the selected app’s package name.
