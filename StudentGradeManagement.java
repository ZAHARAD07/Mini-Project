import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Scanner;

/**
 * Student Grade Management System - console edition.
 *
 * Stores students in MySQL (schema: main.sql) and offers a small menu to
 * add, view, search, update and delete students plus a class report.
 *
 * Build & run (from this folder):
 *     javac -cp ".;mysql-connector-j-9.2.0.jar" StudentGradeManagement.java
 *     java  -cp ".;mysql-connector-j-9.2.0.jar" StudentGradeManagement
 *
 * Connection settings live in db.properties.
 */
public class StudentGradeManagement {

    private static final String CONFIG_FILE = "db.properties";
    private static final int NAME_MAX = 100;

    private static String dbUrl;
    private static String dbUser;
    private static String dbPassword;

    public static void main(String[] args) {
        banner();

        try {
            loadConfig();
        } catch (IOException e) {
            System.err.println("Could not load " + CONFIG_FILE + ": " + e.getMessage());
            return;
        }

        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPassword);
             Scanner sc = new Scanner(System.in)) {

            System.out.printf("Connected to MySQL database '%s'.%n", conn.getCatalog());
            menuLoop(conn, sc);

        } catch (SQLException e) {
            System.err.println("Could not reach the database: " + e.getMessage());
            System.err.println("Check " + CONFIG_FILE + " and make sure MySQL is running.");
            System.err.println("Also make sure the schema is loaded:  mysql -u root -p < main.sql");
        }
    }

    // ---------------------------------------------------------- setup

    private static void banner() {
        System.out.println("===============================================");
        System.out.println("      STUDENT GRADE MANAGEMENT SYSTEM");
        System.out.println("===============================================");
    }

    private static void loadConfig() throws IOException {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
        }
        dbUrl = props.getProperty("db.url");
        dbUser = props.getProperty("db.user", "root");
        dbPassword = props.getProperty("db.password", "");

        if (dbUrl == null || dbUrl.isBlank()) {
            throw new IOException("missing 'db.url' entry in " + CONFIG_FILE);
        }
    }

    // ---------------------------------------------------------- menu

    private static void menuLoop(Connection conn, Scanner sc) {
        boolean running = true;
        while (running) {
            try {
                System.out.println();
                System.out.println("---------------- MENU ----------------");
                System.out.println(" 1. Add a student");
                System.out.println(" 2. View all students");
                System.out.println(" 3. Search a student");
                System.out.println(" 4. Update a student");
                System.out.println(" 5. Delete a student");
                System.out.println(" 6. Class report");
                System.out.println(" 0. Exit");

                int choice = readInt(sc, "Choose an option: ");
                System.out.println();

                switch (choice) {
                    case 1 -> addStudent(conn, sc);
                    case 2 -> viewStudents(conn);
                    case 3 -> searchStudent(conn, sc);
                    case 4 -> updateStudent(conn, sc);
                    case 5 -> deleteStudent(conn, sc);
                    case 6 -> classReport(conn);
                    case 0 -> {
                        running = false;
                        System.out.println("Goodbye!");
                    }
                    default -> System.out.println("Please choose one of the options shown.");
                }
            } catch (EndOfInputException e) {
                System.out.println();
                System.out.println("No more input - goodbye!");
                return;
            }
        }
    }

    // ---------------------------------------------------------- CRUD

    private static void addStudent(Connection conn, Scanner sc) {
        System.out.println("--- Add student ---");
        String name = readName(sc, "Student name : ");
        int marks = readMarks(sc, "Marks (0-100) : ");

        String sql = "INSERT INTO student (name, marks) VALUES (?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setInt(2, marks);
            ps.executeUpdate();

            System.out.printf("Saved: %s | Marks: %d | Grade: %s | %s%n",
                    name, marks, gradeFor(marks), marks >= 50 ? "PASS" : "FAIL");
        } catch (SQLException e) {
            System.err.println("Could not add student: " + e.getMessage());
        }
    }

    private static void viewStudents(Connection conn) {
        String sql = "SELECT id, name, marks FROM student ORDER BY marks DESC, name ASC";
        try (PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println("--- All students (best marks first) ---");
            printTableHeader();
            boolean anyRow = false;
            while (rs.next()) {
                anyRow = true;
                printRow(rs.getInt("id"), rs.getString("name"), rs.getInt("marks"));
            }
            if (!anyRow) {
                System.out.println("  (no students yet - pick option 1 to add one)");
            }
        } catch (SQLException e) {
            System.err.println("Could not read students: " + e.getMessage());
        }
    }

    private static void searchStudent(Connection conn, Scanner sc) {
        String term = readLine(sc, "Enter a student ID or part of a name: ");
        if (term.isEmpty()) {
            System.out.println("  Nothing entered.");
            return;
        }

        Integer id = tryParseInt(term);
        String sql = id != null
                ? "SELECT id, name, marks FROM student WHERE id = ?"
                : "SELECT id, name, marks FROM student WHERE name LIKE ? ORDER BY name";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (id != null) {
                ps.setInt(1, id);
            } else {
                ps.setString(1, "%" + term + "%");
            }

            try (ResultSet rs = ps.executeQuery()) {
                printTableHeader();
                boolean anyRow = false;
                while (rs.next()) {
                    anyRow = true;
                    printRow(rs.getInt("id"), rs.getString("name"), rs.getInt("marks"));
                }
                if (!anyRow) {
                    System.out.println("  No student matched \"" + term + "\".");
                }
            }
        } catch (SQLException e) {
            System.err.println("Search failed: " + e.getMessage());
        }
    }

    private static void updateStudent(Connection conn, Scanner sc) {
        String input = readLine(sc, "Enter the student ID to update: ");
        Integer id = tryParseInt(input);
        if (id == null) {
            System.out.println("  Please enter a numeric ID.");
            return;
        }

        String selectSql = "SELECT name, marks FROM student WHERE id = ?";
        String currentName;
        int currentMarks;

        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("  No student with ID " + id + ".");
                    return;
                }
                currentName = rs.getString("name");
                currentMarks = rs.getInt("marks");
            }
        } catch (SQLException e) {
            System.err.println("Could not read student: " + e.getMessage());
            return;
        }

        System.out.printf("Current: %s | Marks: %d | Grade: %s%n",
                currentName, currentMarks, gradeFor(currentMarks));

        String nameInput = readLine(sc,
                String.format("New name [%s] (press Enter to keep): ", currentName));
        String name = nameInput.isEmpty() ? currentName : nameInput;
        if (name.length() > NAME_MAX) {
            System.out.println("  Name is too long (max " + NAME_MAX + "), keeping the old name.");
            name = currentName;
        }

        int marks = readOptionalMarks(sc,
                String.format("New marks [%d] (press Enter to keep): ", currentMarks), currentMarks);

        String updateSql = "UPDATE student SET name = ?, marks = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
            ps.setString(1, name);
            ps.setInt(2, marks);
            ps.setInt(3, id);
            int updated = ps.executeUpdate();

            if (updated > 0) {
                System.out.printf("Updated: %s | Marks: %d | Grade: %s | %s%n",
                        name, marks, gradeFor(marks), marks >= 50 ? "PASS" : "FAIL");
            } else {
                System.out.println("  Nothing was updated.");
            }
        } catch (SQLException e) {
            System.err.println("Could not update student: " + e.getMessage());
        }
    }

    private static void deleteStudent(Connection conn, Scanner sc) {
        Integer id = tryParseInt(readLine(sc, "Enter the student ID to delete: "));
        if (id == null) {
            System.out.println("  Please enter a numeric ID.");
            return;
        }

        String selectSql = "SELECT name, marks FROM student WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    System.out.println("  No student with ID " + id + ".");
                    return;
                }
                System.out.printf("Found: %s | Marks: %d | Grade: %s%n",
                        rs.getString("name"), rs.getInt("marks"), gradeFor(rs.getInt("marks")));
            }
        } catch (SQLException e) {
            System.err.println("Could not read student: " + e.getMessage());
            return;
        }

        String answer = readLine(sc, "Delete this student? (y/n): ");
        if (!answer.equalsIgnoreCase("y") && !answer.equalsIgnoreCase("yes")) {
            System.out.println("Cancelled - nothing was deleted.");
            return;
        }

        String deleteSql = "DELETE FROM student WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
            ps.setInt(1, id);
            int deleted = ps.executeUpdate();
            System.out.println(deleted > 0
                    ? "Deleted student " + id + "."
                    : "  Nothing was deleted.");
        } catch (SQLException e) {
            System.err.println("Could not delete student: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------- report

    private static void classReport(Connection conn) {
        System.out.println("---------------- CLASS REPORT ----------------");

        String statsSql = "SELECT total_students, average_marks, highest_marks, lowest_marks, "
                + "passed, failed FROM v_class_stats";
        int total = 0;
        try (PreparedStatement ps = conn.prepareStatement(statsSql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                total = rs.getInt("total_students");
                System.out.printf("  Students : %d%n", total);
                System.out.printf("  Average  : %.2f%n", rs.getDouble("average_marks"));
                System.out.printf("  Highest  : %d%n", rs.getInt("highest_marks"));
                System.out.printf("  Lowest   : %d%n", rs.getInt("lowest_marks"));
                System.out.printf("  Passed   : %d%n", rs.getInt("passed"));
                System.out.printf("  Failed   : %d%n", rs.getInt("failed"));
            }
        } catch (SQLException e) {
            System.err.println("Could not build report: " + e.getMessage());
            System.err.println("(Did you load main.sql?  mysql -u root -p < main.sql)");
            return;
        }

        if (total == 0) {
            System.out.println("  (no students yet - pick option 1 to add one)");
            return;
        }

        String distSql = "SELECT grade, student_count FROM v_grade_distribution";
        try (PreparedStatement ps = conn.prepareStatement(distSql);
             ResultSet rs = ps.executeQuery()) {

            System.out.println();
            System.out.println("  Grade distribution");
            System.out.println("  " + "-".repeat(40));
            while (rs.next()) {
                String grade = rs.getString("grade");
                int count = rs.getInt("student_count");
                System.out.printf("  %-3s | %-20s %d%n", grade, "#".repeat(count), count);
            }
            System.out.println("  " + "-".repeat(40));
        } catch (SQLException e) {
            System.err.println("Could not build grade distribution: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------- helpers

    /** Same scale as the v_grade_report view in main.sql - keep both in sync. */
    private static String gradeFor(int marks) {
        if (marks >= 90) return "A+";
        if (marks >= 80) return "A";
        if (marks >= 70) return "B";
        if (marks >= 60) return "C";
        if (marks >= 50) return "D";
        return "F";
    }

    private static int readInt(Scanner sc, String prompt) {
        while (true) {
            String line = readLine(sc, prompt);
            Integer value = tryParseInt(line);
            if (value != null) {
                return value;
            }
            System.out.println("  Please enter a whole number.");
        }
    }

    private static int readMarks(Scanner sc, String prompt) {
        while (true) {
            int marks = readInt(sc, prompt);
            if (marks >= 0 && marks <= 100) {
                return marks;
            }
            System.out.println("  Marks must be between 0 and 100.");
        }
    }

    /** Blank input keeps the current value. */
    private static int readOptionalMarks(Scanner sc, String prompt, int current) {
        while (true) {
            String line = readLine(sc, prompt);
            if (line.isEmpty()) {
                return current;
            }
            Integer value = tryParseInt(line);
            if (value == null) {
                System.out.println("  Please enter a whole number.");
            } else if (value < 0 || value > 100) {
                System.out.println("  Marks must be between 0 and 100.");
            } else {
                return value;
            }
        }
    }

    private static String readName(Scanner sc, String prompt) {
        while (true) {
            String name = readLine(sc, prompt);
            if (name.isEmpty()) {
                System.out.println("  Name cannot be empty.");
            } else if (name.length() > NAME_MAX) {
                System.out.println("  Name is too long (max " + NAME_MAX + " characters).");
            } else {
                return name;
            }
        }
    }

    /** Prints the prompt, returns one trimmed line, and fails fast when input ends. */
    private static String readLine(Scanner sc, String prompt) {
        System.out.print(prompt);
        if (!sc.hasNextLine()) {
            throw new EndOfInputException();
        }
        return sc.nextLine().trim();
    }

    /** Raised when stdin runs out (piped input finished, or the user pressed Ctrl+Z / Ctrl+D). */
    private static class EndOfInputException extends RuntimeException {
        EndOfInputException() {
            super("end of input");
        }
    }

    /** Returns null when the text is not a valid integer. */
    private static Integer tryParseInt(String text) {
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void printTableHeader() {
        System.out.printf("  %-4s  %-30s  %5s  %-4s  %s%n", "ID", "NAME", "MARKS", "GR.", "RESULT");
        System.out.println("  " + "-".repeat(60));
    }

    private static void printRow(int id, String name, int marks) {
        System.out.printf("  %-4d  %-30s  %5d  %-4s  %s%n",
                id, name, marks, gradeFor(marks), marks >= 50 ? "PASS" : "FAIL");
    }
}
