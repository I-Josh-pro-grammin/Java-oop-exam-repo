package rndl.concurrent;

import rndl.service.LibraryService;

public class BorrowTask implements Runnable {
    private final LibraryService libraryService;
    private final String memberId;
    private final String isbn;
    private final String librarianName;

    public BorrowTask(LibraryService libraryService, String memberId, String isbn, String librarianName) {
        this.libraryService = libraryService;
        this.memberId = memberId;
        this.isbn = isbn;
        this.librarianName = librarianName;
    }

    @Override
    public void run() {
        System.out.println("⏳ [" + librarianName + "] is processing borrowing request for Member " + memberId + " and Book ISBN " + isbn);
        
        // Simulating some processing delay before attempting checkout
        try {
            Thread.sleep((long) (Math.random() * 200));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[" + librarianName + "] was interrupted.");
            return;
        }

        boolean success = libraryService.borrowBook(memberId, isbn);
        
        if (success) {
            System.out.println("✨ [" + librarianName + "] successfully completed request for Member " + memberId);
        } else {
            System.out.println("❌ [" + librarianName + "] failed to process request for Member " + memberId);
        }
    }
}
