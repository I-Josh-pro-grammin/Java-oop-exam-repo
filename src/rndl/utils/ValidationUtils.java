package rndl.utils;

import java.time.Year;

public class ValidationUtils {

    /**
     * Validates ISBN-10 or ISBN-13 format and checksum.
     */
    public static boolean isValidISBN(String isbn) {
        if (isbn == null) return false;
        
        // Remove hyphens and spaces
        String clean = isbn.replaceAll("[\\s\\-]", "");

        if (clean.length() == 10) {
            return isValidISBN10(clean);
        } else if (clean.length() == 13) {
            return isValidISBN13(clean);
        }
        return false;
    }

    private static boolean isValidISBN10(String isbn) {
        if (!isbn.matches("^\\d{9}[\\dXdx]$")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += (isbn.charAt(i) - '0') * (10 - i);
        }
        char lastChar = isbn.charAt(9);
        if (lastChar == 'X' || lastChar == 'x') {
            sum += 10;
        } else {
            sum += (lastChar - '0');
        }
        return sum % 11 == 0;
    }

    private static boolean isValidISBN13(String isbn) {
        if (!isbn.matches("^\\d{13}$")) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            int digit = isbn.charAt(i) - '0';
            sum += (i % 2 == 0) ? digit : digit * 3;
        }
        return sum % 10 == 0;
    }

    /**
     * Validates a book title.
     * Cannot be empty and must contain at least one letter or digit.
     */
    public static boolean isValidTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return false;
        }
        // Ensure it contains letters or digits, not just special characters
        return title.matches(".*[a-zA-Z0-9].*");
    }

    /**
     * Validates an author's name.
     * Cannot contain numbers or arbitrary special characters. Must contain letters.
     */
    public static boolean isValidAuthor(String author) {
        if (author == null || author.trim().isEmpty()) {
            return false;
        }
        // Permitted: letters, spaces, dots, hyphens, and apostrophes
        return author.matches("^[a-zA-Z\\s\\.\\-\\']+$") && author.matches(".*[a-zA-Z].*");
    }

    /**
     * Validates publication year.
     * Must be between 1000 and the current calendar year.
     */
    public static boolean isValidPublicationYear(int year) {
        int currentYear = Year.now().getValue();
        return year >= 1000 && year <= currentYear;
    }

    /**
     * Validates member name.
     * Permitted: letters, spaces, dots, hyphens, and apostrophes. No numbers.
     */
    public static boolean isValidMemberName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return false;
        }
        return name.matches("^[a-zA-Z\\s\\.\\-\\']+$") && name.matches(".*[a-zA-Z].*");
    }

    /**
     * Validates member ID.
     * Alphanumeric with optional hyphens (e.g., M001 or M-99).
     */
    public static boolean isValidMemberId(String id) {
        if (id == null || id.trim().isEmpty()) {
            return false;
        }
        return id.matches("^[a-zA-Z0-9\\-]+$");
    }
}
