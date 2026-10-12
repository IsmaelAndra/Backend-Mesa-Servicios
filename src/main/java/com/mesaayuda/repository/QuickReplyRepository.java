package com.mesaayuda.repository;

import com.mesaayuda.entity.QuickReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface QuickReplyRepository extends JpaRepository<QuickReply, UUID> {

    /** Las propias y las compartidas, por título. */
    @Query("SELECT q FROM QuickReply q WHERE q.deletedQuickReply = false "
            + "AND (q.sharedQuickReply = true OR q.ownerQuickReply.idUser = :usuario) "
            + "ORDER BY lower(q.titleQuickReply)")
    List<QuickReply> visiblesPara(@Param("usuario") UUID usuario);
}
