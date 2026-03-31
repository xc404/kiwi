package com.kiwi.project.notification;

import com.kiwi.project.notification.dao.NotificationMessageDao;
import com.kiwi.project.notification.dto.NotificationDto;
import com.kiwi.project.notification.model.NotificationMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationMessageDao notificationMessageDao;

    public List<NotificationDto> listForUser(String userId) {
        return notificationMessageDao.findByUserIdOrderByCreatedTimeDesc(userId).stream()
                .map(this::toDto)
                .toList();
    }



    private NotificationDto toDto(NotificationMessage m) {
        NotificationDto dto = new NotificationDto();
        dto.setId(m.getId());
        dto.setChannel(m.getChannel());
        dto.setTitle(m.getTitle());
        dto.setSummary(m.getSummary());
        dto.setRead(Boolean.TRUE.equals(m.getRead()));
        dto.setExtra(m.getExtra());
        if (m.getCreatedTime() != null) {
            dto.setCreatedAt(m.getCreatedTime().toInstant().toString());
        } else {
            dto.setCreatedAt(Instant.now().toString());
        }
        if (m.getTagText() != null && !m.getTagText().isEmpty()) {
            NotificationDto.TagDto tag = new NotificationDto.TagDto();
            tag.setText(m.getTagText());
            tag.setColor(m.getTagColor() != null ? m.getTagColor() : "default");
            dto.setTag(tag);
        }
        return dto;
    }
}
