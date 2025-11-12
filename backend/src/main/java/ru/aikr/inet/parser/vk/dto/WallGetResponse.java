package ru.aikr.inet.parser.vk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WallGetResponse {

    private Response response;

    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Response {

        private List<WallPost> items;

        public List<WallPost> getItems() {
            return items;
        }

        public void setItems(List<WallPost> items) {
            this.items = items;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WallPost {

        private Long id;
        private Long date;
        private List<Attachment> attachments;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Long getDate() {
            return date;
        }

        public void setDate(Long date) {
            this.date = date;
        }

        public List<Attachment> getAttachments() {
            return attachments;
        }

        public void setAttachments(List<Attachment> attachments) {
            this.attachments = attachments;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attachment {

        private String type;
        private Photo photo;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public Photo getPhoto() {
            return photo;
        }

        public void setPhoto(Photo photo) {
            this.photo = photo;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Photo {

        private List<PhotoSize> sizes;

        public List<PhotoSize> getSizes() {
            return sizes;
        }

        public void setSizes(List<PhotoSize> sizes) {
            this.sizes = sizes;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PhotoSize {

        private String url;
        private Integer width;
        private Integer height;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public Integer getWidth() {
            return width;
        }

        public void setWidth(Integer width) {
            this.width = width;
        }

        public Integer getHeight() {
            return height;
        }

        public void setHeight(Integer height) {
            this.height = height;
        }
    }
}
