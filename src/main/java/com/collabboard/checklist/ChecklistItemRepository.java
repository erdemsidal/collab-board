package com.collabboard.checklist;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ChecklistItemRepository extends JpaRepository<ChecklistItem, Long> {

    /** Listenin maddeleri, sırasıyla. */
    List<ChecklistItem> findByChecklistIdOrderByPositionAscIdAsc(Long checklistId);

    /** Birden çok listenin maddeleri, tek sorguda (kart penceresi açılınca). */
    List<ChecklistItem> findByChecklistIdInOrderByPositionAscIdAsc(Collection<Long> checklistIds);

    long countByChecklistId(Long checklistId);
}
