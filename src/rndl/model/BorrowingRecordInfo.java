package rndl.model;

import java.time.LocalDate;

public record BorrowingRecordInfo(
    int recordId,
    String memberId,
    String memberName,
    String isbn,
    String bookTitle,
    LocalDate borrowDate,
    LocalDate returnDate
) {}
