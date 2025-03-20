package ru.aikr.inet.parser.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import ru.aikr.inet.parser.model.WebImage;
import ru.aikr.inet.parser.service.VKPublishService;
import ru.aikr.inet.parser.service.WebImageService;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class FishkiRestControllerTest {

    @Mock
    private WebImageService webImageService;

    @Mock
    private VKPublishService vkPublishService;

    @InjectMocks
    private FishkiRestController fishkiRestController;

    private MockMvc mockMvc;


    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(fishkiRestController)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8) // Важно!
                .build();
    }

    @Test
    void shouldReturnImagesFromPages() throws Exception {
        List<WebImage> mockImages = Collections.singletonList(new WebImage("test_url"));
        when(webImageService.getImagesFromPages(1, 5)).thenReturn(mockImages);

        mockMvc.perform(get("/api/v1/sites/fishki/images/1/to/5")
                        .characterEncoding("UTF-8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].directLink").value("test_url"));
    }

    @Test
    void shouldPublishImages() throws Exception {
        when(vkPublishService.generatePostsAndPublishToCommunityWall(any())).thenReturn(true);

        mockMvc.perform(post("/api/v1/sites/fishki/images/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"directLink\":\"test_url\"}]"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON + ";charset=UTF-8")) // Проверка типа и кодировки
                .andExpect(content().string("Опубликовано 1 изображений"));
    }
}