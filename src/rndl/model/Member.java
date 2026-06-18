package rndl.model;

import java.util.ArrayList;
import java.util.List;

public class Member {
    private String id;
    private String name;
    private List<Book> borrowedBooks;

    // Constructors
    public Member() {
        this.borrowedBooks = new ArrayList<>();
    }

    public Member(String id, String name) {
        this.id = id;
        this.name = name;
        this.borrowedBooks = new ArrayList<>();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Book> getBorrowedBooks() {
        return new ArrayList<>(borrowedBooks); // Return a copy for encapsulation
    }

    public void setBorrowedBooks(List<Book> borrowedBooks) {
        this.borrowedBooks = borrowedBooks != null ? new ArrayList<>(borrowedBooks) : new ArrayList<>();
    }

    // Helper methods to borrow and return books
    public void borrowBook(Book book) {
        if (borrowedBooks.size() >= 5) {
            throw new IllegalStateException("Member cannot borrow more than 5 books.");
        }
        if (!borrowedBooks.contains(book)) {
            borrowedBooks.add(book);
        }
    }

    public void returnBook(Book book) {
        borrowedBooks.remove(book);
    }

    @Override
    public String toString() {
        return "Member{" +
                "ID='" + id + '\'' +
                ", Name='" + name + '\'' +
                ", BorrowedBooksCount=" + borrowedBooks.size() +
                '}';
    }
}
