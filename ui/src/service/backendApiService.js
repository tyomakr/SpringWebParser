import axios from "axios";

/**
 * origin = http://localhost:3333   (точно там же, где отдан index.html)
 * в контейнере будет http://frontend:80 – браузеру это прозрачно.
 */
const API_ROOT = `${window.location.origin}/api/v1/`;

const API_IMAGES_URL        = `${API_ROOT}sites/fishki/images/`;
const API_IMAGES_URL_SUFFIX = "/to/";

const backendApiService = {
    getWebImagesOnPages(num1, num2) {
        return axios.get(`${API_IMAGES_URL}${num1}${API_IMAGES_URL_SUFFIX}${num2}`);
    },
    saveAndPublishSelectedImages(images) {
        return axios.post(API_IMAGES_URL, images);
    },
};

export default backendApiService;