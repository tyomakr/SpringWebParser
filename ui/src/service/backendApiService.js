import axios from "axios";

/**
 * origin = http://localhost:3333   (точно там же, где отдан index.html)
 * в контейнере будет http://frontend:80 – браузеру это прозрачно.
 */
const API_ROOT = `${window.location.origin}/api/v1/`;

const API_IMAGES_URL        = `${API_ROOT}sites/fishki/images/`;
const API_IMAGES_URL_SUFFIX = "/to/";

// Создаем инстанс axios с настройками для правильной обработки всех статусов 2xx
const apiClient = axios.create({
    validateStatus: function (status) {
        // Считаем успешными все статусы от 200 до 299 (включая 202 ACCEPTED)
        return status >= 200 && status < 300;
    },
});

const backendApiService = {
    getWebImagesOnPages(num1, num2) {
        return apiClient.get(`${API_IMAGES_URL}${num1}${API_IMAGES_URL_SUFFIX}${num2}`);
    },
    saveAndPublishSelectedImages(images) {
        return apiClient.post(API_IMAGES_URL, images);
    },
};

export default backendApiService;