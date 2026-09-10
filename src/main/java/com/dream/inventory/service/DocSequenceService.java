package com.dream.inventory.service;

import com.dream.inventory.entity.SysDocSequence;
import com.dream.inventory.repository.SysDocSequenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
public class DocSequenceService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SysDocSequenceRepository docSequenceRepository;

    @Transactional(rollbackFor = Exception.class)
    public String nextNo(String prefix) {
        LocalDate bizDate = LocalDate.now(ZoneOffset.UTC);
        SysDocSequence seq = docSequenceRepository.findForUpdate(prefix, bizDate)
                .orElseGet(() -> docSequenceRepository.save(SysDocSequence.builder()
                        .prefix(prefix)
                        .bizDate(bizDate)
                        .currentSeq(0)
                        .build()));
        int next = seq.getCurrentSeq() + 1;
        seq.setCurrentSeq(next);
        docSequenceRepository.save(seq);
        return prefix + bizDate.format(DATE_FMT) + String.format("%06d", next);
    }
}
