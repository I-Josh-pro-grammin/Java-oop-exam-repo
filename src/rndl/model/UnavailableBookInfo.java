package rndl.model;

import java.time.LocalDate;

public record UnavailableBookInfo(
    Book book,
    String memberId,
    String memberName,
    LocalDate borrowDate,
    LocalDate returnDate
) {}
