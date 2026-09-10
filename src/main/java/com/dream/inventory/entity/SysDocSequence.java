package com.dream.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDate;

@Entity
@Table(name = "sys_doc_sequence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(SysDocSequence.SysDocSequenceId.class)
public class SysDocSequence {

    @Id
    @Column(length = 8)
    private String prefix;

    @Id
    @Column(name = "biz_date")
    private LocalDate bizDate;

    @Column(name = "current_seq", nullable = false)
    @Builder.Default
    private Integer currentSeq = 0;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class SysDocSequenceId implements Serializable {
        private String prefix;
        private LocalDate bizDate;
    }
}
