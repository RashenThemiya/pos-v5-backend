package com.pos.system.repository;

import com.pos.system.model.sale.CreditNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CreditNoteRepository extends JpaRepository<CreditNote, Long> {
    boolean existsByCreditNoteNo(String creditNoteNo);
    Optional<CreditNote> findByReturnId(Long returnId);
}
