package rndl.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DatabaseManager {
    private static final String URL = "jdbc:postgresql://localhost:5432/rndl_library";
    private static final String USER = "postgres";
    private static final String PASSWORD = "100aistuff";

    static {
        try {
            // Load the PostgreSQL JDBC driver explicitly
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC driver not found: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    public static void initializeSchema() {
        String createBooksTable = "CREATE TABLE IF NOT EXISTS books (" +
                "isbn VARCHAR(20) PRIMARY KEY, " +
                "title VARCHAR(255) NOT NULL, " +
                "author VARCHAR(255) NOT NULL, " +
                "publication_year INT, " +
                "available BOOLEAN DEFAULT TRUE" +
                ");";

        String createMembersTable = "CREATE TABLE IF NOT EXISTS members (" +
                "member_id VARCHAR(50) PRIMARY KEY, " +
                "name VARCHAR(255) NOT NULL" +
                ");";

        String createBorrowingRecordsTable = "CREATE TABLE IF NOT EXISTS borrowing_records (" +
                "record_id SERIAL PRIMARY KEY, " +
                "member_id VARCHAR(50) REFERENCES members(member_id) ON DELETE CASCADE, " +
                "isbn VARCHAR(20) REFERENCES books(isbn) ON DELETE CASCADE, " +
                "borrow_date DATE NOT NULL, " +
                "return_date DATE, " +
                "CONSTRAINT chk_dates CHECK (return_date >= borrow_date)" +
                ");";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            
            System.out.println("Initializing database schema...");
            stmt.execute(createBooksTable);
            stmt.execute(createMembersTable);
            stmt.execute(createBorrowingRecordsTable);
            System.out.println("Schema initialization completed successfully.");
            
        } catch (SQLException e) {
            System.err.println("Error initializing schema: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void clearDatabase() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            System.out.println("Cleaning up database tables...");
            stmt.execute("DROP TABLE IF EXISTS borrowing_records CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS members CASCADE;");
            stmt.execute("DROP TABLE IF EXISTS books CASCADE;");
            System.out.println("Cleaned database tables.");
        } catch (SQLException e) {
            System.err.println("Error clearing database: " + e.getMessage());
        }
    }
}
