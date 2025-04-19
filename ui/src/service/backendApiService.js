import axios from "axios";

// Используем переменную окружения или путь по умолчанию
const BASE_URL = process.env.REACT_APP_API_URL || 'http://localhost:8111';
const API_URL = `${BASE_URL}/api/v1/`;
const API_IMAGES_URL = `${API_URL}sites/fishki/images/`;

const backendApiService = {
    getWebImagesOnPages(num1, num2) {
        return axios.get(`${API_IMAGES_URL}${num1}/to/${num2}`);
    },

    saveAndPublishSelectedImages(images) {
        return axios.post(API_IMAGES_URL, images);
    }
};

export default backendApiService;