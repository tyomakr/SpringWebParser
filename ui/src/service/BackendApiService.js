import axios from "axios";
import storeFI from "../store/storeFI";

// const API_URL = 'http://localhost:8111/api/v1/'
const API_URL = '/api/v1/'
const API_IMAGES_URL = API_URL + 'sites/fishki/images/'
const API_IMAGES_URL_SUFFIX = '/to/'

class BackendApiService {

    getWebImagesOnPages(num1, num2) {
        return axios.get(API_IMAGES_URL + num1 + API_IMAGES_URL_SUFFIX + num2);
    }

    saveAndPublishSelectedImages(images) {
        return axios.post(API_IMAGES_URL, images)
    }
}

export default new BackendApiService();