package rndl.service;

import rndl.db.DatabaseManager;
import rndl.model.Book;
import rndl.model.Member;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class LibraryService {

    public boolean registerBook(Book book) {
        String query = "INSERT INTO books (isbn, title, author, publication_year, available) " +
                       "VALUES (?, ?, ?, ?, ?) " +
                       "ON CONFLICT (isbn) DO NOTHING;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, book.getIsbn());
            pstmt.setString(2, book.getTitle());
            pstmt.setString(3, book.getAuthor());
            pstmt.setInt(4, book.getPublicationYear());
            pstmt.setBoolean(5, book.isAvailable());
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Registered book: " + book.getTitle() + " (ISBN: " + book.getIsbn() + ")");
                return true;
            } else {
                System.out.println("❌ Registration Failed: A book with ISBN " + book.getIsbn() + " already exists.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error registering book: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public boolean registerMember(Member member) {
        String query = "INSERT INTO members (member_id, name) " +
                       "VALUES (?, ?) " +
                       "ON CONFLICT (member_id) DO NOTHING;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, member.getId());
            pstmt.setString(2, member.getName());
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Registered member: " + member.getName() + " (ID: " + member.getId() + ")");
                return true;
            } else {
                System.out.println("❌ Registration Failed: A member with ID " + member.getId() + " already exists.");
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Error registering member: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public Member getMember(String memberId) {
        String memberQuery = "SELECT name FROM members WHERE member_id = ?;";
        String booksQuery = "SELECT b.isbn, b.title, b.author, b.publication_year, b.available " +
                            "FROM books b " +
                            "JOIN borrowing_records r ON b.isbn = r.isbn " +
                            "WHERE r.member_id = ? AND r.return_date IS NULL;";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement memberPstmt = conn.prepareStatement(memberQuery);
             PreparedStatement booksPstmt = conn.prepareStatement(booksQuery)) {
            
            memberPstmt.setString(1, memberId);
            try (ResultSet rs = memberPstmt.executeQuery()) {
                if (rs.next()) {
                    Member member = new Member(memberId, rs.getString("name"));
                    
                    booksPstmt.setString(1, memberId);
                    try (ResultSet booksRs = booksPstmt.executeQuery()) {
                        List<Book> borrowed = new ArrayList<>();
                        while (booksRs.next()) {
                            borrowed.add(new Book(
                                    booksRs.getString("isbn"),
                                    booksRs.getString("title"),
                                    booksRs.getString("author"),
                                    booksRs.getInt("publication_year"),
                                    booksRs.getBoolean("available")
                            ));
                        }
                        member.setBorrowedBooks(borrowed);
                    }
                    return member;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching member " + memberId + ": " + e.getMessage());
        }
        return null;
    }

    public Book getBook(String isbn) {
        String query = "SELECT isbn, title, author, publication_year, available FROM books WHERE isbn = ?;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setString(1, isbn);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Book(
                            rs.getString("isbn"),
                            rs.getString("title"),
                            rs.getString("author"),
                            rs.getInt("publication_year"),
                            rs.getBoolean("available")
                    );
                }
            }
        } catch (SQLException e) {
            System.err.println("Error fetching book " + isbn + ": " + e.getMessage());
        }
        return null;
    }

    public List<Book> listAvailableBooks() {
        List<Book> availableBooks = new ArrayList<>();
        String query = "SELECT b.isbn, b.title, b.author, b.publication_year " +
                       "FROM books b " +
                       "WHERE b.isbn NOT IN ( " +
                       "    SELECT r.isbn FROM borrowing_records r WHERE r.return_date IS NULL " +
                       ") " +
                       "ORDER BY b.title;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                availableBooks.add(new Book(
                        rs.getString("isbn"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getInt("publication_year"),
                        true // It is available since it passed the NOT IN filter
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error listing available books: " + e.getMessage());
        }
        return availableBooks;
    }

    /**
     * Borrow a book for a member.
     * Thread-safety: Synchronized on isbn.intern() to serialize borrow requests for the SAME book.
     * Database transaction ensures atomic check-and-update.
     */
    public boolean borrowBook(String memberId, String isbn) {
        // String.intern() ensures that we synchronize on the same unique object for the same ISBN string.
        synchronized (isbn.intern()) {
            Connection conn = null;
            PreparedStatement lockBookStmt = null;
            PreparedStatement checkMemberStmt = null;
            PreparedStatement checkBorrowedStmt = null;
            PreparedStatement countBorrowedStmt = null;
            PreparedStatement insertRecordStmt = null;
            PreparedStatement updateBookStmt = null;

            try {
                conn = DatabaseManager.getConnection();
                // Start transaction
                conn.setAutoCommit(false);

                // 1. Lock the book row in 'books' to serialize database updates for this book
                String lockBookQuery = "SELECT title FROM books WHERE isbn = ? FOR UPDATE;";
                lockBookStmt = conn.prepareStatement(lockBookQuery);
                lockBookStmt.setString(1, isbn);
                
                String bookTitle = "";
                try (ResultSet rs = lockBookStmt.executeQuery()) {
                    if (rs.next()) {
                        bookTitle = rs.getString("title");
                    } else {
                        System.out.println("❌ Borrow Failed: Book with ISBN " + isbn + " does not exist.");
                        conn.rollback();
                        return false;
                    }
                }

                // 1b. Check if the member exists in the database
                String checkMemberQuery = "SELECT name FROM members WHERE member_id = ?;";
                checkMemberStmt = conn.prepareStatement(checkMemberQuery);
                checkMemberStmt.setString(1, memberId);
                
                boolean memberExists = false;
                try (ResultSet rs = checkMemberStmt.executeQuery()) {
                    if (rs.next()) {
                        memberExists = true;
                    }
                }

                if (!memberExists) {
                    System.out.println("❌ Borrow Failed: Member with ID " + memberId + " does not exist.");
                    conn.rollback();
                    return false;
                }

                // 2. Check if the book is currently borrowed (active record in borrowing_records)
                String checkBorrowedQuery = "SELECT 1 FROM borrowing_records WHERE isbn = ? AND return_date IS NULL;";
                checkBorrowedStmt = conn.prepareStatement(checkBorrowedQuery);
                checkBorrowedStmt.setString(1, isbn);
                
                boolean isAlreadyBorrowed = false;
                try (ResultSet rs = checkBorrowedStmt.executeQuery()) {
                    if (rs.next()) {
                        isAlreadyBorrowed = true;
                    }
                }

                if (isAlreadyBorrowed) {
                    System.out.println("❌ Borrow Failed: Book '" + bookTitle + "' (ISBN: " + isbn + ") is already borrowed.");
                    conn.rollback();
                    return false;
                }

                // 3. Check if the member already has 5 borrowed books
                String countBorrowedQuery = "SELECT COUNT(*) FROM borrowing_records WHERE member_id = ? AND return_date IS NULL;";
                countBorrowedStmt = conn.prepareStatement(countBorrowedQuery);
                countBorrowedStmt.setString(1, memberId);
                
                int borrowedCount = 0;
                try (ResultSet rs = countBorrowedStmt.executeQuery()) {
                    if (rs.next()) {
                        borrowedCount = rs.getInt(1);
                    }
                }

                if (borrowedCount >= 5) {
                    System.out.println("❌ Borrow Failed: Member " + memberId + " has already borrowed " + borrowedCount + " books (limit: 5). You must atleast return one to borrow another");
                    conn.rollback();
                    return false;
                }

                // 4. Record borrowing transaction
                String insertRecordQuery = "INSERT INTO borrowing_records (member_id, isbn, borrow_date) VALUES (?, ?, CURRENT_DATE);";
                insertRecordStmt = conn.prepareStatement(insertRecordQuery);
                insertRecordStmt.setString(1, memberId);
                insertRecordStmt.setString(2, isbn);
                insertRecordStmt.executeUpdate();

                // 5. Update books.available (keeps column in sync with prompt requirement)
                String updateBookQuery = "UPDATE books SET available = FALSE WHERE isbn = ?;";
                updateBookStmt = conn.prepareStatement(updateBookQuery);
                updateBookStmt.setString(1, isbn);
                updateBookStmt.executeUpdate();

                // Commit transaction
                conn.commit();
                System.out.println("✅ Borrow Success: Member " + memberId + " borrowed '" + bookTitle + "' (ISBN: " + isbn + ")");
                return true;

            } catch (SQLException e) {
                System.err.println("Database error during borrow operations: " + e.getMessage());
                if (conn != null) {
                    try {
                        System.out.println("Rolling back transaction...");
                        conn.rollback();
                    } catch (SQLException ex) {
                        System.err.println("Rollback failed: " + ex.getMessage());
                    }
                }
                return false;
            } finally {
                // Ensure resources are closed
                try { if (lockBookStmt != null) lockBookStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (checkMemberStmt != null) checkMemberStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (checkBorrowedStmt != null) checkBorrowedStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (countBorrowedStmt != null) countBorrowedStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (insertRecordStmt != null) insertRecordStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (updateBookStmt != null) updateBookStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (conn != null) conn.close(); } catch (SQLException e) { /* ignore */ }
            }
        }
    }

    /**
     * Return a book.
     * Thread-safety: Synchronized on isbn.intern().
     */
    public boolean returnBook(String memberId, String isbn) {
        synchronized (isbn.intern()) {
            Connection conn = null;
            PreparedStatement selectRecordStmt = null;
            PreparedStatement updateRecordStmt = null;
            PreparedStatement updateBookStmt = null;

            try {
                conn = DatabaseManager.getConnection();
                conn.setAutoCommit(false);

                // 1. Locate the active borrowing record and lock it
                String selectRecordQuery = "SELECT record_id FROM borrowing_records WHERE member_id = ? AND isbn = ? AND return_date IS NULL LIMIT 1 FOR UPDATE;";
                selectRecordStmt = conn.prepareStatement(selectRecordQuery);
                selectRecordStmt.setString(1, memberId);
                selectRecordStmt.setString(2, isbn);
                
                int recordId = -1;
                try (ResultSet rs = selectRecordStmt.executeQuery()) {
                    if (rs.next()) {
                        recordId = rs.getInt("record_id");
                    }
                }

                if (recordId == -1) {
                    System.out.println("❌ Return Failed: No active borrowing record found for member " + memberId + " and ISBN " + isbn);
                    conn.rollback();
                    return false;
                }

                // 2. Update return_date
                String updateRecordQuery = "UPDATE borrowing_records SET return_date = CURRENT_DATE WHERE record_id = ?;";
                updateRecordStmt = conn.prepareStatement(updateRecordQuery);
                updateRecordStmt.setInt(1, recordId);
                updateRecordStmt.executeUpdate();

                // 3. Mark book as available again
                String updateBookQuery = "UPDATE books SET available = TRUE WHERE isbn = ?;";
                updateBookStmt = conn.prepareStatement(updateBookQuery);
                updateBookStmt.setString(1, isbn);
                updateBookStmt.executeUpdate();

                conn.commit();
                
                Book book = getBook(isbn);
                String bookTitle = book != null ? book.getTitle() : "";
                System.out.println("✅ Return Success: Member " + memberId + " returned '" + bookTitle + "' (ISBN: " + isbn + ")");
                return true;

            } catch (SQLException e) {
                System.err.println("Database error during return operations: " + e.getMessage());
                if (conn != null) {
                    try {
                        System.out.println("Rolling back transaction...");
                        conn.rollback();
                    } catch (SQLException ex) {
                        System.err.println("Rollback failed: " + ex.getMessage());
                    }
                }
                return false;
            } finally {
                try { if (selectRecordStmt != null) selectRecordStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (updateRecordStmt != null) updateRecordStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (updateBookStmt != null) updateBookStmt.close(); } catch (SQLException e) { /* ignore */ }
                try { if (conn != null) conn.close(); } catch (SQLException e) { /* ignore */ }
            }
        }
    }

    public List<rndl.model.UnavailableBookInfo> listUnavailableBooksWithBorrowers() {
        List<rndl.model.UnavailableBookInfo> list = new ArrayList<>();
        String query = "SELECT b.isbn, b.title, b.author, b.publication_year, b.available, " +
                       "       m.member_id, m.name AS member_name, r.borrow_date, r.return_date " +
                       "FROM books b " +
                       "JOIN borrowing_records r ON b.isbn = r.isbn " +
                       "JOIN members m ON r.member_id = m.member_id " +
                       "WHERE r.return_date IS NULL " +
                       "ORDER BY b.title;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                Book book = new Book(
                        rs.getString("isbn"),
                        rs.getString("title"),
                        rs.getString("author"),
                        rs.getInt("publication_year"),
                        rs.getBoolean("available")
                );
                java.sql.Date sqlBorrowDate = rs.getDate("borrow_date");
                java.time.LocalDate borrowDate = sqlBorrowDate != null ? sqlBorrowDate.toLocalDate() : null;
                
                java.sql.Date sqlReturnDate = rs.getDate("return_date");
                java.time.LocalDate returnDate = sqlReturnDate != null ? sqlReturnDate.toLocalDate() : null;
                
                list.add(new rndl.model.UnavailableBookInfo(
                        book,
                        rs.getString("member_id"),
                        rs.getString("member_name"),
                        borrowDate,
                        returnDate
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error listing unavailable books with borrowers: " + e.getMessage());
        }
        return list;
    }

    public List<Member> listMembers() {
        List<Member> membersList = new ArrayList<>();
        String query = "SELECT m.member_id, m.name AS member_name, b.isbn, b.title, b.author, b.publication_year, b.available " +
                       "FROM members m " +
                       "LEFT JOIN borrowing_records r ON m.member_id = r.member_id AND r.return_date IS NULL " +
                       "LEFT JOIN books b ON r.isbn = b.isbn " +
                       "ORDER BY m.name, b.title;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            
            Member currentMember = null;
            List<Book> currentBooks = new ArrayList<>();
            
            while (rs.next()) {
                String memberId = rs.getString("member_id");
                
                if (currentMember == null || !currentMember.getId().equals(memberId)) {
                    if (currentMember != null) {
                        currentMember.setBorrowedBooks(currentBooks);
                    }
                    currentMember = new Member(memberId, rs.getString("member_name"));
                    membersList.add(currentMember);
                    currentBooks = new ArrayList<>();
                }
                
                String isbn = rs.getString("isbn");
                if (isbn != null) {
                    currentBooks.add(new Book(
                            isbn,
                            rs.getString("title"),
                            rs.getString("author"),
                            rs.getInt("publication_year"),
                            rs.getBoolean("available")
                    ));
                }
            }
            if (currentMember != null) {
                currentMember.setBorrowedBooks(currentBooks);
            }
            
        } catch (SQLException e) {
            System.err.println("Error listing members: " + e.getMessage());
        }
        return membersList;
    }

    public List<rndl.model.BorrowingRecordInfo> listAllBorrowingRecords() {
        List<rndl.model.BorrowingRecordInfo> list = new ArrayList<>();
        String query = "SELECT r.record_id, m.member_id, m.name AS member_name, b.isbn, b.title AS book_title, r.borrow_date, r.return_date " +
                       "FROM borrowing_records r " +
                       "JOIN members m ON r.member_id = m.member_id " +
                       "JOIN books b ON r.isbn = b.isbn " +
                       "ORDER BY r.record_id DESC;";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                java.sql.Date sqlBorrowDate = rs.getDate("borrow_date");
                java.time.LocalDate borrowDate = sqlBorrowDate != null ? sqlBorrowDate.toLocalDate() : null;
                
                java.sql.Date sqlReturnDate = rs.getDate("return_date");
                java.time.LocalDate returnDate = sqlReturnDate != null ? sqlReturnDate.toLocalDate() : null;
                
                list.add(new rndl.model.BorrowingRecordInfo(
                        rs.getInt("record_id"),
                        rs.getString("member_id"),
                        rs.getString("member_name"),
                        rs.getString("isbn"),
                        rs.getString("book_title"),
                        borrowDate,
                        returnDate
                ));
            }
        } catch (SQLException e) {
            System.err.println("Error listing borrowing history records: " + e.getMessage());
        }
        return list;
    }
}
