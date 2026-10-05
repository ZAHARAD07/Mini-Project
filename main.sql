-- ============================================================
--  Student Grade Management System - Database Schema
--  Engine: MySQL 8.0
--
--  Load it with:
--      mysql -u root -p < main.sql
--  or from the mysql client:
--      mysql> source main.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS college;
USE college;

-- ------------------------------------------------------------
-- Table: student
--   One row per student. The grade is NOT stored, it is
--   derived from marks (see view v_grade_report below) so the
--   grade can never drift out of sync with the marks.
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS student (
    id         INT AUTO_INCREMENT PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    marks      INT          NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- MySQL 8.0.16+ enforces CHECK constraints
    CONSTRAINT chk_marks CHECK (marks BETWEEN 0 AND 100),

    -- speeds up "search by name"
    INDEX idx_student_name (name)
);

-- ------------------------------------------------------------
-- View: every student with the grade calculated in SQL.
--   The same scale is used by StudentGradeManagement.java
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_grade_report AS
SELECT id,
       name,
       marks,
       CASE
           WHEN marks >= 90 THEN 'A+'
           WHEN marks >= 80 THEN 'A'
           WHEN marks >= 70 THEN 'B'
           WHEN marks >= 60 THEN 'C'
           WHEN marks >= 50 THEN 'D'
           ELSE 'F'
       END AS grade,
       IF(marks >= 50, 'PASS', 'FAIL') AS result
FROM student;

-- ------------------------------------------------------------
-- View: one row of class statistics (safe on an empty table).
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_class_stats AS
SELECT COUNT(*)                          AS total_students,
       COALESCE(ROUND(AVG(marks), 2), 0) AS average_marks,
       COALESCE(MAX(marks), 0)           AS highest_marks,
       COALESCE(MIN(marks), 0)           AS lowest_marks,
       COALESCE(SUM(marks >= 50), 0)     AS passed,
       COALESCE(SUM(marks < 50), 0)      AS failed
FROM student;

-- ------------------------------------------------------------
-- View: how many students earned each grade.
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_grade_distribution AS
SELECT grade,
       COUNT(*) AS student_count
FROM v_grade_report
GROUP BY grade
ORDER BY FIELD(grade, 'A+', 'A', 'B', 'C', 'D', 'F');

-- ------------------------------------------------------------
-- Sample data (optional).
--   INSERT IGNORE skips rows whose id already exists, so
--   re-running this script will not create duplicates.
--   Remove everything:  DELETE FROM student;
-- ------------------------------------------------------------
INSERT IGNORE INTO student (id, name, marks) VALUES
    (1, 'Aarav Sharma',   92),
    (2, 'Diya Patel',     78),
    (3, 'Rohan Gupta',    65),
    (4, 'Ananya Singh',   88),
    (5, 'Kabir Mehta',    54),
    (6, 'Ishita Rao',     97),
    (7, 'Vivaan Verma',   41),
    (8, 'Sara Khan',      73);

-- ------------------------------------------------------------
-- Handy queries to try in the mysql client
-- ------------------------------------------------------------
-- SELECT * FROM v_grade_report ORDER BY marks DESC;
-- SELECT * FROM v_class_stats;
-- SELECT * FROM v_grade_distribution;
-- SELECT * FROM v_grade_report WHERE result = 'FAIL';
-- SELECT name, marks FROM student WHERE marks >= 90 ORDER BY marks DESC;
-- SELECT COUNT(*) AS distinc_grades FROM (SELECT DISTINCT grade FROM v_grade_report) g;
