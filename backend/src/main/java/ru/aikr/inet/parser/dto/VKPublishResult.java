package ru.aikr.inet.parser.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Результат публикации изображений во ВКонтакте
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VKPublishResult {
    /**
     * Количество успешно загруженных изображений
     */
    private int uploadedCount;
    
    /**
     * Количество успешно опубликованных изображений
     */
    private int publishedCount;
    
    /**
     * Количество постов, которые были успешно опубликованы
     */
    private int postsPublished;
    
    /**
     * Количество постов, которые не удалось опубликовать (но изображения загружены)
     */
    private int postsFailed;
    
    /**
     * Общее количество обработанных изображений
     */
    private int totalProcessed;
    
    /**
     * Сообщение об ошибках (если есть)
     */
    private String errorMessage;
    
    public boolean isSuccess() {
        return publishedCount > 0 && postsFailed == 0;
    }
    
    public boolean isPartialSuccess() {
        return uploadedCount > 0 && publishedCount < uploadedCount;
    }
    
    @Override
    public String toString() {
        return String.format(
            "VKPublishResult{uploaded=%d, published=%d, posts=%d/%d, total=%d}",
            uploadedCount, publishedCount, postsPublished, postsPublished + postsFailed, totalProcessed
        );
    }
}

