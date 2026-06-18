package rndl;

import rndl.db.DatabaseManager;
import rndl.model.Book;
import rndl.model.Member;
import rndl.service.LibraryService;
import rndl.concurrent.BorrowTask;
import rndl.utils.ValidationUtils;

import java.util.List;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) {
        System.out.println("==================================================");
        System.out.println(" Rwanda National Digital Library (RNDL) System ");
        System.out.println("==================================================");

        // Initialize Database Schema (Creates tables if they don't exist, preserving existing data)
        DatabaseManager.initializeSchema();

        LibraryService libraryService = new LibraryService();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.println("\n=================== MAIN MENU ===================");
            System.out.println("1. Register a New Book");
            System.out.println("2. Register a New Member");
            System.out.println("3. Borrow a Book");
            System.out.println("4. Return a Book");
            System.out.println("5. List All Available Books");
            System.out.println("6. List All Borrowed (Unavailable) Books");
            System.out.println("7. List All Registered Members");
            System.out.println("8. List All Borrowing Records (History)");
            System.out.println("9. Run Concurrent borrowing Simulations");
            System.out.println("10. Clear Everything to start again");
            System.out.println("11. Exit");
            System.out.print("Choose an option (1-11): ");

            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1":
                    registerBookFlow(libraryService, scanner);
                    break;
                case "2":
                    registerMemberFlow(libraryService, scanner);
                    break;
                case "3": 
                    borrowBookFlow(libraryService, scanner);
                    break;
                case "4":
                    returnBookFlow(libraryService, scanner);
                    break;
                case "5":
                    printAvailableBooks(libraryService);
                    break;
                case "6":
                    printUnavailableBooks(libraryService);
                    break;
                case "7":
                    printMembers(libraryService);
                    break;
                case "8":
                    printAllBorrowingRecords(libraryService);
                    break;
                case "9":
                    runSimulations(libraryService);
                    break;
                case "10":
                    clearDatabase();
                    break;
                case "11":
                    System.out.println("Exiting RNDL System. Goodbye!");
                    scanner.close();
                    System.exit(0);
                    break;
                default:
                    System.out.println("❌ Invalid option. Please enter a number between 1 and 11.");
            }
        }
    }

    private static void registerBookFlow(LibraryService service, Scanner scanner) {
        System.out.println("\n--- Register a New Book ---");
        System.out.print("Enter ISBN: ");
        String isbn = scanner.nextLine().trim();
        if (!ValidationUtils.isValidISBN(isbn)) {
            System.out.println("❌ Invalid ISBN format. (Must be a valid ISBN-10 or ISBN-13 with standard checksum).");
            return;
        }

        System.out.print("Enter Title: ");
        String title = scanner.nextLine().trim();
        if (!ValidationUtils.isValidTitle(title)) {
            System.out.println("❌ Invalid Title. Cannot be empty and must contain alphanumeric characters.");
            return;
        }

        System.out.print("Enter Author: ");
        String author = scanner.nextLine().trim();
        if (!ValidationUtils.isValidAuthor(author)) {
            System.out.println("❌ Invalid Author Name. (Must contain letters and cannot contain numbers or special characters).");
            return;
        }

        System.out.print("Enter Publication Year: ");
        int year;
        try {
            year = Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("❌ Invalid year format. Registration cancelled.");
            return;
        }
        if (!ValidationUtils.isValidPublicationYear(year)) {
            System.out.println("❌ Invalid Publication Year. (Must be between 1000 and the current calendar year).");
            return;
        }

        Book book = new Book(isbn, title, author, year);
        service.registerBook(book);
    }

    private static void registerMemberFlow(LibraryService service, Scanner scanner) {
        System.out.println("\n--- Register a New Member ---");
        System.out.print("Enter Member ID: ");
        String id = scanner.nextLine().trim();
        if (!ValidationUtils.isValidMemberId(id)) {
            System.out.println("❌ Invalid Member ID. (Only alphanumeric characters and hyphens allowed).");
            return;
        }

        System.out.print("Enter Name: ");
        String name = scanner.nextLine().trim();
        if (!ValidationUtils.isValidMemberName(name)) {
            System.out.println("❌ Invalid Name. (Must contain letters and cannot contain numbers or special characters).");
            return;
        }

        Member member = new Member(id, name);
        service.registerMember(member);
    }

    private static void borrowBookFlow(LibraryService service, Scanner scanner) {
        System.out.println("\n--- Borrow a Book ---");
        System.out.print("Enter Member ID: ");
        String memberId = scanner.nextLine().trim();
        System.out.print("Enter Book ISBN: ");
        String isbn = scanner.nextLine().trim();

        if (!ValidationUtils.isValidMemberId(memberId)) {
            System.out.println("❌ Invalid Member ID format.");
            return;
        }
        if (!ValidationUtils.isValidISBN(isbn)) {
            System.out.println("❌ Invalid ISBN format.");
            return;
        }

        service.borrowBook(memberId, isbn);
    }

    private static void returnBookFlow(LibraryService service, Scanner scanner) {
        System.out.println("\n--- Return a Book ---");
        System.out.print("Enter Member ID: ");
        String memberId = scanner.nextLine().trim();
        System.out.print("Enter Book ISBN: ");
        String isbn = scanner.nextLine().trim();

        if (!ValidationUtils.isValidMemberId(memberId)) {
            System.out.println("❌ Invalid Member ID format.");
            return;
        }
        if (!ValidationUtils.isValidISBN(isbn)) {
            System.out.println("❌ Invalid ISBN format.");
            return;
        }

        service.returnBook(memberId, isbn);
    }

    private static void printAvailableBooks(LibraryService service) {
        System.out.println("\n--- Current Available Books in RNDL ---");
        List<Book> available = service.listAvailableBooks();
        int count = available.size();
        System.out.println("Total available books: " + count);
        
        if (available.isEmpty()) {
            System.out.println("No books are currently available.");
        } else {
            for (Book book : available) {
                System.out.println(book.getPublicationYear() + " | " + book.getTitle() + " by " + book.getAuthor() + " (ISBN: " + book.getIsbn() + ")");
            }
        }
    }

    private static void printUnavailableBooks(LibraryService service) {
        System.out.println("\n--- Current Borrowed (Unavailable) Books in RNDL ---");
        List<rndl.model.UnavailableBookInfo> unavailable = service.listUnavailableBooksWithBorrowers();
        int count = unavailable.size();
        System.out.println("Total borrowed books: " + count);

        if (unavailable.isEmpty()) {
            System.out.println("No books are currently borrowed.");
        } else {
            for (rndl.model.UnavailableBookInfo info : unavailable) {
                System.out.println(info.book().getPublicationYear() + " | " + info.book().getTitle() + " by " + info.book().getAuthor() + " (ISBN: " + info.book().getIsbn() + ")");
                String returnDateStr = info.returnDate() != null ? info.returnDate().toString() : "Pending";
                System.out.println("   ➡️ Borrowed by: " + info.memberName() + " (ID: " + info.memberId() + ") on " + info.borrowDate() + " [Return Date: " + returnDateStr + "]");
            }
        }
    }

    private static void printMembers(LibraryService service) {
        System.out.println("\n--- Registered Members in RNDL ---");
        List<Member> members = service.listMembers();
        int count = members.size();
        System.out.println("Total members: " + count);

        if (members.isEmpty()) {
            System.out.println("No members are currently registered.");
        } else {
            for (Member m : members) {
                System.out.println("👤 " + m.getName() + " (ID: " + m.getId() + ")");
                List<Book> borrowed = m.getBorrowedBooks();
                if (borrowed.isEmpty()) {
                    System.out.println("   (No currently borrowed books)");
                } else {
                    System.out.println("   Borrowed books (" + borrowed.size() + "):");
                    for (Book b : borrowed) {
                        System.out.println("     - " + b.getTitle() + " (ISBN: " + b.getIsbn() + ")");
                    }
                }
            }
        }
    }

    private static void printAllBorrowingRecords(LibraryService service) {
        System.out.println("\n--- All Borrowing Records (History) in RNDL ---");
        List<rndl.model.BorrowingRecordInfo> records = service.listAllBorrowingRecords();
        int count = records.size();
        System.out.println("Total borrowing transactions: " + count);

        if (records.isEmpty()) {
            System.out.println("No borrowing records exist.");
        } else {
            for (rndl.model.BorrowingRecordInfo r : records) {
                String returnDateStr = r.returnDate() != null ? r.returnDate().toString() : "Pending";
                System.out.println("[" + r.recordId() + "] Member: " + r.memberName() + " (ID: " + r.memberId() + ")");
                System.out.println("    Book: " + r.bookTitle() + " (ISBN: " + r.isbn() + ")");
                System.out.println("    Borrow Date: " + r.borrowDate() + " | Return Date: " + returnDateStr);
                System.out.println("----------------------------------------------");
            }
        }
    }

    private static void clearDatabase() {
        System.out.println("\nClearing Database (Starting from Scratch)...");
        DatabaseManager.clearDatabase();
        DatabaseManager.initializeSchema();
        System.out.println("Database cleared successfully. All tables are empty.");
    }

    private static void seedDemoData(LibraryService libraryService) {
        Book b1 = new Book("978-0134685991", "Effective Java", "Joshua Bloch", 2018);
        Book b2 = new Book("978-0596009205", "Head First Design Patterns", "Eric Freeman", 2004);
        Book b3 = new Book("978-0132350884", "Clean Code", "Robert C. Martin", 2008);
        Book b4 = new Book("978-0134494166", "Java Concurrency in Practice", "Brian Goetz", 2006);
        Book b5 = new Book("978-0321356680", "Introduction to Algorithms", "Thomas H. Cormen", 2009);
        Book b6 = new Book("978-0131103627", "The C Programming Language", "Brian W. Kernighan", 1988);
        Book b7 = new Book("978-0201633610", "Design Patterns", "Erich Gamma", 1994);

        System.out.println("\n--- Seeding Books ---");
        libraryService.registerBook(b1);
        libraryService.registerBook(b2);
        libraryService.registerBook(b3);
        libraryService.registerBook(b4);
        libraryService.registerBook(b5);
        libraryService.registerBook(b6);
        libraryService.registerBook(b7);

        Member m1 = new Member("M001", "Kagame Jean");
        Member m2 = new Member("M002", "Mutoni Aline");
        Member m3 = new Member("M003", "Rwandarushya Eric");

        System.out.println("\n--- Seeding Members ---");
        libraryService.registerMember(m1);
        libraryService.registerMember(m2);
        libraryService.registerMember(m3);
        
        System.out.println("\nSeeding completed successfully.");
    }

    private static void runSimulations(LibraryService libraryService) {
        // Run seed first to make sure there's clean data for the scenarios
        clearDatabase();
        seedDemoData(libraryService);

        printAvailableBooks(libraryService);

        System.out.println("\n=== SCENARIO 1: Concurrent Borrowing of the Same Book (Effective Java) ===");
        ExecutorService scenario1Executor = Executors.newFixedThreadPool(3);
        scenario1Executor.submit(new BorrowTask(libraryService, "M001", "978-0134685991", "Librarian-1"));
        scenario1Executor.submit(new BorrowTask(libraryService, "M002", "978-0134685991", "Librarian-2"));
        scenario1Executor.submit(new BorrowTask(libraryService, "M003", "978-0134685991", "Librarian-3"));
        shutdownExecutor(scenario1Executor);

        printAvailableBooks(libraryService);

        System.out.println("\n=== SCENARIO 2: Enforcing 5-Book Limit for Member Mutoni Aline (M002) ===");
        ExecutorService scenario2Executor = Executors.newFixedThreadPool(4);
        String[] isbnsToBorrow = {
            "978-0596009205",
            "978-0132350884",
            "978-0134494166",
            "978-0321356680",
            "978-0131103627",
            "978-0201633610"
        };
        for (int i = 0; i < isbnsToBorrow.length; i++) {
            scenario2Executor.submit(new BorrowTask(libraryService, "M002", isbnsToBorrow[i], "Librarian-A" + (i + 1)));
        }
        shutdownExecutor(scenario2Executor);

        Member memberM002 = libraryService.getMember("M002");
        System.out.println("\nMember Status for M002:");
        if (memberM002 != null) {
            System.out.println("Name: " + memberM002.getName());
            System.out.println("Borrowed Books Count: " + memberM002.getBorrowedBooks().size());
            for (Book b : memberM002.getBorrowedBooks()) {
                System.out.println(" - " + b.getTitle() + " (" + b.getIsbn() + ")");
            }
        }

        printAvailableBooks(libraryService);

        System.out.println("\n=== SCENARIO 3: Returning a Book and Borrowing Again ===");
        if (memberM002 != null && !memberM002.getBorrowedBooks().isEmpty()) {
            Book bookToReturn = memberM002.getBorrowedBooks().get(0);
            System.out.println("🔄 M002 returning: " + bookToReturn.getTitle());
            boolean returnSuccess = libraryService.returnBook("M002", bookToReturn.getIsbn());
            
            if (returnSuccess) {
                String failedIsbn = "";
                for (String isbn : isbnsToBorrow) {
                    boolean isAlreadyBorrowed = false;
                    for (Book b : memberM002.getBorrowedBooks()) {
                        if (b.getIsbn().equals(isbn)) {
                            isAlreadyBorrowed = true;
                            break;
                        }
                    }
                    if (!isAlreadyBorrowed) {
                        failedIsbn = isbn;
                        break;
                    }
                }
                if (!failedIsbn.isEmpty()) {
                    Book bookToBorrowAgain = libraryService.getBook(failedIsbn);
                    System.out.println("🔄 M002 trying to borrow '" + (bookToBorrowAgain != null ? bookToBorrowAgain.getTitle() : failedIsbn) + "' now that they have a slot...");
                    libraryService.borrowBook("M002", failedIsbn);
                }
            }
        }

        memberM002 = libraryService.getMember("M002");
        System.out.println("\nFinal Member Status for M002:");
        if (memberM002 != null) {
            System.out.println("Borrowed Books Count: " + memberM002.getBorrowedBooks().size());
            for (Book b : memberM002.getBorrowedBooks()) {
                System.out.println(" - " + b.getTitle() + " (" + b.getIsbn() + ")");
            }
        }

        printAvailableBooks(libraryService);
    }

    private static void shutdownExecutor(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
