# Student Grade Management System

A small console application that stores students and their marks in **MySQL**
and calculates grades. No GUI — just a menu in the terminal.

```
===============================================
      STUDENT GRADE MANAGEMENT SYSTEM
===============================================
Connected to MySQL database 'college'.

---------------- MENU ----------------
 1. Add a student
 2. View all students
 3. Search a student
 4. Update a student
 5. Delete a student
 6. Class report
 0. Exit
```

## Files

| File | Purpose |
|------|---------|
| `StudentGradeManagement.java` | The console app (JDBC, no frameworks) |
| `main.sql` | Database schema, views and sample data |
| `db.properties` | MySQL connection settings (change these) |
| `mysql-connector-j-9.2.0.jar` | MySQL JDBC driver |
| `run.bat` | Compiles and starts the app on Windows |

## Requirements

- JDK 8 or newer (tested on JDK 27)
- MySQL 8.0 (tested on 8.0.46)

## Setup

**1. Create the database** (run once, from this folder):

```
mysql -u root -p < main.sql
```

This creates the `college` database, the `student` table, three views and
eight sample rows.

**2. Check the connection settings** in `db.properties`:

```properties
db.url=jdbc:mysql://localhost:3306/college?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC
db.user=root
db.password=your_password_here
```

**3. Compile and run:**

```
run.bat
```

Or by hand:

```
javac -cp ".;mysql-connector-j-9.2.0.jar" StudentGradeManagement.java
java  -cp ".;mysql-connector-j-9.2.0.jar" StudentGradeManagement
```

(On Linux/macOS replace the `;` in the classpath with `:`.)

## Database design

```
student (table)
------------------------------
id          INT, auto increment, primary key
name        VARCHAR(100), not null
marks       INT, 0-100 (enforced by a CHECK constraint)
created_at  TIMESTAMP, defaults to now
```

The grade is **not** stored — it is derived from `marks`, so it can never get
out of sync. It is calculated in two places, which use the same scale:

- `StudentGradeManagement.gradeFor(int marks)` in Java
- the `v_grade_report` view in `main.sql`

| Marks | Grade |
|-------|-------|
| 90-100 | A+ |
| 80-89 | A |
| 70-79 | B |
| 60-69 | C |
| 50-59 | D |
| 0-49 | F (FAIL) |

### Views

| View | What it returns |
|------|-----------------|
| `v_grade_report` | every student with grade and PASS/FAIL |
| `v_class_stats` | count, average, highest, lowest, passed, failed |
| `v_grade_distribution` | how many students earned each grade |

Try them in the `mysql` client:

```sql
USE college;
SELECT * FROM v_grade_report ORDER BY marks DESC;
SELECT * FROM v_class_stats;
```

## Notes

- All SQL uses `PreparedStatement`, so names containing quotes cannot break
  the queries or inject SQL.
- Input is validated: marks must be 0-100, names cannot be empty, and
  non-numeric input is re-prompted instead of crashing.
- Re-running `main.sql` is safe: it uses `IF NOT EXISTS` / `INSERT IGNORE`,
  so it will not drop your data. To clear all students: `DELETE FROM student;`
- `db.password` is stored in plain text, which is fine for a local study
  project — do not commit real credentials for a real system.
